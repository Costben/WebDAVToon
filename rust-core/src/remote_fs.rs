﻿﻿﻿﻿﻿use crate::models::{Folder, FolderInspection, Photo, SortOrder};
use futures::StreamExt;
use opendal::Operator;
use std::sync::Arc;

#[derive(Clone)]
pub enum Protocol {
    WebDav,
}

pub struct RemoteService {
    op: Option<Arc<Operator>>,
    base_url: String,
    protocol: Protocol,
}

impl RemoteService {
    pub fn new_webdav(endpoint: &str, username: &str, password: &str) -> Result<Self, String> {
        let builder = opendal::services::Webdav::default()
            .endpoint(endpoint)
            .username(username)
            .password(password);

        let op = Operator::new(builder)
            .map_err(|e| e.to_string())?
            .finish();

        Ok(Self {
            op: Some(Arc::new(op)),
            base_url: endpoint.to_string(),
            protocol: Protocol::WebDav,
        })
    }

    pub async fn read_file(&self, path: &str) -> Result<Vec<u8>, String> {
        match self.protocol {
            Protocol::WebDav => {
                if let Some(op) = &self.op {
                    op.read(path).await.map(|b| b.to_vec()).map_err(|e| e.to_string())
                } else {
                    Err("WebDAV not initialized".to_string())
                }
            },
        }
    }

    pub async fn delete_file(&self, path: &str) -> Result<(), String> {
        match self.protocol {
            Protocol::WebDav => {
                if let Some(op) = &self.op {
                    op.delete(path).await.map_err(|e| e.to_string())
                } else {
                    Err("WebDAV not initialized".to_string())
                }
            },
        }
    }

    pub async fn list_root_names(&self) -> Result<Vec<String>, String> {
        match self.protocol {
            Protocol::WebDav => self.list_root_names_webdav().await,
        }
    }

    async fn list_root_names_webdav(&self) -> Result<Vec<String>, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        let mut names = Vec::new();
        // List root
        let mut lister = op.lister("/").await.map_err(|e| e.to_string())?;
        
        let mut count = 0;
        while let Some(entry_res) = lister.next().await {
            if count >= 20 { break; } // Limit to 20 items
            if let Ok(entry) = entry_res {
                let name = entry.name().to_string();
                if name.is_empty() || name == "/" { continue; }
                names.push(name);
                count += 1;
            }
        }
        Ok(names)
    }

    pub async fn list_photos(&self, path: &str, sort_order: SortOrder, recursive: bool) -> Result<Vec<Photo>, String> {
        match self.protocol {
            Protocol::WebDav => self.list_photos_webdav(path, sort_order, recursive).await,
        }
    }

    async fn list_photos_webdav(&self, path: &str, sort_order: SortOrder, recursive: bool) -> Result<Vec<Photo>, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        let mut photos = Vec::new();
        
        if recursive {
            let mut lister = op.lister_with(path).recursive(true).await.map_err(|e| e.to_string())?;
            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                if entry.path().ends_with('/') { continue; }
                let name = entry.name();
                if is_hidden_path(entry.path()) || !is_image_file(name) { continue; }
                
                let metadata = entry.metadata();
                let last_modified = metadata.last_modified().map(|t| t.timestamp() as u64).unwrap_or(0);
                
                if metadata.mode().is_file() {
                    let full_uri = format!("{}/{}", self.base_url.trim_end_matches('/'), entry.path().trim_start_matches('/'));
                    photos.push(Photo {
                        id: entry.path().to_string(),
                        title: name.to_string(),
                        uri: full_uri,
                        is_local: false,
                        size: metadata.content_length(),
                        date_modified: last_modified,
                    });
                }
            }
        } else {
            let mut lister = op.lister(path).await.map_err(|e| e.to_string())?;
            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                if entry.path().ends_with('/') { continue; }
                let name = entry.name();
                if is_hidden_path(entry.path()) || !is_image_file(name) { continue; }
                
                let metadata = entry.metadata();
                let last_modified = metadata.last_modified().map(|t| t.timestamp() as u64).unwrap_or(0);
                
                if metadata.mode().is_file() {
                    let full_uri = format!("{}/{}", self.base_url.trim_end_matches('/'), entry.path().trim_start_matches('/'));
                    photos.push(Photo {
                        id: entry.path().to_string(),
                        title: name.to_string(),
                        uri: full_uri,
                        is_local: false,
                        size: metadata.content_length(),
                        date_modified: last_modified,
                    });
                }
            }
        }
        sort_photos(&mut photos, sort_order);
        Ok(photos)
    }

    pub async fn list_folders(&self, path: &str) -> Result<Vec<Folder>, String> {
        match self.protocol {
            Protocol::WebDav => self.list_folders_webdav(path).await,
        }
    }


    async fn list_folders_webdav(&self, path: &str) -> Result<Vec<Folder>, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        // ... (existing WebDAV logic)
        let mut lister = op.lister(path).await.map_err(|e| e.to_string())?;
        let mut candidates = Vec::new();

        while let Some(entry) = lister.next().await {
            let entry = entry.map_err(|e| e.to_string())?;
            let path_str = entry.path();
            if is_hidden_path(path_str) { continue; }
            if path_str.contains("__MACOSX") { continue; }

            if entry.path().ends_with('/') && entry.path() != path {
                 candidates.push((entry.path().to_string(), entry.name().to_string()));
            }
        }
        
        let op_clone = op.clone();
        let base_url = self.base_url.clone();
        let protocol = self.protocol.clone();

        let tasks = futures::stream::iter(candidates)
            .map(|(folder_path, name)| {
                let op = op_clone.clone();
                let base_url = base_url.clone();
                let protocol = protocol.clone();
                async move {
                    if let Some(preview_uris) = Self::check_has_images_webdav(op, &folder_path, &base_url).await {
                         Some(Folder {
                            path: folder_path,
                            name,
                            is_local: false,
                            has_sub_folders: true, 
                            preview_uris,
                        })
                    } else {
                        None
                    }
                }
            })
            .buffer_unordered(4); 

        let mut folders: Vec<Folder> = tasks.filter_map(|opt| async { opt }).collect().await;
        folders.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        Ok(folders)
    }


    async fn check_has_images_webdav(op: Arc<Operator>, folder_path: &str, base_url: &str) -> Option<Vec<String>> {
         // Use recursive=true
         match op.lister_with(folder_path).recursive(true).await {
             Ok(mut lister) => {
                 let mut checks = 0;
                 let mut preview_uris = Vec::new();
                 while let Some(result) = lister.next().await {
                      if checks > 100 { 
                          if !preview_uris.is_empty() { return Some(preview_uris); }
                          return None; 
                      }
                      checks += 1;
                      if let Ok(entry) = result {
                          let path_str = entry.path();
                          if is_hidden_path(path_str) { continue; }
                          if path_str.contains("__MACOSX") { continue; }
                          if !entry.path().ends_with('/') {
                              if entry.metadata().content_length() == 0 { continue; }
                              let name = path_str.trim_matches('/').split('/').last().unwrap_or("");
                              if is_image_file(name) {
                                  let full_uri = format!("{}/{}", base_url.trim_end_matches('/'), entry.path().trim_start_matches('/'));
                                  preview_uris.push(full_uri);
                                  if preview_uris.len() >= 4 { return Some(preview_uris); }
                              }
                          }
                      }
                 }
                 if !preview_uris.is_empty() { Some(preview_uris) } else { None }
             }
             Err(_) => None
         }
    }

    pub async fn inspect_folder(&self, folder_path: &str) -> Result<FolderInspection, String> {
        match self.protocol {
            Protocol::WebDav => self.inspect_folder_webdav(folder_path).await,
        }
    }

    async fn inspect_folder_webdav(&self, folder_path: &str) -> Result<FolderInspection, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        let base_url = &self.base_url;
        let mut has_sub_folders = false;
        let mut preview_uris = Vec::new();
        
        if let Ok(mut lister) = op.lister_with(folder_path).recursive(true).await {
             let mut checks = 0;
             while let Some(Ok(entry)) = lister.next().await {
                 if checks >= 50 { 
                     if !has_sub_folders { has_sub_folders = true; }
                     break; 
                 }
                 checks += 1;
                 
                 let path_str = entry.path();
                 if is_hidden_path(path_str) { continue; }
                 
                 if entry.path().ends_with('/') && entry.path() != folder_path {
                     has_sub_folders = true;
                 }
                 
                 let name = path_str.trim_matches('/').split('/').last().unwrap_or("");
                 if !entry.path().ends_with('/') && is_image_file(name) {
                     let full_uri = format!("{}/{}", base_url.trim_end_matches('/'), entry.path().trim_start_matches('/'));
                     if preview_uris.len() < 4 {
                        preview_uris.push(full_uri);
                     }
                 }
                 if has_sub_folders && preview_uris.len() >= 4 { break; }
             }
        }
        Ok(FolderInspection { has_sub_folders, preview_uris })
    }
}

fn is_hidden_path(path: &str) -> bool {
    path
        .trim_matches('/')
        .split('/')
        .any(|s| s.len() > 1 && s.starts_with('.'))
}

fn is_image_file(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.ends_with(".jpg") || lower.ends_with(".jpeg") || lower.ends_with(".png") || lower.ends_with(".webp") || lower.ends_with(".gif")
}

fn sort_photos(photos: &mut Vec<Photo>, order: SortOrder) {
    match order {
        SortOrder::NameAsc => photos.sort_by(|a, b| a.title.to_lowercase().cmp(&b.title.to_lowercase())),
        SortOrder::NameDesc => photos.sort_by(|a, b| b.title.to_lowercase().cmp(&a.title.to_lowercase())),
        SortOrder::DateDesc => photos.sort_by(|a, b| b.date_modified.cmp(&a.date_modified)),
    }
}
