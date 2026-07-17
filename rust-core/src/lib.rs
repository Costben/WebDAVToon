uniffi::setup_scaffolding!();

mod database;
mod media_proxy;
mod models;
mod remote_fs;
mod repository;
mod runtime;
mod smb_fs;

pub use models::{
    Folder, FolderInspection, MediaProxyInfo, MediaType, Photo, RemoteConfig, RemoteProtocol,
    SmbShare, SortOrder,
};
use repository::{RepoError, Repository};
use std::sync::Mutex;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum WebDavToonError {
    #[error("Configuration error: {0}")]
    ConfigError(String),
    #[error("Repository error: {0}")]
    RepositoryError(#[from] RepoError),
}

#[derive(uniffi::Object)]
pub struct RustRepository {
    inner: Mutex<Repository>,
}

#[uniffi::export]
impl RustRepository {
    #[uniffi::constructor]
    pub fn new(db_path: String) -> Self {
        Self {
            inner: Mutex::new(Repository::new(db_path)),
        }
    }

    pub fn init_remote(&self, config: RemoteConfig) -> Result<(), WebDavToonError> {
        let mut repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        repo.init_remote(config)?;
        Ok(())
    }

    pub fn read_file(&self, path: String) -> Result<Vec<u8>, WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.read_file(path)?)
    }

    pub fn delete_photo(&self, path: String) -> Result<(), WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.delete_photo(path)?)
    }

    pub fn delete_folder(&self, path: String) -> Result<(), WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.delete_folder(path)?)
    }

    pub fn get_photos(
        &self,
        path: String,
        sort_order: SortOrder,
        force_refresh: bool,
        recursive: bool,
    ) -> Result<Vec<Photo>, WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.get_photos(path, sort_order, force_refresh, recursive)?)
    }

    pub fn get_folders(
        &self,
        path: String,
        force_refresh: bool,
    ) -> Result<Vec<Folder>, WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.get_folders(path, force_refresh)?)
    }

    pub fn inspect_folder(&self, path: String) -> Result<FolderInspection, WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.inspect_folder(path)?)
    }

    pub fn test_remote(&self, config: RemoteConfig) -> Result<String, WebDavToonError> {
        let repo = self
            .inner
            .lock()
            .map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.test_remote(config)?)
    }
}

/// Starts the loopback media proxy on first call and returns its port and
/// auth token. Kotlin converts smb:// and ftp:// virtual URIs into
/// `http://127.0.0.1:{port}/{token}/{path}` requests against it.
#[uniffi::export]
pub fn ensure_media_proxy() -> Result<MediaProxyInfo, WebDavToonError> {
    media_proxy::ensure_media_proxy().map_err(WebDavToonError::ConfigError)
}

#[uniffi::export]
pub fn list_smb_shares(
    host: String,
    port: u16,
    username: String,
    password: String,
    domain: Option<String>,
) -> Result<Vec<SmbShare>, WebDavToonError> {
    runtime::global()
        .block_on(smb_fs::enumerate_shares(
            &host,
            port,
            &username,
            password,
            domain.as_deref(),
        ))
        .map_err(WebDavToonError::ConfigError)
}

#[uniffi::export]
pub fn init_logger() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("RustCore"),
    );
    log::info!("Rust logger initialized");
}

#[uniffi::export]
pub fn hello_from_rust(name: String) -> String {
    format!("Hello, {}! This is from Rust Core.", name)
}
