use crate::database::Database;
use crate::models::{Folder, FolderInspection, Photo, SortOrder};
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
    remote: Option<RemoteService>,
    db: Option<Arc<Mutex<Database>>>,
    rt: Arc<Runtime>,
}

impl Repository {
    pub fn new(db_path: String) -> Self {
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime");

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
            rt: Arc::new(rt),
        }
    }

    pub fn init_webdav(
        &mut self,
        endpoint: String,
        username: String,
        password: String,
    ) -> Result<(), RepoError> {
        let service = RemoteService::new_webdav(&endpoint, &username, &password)
            .map_err(RepoError::Remote)?;
        self.remote = Some(service);
        Ok(())
    }

    pub fn read_file(&self, path: String) -> Result<Vec<u8>, RepoError> {
        self.rt.block_on(async {
            if let Some(ref service) = self.remote {
                service.read_file(&path).await.map_err(RepoError::Remote)
            } else {
                Err(RepoError::Config(
                    "Remote service not initialized".to_string(),
                ))
            }
        })
    }

    pub fn delete_photo(&self, path: String) -> Result<(), RepoError> {
        self.rt.block_on(async {
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
        let photos = self.rt.block_on(async {
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
        let folders = self.rt.block_on(async {
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
        self.rt.block_on(async {
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

    pub fn test_webdav(
        &self,
        endpoint: String,
        username: String,
        password: String,
    ) -> Result<String, RepoError> {
        self.rt.block_on(async {
            let service = RemoteService::new_webdav(&endpoint, &username, &password)
                .map_err(RepoError::Remote)?;

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
