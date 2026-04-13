use crate::models::{Folder, FolderInspection, MediaType, Photo, SortOrder};
use futures::StreamExt;
use opendal::Operator;
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::Arc;
use std::time::Instant;

const MAX_PREVIEW_SCAN_FOLDERS: usize = 24;
const MAX_PREVIEW_SCAN_ENTRIES: usize = 256;
const MAX_PREVIEW_ROOT_CHILDREN_TO_QUEUE: usize = 24;
const MAX_CONCURRENT_FOLDER_SCANS: usize = 6;
const MAX_ON_DEMAND_PREVIEW_SCAN_ENTRIES: usize = 16384;
const MAX_ON_DEMAND_ROOT_CHILDREN_TO_QUEUE: usize = 2048;

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

        let op = Operator::new(builder).map_err(|e| e.to_string())?.finish();

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
                    op.read(path)
                        .await
                        .map(|b| b.to_vec())
                        .map_err(|e| e.to_string())
                } else {
                    Err("WebDAV not initialized".to_string())
                }
            }
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
            }
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
            if count >= 20 {
                break;
            } // Limit to 20 items
            if let Ok(entry) = entry_res {
                let name = entry.name().to_string();
                if name.is_empty() || name == "/" {
                    continue;
                }
                names.push(name);
                count += 1;
            }
        }
        Ok(names)
    }

    pub async fn list_photos(
        &self,
        path: &str,
        sort_order: SortOrder,
        recursive: bool,
    ) -> Result<Vec<Photo>, String> {
        match self.protocol {
            Protocol::WebDav => self.list_photos_webdav(path, sort_order, recursive).await,
        }
    }

    async fn list_photos_webdav(
        &self,
        path: &str,
        sort_order: SortOrder,
        recursive: bool,
    ) -> Result<Vec<Photo>, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        let mut photos = Vec::new();
        let started = Instant::now();
        let mut scanned_files = 0usize;
        let mut skipped_unsupported = 0usize;
        let mut skipped_hidden = 0usize;
        let mut scanned_directories = 0usize;

        if recursive {
            let mut lister = op
                .lister_with(path)
                .recursive(true)
                .await
                .map_err(|e| e.to_string())?;
            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                if entry.path().ends_with('/') {
                    scanned_directories += 1;
                    continue;
                }
                scanned_files += 1;
                let name = entry.name();
                if is_hidden_path(entry.path()) {
                    skipped_hidden += 1;
                    continue;
                }
                let Some(media_type) = detect_media_type(name) else {
                    skipped_unsupported += 1;
                    continue;
                };

                let metadata = entry.metadata();
                let last_modified = metadata
                    .last_modified()
                    .map(|t| t.timestamp() as u64)
                    .unwrap_or(0);

                if metadata.mode().is_file() {
                    let full_uri = format!(
                        "{}/{}",
                        self.base_url.trim_end_matches('/'),
                        entry.path().trim_start_matches('/')
                    );
                    photos.push(Photo {
                        id: entry.path().to_string(),
                        title: name.to_string(),
                        uri: full_uri,
                        is_local: false,
                        size: metadata.content_length(),
                        date_modified: last_modified,
                        media_type,
                        duration_ms: None,
                    });
                }
            }
        } else {
            let mut lister = op.lister(path).await.map_err(|e| e.to_string())?;
            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                if entry.path().ends_with('/') {
                    scanned_directories += 1;
                    continue;
                }
                scanned_files += 1;
                let name = entry.name();
                if is_hidden_path(entry.path()) {
                    skipped_hidden += 1;
                    continue;
                }
                let Some(media_type) = detect_media_type(name) else {
                    skipped_unsupported += 1;
                    continue;
                };

                let metadata = entry.metadata();
                let last_modified = metadata
                    .last_modified()
                    .map(|t| t.timestamp() as u64)
                    .unwrap_or(0);

                if metadata.mode().is_file() {
                    let full_uri = format!(
                        "{}/{}",
                        self.base_url.trim_end_matches('/'),
                        entry.path().trim_start_matches('/')
                    );
                    photos.push(Photo {
                        id: entry.path().to_string(),
                        title: name.to_string(),
                        uri: full_uri,
                        is_local: false,
                        size: metadata.content_length(),
                        date_modified: last_modified,
                        media_type,
                        duration_ms: None,
                    });
                }
            }
        }
        sort_photos(&mut photos, sort_order);
        log::info!(
            "list_photos_webdav path={} recursive={} scanned_directories={} scanned_files={} matched_media={} skipped_hidden={} skipped_unsupported={} elapsed_ms={}",
            path,
            recursive,
            scanned_directories,
            scanned_files,
            photos.len(),
            skipped_hidden,
            skipped_unsupported,
            started.elapsed().as_millis()
        );
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
        log::info!("list_folders_webdav start path={}", path);
        let mut lister = op.lister(path).await.map_err(|e| e.to_string())?;
        log::info!(
            "list_folders_webdav lister_ready path={} elapsed_ms={}",
            path,
            started.elapsed().as_millis()
        );
        let mut candidates = HashMap::<String, FolderAggregate>::new();
        let mut direct_entries = 0usize;
        let mut direct_files = 0usize;

        while let Some(entry) = lister.next().await {
            let entry = entry.map_err(|e| e.to_string())?;
            let path_str = entry.path();
            if is_hidden_path(path_str) {
                continue;
            }
            if path_str.contains("__MACOSX") {
                continue;
            }

            direct_entries += 1;
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
                        saw_media: false,
                    },
                );
            } else if !entry.path().ends_with('/') {
                direct_files += 1;
            }

            if direct_entries % 256 == 0 {
                log::info!(
                    "list_folders_webdav progress path={} direct_entries={} direct_folders={} direct_files={} elapsed_ms={}",
                    path,
                    direct_entries,
                    candidates.len(),
                    direct_files,
                    started.elapsed().as_millis()
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

        let direct_children_count = candidates.len();
        if direct_children_count > MAX_PREVIEW_SCAN_FOLDERS {
            let mut folders: Vec<Folder> = candidates
                .into_values()
                .map(|folder| Folder {
                    path: folder.path,
                    name: folder.name,
                    is_local: false,
                    has_sub_folders: true,
                    preview_uris: Vec::new(),
                    date_modified: folder.date_modified,
                })
                .collect();

            folders.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
            log::info!(
                "list_folders_webdav fast_path path={} direct_children={} visible_folders={} elapsed_ms={}",
                path,
                direct_children_count,
                folders.len(),
                started.elapsed().as_millis()
            );
            return Ok(folders);
        }

        let mut scanned_entries = 0usize;
        let mut media_entries = 0usize;
        let op = Arc::clone(op);
        let mut scanned_candidates = Vec::with_capacity(direct_children_count);
        let mut scans = futures::stream::iter(candidates.into_values().map(|folder| {
            let op = Arc::clone(&op);
            async move {
                let scan = self
                    .scan_folder_preview_webdav(op.as_ref(), &folder.path)
                    .await?;
                Ok::<_, String>((folder, scan))
            }
        }))
        .buffer_unordered(MAX_CONCURRENT_FOLDER_SCANS);

        while let Some(result) = scans.next().await {
            let (mut folder, scan) = result?;
            folder.has_sub_folders = scan.has_sub_folders;
            folder.preview_uris = scan.preview_uris;
            folder.saw_media = scan.saw_media || scan.truncated;
            folder.date_modified = folder.date_modified.max(scan.date_modified);
            scanned_entries += scan.scanned_entries;
            media_entries += scan.media_entries;
            scanned_candidates.push(folder);
        }

        let mut folders: Vec<Folder> = scanned_candidates
            .into_iter()
            .filter(|folder| folder.saw_media)
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
            "list_folders_webdav path={} direct_children={} visible_folders={} scanned_entries={} media_entries={} elapsed_ms={}",
            path,
            direct_children_count,
            folders.len(),
            scanned_entries,
            media_entries,
            started.elapsed().as_millis()
        );
        Ok(folders)
    }

    async fn scan_folder_preview_webdav(
        &self,
        op: &Operator,
        folder_path: &str,
    ) -> Result<FolderScanResult, String> {
        let mut result = FolderScanResult::default();
        let mut queue = VecDeque::new();
        let mut contributed_video_children = HashSet::new();
        let mut queued_root_children = 0usize;
        queue.push_back(folder_path.to_string());

        while let Some(current_folder) = queue.pop_front() {
            let mut lister = op
                .lister(&current_folder)
                .await
                .map_err(|e| e.to_string())?;

            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                let path_str = entry.path();
                if is_hidden_path(path_str) || path_str.contains("__MACOSX") {
                    continue;
                }

                result.scanned_entries += 1;
                if result.scanned_entries >= MAX_PREVIEW_SCAN_ENTRIES {
                    result.truncated = true;
                    break;
                }

                if path_str.ends_with('/') {
                    if path_str == current_folder {
                        continue;
                    }

                    if current_folder == folder_path {
                        result.has_sub_folders = true;
                    }

                    if result.preview_uris.len() < 4 {
                        if current_folder == folder_path {
                            if queued_root_children >= MAX_PREVIEW_ROOT_CHILDREN_TO_QUEUE {
                                result.truncated = true;
                                continue;
                            }
                            queued_root_children += 1;
                        }
                        queue.push_back(path_str.to_string());
                    }
                    continue;
                }

                let metadata = entry.metadata();
                if !metadata.mode().is_file() {
                    continue;
                }

                let name = file_name(path_str);
                let Some(media_type) = detect_media_type(name) else {
                    continue;
                };

                result.saw_media = true;
                result.media_entries += 1;

                if let Some(last_modified) = metadata.last_modified().map(|t| t.timestamp() as u64)
                {
                    result.date_modified = result.date_modified.max(last_modified);
                }

                if result.preview_uris.len() < 4 {
                    if let Some(child_key) = preview_dedupe_key(folder_path, path_str, media_type) {
                        if !contributed_video_children.insert(child_key) {
                            continue;
                        }
                    }

                    let full_uri = format!(
                        "{}/{}",
                        self.base_url.trim_end_matches('/'),
                        path_str.trim_start_matches('/')
                    );
                    result.preview_uris.push(full_uri);

                }
            }

            if result.preview_uris.len() >= 4 || result.truncated {
                break;
            }
        }

        Ok(result)
    }

    pub async fn inspect_folder(&self, folder_path: &str) -> Result<FolderInspection, String> {
        match self.protocol {
            Protocol::WebDav => self.inspect_folder_webdav(folder_path).await,
        }
    }

    async fn inspect_folder_webdav(&self, folder_path: &str) -> Result<FolderInspection, String> {
        let op = self.op.as_ref().ok_or("WebDAV not initialized")?;
        let started = Instant::now();
        let scan = self
            .scan_folder_preview_webdav_on_demand(op.as_ref(), folder_path)
            .await?;
        log::info!(
            "inspect_folder_webdav path={} previews={} has_sub_folders={} scanned_entries={} media_entries={} truncated={} elapsed_ms={}",
            folder_path,
            scan.preview_uris.len(),
            scan.has_sub_folders,
            scan.scanned_entries,
            scan.media_entries,
            scan.truncated,
            started.elapsed().as_millis()
        );
        Ok(FolderInspection {
            has_sub_folders: scan.has_sub_folders || scan.truncated,
            preview_uris: scan.preview_uris,
        })
    }

    async fn scan_folder_preview_webdav_on_demand(
        &self,
        op: &Operator,
        folder_path: &str,
    ) -> Result<FolderScanResult, String> {
        let mut result = FolderScanResult::default();
        let mut queue = VecDeque::new();
        let mut contributed_video_children = HashSet::new();
        let mut queued_root_children = 0usize;
        queue.push_back(folder_path.to_string());

        while let Some(current_folder) = queue.pop_front() {
            let mut lister = op
                .lister(&current_folder)
                .await
                .map_err(|e| e.to_string())?;

            while let Some(entry) = lister.next().await {
                let entry = entry.map_err(|e| e.to_string())?;
                let path_str = entry.path();
                if is_hidden_path(path_str) || path_str.contains("__MACOSX") {
                    continue;
                }

                if path_str.ends_with('/') {
                    if path_str == current_folder {
                        continue;
                    }

                    if current_folder == folder_path {
                        result.has_sub_folders = true;
                    }

                    if result.preview_uris.len() < 4 {
                        if current_folder == folder_path {
                            if queued_root_children >= MAX_ON_DEMAND_ROOT_CHILDREN_TO_QUEUE {
                                continue;
                            }
                            queued_root_children += 1;
                        }
                        queue.push_back(path_str.to_string());
                    }
                    continue;
                }

                result.scanned_entries += 1;
                if result.scanned_entries >= MAX_ON_DEMAND_PREVIEW_SCAN_ENTRIES {
                    result.truncated = true;
                    break;
                }

                let metadata = entry.metadata();
                if !metadata.mode().is_file() {
                    continue;
                }

                let name = file_name(path_str);
                let Some(media_type) = detect_media_type(name) else {
                    continue;
                };

                result.saw_media = true;
                result.media_entries += 1;

                if let Some(last_modified) = metadata.last_modified().map(|t| t.timestamp() as u64)
                {
                    result.date_modified = result.date_modified.max(last_modified);
                }

                if let Some(child_key) = preview_dedupe_key(folder_path, path_str, media_type) {
                    if !contributed_video_children.insert(child_key) {
                        continue;
                    }
                }

                let full_uri = format!(
                    "{}/{}",
                    self.base_url.trim_end_matches('/'),
                    path_str.trim_start_matches('/')
                );
                result.preview_uris.push(full_uri);

            }

            if result.preview_uris.len() >= 4 || result.truncated {
                break;
            }
        }

        Ok(result)
    }
}

fn is_hidden_path(path: &str) -> bool {
    path.trim_matches('/')
        .split('/')
        .any(|s| s.len() > 1 && s.starts_with('.'))
}

fn detect_media_type(name: &str) -> Option<MediaType> {
    let lower = name.to_ascii_lowercase();
    if lower.ends_with(".jpg")
        || lower.ends_with(".jpeg")
        || lower.ends_with(".png")
        || lower.ends_with(".webp")
        || lower.ends_with(".gif")
        || lower.ends_with(".bmp")
        || lower.ends_with(".heic")
        || lower.ends_with(".heif")
    {
        return Some(MediaType::Image);
    }

    if lower.ends_with(".mp4")
        || lower.ends_with(".mkv")
        || lower.ends_with(".mov")
        || lower.ends_with(".avi")
        || lower.ends_with(".webm")
        || lower.ends_with(".m4v")
        || lower.ends_with(".3gp")
        || lower.ends_with(".ts")
        || lower.ends_with(".m2ts")
        || lower.ends_with(".wmv")
        || lower.ends_with(".asf")
    {
        return Some(MediaType::Video);
    }

    None
}

struct FolderAggregate {
    path: String,
    name: String,
    has_sub_folders: bool,
    preview_uris: Vec<String>,
    date_modified: u64,
    saw_media: bool,
}

#[derive(Default)]
struct FolderScanResult {
    has_sub_folders: bool,
    preview_uris: Vec<String>,
    date_modified: u64,
    saw_media: bool,
    truncated: bool,
    scanned_entries: usize,
    media_entries: usize,
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

fn preview_dedupe_key(
    parent_path: &str,
    entry_path: &str,
    media_type: MediaType,
) -> Option<String> {
    match media_type {
        MediaType::Video => immediate_child_name(parent_path, entry_path),
        MediaType::Image => None,
    }
}

fn sort_photos(photos: &mut Vec<Photo>, order: SortOrder) {
    match order {
        SortOrder::NameAsc => {
            photos.sort_by(|a, b| a.title.to_lowercase().cmp(&b.title.to_lowercase()))
        }
        SortOrder::NameDesc => {
            photos.sort_by(|a, b| b.title.to_lowercase().cmp(&a.title.to_lowercase()))
        }
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
            media_type: MediaType::Image,
            duration_ms: None,
        }
    }

    #[test]
    fn hidden_path_detects_nested_dot_directories() {
        assert!(is_hidden_path("/visible/.secret/chapter01"));
        assert!(!is_hidden_path("/visible/chapter01"));
    }

    #[test]
    fn media_file_detection_supports_images_and_videos() {
        assert_eq!(Some(MediaType::Image), detect_media_type("Cover.JPG"));
        assert_eq!(Some(MediaType::Image), detect_media_type("panel.WebP"));
        assert_eq!(Some(MediaType::Video), detect_media_type("clip.MP4"));
        assert_eq!(Some(MediaType::Video), detect_media_type("movie.mkv"));
        assert_eq!(Some(MediaType::Video), detect_media_type("movie.WMV"));
        assert_eq!(Some(MediaType::Video), detect_media_type("stream.asf"));
        assert_eq!(None, detect_media_type("notes.txt"));
    }

    #[test]
    fn relative_path_handles_root_and_nested_paths() {
        assert_eq!(
            Some("library/chapter01/page01.jpg".to_string()),
            relative_path("/", "/library/chapter01/page01.jpg")
        );
        assert_eq!(
            Some("chapter01/page01.jpg".to_string()),
            relative_path("/library", "/library/chapter01/page01.jpg")
        );
        assert_eq!(
            None,
            relative_path("/library", "/other/chapter01/page01.jpg")
        );
    }

    #[test]
    fn immediate_child_name_returns_first_segment_below_parent() {
        assert_eq!(
            Some("library".to_string()),
            immediate_child_name("/", "/library/chapter01/page01.jpg")
        );
        assert_eq!(
            Some("chapter01".to_string()),
            immediate_child_name("/library", "/library/chapter01/page01.jpg")
        );
        assert_eq!(
            None,
            immediate_child_name("/library", "/other/chapter01/page01.jpg")
        );
    }

    #[test]
    fn preview_dedupe_key_only_applies_to_videos() {
        assert_eq!(
            None,
            preview_dedupe_key(
                "/library",
                "/library/chapter01/page01.jpg",
                MediaType::Image
            )
        );
        assert_eq!(
            Some("chapter01".to_string()),
            preview_dedupe_key("/library", "/library/chapter01/clip.mp4", MediaType::Video)
        );
    }

    #[test]
    fn sort_photos_supports_all_supported_orders() {
        let mut by_name_asc = vec![photo("beta", 2), photo("Alpha", 1)];
        sort_photos(&mut by_name_asc, SortOrder::NameAsc);
        assert_eq!(
            vec!["Alpha", "beta"],
            by_name_asc
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );

        let mut by_name_desc = vec![photo("beta", 2), photo("Alpha", 1)];
        sort_photos(&mut by_name_desc, SortOrder::NameDesc);
        assert_eq!(
            vec!["beta", "Alpha"],
            by_name_desc
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );

        let mut by_date_desc = vec![photo("older", 1), photo("newer", 5)];
        sort_photos(&mut by_date_desc, SortOrder::DateDesc);
        assert_eq!(
            vec!["newer", "older"],
            by_date_desc
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );
    }
}
