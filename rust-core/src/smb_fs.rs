//! SMB2/3 backend built on the pure-Rust `smb` crate.
//!
//! Configuration follows the precedent recorded in docs/TECHNICAL_DOCS.md for
//! running this crate family on Android: NTLM enabled, Kerberos disabled
//! (avoids `Sspi: NoCredentials`), dialects clamped to 3.0 - 3.1.1.

use crate::remote_fs::{ByteStream, EntryMeta, EntryResult};
use bytes::Bytes;
use futures::StreamExt;
use smb::{
    Client, ClientConfig, ConnectionConfig, Dialect, Directory, FileAccessMask, FileCreateArgs,
    FileDispositionInformation, FileFullDirectoryInformation, GetLen, Resource, ResourceHandle,
    UncPath,
};
use std::collections::VecDeque;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::RwLock;

const READ_CHUNK_BYTES: u64 = 1024 * 1024;
/// Seconds between the Windows FILETIME epoch (1601) and the Unix epoch (1970).
const FILETIME_UNIX_OFFSET_SECS: u64 = 11_644_473_600;

pub struct SmbFs {
    host: String,
    port: u16,
    share: String,
    /// Path inside the share acting as the service root ("" or "a/b").
    root_prefix: String,
    /// Username, possibly domain-qualified as `DOMAIN\user`.
    auth_username: String,
    password: String,
    client: RwLock<Option<Arc<Client>>>,
}

impl SmbFs {
    pub fn new(
        host: String,
        port: u16,
        share: String,
        root_prefix: String,
        username: &str,
        password: &str,
        domain: Option<&str>,
    ) -> Result<Self, String> {
        if username.is_empty() {
            return Err("SMB requires a username (guest access is not supported)".to_string());
        }
        let auth_username = match domain {
            Some(d) if !d.is_empty() => format!("{}\\{}", d, username),
            _ => username.to_string(),
        };
        Ok(Self {
            host,
            port,
            share,
            root_prefix,
            auth_username,
            password: password.to_string(),
            client: RwLock::new(None),
        })
    }

    fn client_config(&self) -> ClientConfig {
        ClientConfig {
            // DFS namespaces are out of scope; fail with a clear error instead
            // of attempting referral resolution.
            dfs: false,
            connection: ConnectionConfig {
                port: (self.port != 445).then_some(self.port),
                timeout: Some(Duration::from_secs(30)),
                min_dialect: Some(Dialect::Smb030),
                max_dialect: Some(Dialect::Smb0311),
                auth_methods: smb::connection::AuthMethodsConfig {
                    ntlm: true,
                    kerberos: false,
                },
                ..Default::default()
            },
            ..Default::default()
        }
    }

    fn share_unc(&self) -> Result<UncPath, String> {
        UncPath::new(&self.host)
            .and_then(|u| u.with_share(&self.share))
            .map_err(|e| e.to_string())
    }

    /// Builds the UNC path for an app-relative path ('/'-separated, dirs may
    /// carry a trailing '/'). `UncPath::with_path` converts '/' to '\\'.
    fn unc_for(&self, rel_path: &str) -> Result<UncPath, String> {
        let unc = self.share_unc()?;
        let inner = self.share_relative(rel_path);
        if inner.is_empty() {
            Ok(unc)
        } else {
            Ok(unc.with_path(&inner))
        }
    }

    fn share_relative(&self, rel_path: &str) -> String {
        let trimmed = rel_path.trim_matches('/');
        match (self.root_prefix.is_empty(), trimmed.is_empty()) {
            (true, _) => trimmed.to_string(),
            (false, true) => self.root_prefix.clone(),
            (false, false) => format!("{}/{}", self.root_prefix, trimmed),
        }
    }

    async fn client(&self) -> Result<Arc<Client>, String> {
        if let Some(client) = self.client.read().await.as_ref() {
            return Ok(Arc::clone(client));
        }
        let mut guard = self.client.write().await;
        if let Some(client) = guard.as_ref() {
            return Ok(Arc::clone(client));
        }
        let client = self.connect().await?;
        *guard = Some(Arc::clone(&client));
        Ok(client)
    }

    async fn invalidate_client(&self) {
        *self.client.write().await = None;
    }

    async fn connect(&self) -> Result<Arc<Client>, String> {
        let client = Client::new(self.client_config());
        let unc = self.share_unc()?;
        client
            .share_connect(&unc, &self.auth_username, self.password.clone())
            .await
            .map_err(|e| format!("SMB connect to \\\\{}\\{} failed: {}", self.host, self.share, e))?;
        Ok(Arc::new(client))
    }

    /// Runs a read-only operation with one reconnect-and-retry when the first
    /// attempt fails. SMB sessions die silently with the TCP connection (app
    /// backgrounded, Wi-Fi roam), unlike stateless HTTP.
    async fn open_resource(&self, rel_path: &str, access: FileAccessMask) -> Result<Resource, String> {
        let unc = self.unc_for(rel_path)?;
        let args = FileCreateArgs::make_open_existing(access);
        let first_err = {
            let client = self.client().await?;
            match client.create_file(&unc, &args).await {
                Ok(resource) => return Ok(resource),
                Err(e) => e,
            }
        };
        if !is_retryable(&first_err) {
            return Err(first_err.to_string());
        }
        log::warn!(
            "SMB operation on {} failed ({}); reconnecting once",
            rel_path,
            first_err
        );
        self.invalidate_client().await;
        let client = self.client().await?;
        client
            .create_file(&unc, &args)
            .await
            .map_err(|e| format!("{} (after reconnect: {})", first_err, e))
    }

    async fn open_directory(&self, rel_path: &str) -> Result<Arc<Directory>, String> {
        let resource = self
            .open_resource(rel_path, FileAccessMask::new().with_generic_read(true))
            .await?;
        match resource {
            Resource::Directory(dir) => Ok(Arc::new(dir)),
            other => {
                close_resource(&other).await;
                Err(format!("SMB path is not a directory: {}", rel_path))
            }
        }
    }

    async fn open_file(&self, rel_path: &str) -> Result<smb::File, String> {
        let resource = self
            .open_resource(rel_path, FileAccessMask::new().with_generic_read(true))
            .await?;
        match resource {
            Resource::File(file) => Ok(file),
            other => {
                close_resource(&other).await;
                Err(format!("SMB path is not a file: {}", rel_path))
            }
        }
    }

    pub async fn list_dir(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        let dir = self.open_directory(rel_path).await?;
        let entries = collect_dir_entries(&dir, rel_path).await;
        let _ = dir.close().await;
        entries
    }

    pub async fn list_recursive(&self, rel_path: &str) -> Result<Vec<EntryResult>, String> {
        let mut out = Vec::new();
        let mut queue = VecDeque::new();
        queue.push_back(rel_path.to_string());

        while let Some(current) = queue.pop_front() {
            let entries = self.list_dir(&current).await?;
            for entry_res in entries {
                if let Ok(entry) = &entry_res {
                    if entry.is_dir {
                        queue.push_back(entry.rel_path.clone());
                    }
                }
                out.push(entry_res);
            }
        }
        Ok(out)
    }

    pub async fn read_all(&self, rel_path: &str) -> Result<Vec<u8>, String> {
        let file = self.open_file(rel_path).await?;
        let total = file.get_len().await.map_err(|e| e.to_string())?;
        let mut data = Vec::with_capacity(total.min(64 * 1024 * 1024) as usize);
        let mut pos = 0u64;
        loop {
            let want = READ_CHUNK_BYTES.min(total.saturating_sub(pos));
            if want == 0 {
                break;
            }
            let mut buf = vec![0u8; want as usize];
            let read = match file.read_block(&mut buf, pos, None, false).await {
                Ok(n) => n,
                Err(e) => {
                    let _ = file.close().await;
                    return Err(e.to_string());
                }
            };
            if read == 0 {
                break;
            }
            data.extend_from_slice(&buf[..read]);
            pos += read as u64;
        }
        let _ = file.close().await;
        Ok(data)
    }

    pub async fn stat_size(&self, rel_path: &str) -> Result<u64, String> {
        let file = self.open_file(rel_path).await?;
        let size = file.get_len().await.map_err(|e| e.to_string());
        let _ = file.close().await;
        size
    }

    pub async fn read_stream(
        &self,
        rel_path: &str,
        offset: u64,
        length: Option<u64>,
    ) -> Result<ByteStream, String> {
        let file = self.open_file(rel_path).await?;
        Ok(file_byte_stream(file, offset, length))
    }

    pub async fn delete_file(&self, rel_path: &str) -> Result<(), String> {
        self.delete_path(rel_path).await
    }

    pub async fn delete_dir(&self, rel_path: &str) -> Result<(), String> {
        // SMB has no recursive delete; remove contents bottom-up. Fail fast if
        // any listing entry errored — deleting from a partial listing risks
        // removing parents of unseen children.
        let entries = self.list_recursive(rel_path).await?;
        let mut files = Vec::new();
        let mut dirs = Vec::new();
        for entry_res in entries {
            let entry = entry_res?;
            if entry.is_dir {
                dirs.push(entry.rel_path);
            } else {
                files.push(entry.rel_path);
            }
        }

        for file in files {
            self.delete_path(&file).await?;
        }
        dirs.sort_by_key(|d| std::cmp::Reverse(d.trim_matches('/').matches('/').count()));
        for dir in dirs {
            self.delete_path(&dir).await?;
        }
        self.delete_path(rel_path).await
    }

    async fn delete_path(&self, rel_path: &str) -> Result<(), String> {
        let resource = self
            .open_resource(rel_path, FileAccessMask::new().with_delete(true))
            .await?;
        let handle = resource_handle(&resource)?;
        // Default FileDispositionInformation carries delete_pending = true:
        // the entry is removed when the handle closes.
        let set = handle
            .set_info(FileDispositionInformation::default())
            .await
            .map_err(|e| e.to_string());
        let closed = handle.close().await.map_err(|e| e.to_string());
        set?;
        closed
    }
}

fn resource_handle(resource: &Resource) -> Result<&ResourceHandle, String> {
    match resource {
        Resource::File(file) => Ok(file.handle()),
        Resource::Directory(dir) => Ok(dir.handle()),
        Resource::Pipe(_) => Err("Unexpected pipe resource".to_string()),
    }
}

async fn close_resource(resource: &Resource) {
    if let Ok(handle) = resource_handle(resource) {
        let _ = handle.close().await;
    }
}

fn is_retryable(error: &smb::Error) -> bool {
    // A server that answered with an SMB status has a live connection; anything
    // else (transport, timeout, parse) is worth one reconnect attempt.
    !matches!(error, smb::Error::ReceivedErrorMessage(..))
}

async fn collect_dir_entries(
    dir: &Arc<Directory>,
    parent_rel: &str,
) -> Result<Vec<EntryResult>, String> {
    let mut out = Vec::new();
    let mut stream = Directory::query::<FileFullDirectoryInformation>(dir, "*")
        .await
        .map_err(|e| e.to_string())?;
    while let Some(item) = stream.next().await {
        match item {
            Ok(info) => {
                let name = info.file_name.to_string();
                if name == "." || name == ".." {
                    continue;
                }
                let is_dir = info.file_attributes.directory();
                out.push(Ok(EntryMeta {
                    rel_path: join_rel(parent_rel, &name, is_dir),
                    name,
                    is_dir,
                    is_file: !is_dir,
                    size: info.end_of_file,
                    last_modified: filetime_to_unix_secs(*info.last_write_time),
                }));
            }
            Err(e) => out.push(Err(e.to_string())),
        }
    }
    Ok(out)
}

/// Joins a parent rel path and a child name into the opendal-style rel path
/// convention: no leading '/', directories end with '/'.
fn join_rel(parent: &str, name: &str, is_dir: bool) -> String {
    let mut path = parent.trim_matches('/').to_string();
    if !path.is_empty() {
        path.push('/');
    }
    path.push_str(name);
    if is_dir {
        path.push('/');
    }
    path
}

/// Converts a raw FILETIME value (100ns ticks since 1601) to Unix seconds.
fn filetime_to_unix_secs(filetime_ticks: u64) -> u64 {
    (filetime_ticks / 10_000_000).saturating_sub(FILETIME_UNIX_OFFSET_SECS)
}

fn file_byte_stream(file: smb::File, offset: u64, length: Option<u64>) -> ByteStream {
    struct StreamState {
        file: smb::File,
        pos: u64,
        remaining: Option<u64>,
        failed: bool,
    }

    Box::pin(futures::stream::unfold(
        StreamState {
            file,
            pos: offset,
            remaining: length,
            failed: false,
        },
        |mut state| async move {
            if state.failed {
                let _ = state.file.close().await;
                return None;
            }
            let want = match state.remaining {
                Some(remaining) => READ_CHUNK_BYTES.min(remaining),
                None => READ_CHUNK_BYTES,
            };
            if want == 0 {
                let _ = state.file.close().await;
                return None;
            }
            let mut buf = vec![0u8; want as usize];
            match state.file.read_block(&mut buf, state.pos, None, false).await {
                Ok(0) => {
                    let _ = state.file.close().await;
                    None
                }
                Ok(read) => {
                    buf.truncate(read);
                    state.pos += read as u64;
                    if let Some(remaining) = state.remaining.as_mut() {
                        *remaining = remaining.saturating_sub(read as u64);
                    }
                    Some((Ok(Bytes::from(buf)), state))
                }
                Err(e) => {
                    state.failed = true;
                    Some((Err(e.to_string()), state))
                }
            }
        },
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn join_rel_follows_opendal_conventions() {
        assert_eq!("comics/", join_rel("/", "comics", true));
        assert_eq!("cover.jpg", join_rel("", "cover.jpg", false));
        assert_eq!("comics/ch01/", join_rel("comics/", "ch01", true));
        assert_eq!("comics/ch01/p01.jpg", join_rel("comics/ch01/", "p01.jpg", false));
    }

    #[test]
    fn filetime_conversion_matches_known_values() {
        // 2009-07-25T23:00:00Z == FILETIME 128930364000000000
        assert_eq!(1_248_562_800, filetime_to_unix_secs(128_930_364_000_000_000));
        // Values before 1970 clamp to 0 instead of wrapping.
        assert_eq!(0, filetime_to_unix_secs(0));
        assert_eq!(0, filetime_to_unix_secs(1));
    }

    #[test]
    fn share_relative_prepends_root_prefix() {
        let fs = SmbFs::new(
            "nas.lan".into(),
            445,
            "media".into(),
            "comics/ongoing".into(),
            "user",
            "pass",
            None,
        )
        .expect("smbfs");
        assert_eq!("comics/ongoing", fs.share_relative("/"));
        assert_eq!("comics/ongoing/ch01", fs.share_relative("ch01/"));

        let no_prefix = SmbFs::new(
            "nas.lan".into(),
            445,
            "media".into(),
            String::new(),
            "user",
            "pass",
            Some("WORKGROUP"),
        )
        .expect("smbfs");
        assert_eq!("", no_prefix.share_relative("/"));
        assert_eq!("ch01/p1.jpg", no_prefix.share_relative("/ch01/p1.jpg"));
        assert_eq!("WORKGROUP\\user", no_prefix.auth_username);
    }

    #[test]
    fn empty_username_is_rejected() {
        assert!(SmbFs::new(
            "nas.lan".into(),
            445,
            "media".into(),
            String::new(),
            "",
            "",
            None,
        )
        .is_err());
    }
}
