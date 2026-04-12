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
