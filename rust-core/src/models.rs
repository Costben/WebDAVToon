use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum MediaType {
    Image,
    Video,
}

impl Default for MediaType {
    fn default() -> Self {
        MediaType::Image
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Photo {
    pub id: String,
    pub title: String,
    pub uri: String,
    pub is_local: bool,
    pub size: u64,
    pub date_modified: u64,
    #[serde(default)]
    pub media_type: MediaType,
    #[serde(default)]
    pub duration_ms: Option<u64>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FolderInspection {
    pub has_sub_folders: bool,
    pub preview_uris: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Folder {
    pub path: String,
    pub name: String,
    pub is_local: bool,
    pub has_sub_folders: bool,
    pub preview_uris: Vec<String>,
    pub date_modified: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct SmbShare {
    pub name: String,
    pub remark: String,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum SortOrder {
    NameAsc,
    NameDesc,
    DateDesc,
}

impl Default for SortOrder {
    fn default() -> Self {
        SortOrder::DateDesc
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum RemoteProtocol {
    WebDav,
    Smb,
    Ftp,
}

impl RemoteProtocol {
    pub fn label(&self) -> &'static str {
        match self {
            RemoteProtocol::WebDav => "webdav",
            RemoteProtocol::Smb => "smb",
            RemoteProtocol::Ftp => "ftp",
        }
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteConfig {
    pub protocol: RemoteProtocol,
    /// Canonical endpoint string produced by the Kotlin normalizer, e.g.
    /// `https://host/dav`, `smb://host/share/sub`, `ftp://host:2121/pub`.
    /// Stored verbatim as the base for minted photo URIs.
    pub endpoint: String,
    pub username: String,
    pub password: String,
    /// SMB domain / workgroup; ignored by other protocols.
    pub domain: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct MediaProxyInfo {
    pub port: u16,
    pub token: String,
}
