use crate::models::{Folder, MediaType, Photo, SortOrder};
use rusqlite::{params, Connection, Result};
use std::path::Path;

const FOLDER_PREVIEW_STRATEGY_VERSION: i64 = 6;

pub struct Database {
    conn: Connection,
}

impl Database {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self> {
        let conn = Connection::open(path)?;
        let db = Self { conn };
        db.init()?;
        Ok(db)
    }

    fn init(&self) -> Result<()> {
        // 创建 photos 表
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS photos (
                id TEXT PRIMARY KEY,
                parent_path TEXT NOT NULL,
                title TEXT NOT NULL,
                uri TEXT NOT NULL,
                is_local INTEGER NOT NULL,
                size INTEGER NOT NULL,
                date_modified INTEGER NOT NULL,
                media_type TEXT NOT NULL DEFAULT 'image',
                duration_ms INTEGER,
                last_synced INTEGER NOT NULL
            )",
            [],
        )?;

        // 创建 folders 表
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS folders (
                path TEXT PRIMARY KEY,
                parent_path TEXT NOT NULL,
                name TEXT NOT NULL,
                is_local INTEGER NOT NULL,
                has_sub_folders INTEGER NOT NULL,
                preview_uris TEXT, -- JSON array
                preview_strategy_version INTEGER NOT NULL DEFAULT 0,
                date_modified INTEGER NOT NULL DEFAULT 0,
                last_synced INTEGER NOT NULL
            )",
            [],
        )?;

        let has_date_modified = self.table_has_column("folders", "date_modified")?;

        if !has_date_modified {
            let _ = self.conn.execute(
                "ALTER TABLE folders ADD COLUMN date_modified INTEGER NOT NULL DEFAULT 0",
                [],
            );
        }

        let has_preview_strategy_version =
            self.table_has_column("folders", "preview_strategy_version")?;
        if !has_preview_strategy_version {
            let _ = self.conn.execute(
                "ALTER TABLE folders ADD COLUMN preview_strategy_version INTEGER NOT NULL DEFAULT 0",
                [],
            );
        }

        let has_media_type = self.table_has_column("photos", "media_type")?;
        if !has_media_type {
            let _ = self.conn.execute(
                "ALTER TABLE photos ADD COLUMN media_type TEXT NOT NULL DEFAULT 'image'",
                [],
            );
        }

        let has_duration_ms = self.table_has_column("photos", "duration_ms")?;
        if !has_duration_ms {
            let _ = self
                .conn
                .execute("ALTER TABLE photos ADD COLUMN duration_ms INTEGER", []);
        }

        // 创建索引
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_photos_parent ON photos(parent_path)",
            [],
        )?;
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders(parent_path)",
            [],
        )?;

        self.trim_folder_preview_cache()?;

        Ok(())
    }

    pub fn save_photos(&mut self, parent_path: &str, photos: &[Photo]) -> Result<()> {
        let tx = self.conn.transaction()?;

        // 清除旧数据 (简单策略：全量覆盖该目录)
        tx.execute("DELETE FROM photos WHERE parent_path = ?", [parent_path])?;

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        {
            let mut stmt = tx.prepare(
                "INSERT INTO photos (id, parent_path, title, uri, is_local, size, date_modified, media_type, duration_ms, last_synced)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )?;

            for photo in photos {
                stmt.execute(params![
                    photo.id,
                    parent_path,
                    photo.title,
                    photo.uri,
                    photo.is_local as i32,
                    photo.size as i64,
                    photo.date_modified as i64,
                    media_type_to_db(photo.media_type),
                    photo.duration_ms.map(|it| it as i64),
                    now as i64
                ])?;
            }
        }

        tx.commit()
    }

    pub fn get_photos(&self, parent_path: &str, sort_order: SortOrder) -> Result<Vec<Photo>> {
        let order_clause = match sort_order {
            SortOrder::NameAsc => "title ASC",
            SortOrder::NameDesc => "title DESC",
            SortOrder::DateDesc => "date_modified DESC",
        };

        let mut stmt = self.conn.prepare(&format!(
            "SELECT id, title, uri, is_local, size, date_modified, media_type, duration_ms FROM photos WHERE parent_path = ? ORDER BY {}",
            order_clause
        ))?;

        let photo_iter = stmt.query_map([parent_path], |row| {
            let media_type_raw: Option<String> = row.get(6)?;
            Ok(Photo {
                id: row.get(0)?,
                title: row.get(1)?,
                uri: row.get(2)?,
                is_local: row.get::<_, i32>(3)? != 0,
                size: row.get::<_, i64>(4)? as u64,
                date_modified: row.get::<_, i64>(5)? as u64,
                media_type: media_type_from_db(media_type_raw.as_deref()),
                duration_ms: row.get::<_, Option<i64>>(7)?.map(|it| it as u64),
            })
        })?;

        let mut photos = Vec::new();
        for photo in photo_iter {
            photos.push(photo?);
        }
        Ok(photos)
    }

    pub fn delete_photo(&mut self, id: &str) -> Result<()> {
        self.conn.execute("DELETE FROM photos WHERE id = ?", [id])?;
        Ok(())
    }

    pub fn save_folders(&mut self, parent_path: &str, folders: &[Folder]) -> Result<()> {
        let tx = self.conn.transaction()?;

        // 清除旧数据
        tx.execute("DELETE FROM folders WHERE parent_path = ?", [parent_path])?;

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        {
            let mut stmt = tx.prepare(
                "INSERT INTO folders (path, parent_path, name, is_local, has_sub_folders, preview_uris, preview_strategy_version, date_modified, last_synced)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )?;

            for folder in folders {
                let previews_json = serde_json::to_string(&folder.preview_uris).unwrap_or_default();
                stmt.execute(params![
                    folder.path,
                    parent_path,
                    folder.name,
                    folder.is_local as i32,
                    folder.has_sub_folders as i32,
                    previews_json,
                    FOLDER_PREVIEW_STRATEGY_VERSION,
                    folder.date_modified as i64,
                    now as i64
                ])?;
            }
        }

        tx.commit()
    }

    pub fn get_folders(&self, parent_path: &str) -> Result<Vec<Folder>> {
        let mut stmt = self.conn.prepare(
            "SELECT path, name, is_local, has_sub_folders, preview_uris, date_modified
             FROM folders
             WHERE parent_path = ?
               AND preview_strategy_version = ?
             ORDER BY name COLLATE NOCASE ASC",
        )?;

        let folder_iter = stmt.query_map(
            params![parent_path, FOLDER_PREVIEW_STRATEGY_VERSION],
            |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, i32>(2)? != 0,
                    row.get::<_, i32>(3)? != 0,
                    row.get::<_, Option<String>>(4)?.unwrap_or_default(),
                    row.get::<_, i64>(5)? as u64,
                ))
            },
        )?;

        let mut folders = Vec::new();
        let mut trimmed_updates = Vec::new();
        for folder in folder_iter {
            let (path, name, is_local, has_sub_folders, preview_json, date_modified) = folder?;
            let preview_uris_full =
                serde_json::from_str::<Vec<String>>(&preview_json).unwrap_or_default();
            let preview_uris = preview_uris_full
                .iter()
                .take(4)
                .cloned()
                .collect::<Vec<_>>();

            if preview_json.len() > 4096 {
                let trimmed_json = serde_json::to_string(&preview_uris).unwrap_or_default();
                trimmed_updates.push((path.clone(), trimmed_json));
            }

            folders.push(Folder {
                path,
                name,
                is_local,
                has_sub_folders,
                preview_uris,
                date_modified,
            });
        }

        for (path, trimmed_json) in trimmed_updates {
            self.conn.execute(
                "UPDATE folders SET preview_uris = ? WHERE path = ?",
                params![trimmed_json, path],
            )?;
        }
        Ok(folders)
    }

    fn trim_folder_preview_cache(&self) -> Result<()> {
        let oversized_rows = {
            let mut stmt = self.conn.prepare(
                "SELECT path, preview_uris
                 FROM folders
                 WHERE preview_uris IS NOT NULL
                   AND LENGTH(preview_uris) > 4096",
            )?;

            let row_iter = stmt.query_map([], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            })?;

            let mut rows = Vec::new();
            for row in row_iter {
                rows.push(row?);
            }
            rows
        };

        if oversized_rows.is_empty() {
            return Ok(());
        }

        for (path, preview_json) in oversized_rows {
            let trimmed = serde_json::from_str::<Vec<String>>(&preview_json)
                .unwrap_or_default()
                .into_iter()
                .take(4)
                .collect::<Vec<_>>();
            let trimmed_json = serde_json::to_string(&trimmed).unwrap_or_default();
            self.conn.execute(
                "UPDATE folders SET preview_uris = ? WHERE path = ?",
                params![trimmed_json, path],
            )?;
        }

        Ok(())
    }

    fn table_has_column(&self, table: &str, column: &str) -> Result<bool> {
        let mut stmt = self
            .conn
            .prepare(&format!("PRAGMA table_info({})", table))?;
        let columns = stmt.query_map([], |row| row.get::<_, String>(1))?;

        for current in columns {
            if current? == column {
                return Ok(true);
            }
        }

        Ok(false)
    }
}

fn media_type_to_db(value: MediaType) -> &'static str {
    match value {
        MediaType::Image => "image",
        MediaType::Video => "video",
    }
}

fn media_type_from_db(value: Option<&str>) -> MediaType {
    match value.unwrap_or_default().to_ascii_lowercase().as_str() {
        "video" => MediaType::Video,
        _ => MediaType::Image,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_db_path(name: &str) -> std::path::PathBuf {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        std::env::temp_dir().join(format!("webdavtoon-{name}-{unique}.db"))
    }

    fn photo(id: &str, title: &str, date_modified: u64) -> Photo {
        Photo {
            id: id.to_string(),
            title: title.to_string(),
            uri: format!("https://example.com/{id}.jpg"),
            is_local: false,
            size: 42,
            date_modified,
            media_type: MediaType::Image,
            duration_ms: None,
        }
    }

    #[test]
    fn save_and_get_photos_respects_sort_order() {
        let db_path = temp_db_path("photos");
        let mut db = Database::open(&db_path).expect("open db");

        db.save_photos(
            "/library",
            &[
                photo("b", "Beta", 2),
                photo("a", "Alpha", 3),
                photo("c", "Gamma", 1),
            ],
        )
        .expect("save photos");

        let by_name = db
            .get_photos("/library", SortOrder::NameAsc)
            .expect("get by name");
        assert_eq!(
            vec!["Alpha", "Beta", "Gamma"],
            by_name
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );

        let by_date = db
            .get_photos("/library", SortOrder::DateDesc)
            .expect("get by date");
        assert_eq!(
            vec!["Alpha", "Beta", "Gamma"],
            by_date
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );

        let _ = std::fs::remove_file(db_path);
    }

    #[test]
    fn delete_photo_removes_cached_row() {
        let db_path = temp_db_path("delete-photo");
        let mut db = Database::open(&db_path).expect("open db");

        db.save_photos(
            "/library",
            &[photo("first", "First", 1), photo("second", "Second", 2)],
        )
        .expect("save photos");
        db.delete_photo("first").expect("delete photo");

        let remaining = db
            .get_photos("/library", SortOrder::NameAsc)
            .expect("get photos");
        assert_eq!(
            vec!["Second"],
            remaining
                .iter()
                .map(|it| it.title.as_str())
                .collect::<Vec<_>>()
        );

        let _ = std::fs::remove_file(db_path);
    }

    fn folder(
        path: &str,
        name: &str,
        has_sub_folders: bool,
        preview_uris: &[&str],
        date_modified: u64,
    ) -> Folder {
        Folder {
            path: path.to_string(),
            name: name.to_string(),
            is_local: false,
            has_sub_folders,
            preview_uris: preview_uris.iter().map(|it| it.to_string()).collect(),
            date_modified,
        }
    }

    #[test]
    fn save_and_get_folders_round_trip_cached_metadata() {
        let db_path = temp_db_path("folders");
        let mut db = Database::open(&db_path).expect("open db");

        db.save_folders(
            "/library",
            &[
                folder(
                    "/library/beta/",
                    "beta",
                    false,
                    &["https://example.com/beta/1.jpg"],
                    2,
                ),
                folder(
                    "/library/alpha/",
                    "alpha",
                    true,
                    &[
                        "https://example.com/alpha/1.jpg",
                        "https://example.com/alpha/2.jpg",
                    ],
                    5,
                ),
            ],
        )
        .expect("save folders");

        let folders = db.get_folders("/library").expect("get folders");
        assert_eq!(
            vec!["alpha", "beta"],
            folders
                .iter()
                .map(|it| it.name.as_str())
                .collect::<Vec<_>>()
        );
        assert_eq!(2, folders[0].preview_uris.len());
        assert!(folders[0].has_sub_folders);
        assert_eq!(5, folders[0].date_modified);

        let _ = std::fs::remove_file(db_path);
    }
}
