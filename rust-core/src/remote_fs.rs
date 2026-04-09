use crate::models::{Folder, FolderInspection, Photo, SortOrder};
use futures::StreamExt;
use opendal::Operator;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Instant;

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
        let started = Instant::now();
        let mut lister = op.lister(path).await.map_err(|e| e.to_string())?;
        let mut candidates = HashMap::<String, FolderAggregate>::new();

        while let Some(entry) = lister.next().await {
            let entry = entry.map_err(|e| e.to_string())?;
            let path_str = entry.path();
            if is_hidden_path(path_str) { continue; }
            if path_str.contains("__MACOSX") { continue; }

            if entry.path().ends_with('/') && entry.path() != path {
                let key = folder_key(entry.name());
                if key.is_empty() {
                    continue;
                }

                let initial_modified = entry
                    .metadata()
                    .last_modified()
                    .map(|t| t.timestamp() as u64)
                    .unwrap_or(0);

                candidates.insert(
                    key,
                    FolderAggregate {
                        path: entry.path().to_string(),
                        name: entry.name().to_string(),
                        has_sub_folders: false,
                        preview_uris: Vec::new(),
                        date_modified: initial_modified,
                        saw_image: false,
                    },
                );
            }
        }

        if candidates.is_empty() {
            log::info!(
                "list_folders_webdav path={} direct_children=0 visible_folders=0 scanned_entries=0 elapsed_ms={}",
                path,
                started.elapsed().as_millis()
            );
            return Ok(Vec::new());
        }

        let mut scanned_entries = 0usize;
        let mut image_entries = 0usize;
        let mut recursive_lister = op
            .lister_with(path)
            .recursive(true)
            .await
            .map_err(|e| e.to_string())?;

        while let Some(entry) = recursive_lister.next().await {
            let entry = entry.map_err(|e| e.to_string())?;
            let path_str = entry.path();
            if is_hidden_path(path_str) { continue; }
            if path_str.contains("__MACOSX") { continue; }

            let Some(relative_path) = relative_path(path, path_str) else {
                continue;
            };

            scanned_entries += 1;

            let Some(immediate_child) = immediate_child_name(path, path_str) else {
                continue;
            };

            let Some(folder) = candidates.get_mut(&immediate_child) else {
                continue;
            };

            if entry.path().ends_with('/') {
                if relative_path.contains('/') {
                    folder.has_sub_folders = true;
                }
                continue;
            }

            let metadata = entry.metadata();
            if metadata.content_length() == 0 {
                continue;
            }

            let name = file_name(path_str);
            if !is_image_file(name) {
                continue;
            }

            image_entries += 1;
            folder.saw_image = true;

            if let Some(last_modified) = metadata.last_modified().map(|t| t.timestamp() as u64) {
                folder.date_modified = folder.date_modified.max(last_modified);
            }

            if folder.preview_uris.len() < 4 {
                let full_uri = format!(
                    "{}/{}",
                    self.base_url.trim_end_matches('/'),
                    entry.path().trim_start_matches('/')
                );
                folder.preview_uris.push(full_uri);
            }
        }

        let direct_children_count = candidates.len();

        let mut folders: Vec<Folder> = candidates
            .into_values()
            .filter(|folder| folder.saw_image)
            .map(|folder| Folder {
                path: folder.path,
                name: folder.name,
                is_local: false,
                has_sub_folders: folder.has_sub_folders,
                preview_uris: folder.preview_uris,
                date_modified: folder.date_modified,
            })
            .collect();

        folders.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        log::info!(
            "list_folders_webdav path={} direct_children={} visible_folders={} scanned_entries={} image_entries={} elapsed_ms={}",
            path,
            direct_children_count,
            folders.len(),
            scanned_entries,
            image_entries,
            started.elapsed().as_millis()
        );
        Ok(folders)
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

struct FolderAggregate {
    path: String,
    name: String,
    has_sub_folders: bool,
    preview_uris: Vec<String>,
    date_modified: u64,
    saw_image: bool,
}

fn folder_key(name: &str) -> String {
    name.trim_matches('/').to_string()
}

fn file_name(path: &str) -> &str {
    path.trim_matches('/').split('/').last().unwrap_or("")
}

fn relative_path(parent_path: &str, entry_path: &str) -> Option<String> {
    let normalized_entry = entry_path.trim_matches('/');
    if normalized_entry.is_empty() {
        return None;
    }

    let normalized_parent = parent_path.trim_matches('/');
    if normalized_parent.is_empty() {
        return Some(normalized_entry.to_string());
    }

    normalized_entry
        .strip_prefix(normalized_parent)
        .and_then(|rest| rest.strip_prefix('/'))
        .map(|rest| rest.to_string())
}

fn immediate_child_name(parent_path: &str, entry_path: &str) -> Option<String> {
    relative_path(parent_path, entry_path)?
        .split('/')
        .next()
        .filter(|segment| !segment.is_empty())
        .map(str::to_string)
}

fn sort_photos(photos: &mut Vec<Photo>, order: SortOrder) {
    match order {
        SortOrder::NameAsc => photos.sort_by(|a, b| a.title.to_lowercase().cmp(&b.title.to_lowercase())),
        SortOrder::NameDesc => photos.sort_by(|a, b| b.title.to_lowercase().cmp(&a.title.to_lowercase())),
        SortOrder::DateDesc => photos.sort_by(|a, b| b.date_modified.cmp(&a.date_modified)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn photo(title: &str, date_modified: u64) -> Photo {
        Photo {
            id: title.to_string(),
            title: title.to_string(),
            uri: format!("https://example.com/{}", title),
            is_local: false,
            size: 1,
            date_modified,
        }
    }

    #[test]
    fn hidden_path_detects_nested_dot_directories() {
        assert!(is_hidden_path("/visible/.secret/chapter01"));
        assert!(!is_hidden_path("/visible/chapter01"));
    }

    #[test]
    fn image_file_detection_is_case_insensitive() {
        assert!(is_image_file("Cover.JPG"));
        assert!(is_image_file("panel.WebP"));
        assert!(!is_image_file("notes.txt"));
    }

    #[test]
    fn relative_path_handles_root_and_nested_paths() {
        assert_eq!(Some("library/chapter01/page01.jpg".to_string()), relative_path("/", "/library/chapter01/page01.jpg"));
        assert_eq!(Some("chapter01/page01.jpg".to_string()), relative_path("/library", "/library/chapter01/page01.jpg"));
        assert_eq!(None, relative_path("/library", "/other/chapter01/page01.jpg"));
    }

    #[test]
    fn immediate_child_name_returns_first_segment_below_parent() {
        assert_eq!(Some("library".to_string()), immediate_child_name("/", "/library/chapter01/page01.jpg"));
        assert_eq!(Some("chapter01".to_string()), immediate_child_name("/library", "/library/chapter01/page01.jpg"));
        assert_eq!(None, immediate_child_name("/library", "/other/chapter01/page01.jpg"));
    }

    #[test]
    fn sort_photos_supports_all_supported_orders() {
        let mut by_name_asc = vec![photo("beta", 2), photo("Alpha", 1)];
        sort_photos(&mut by_name_asc, SortOrder::NameAsc);
        assert_eq!(vec!["Alpha", "beta"], by_name_asc.iter().map(|it| it.title.as_str()).collect::<Vec<_>>());

        let mut by_name_desc = vec![photo("beta", 2), photo("Alpha", 1)];
        sort_photos(&mut by_name_desc, SortOrder::NameDesc);
        assert_eq!(vec!["beta", "Alpha"], by_name_desc.iter().map(|it| it.title.as_str()).collect::<Vec<_>>());

        let mut by_date_desc = vec![photo("older", 1), photo("newer", 5)];
        sort_photos(&mut by_date_desc, SortOrder::DateDesc);
        assert_eq!(vec!["newer", "older"], by_date_desc.iter().map(|it| it.title.as_str()).collect::<Vec<_>>());
    }
}
