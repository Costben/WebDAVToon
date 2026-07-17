use crate::database::Database;
use crate::models::{Folder, FolderInspection, Photo, RemoteConfig, SortOrder};
use crate::remote_fs::RemoteService;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use tokio::runtime::Runtime;

#[derive(thiserror::Error, Debug, uniffi::Error)]
pub enum RepoError {
    #[error("Remote FS error: {0}")]
    Remote(String),
    #[error("Configuration error: {0}")]
    Config(String),
    #[error("Database error: {0}")]
    Database(String),
}

pub struct Repository {
    remote: Option<Arc<RemoteService>>,
    db: Option<Arc<Mutex<Database>>>,
    current_endpoint: Option<String>,
}

fn runtime() -> &'static Runtime {
    crate::runtime::global()
}

impl Repository {
    pub fn new(db_path: String) -> Self {
        let db = Database::open(&db_path)
            .ok()
            .map(|d| Arc::new(Mutex::new(d)));
        if db.is_none() {
            log::warn!(
                "Failed to open database at {}, running in memory-only mode",
                db_path
            );
        }

        Self {
            remote: None,
            db,
            current_endpoint: None,
        }
    }

    /// Initializes (or re-initializes) the remote service from a protocol
    /// config. The listing cache is cleared whenever the account identity
    /// (protocol, endpoint, username, domain) changes. The service is also
    /// published to the media proxy so byte requests always hit the current
    /// slot.
    pub fn init_remote(&mut self, config: RemoteConfig) -> Result<(), RepoError> {
        let identity = format!(
            "{}|{}|{}|{}",
            config.protocol.label(),
            config.endpoint,
            config.username,
            config.domain.as_deref().unwrap_or_default()
        );
        let changed = self.current_endpoint.as_deref() != Some(identity.as_str());

        let service = Arc::new(RemoteService::new(&config).map_err(RepoError::Remote)?);
        self.remote = Some(Arc::clone(&service));
        crate::media_proxy::set_byte_service(service);

        if changed {
            if let Some(db) = &self.db {
                if let Ok(mut db) = db.lock() {
                    if let Err(e) = db.clear_all_cache() {
                        log::warn!("Failed to clear cache on endpoint switch: {:?}", e);
                    } else {
                        log::info!("Cleared cache on endpoint switch to {}", config.endpoint);
                    }
                }
            }
            self.current_endpoint = Some(identity);
        }

        Ok(())
    }

    pub fn read_file(&self, path: String) -> Result<Vec<u8>, RepoError> {
        runtime().block_on(async {
            if let Some(ref service) = self.remote {
                let started = Instant::now();
                let result = service.read_file(&path).await.map_err(RepoError::Remote);
                if let Ok(ref data) = result {
                    log::info!(
                        "read_file path={} bytes={} elapsed_ms={}",
                        path,
                        data.len(),
                        started.elapsed().as_millis()
                    );
                }
                result
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })
    }

    pub fn delete_photo(&self, path: String) -> Result<(), RepoError> {
        runtime().block_on(async {
            if let Some(ref service) = self.remote {
                service
                    .delete_file(&path)
                    .await
                    .map_err(RepoError::Remote)?;

                // Clear from cache if exists
                if let Some(db) = &self.db {
                    if let Ok(mut db) = db.lock() {
                        let _ = db.delete_photo(&path);
                    }
                }

                Ok(())
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })
    }

    pub fn get_photos(
        &self,
        path: String,
        sort_order: SortOrder,
        force_refresh: bool,
        recursive: bool,
    ) -> Result<Vec<Photo>, RepoError> {
        // 1. Try to load from cache first if not forcing refresh
        // Note: Caching recursive results might be tricky, for now we only cache flat lists or rethink caching strategy
        if !force_refresh && !recursive {
            if let Some(db) = &self.db {
                if let Ok(db) = db.lock() {
                    if let Ok(photos) = db.get_photos(&path, sort_order.clone()) {
                        if !photos.is_empty() {
                            return Ok(photos);
                        }
                    }
                }
            }
        }

        // 2. Load from Remote
        let photos = runtime().block_on(async {
            if let Some(ref service) = self.remote {
                service
                    .list_photos(&path, sort_order.clone(), recursive)
                    .await
                    .map_err(RepoError::Remote)
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })?;

        // 3. Update cache (only for flat list)
        if !recursive {
            if let Some(db) = &self.db {
                if let Ok(mut db) = db.lock() {
                    let _ = db.save_photos(&path, &photos);
                }
            }
        }

        Ok(photos)
    }

    pub fn get_folders(&self, path: String, force_refresh: bool) -> Result<Vec<Folder>, RepoError> {
        if !force_refresh {
            if let Some(db) = &self.db {
                if let Ok(db) = db.lock() {
                    if let Ok(folders) = db.get_folders(&path) {
                        if !folders.is_empty() {
                            log::info!(
                                "get_folders cache hit path={} count={}",
                                path,
                                folders.len()
                            );
                            return Ok(folders);
                        }
                    }
                }
            }
        }

        let started = Instant::now();
        let folders = runtime().block_on(async {
            if let Some(ref service) = self.remote {
                service.list_folders(&path).await.map_err(RepoError::Remote)
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })?;

        if let Some(db) = &self.db {
            if let Ok(mut db) = db.lock() {
                let _ = db.save_folders(&path, &folders);
            }
        }

        log::info!(
            "get_folders remote path={} force_refresh={} count={} elapsed_ms={}",
            path,
            force_refresh,
            folders.len(),
            started.elapsed().as_millis()
        );

        Ok(folders)
    }

    pub fn inspect_folder(&self, path: String) -> Result<FolderInspection, RepoError> {
        runtime().block_on(async {
            if let Some(ref service) = self.remote.as_ref() {
                service
                    .inspect_folder(&path)
                    .await
                    .map_err(RepoError::Remote)
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })
    }

    pub fn delete_folder(&self, path: String) -> Result<(), RepoError> {
        runtime().block_on(async {
            if let Some(ref service) = self.remote {
                service.delete_dir(&path).await.map_err(RepoError::Remote)
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })
    }

    pub fn test_remote(&self, config: RemoteConfig) -> Result<String, RepoError> {
        runtime().block_on(async {
            let service = RemoteService::new(&config).map_err(RepoError::Remote)?;

            let names = service.list_root_names().await.map_err(RepoError::Remote)?;

            let mut report = String::from("Connection Successful!\n\nRoot contents (first 20):\n");
            for name in names {
                report.push_str(&format!("- {}\n", name));
            }
            if report.ends_with(":\n") {
                report.push_str("(Empty)");
            }
            Ok(report)
        })
    }
}
