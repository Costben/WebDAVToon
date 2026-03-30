uniffi::setup_scaffolding!();

mod database;
mod models;
mod repository;
mod remote_fs;

pub use models::{Folder, FolderInspection, Photo, SortOrder};
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

    pub fn init_webdav(&self, endpoint: String, username: String, password: String) -> Result<(), WebDavToonError> {
        let mut repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        repo.init_webdav(endpoint, username, password)?;
        Ok(())
    }

    pub fn read_file(&self, path: String) -> Result<Vec<u8>, WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.read_file(path)?)
    }

    pub fn delete_photo(&self, path: String) -> Result<(), WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.delete_photo(path)?)
    }

    pub fn get_photos(&self, path: String, sort_order: SortOrder, force_refresh: bool, recursive: bool) -> Result<Vec<Photo>, WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.get_photos(path, sort_order, force_refresh, recursive)?)
    }

    pub fn get_folders(&self, path: String, force_refresh: bool) -> Result<Vec<Folder>, WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.get_folders(path, force_refresh)?)
    }

    pub fn inspect_folder(&self, path: String) -> Result<FolderInspection, WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.inspect_folder(path)?)
    }

    pub fn test_webdav(&self, endpoint: String, username: String, password: String) -> Result<String, WebDavToonError> {
        let repo = self.inner.lock().map_err(|_| WebDavToonError::ConfigError("Lock poisoned".into()))?;
        Ok(repo.test_webdav(endpoint, username, password)?)
    }
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
