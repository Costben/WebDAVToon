use crate::models::{
    Folder, FolderInspection, MediaType, Photo, RemoteConfig, RemoteProtocol, SortOrder,
};
use bytes::Bytes;
use futures::StreamExt;
use opendal::Operator;
use std::collections::{HashMap, HashSet, VecDeque};
use std::pin::Pin;
use std::sync::Arc;
use std::time::Instant;

const MAX_PREVIEW_SCAN_FOLDERS: usize = 24;
const MAX_PREVIEW_SCAN_ENTRIES: usize = 256;
const MAX_PREVIEW_ROOT_CHILDREN_TO_QUEUE: usize = 24;
const MAX_CONCURRENT_FOLDER_SCANS_WEBDAV: usize = 6;
const MAX_CONCURRENT_FOLDER_SCANS_FTP: usize = 3;
const MAX_CONCURRENT_FOLDER_SCANS_SMB: usize = 4;
const MAX_ON_DEMAND_PREVIEW_SCAN_ENTRIES: usize = 16384;
const MAX_ON_DEMAND_ROOT_CHILDREN_TO_QUEUE: usize = 2048;

/// One directory-listing entry, protocol agnostic.
/// `rel_path` is relative to the service root; directories end with '/'.
#[derive(Debug, Clone)]
pub struct EntryMeta {
    pub rel_path: String,
    pub name: String,
    pub is_dir: bool,
    pub is_file: bool,
    pub size: u64,
    pub last_modified: u64,
}

pub type EntryResult = Result<EntryMeta, String>;
pub type ByteStream = Pin<Box<dyn futures::Stream<Item = Result<Bytes, String>> + Send>>;

pub struct OpendalFs {
    op: Arc<Operator>,
    flavor: RemoteProtocol,
}

impl OpendalFs {
    fn new_webdav(endpoint: &str, username: &str, password: &str) -> Result<Self, String> {
        let builder = opendal::services::Webdav::default()
            .endpoint(endpoint)
            .username(username)
            .password(password);

        let op = Operator::new(builder).map_err(|e| e.to_string())?.finish();
        Ok(Self {
            op: Arc::new(op),
            flavor: RemoteProtocol::WebDav,
        })
    }

    fn new_ftp(endpoint: &str, username: &str, password: &str) -> Result<Self, String> {
        let parsed = parse_ftp_endpoint(endpoint)?;
        let mut builder = opendal::services::Ftp::default().endpoint(&parsed.endpoint);
        if parsed.root != "/" {
            builder = builder.root(&parsed.root);
        }
        if !username.is_empty() {
            builder = builder.user(username);
        }
        if !password.is_empty() {
            builder = builder.password(password);
        }

        let op = Operator::new(builder).map_err(|e| e.to_string())?.finish();
        Ok(Self {
            op: Arc::new(op),
            flavor: RemoteProtocol::Ftp,
        })
    }

    #[cfg(test)]
    pub(crate) fn new_local_fs_for_tests(root: &str) -> Result<Self, String> {
        let builder = opendal::services::Fs::default().root(root);
        let op = Operator::new(builder).map_err(|e| e.to_string())?.finish();
        Ok(Self {
            op: Arc::new(op),
            flavor: RemoteProtocol::WebDav,
        })
    }

    async fn list_dir(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        let mut lister = self.op.lister(rel_path).await.map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        while let Some(next) = lister.next().await {
            out.push(
                next.map(|entry| entry_meta_from_opendal(&entry))
                    .map_err(|e| e.to_string()),
            );
        }
        Ok(out)
    }

    async fn list_recursive(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        let mut lister = self
            .op
            .lister_with(rel_path)
            .recursive(true)
            .await
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        while let Some(next) = lister.next().await {
            out.push(
                next.map(|entry| entry_meta_from_opendal(&entry))
                    .map_err(|e| e.to_string()),
            );
        }
        Ok(out)
    }

    async fn read_all(&self, rel_path: &str) -> Result<Vec<u8>, String> {
        self.op
            .read(rel_path)
            .await
            .map(|b| b.to_vec())
            .map_err(|e| e.to_string())
    }

    async fn stat_size(&self, rel_path: &str) -> Result<u64, String> {
        self.op
            .stat(rel_path)
            .await
            .map(|m| m.content_length())
            .map_err(|e| e.to_string())
    }

    async fn read_stream(
        &self,
        rel_path: &str,
        offset: u64,
        length: Option<u64>,
    ) -> Result<ByteStream, String> {
        let reader = self.op.reader(rel_path).await.map_err(|e| e.to_string())?;
        let stream = match length {
            Some(len) => {
                reader
                    .into_bytes_stream(offset..offset.saturating_add(len))
                    .await
            }
            None => reader.into_bytes_stream(offset..).await,
        }
        .map_err(|e| e.to_string())?;
        Ok(Box::pin(
            stream.map(|chunk| chunk.map_err(|e| e.to_string())),
        ))
    }

    async fn delete_file(&self, rel_path: &str) -> Result<(), String> {
        self.op.delete(rel_path).await.map_err(|e| e.to_string())
    }

    async fn delete_dir(&self, rel_path: &str) -> Result<(), String> {
        let dir = ensure_dir_path(rel_path);
        match self.flavor {
            // A WebDAV DELETE on a collection removes it recursively server-side.
            RemoteProtocol::WebDav => self.op.delete(&dir).await.map_err(|e| e.to_string()),
            // FTP RMD only removes empty directories; remove_all walks and deletes.
            _ => self.op.remove_all(&dir).await.map_err(|e| e.to_string()),
        }
    }

    fn max_concurrent_scans(&self) -> usize {
        match self.flavor {
            RemoteProtocol::WebDav => MAX_CONCURRENT_FOLDER_SCANS_WEBDAV,
            RemoteProtocol::Ftp => MAX_CONCURRENT_FOLDER_SCANS_FTP,
            RemoteProtocol::Smb => MAX_CONCURRENT_FOLDER_SCANS_SMB,
        }
    }
}

fn entry_meta_from_opendal(entry: &opendal::Entry) -> EntryMeta {
    let metadata = entry.metadata();
    EntryMeta {
        rel_path: entry.path().to_string(),
        name: entry.name().to_string(),
        is_dir: entry.path().ends_with('/'),
        is_file: metadata.mode().is_file(),
        size: metadata.content_length(),
        last_modified: metadata
            .last_modified()
            .map(|t| t.timestamp() as u64)
            .unwrap_or(0),
    }
}

pub enum Backend {
    Opendal(OpendalFs),
    Smb(crate::smb_fs::SmbFs),
}

impl Backend {
    async fn list_dir(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        match self {
            Backend::Opendal(fs) => fs.list_dir(rel_path).await,
            Backend::Smb(fs) => fs.list_dir(rel_path).await,
        }
    }

    async fn list_recursive(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        match self {
            Backend::Opendal(fs) => fs.list_recursive(rel_path).await,
            Backend::Smb(fs) => fs.list_recursive(rel_path).await,
        }
    }

    async fn read_all(&self, rel_path: &str) -> Result<Vec<u8>, String> {
        match self {
            Backend::Opendal(fs) => fs.read_all(rel_path).await,
            Backend::Smb(fs) => fs.read_all(rel_path).await,
        }
    }

    async fn stat_size(&self, rel_path: &str) -> Result<u64, String> {
        match self {
            Backend::Opendal(fs) => fs.stat_size(rel_path).await,
            Backend::Smb(fs) => fs.stat_size(rel_path).await,
        }
    }

    async fn read_stream(
        &self,
        rel_path: &str,
        offset: u64,
        length: Option<u64>,
    ) -> Result<ByteStream, String> {
        match self {
            Backend::Opendal(fs) => fs.read_stream(rel_path, offset, length).await,
            Backend::Smb(fs) => fs.read_stream(rel_path, offset, length).await,
        }
    }

    async fn delete_file(&self, rel_path: &str) -> Result<(), String> {
        match self {
            Backend::Opendal(fs) => fs.delete_file(rel_path).await,
            Backend::Smb(fs) => fs.delete_file(rel_path).await,
        }
    }

    async fn delete_dir(&self, rel_path: &str) -> Result<(), String> {
        match self {
            Backend::Opendal(fs) => fs.delete_dir(rel_path).await,
            Backend::Smb(fs) => fs.delete_dir(rel_path).await,
        }
    }

    fn max_concurrent_scans(&self) -> usize {
        match self {
            Backend::Opendal(fs) => fs.max_concurrent_scans(),
            Backend::Smb(_) => MAX_CONCURRENT_FOLDER_SCANS_SMB,
        }
    }
}

/// Limits and counting rules for a folder preview scan. Two profiles exist,
/// mirroring the two historical scan variants byte-for-byte:
/// - `NORMAL_SCAN` (list_folders): counts every visible entry (dirs included),
///   truncates when the root child queue cap is hit, caps previews at 4.
/// - `ON_DEMAND_SCAN` (inspect_folder): counts only non-directory entries,
///   silently stops queueing at the root cap, and collects every media file of
///   the folder that first yields previews (downstream code trims to 4).
struct ScanLimits {
    max_entries: usize,
    max_root_children: usize,
    count_all_entries: bool,
    truncate_on_root_cap: bool,
    cap_previews: bool,
}

const NORMAL_SCAN: ScanLimits = ScanLimits {
    max_entries: MAX_PREVIEW_SCAN_ENTRIES,
    max_root_children: MAX_PREVIEW_ROOT_CHILDREN_TO_QUEUE,
    count_all_entries: true,
    truncate_on_root_cap: true,
    cap_previews: true,
};

const ON_DEMAND_SCAN: ScanLimits = ScanLimits {
    max_entries: MAX_ON_DEMAND_PREVIEW_SCAN_ENTRIES,
    max_root_children: MAX_ON_DEMAND_ROOT_CHILDREN_TO_QUEUE,
    count_all_entries: false,
    truncate_on_root_cap: false,
    cap_previews: false,
};

/// SMB variant of `ON_DEMAND_SCAN`: directory listings cost several protocol
/// round-trips each, so previews stop at 4 instead of collecting every media
/// file of the first yielding folder (the Kotlin side does not sort SMB
/// previews, see RustWebDavPhotoRepository.inspectFolder).
const ON_DEMAND_SCAN_CAPPED: ScanLimits = ScanLimits {
    max_entries: MAX_ON_DEMAND_PREVIEW_SCAN_ENTRIES,
    max_root_children: MAX_ON_DEMAND_ROOT_CHILDREN_TO_QUEUE,
    count_all_entries: false,
    truncate_on_root_cap: false,
    cap_previews: true,
};

pub struct RemoteService {
    backend: Backend,
    base_url: String,
    protocol: RemoteProtocol,
}

impl RemoteService {
    pub fn new(config: &RemoteConfig) -> Result<Self, String> {
        let backend = match config.protocol {
            RemoteProtocol::WebDav => Backend::Opendal(OpendalFs::new_webdav(
                &config.endpoint,
                &config.username,
                &config.password,
            )?),
            RemoteProtocol::Ftp => Backend::Opendal(OpendalFs::new_ftp(
                &config.endpoint,
                &config.username,
                &config.password,
            )?),
            RemoteProtocol::Smb => {
                let parsed = parse_smb_endpoint(&config.endpoint)?;
                Backend::Smb(crate::smb_fs::SmbFs::new(
                    parsed.host,
                    parsed.port,
                    parsed.share,
                    parsed.root_prefix,
                    &config.username,
                    &config.password,
                    config.domain.as_deref(),
                )?)
            }
        };

        Ok(Self {
            backend,
            base_url: config.endpoint.clone(),
            protocol: config.protocol,
        })
    }

    #[cfg(test)]
    pub(crate) fn new_local_fs_for_tests(root: &str, base_url: &str) -> Result<Self, String> {
        Ok(Self {
            backend: Backend::Opendal(OpendalFs::new_local_fs_for_tests(root)?),
            base_url: base_url.to_string(),
            protocol: RemoteProtocol::WebDav,
        })
    }

    fn proto_label(&self) -> &'static str {
        self.protocol.label()
    }

    pub async fn read_file(&self, path: &str) -> Result<Vec<u8>, String> {
        self.backend.read_all(path).await
    }

    pub async fn stat_size(&self, path: &str) -> Result<u64, String> {
        self.backend.stat_size(path).await
    }

    pub async fn read_stream(
        &self,
        path: &str,
        offset: u64,
        length: Option<u64>,
    ) -> Result<ByteStream, String> {
        self.backend.read_stream(path, offset, length).await
    }

    pub async fn delete_file(&self, path: &str) -> Result<(), String> {
        self.backend.delete_file(path).await
    }

    pub async fn delete_dir(&self, path: &str) -> Result<(), String> {
        self.backend.delete_dir(path).await
    }

    pub async fn list_root_names(&self) -> Result<Vec<String>, String> {
        let entries = self.backend.list_dir("/").await?;
        let mut names = Vec::new();
        let mut count = 0;
        for entry_res in entries {
            if count >= 20 {
                break;
            } // Limit to 20 items
            if let Ok(entry) = entry_res {
                if entry.name.is_empty() || entry.name == "/" {
                    continue;
                }
                names.push(entry.name);
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
        let started = Instant::now();
        let mut photos = Vec::new();
        let mut scanned_files = 0usize;
        let mut skipped_unsupported = 0usize;
        let mut skipped_hidden = 0usize;
        let mut scanned_directories = 0usize;

        let entries = if recursive {
            self.backend.list_recursive(path).await?
        } else {
            self.backend.list_dir(path).await?
        };

        for entry_res in entries {
            let entry = entry_res?;
            if entry.is_dir {
                scanned_directories += 1;
                continue;
            }
            scanned_files += 1;
            if is_hidden_path(&entry.rel_path) {
                skipped_hidden += 1;
                continue;
            }
            let Some(media_type) = detect_media_type(&entry.name) else {
                skipped_unsupported += 1;
                continue;
            };

            if entry.is_file {
                photos.push(Photo {
                    id: entry.rel_path.clone(),
                    title: entry.name.clone(),
                    uri: mint_uri(&self.base_url, &entry.rel_path),
                    is_local: false,
                    size: entry.size,
                    date_modified: entry.last_modified,
                    media_type,
                    duration_ms: None,
                });
            }
        }

        sort_photos(&mut photos, sort_order);
        log::info!(
            "list_photos protocol={} path={} recursive={} scanned_directories={} scanned_files={} matched_media={} skipped_hidden={} skipped_unsupported={} elapsed_ms={}",
            self.proto_label(),
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
        let proto = self.proto_label();
        let started = Instant::now();
        log::info!("list_folders protocol={} start path={}", proto, path);
        let entries = self.backend.list_dir(path).await?;
        log::info!(
            "list_folders protocol={} lister_ready path={} elapsed_ms={}",
            proto,
            path,
            started.elapsed().as_millis()
        );
        let mut candidates = HashMap::<String, FolderAggregate>::new();
        let mut direct_entries = 0usize;
        let mut direct_files = 0usize;

        for entry_res in entries {
            let entry = entry_res?;
            let path_str = entry.rel_path.as_str();
            if is_hidden_path(path_str) {
                continue;
            }
            if path_str.contains("__MACOSX") {
                continue;
            }

            direct_entries += 1;
            if entry.is_dir && path_str != path {
                let key = folder_key(&entry.name);
                if key.is_empty() {
                    continue;
                }

                candidates.insert(
                    key,
                    FolderAggregate {
                        path: path_str.to_string(),
                        name: entry.name.clone(),
                        has_sub_folders: false,
                        preview_uris: Vec::new(),
                        date_modified: entry.last_modified,
                        saw_media: false,
                    },
                );
            } else if !entry.is_dir {
                direct_files += 1;
            }

            if direct_entries % 256 == 0 {
                log::info!(
                    "list_folders protocol={} progress path={} direct_entries={} direct_folders={} direct_files={} elapsed_ms={}",
                    proto,
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
                "list_folders protocol={} path={} direct_children=0 visible_folders=0 scanned_entries=0 elapsed_ms={}",
                proto,
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
                "list_folders protocol={} fast_path path={} direct_children={} visible_folders={} elapsed_ms={}",
                proto,
                path,
                direct_children_count,
                folders.len(),
                started.elapsed().as_millis()
            );
            return Ok(folders);
        }

        let mut scanned_entries = 0usize;
        let mut media_entries = 0usize;
        let mut scanned_candidates = Vec::with_capacity(direct_children_count);
        let mut scans = futures::stream::iter(candidates.into_values().map(|folder| async move {
            let scan = self.scan_folder_preview(&folder.path, &NORMAL_SCAN).await?;
            Ok::<_, String>((folder, scan))
        }))
        .buffer_unordered(self.backend.max_concurrent_scans());

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
        drop(scans);

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
            "list_folders protocol={} path={} direct_children={} visible_folders={} scanned_entries={} media_entries={} elapsed_ms={}",
            proto,
            path,
            direct_children_count,
            folders.len(),
            scanned_entries,
            media_entries,
            started.elapsed().as_millis()
        );
        Ok(folders)
    }

    pub async fn inspect_folder(&self, folder_path: &str) -> Result<FolderInspection, String> {
        let started = Instant::now();
        let limits = if self.protocol == RemoteProtocol::Smb {
            &ON_DEMAND_SCAN_CAPPED
        } else {
            &ON_DEMAND_SCAN
        };
        let scan = self.scan_folder_preview(folder_path, limits).await?;
        log::info!(
            "inspect_folder protocol={} path={} previews={} has_sub_folders={} scanned_entries={} media_entries={} truncated={} elapsed_ms={}",
            self.proto_label(),
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

    async fn scan_folder_preview(
        &self,
        folder_path: &str,
        limits: &ScanLimits,
    ) -> Result<FolderScanResult, String> {
        let mut result = FolderScanResult::default();
        let mut queue = VecDeque::new();
        let mut contributed_video_children = HashSet::new();
        let mut queued_root_children = 0usize;
        queue.push_back(folder_path.to_string());

        while let Some(current_folder) = queue.pop_front() {
            let entries = self.backend.list_dir(&current_folder).await?;

            for entry_res in entries {
                let entry = entry_res?;
                let path_str = entry.rel_path.as_str();
                if is_hidden_path(path_str) || path_str.contains("__MACOSX") {
                    continue;
                }

                if limits.count_all_entries {
                    result.scanned_entries += 1;
                    if result.scanned_entries >= limits.max_entries {
                        result.truncated = true;
                        break;
                    }
                }

                if entry.is_dir {
                    if path_str == current_folder {
                        continue;
                    }

                    if current_folder == folder_path {
                        result.has_sub_folders = true;
                    }

                    if result.preview_uris.len() < 4 {
                        if current_folder == folder_path {
                            if queued_root_children >= limits.max_root_children {
                                if limits.truncate_on_root_cap {
                                    result.truncated = true;
                                }
                                continue;
                            }
                            queued_root_children += 1;
                        }
                        queue.push_back(path_str.to_string());
                    }
                    continue;
                }

                if !limits.count_all_entries {
                    result.scanned_entries += 1;
                    if result.scanned_entries >= limits.max_entries {
                        result.truncated = true;
                        break;
                    }
                }

                if !entry.is_file {
                    continue;
                }

                let name = file_name(path_str);
                let Some(media_type) = detect_media_type(name) else {
                    continue;
                };

                result.saw_media = true;
                result.media_entries += 1;

                if entry.last_modified > 0 {
                    result.date_modified = result.date_modified.max(entry.last_modified);
                }

                if limits.cap_previews && result.preview_uris.len() >= 4 {
                    continue;
                }

                if let Some(child_key) = preview_dedupe_key(folder_path, path_str, media_type) {
                    if !contributed_video_children.insert(child_key) {
                        continue;
                    }
                }

                result
                    .preview_uris
                    .push(mint_uri(&self.base_url, path_str));
            }

            if result.preview_uris.len() >= 4 || result.truncated {
                break;
            }
        }

        Ok(result)
    }
}

fn mint_uri(base_url: &str, rel_path: &str) -> String {
    format!(
        "{}/{}",
        base_url.trim_end_matches('/'),
        rel_path.trim_start_matches('/')
    )
}

fn ensure_dir_path(path: &str) -> String {
    if path.ends_with('/') {
        path.to_string()
    } else {
        format!("{}/", path)
    }
}

pub(crate) struct ParsedFtp {
    pub endpoint: String,
    pub root: String,
}

/// Parses a canonical `ftp://host[:port][/root]` endpoint into the opendal
/// builder inputs. The default port 21 is made explicit in the endpoint.
pub(crate) fn parse_ftp_endpoint(canonical: &str) -> Result<ParsedFtp, String> {
    let rest = strip_scheme(canonical, "ftp://");
    let (host_part, path_part) = split_host_path(rest);
    if host_part.is_empty() {
        return Err("FTP endpoint is missing a host".to_string());
    }
    let (host, port) = split_host_port(host_part, 21)?;
    Ok(ParsedFtp {
        endpoint: format!("ftp://{}:{}", host, port),
        root: normalize_root(path_part),
    })
}

pub(crate) struct ParsedSmb {
    pub host: String,
    pub port: u16,
    pub share: String,
    /// Path inside the share used as the service root ("" or "sub/dir").
    pub root_prefix: String,
}

/// Parses a canonical `smb://host[:port]/share[/root]` endpoint. The first
/// path segment is the share name and is required.
pub(crate) fn parse_smb_endpoint(canonical: &str) -> Result<ParsedSmb, String> {
    let rest = strip_scheme(canonical, "smb://");
    let (host_part, path_part) = split_host_path(rest);
    if host_part.is_empty() {
        return Err("SMB endpoint is missing a host".to_string());
    }
    let (host, port) = split_host_port(host_part, 445)?;

    let trimmed = path_part.trim_matches('/');
    if trimmed.is_empty() {
        return Err("SMB endpoint must include a share name (host/share)".to_string());
    }
    let mut segments = trimmed.splitn(2, '/');
    let share = segments.next().unwrap_or_default().to_string();
    let root_prefix = segments.next().unwrap_or_default().to_string();
    if share.is_empty() {
        return Err("SMB endpoint must include a share name (host/share)".to_string());
    }

    Ok(ParsedSmb {
        host: host.to_string(),
        port,
        share,
        root_prefix,
    })
}

fn strip_scheme<'a>(input: &'a str, scheme: &str) -> &'a str {
    let trimmed = input.trim();
    if trimmed.len() >= scheme.len() && trimmed[..scheme.len()].eq_ignore_ascii_case(scheme) {
        &trimmed[scheme.len()..]
    } else {
        trimmed
    }
}

fn split_host_path(input: &str) -> (&str, &str) {
    match input.find('/') {
        Some(idx) => (&input[..idx], &input[idx..]),
        None => (input, ""),
    }
}

fn split_host_port(input: &str, default_port: u16) -> Result<(&str, u16), String> {
    match input.rfind(':') {
        Some(idx) => {
            let host = &input[..idx];
            let port = input[idx + 1..]
                .parse::<u16>()
                .map_err(|_| format!("Invalid port in endpoint: {}", input))?;
            Ok((host, port))
        }
        None => Ok((input, default_port)),
    }
}

fn normalize_root(path: &str) -> String {
    let trimmed = path.trim_matches('/');
    if trimmed.is_empty() {
        "/".to_string()
    } else {
        format!("/{}", trimmed)
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

    #[test]
    fn mint_uri_joins_base_and_relative_path_with_single_slash() {
        assert_eq!(
            "https://nas.lan/dav/library/page01.jpg",
            mint_uri("https://nas.lan/dav/", "/library/page01.jpg")
        );
        assert_eq!(
            "smb://nas.lan/media/comics/ch01/p1.jpg",
            mint_uri("smb://nas.lan/media/comics", "ch01/p1.jpg")
        );
        assert_eq!(
            "ftp://192.168.31.100:2121/pub/a.png",
            mint_uri("ftp://192.168.31.100:2121/pub", "/a.png")
        );
    }

    #[test]
    fn parse_ftp_endpoint_defaults_and_custom_values() {
        let plain = parse_ftp_endpoint("ftp://nas.lan").expect("plain host");
        assert_eq!("ftp://nas.lan:21", plain.endpoint);
        assert_eq!("/", plain.root);

        let full = parse_ftp_endpoint("ftp://192.168.31.100:2121/pub/media/").expect("full");
        assert_eq!("ftp://192.168.31.100:2121", full.endpoint);
        assert_eq!("/pub/media", full.root);

        let schemeless = parse_ftp_endpoint("nas.lan/pub").expect("schemeless tolerated");
        assert_eq!("ftp://nas.lan:21", schemeless.endpoint);
        assert_eq!("/pub", schemeless.root);

        assert!(parse_ftp_endpoint("ftp://").is_err());
        assert!(parse_ftp_endpoint("ftp://nas.lan:notaport/pub").is_err());
    }

    #[test]
    fn parse_smb_endpoint_requires_share_and_handles_ports() {
        let basic = parse_smb_endpoint("smb://nas.lan/media").expect("basic");
        assert_eq!("nas.lan", basic.host);
        assert_eq!(445, basic.port);
        assert_eq!("media", basic.share);
        assert_eq!("", basic.root_prefix);

        let nested = parse_smb_endpoint("smb://nas.lan:1445/media/comics/ongoing").expect("nested");
        assert_eq!("nas.lan", nested.host);
        assert_eq!(1445, nested.port);
        assert_eq!("media", nested.share);
        assert_eq!("comics/ongoing", nested.root_prefix);

        assert!(parse_smb_endpoint("smb://nas.lan").is_err());
        assert!(parse_smb_endpoint("smb://nas.lan/").is_err());
        assert!(parse_smb_endpoint("smb:///share").is_err());
    }
}
