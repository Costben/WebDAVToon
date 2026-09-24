// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
//! Loopback HTTP media proxy for protocols without native HTTP byte access
//! (SMB, FTP).
//!
//! The Kotlin side converts stable virtual URIs (`smb://…`, `ftp://…`) into
//! `http://127.0.0.1:{port}/{token}/{percent-encoded-rel-path}` at load time,
//! so Glide, video thumbnail retrievers, share/download flows, and external
//! video players all keep speaking plain HTTP.
//!
//! Deliberately minimal HTTP/1.1: GET/HEAD only, single `Range` support
//! (`206`/`416`), Content-Length framing, keep-alive, binds 127.0.0.1 only.
//! Requests must carry the per-process random token as the first path
//! segment; anything else is a 404. The second segment identifies the owning
//! slot (`{endpoint}\n{username}`, percent-encoded) so favorites minted by
//! any registered slot resolve even while another slot is current. Bodies
//! stream in chunks — whole files are never buffered in memory. WebDAV bytes
//! never go through the proxy.

use crate::models::{MediaProxyInfo, RemoteConfig};
use crate::remote_fs::RemoteService;
use futures::StreamExt;
use percent_encoding::percent_decode_str;
use std::collections::HashMap;
use std::sync::{Arc, LazyLock, Mutex, RwLock};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};

const MAX_REQUEST_HEAD_BYTES: usize = 16 * 1024;

/// Services available to the proxy, keyed by [`slot_key`]. Registration is
/// idempotent per identity; re-registering replaces the previous entry.
static SERVICE_REGISTRY: LazyLock<RwLock<HashMap<String, Arc<RemoteService>>>> =
    LazyLock::new(|| RwLock::new(HashMap::new()));
/// Configs of registered slots, kept so a missing service can be rebuilt
/// lazily (e.g. after the registry was populated by another process-lifetime
/// path that dropped its service).
static CONFIG_REGISTRY: LazyLock<Mutex<HashMap<String, RemoteConfig>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));
static PROXY_INFO: Mutex<Option<MediaProxyInfo>> = Mutex::new(None);

/// Stable identity of a byte-serving slot: canonical endpoint plus username.
/// Must match exactly what the Kotlin side encodes into proxy URLs.
pub fn slot_key(endpoint: &str, username: &str) -> String {
    format!("{}\n{}", endpoint, username)
}

/// Builds a service from `config` and registers it under its slot key.
/// Returns the key. Safe to call repeatedly.
pub fn register_remote(config: &RemoteConfig) -> Result<String, String> {
    let service = Arc::new(RemoteService::new(config)?);
    Ok(register_service(config, service))
}

/// Registers an already-built service plus its config. Idempotent per slot.
pub fn register_service(config: &RemoteConfig, service: Arc<RemoteService>) -> String {
    let key = slot_key(&config.endpoint, &config.username);
    SERVICE_REGISTRY.write().unwrap().insert(key.clone(), service);
    CONFIG_REGISTRY.lock().unwrap().insert(key.clone(), config.clone());
    key
}

/// Looks up the service for a request key; rebuilds it lazily from the
/// config registry on a cache miss.
fn service_for_key(key: &str) -> Option<Arc<RemoteService>> {
    if let Some(service) = SERVICE_REGISTRY.read().unwrap().get(key) {
        return Some(Arc::clone(service));
    }
    let config = CONFIG_REGISTRY.lock().unwrap().get(key).cloned()?;
    match register_remote(&config) {
        Ok(_) => SERVICE_REGISTRY.read().unwrap().get(key).map(Arc::clone),
        Err(e) => {
            log::warn!("media_proxy failed to build service for slot: {}", e);
            None
        }
    }
}

/// Starts the proxy on first call (bind 127.0.0.1, random port + token) and
/// returns the same info on every subsequent call for the process lifetime.
pub fn ensure_media_proxy() -> Result<MediaProxyInfo, String> {
    let mut guard = PROXY_INFO.lock().unwrap();
    if let Some(info) = guard.as_ref() {
        return Ok(info.clone());
    }

    let runtime = crate::runtime::global();
    let listener = runtime
        .block_on(TcpListener::bind(("127.0.0.1", 0)))
        .map_err(|e| format!("Failed to bind media proxy: {}", e))?;
    let port = listener
        .local_addr()
        .map_err(|e| e.to_string())?
        .port();
    let token: Arc<str> = Arc::from(format!("{:032x}", rand::random::<u128>()));

    let info = MediaProxyInfo {
        port,
        token: token.to_string(),
    };
    runtime.spawn(accept_loop(listener, token));
    log::info!("media_proxy listening on 127.0.0.1:{}", port);

    *guard = Some(info.clone());
    Ok(info)
}

async fn accept_loop(listener: TcpListener, token: Arc<str>) {
    loop {
        match listener.accept().await {
            Ok((stream, _)) => {
                let token = Arc::clone(&token);
                tokio::spawn(async move {
                    if let Err(e) = handle_conn(stream, token).await {
                        log::debug!("media_proxy connection closed: {}", e);
                    }
                });
            }
            Err(e) => {
                log::warn!("media_proxy accept failed: {}", e);
            }
        }
    }
}

struct RequestHead {
    method: String,
    target: String,
    range: Option<String>,
    keep_alive: bool,
}

async fn handle_conn(stream: TcpStream, token: Arc<str>) -> Result<(), String> {
    let (read_half, mut writer) = stream.into_split();
    let mut reader = BufReader::new(read_half);

    loop {
        let Some(head) = read_request_head(&mut reader).await? else {
            return Ok(()); // clean EOF between requests
        };
        let request = parse_request_head(&head)?;
        let keep_alive = request.keep_alive;

        handle_request(&request, &token, &mut writer).await?;
        writer.flush().await.map_err(|e| e.to_string())?;

        if !keep_alive {
            return Ok(());
        }
    }
}

async fn handle_request(
    request: &RequestHead,
    token: &str,
    writer: &mut (impl AsyncWriteExt + Unpin),
) -> Result<(), String> {
    let is_head = match request.method.as_str() {
        "GET" => false,
        "HEAD" => true,
        _ => {
            return write_simple_response(writer, "405 Method Not Allowed", "method not allowed")
                .await;
        }
    };

    let Some((slot_key, rel_path)) = authorize_and_decode(&request.target, token) else {
        return write_simple_response(writer, "404 Not Found", "not found").await;
    };

    let Some(service) = service_for_key(&slot_key) else {
        log::info!("media_proxy no service registered for slot key");
        return write_simple_response(writer, "404 Not Found", "not found").await;
    };

    log::debug!(
        "media_proxy request path={} range={:?}",
        rel_path,
        request.range
    );
    let open_started = std::time::Instant::now();
    // Single backend open per request: the size and the byte reader share it.
    let (total, media) = match service.open_ranged(&rel_path).await {
        Ok(opened) => opened,
        Err(e) => {
            log::info!("media_proxy stat failed for {}: {}", rel_path, e);
            return write_simple_response(writer, "404 Not Found", "not found").await;
        }
    };
    log::debug!(
        "media_proxy stat path={} total={} elapsed_ms={}",
        rel_path,
        total,
        open_started.elapsed().as_millis()
    );

    let content_type = content_type_for(&rel_path);
    let range_outcome = match request.range.as_deref() {
        Some(value) => parse_range(value, total),
        None => RangeOutcome::Full,
    };

    let (status, offset, length, extra_headers) = match range_outcome {
        RangeOutcome::Full => (
            "200 OK",
            0u64,
            total,
            vec![format!("Content-Length: {}", total)],
        ),
        RangeOutcome::Partial(start, end) => (
            "206 Partial Content",
            start,
            end - start + 1,
            vec![
                format!("Content-Length: {}", end - start + 1),
                format!("Content-Range: bytes {}-{}/{}", start, end, total),
            ],
        ),
        RangeOutcome::Unsatisfiable => {
            let head = format!(
                "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */{}\r\nContent-Length: 0\r\nAccept-Ranges: bytes\r\n\r\n",
                total
            );
            return writer
                .write_all(head.as_bytes())
                .await
                .map_err(|e| e.to_string());
        }
    };

    let mut head = format!(
        "HTTP/1.1 {}\r\nContent-Type: {}\r\nAccept-Ranges: bytes\r\n",
        status, content_type
    );
    for header in &extra_headers {
        head.push_str(header);
        head.push_str("\r\n");
    }
    head.push_str("\r\n");

    if is_head || length == 0 {
        return writer
            .write_all(head.as_bytes())
            .await
            .map_err(|e| e.to_string());
    }

    let mut body = match media.read_range(offset, Some(length)).await {
        Ok(stream) => stream,
        Err(e) => {
            log::warn!("media_proxy read_range failed for {}: {}", rel_path, e);
            return write_simple_response(writer, "502 Bad Gateway", "read failed").await;
        }
    };

    let started = std::time::Instant::now();
    writer
        .write_all(head.as_bytes())
        .await
        .map_err(|e| e.to_string())?;

    let mut sent = 0u64;
    while let Some(chunk) = body.next().await {
        let chunk = chunk.map_err(|e| {
            // Headers are already on the wire; the only safe move is to drop
            // the connection so the client sees a truncated body and retries.
            format!("mid-stream read error for {}: {}", rel_path, e)
        })?;
        if chunk.is_empty() {
            continue;
        }
        let allowed = (length - sent).min(chunk.len() as u64) as usize;
        writer
            .write_all(&chunk[..allowed])
            .await
            .map_err(|e| e.to_string())?;
        sent += allowed as u64;
        if sent >= length {
            break;
        }
    }
    let elapsed_ms = started.elapsed().as_millis();
    // Slow serves stay at info; routine ones drop to debug to keep logcat
    // usable during bulk thumbnail loading.
    if elapsed_ms >= 500 {
        log::info!(
            "media_proxy served slow path={} status={} offset={} bytes={} elapsed_ms={}",
            rel_path,
            status,
            offset,
            sent,
            elapsed_ms
        );
    } else {
        log::debug!(
            "media_proxy served path={} status={} offset={} bytes={} elapsed_ms={}",
            rel_path,
            status,
            offset,
            sent,
            elapsed_ms
        );
    }

    if sent < length {
        return Err(format!(
            "short body for {}: sent {} of {}",
            rel_path, sent, length
        ));
    }
    Ok(())
}

/// Validates `/{token}/{encoded-slot-key}/{encoded-path}` and returns the
/// decoded slot key and remote path.
fn authorize_and_decode(target: &str, token: &str) -> Option<(String, String)> {
    let target = target.split(['?', '#']).next().unwrap_or("");
    let without_slash = target.strip_prefix('/')?;
    let (request_token, remainder) = without_slash.split_once('/')?;
    if request_token != token {
        return None;
    }
    let (encoded_key, encoded_path) = remainder.split_once('/')?;
    if encoded_path.is_empty() {
        return None;
    }
    let key = percent_decode_str(encoded_key).decode_utf8().ok()?.into_owned();
    if !key.contains('\n') {
        return None; // not a valid slot identity
    }
    let decoded = percent_decode_str(encoded_path)
        .decode_utf8()
        .ok()?
        .into_owned();
    if decoded.contains("../") {
        return None;
    }
    Some((key, decoded.trim_start_matches('/').to_string()))
}

async fn read_request_head<R: AsyncBufReadExt + Unpin>(
    reader: &mut R,
) -> Result<Option<String>, String> {
    let mut head: Vec<u8> = Vec::new();
    loop {
        let mut line = Vec::new();
        let read = reader
            .read_until(b'\n', &mut line)
            .await
            .map_err(|e| e.to_string())?;
        if read == 0 {
            return if head.is_empty() {
                Ok(None)
            } else {
                Err("truncated request head".to_string())
            };
        }
        if head.len() + line.len() > MAX_REQUEST_HEAD_BYTES {
            return Err("request head too large".to_string());
        }
        let is_blank = line == b"\r\n" || line == b"\n";
        if is_blank {
            if head.is_empty() {
                continue; // tolerate leading blank lines (RFC 9112 §2.2)
            }
            break;
        }
        head.extend_from_slice(&line);
    }
    Ok(Some(String::from_utf8_lossy(&head).into_owned()))
}

fn parse_request_head(head: &str) -> Result<RequestHead, String> {
    let mut lines = head.lines();
    let request_line = lines.next().ok_or("empty request")?;
    let mut parts = request_line.split_whitespace();
    let method = parts.next().ok_or("missing method")?.to_string();
    let target = parts.next().ok_or("missing target")?.to_string();
    let version = parts.next().unwrap_or("HTTP/1.1");

    let mut range = None;
    let mut connection = None;
    for line in lines {
        let Some((name, value)) = line.split_once(':') else {
            continue;
        };
        let name = name.trim().to_ascii_lowercase();
        let value = value.trim();
        match name.as_str() {
            "range" => range = Some(value.to_string()),
            "connection" => connection = Some(value.to_ascii_lowercase()),
            _ => {}
        }
    }

    let keep_alive = match connection.as_deref() {
        Some(value) => !value.contains("close"),
        None => version != "HTTP/1.0",
    };

    Ok(RequestHead {
        method,
        target,
        range,
        keep_alive,
    })
}

#[derive(Debug, PartialEq, Eq)]
enum RangeOutcome {
    /// Serve the whole file with 200 (no header, or a malformed one we ignore).
    Full,
    /// Serve `start..=end` with 206.
    Partial(u64, u64),
    /// Reply 416.
    Unsatisfiable,
}

/// RFC 7233 single-range subset. Malformed values fall back to `Full` (200),
/// syntactically valid but unservable ranges are `Unsatisfiable` (416).
fn parse_range(value: &str, total: u64) -> RangeOutcome {
    let Some(spec) = value.trim().strip_prefix("bytes=") else {
        return RangeOutcome::Full;
    };
    if spec.contains(',') {
        return RangeOutcome::Full; // multi-range unsupported; 200 is always legal
    }
    let spec = spec.trim();

    if let Some(suffix) = spec.strip_prefix('-') {
        return match suffix.parse::<u64>() {
            Ok(0) => RangeOutcome::Unsatisfiable,
            Ok(_) if total == 0 => RangeOutcome::Unsatisfiable,
            Ok(n) => RangeOutcome::Partial(total.saturating_sub(n), total - 1),
            Err(_) => RangeOutcome::Full,
        };
    }

    let Some((start_str, end_str)) = spec.split_once('-') else {
        return RangeOutcome::Full;
    };
    let Ok(start) = start_str.trim().parse::<u64>() else {
        return RangeOutcome::Full;
    };
    if start >= total {
        return RangeOutcome::Unsatisfiable;
    }
    match end_str.trim() {
        "" => RangeOutcome::Partial(start, total - 1),
        end_str => match end_str.parse::<u64>() {
            Ok(end) if start <= end => RangeOutcome::Partial(start, end.min(total - 1)),
            _ => RangeOutcome::Full,
        },
    }
}

fn content_type_for(path: &str) -> &'static str {
    let extension = path
        .rsplit('/')
        .next()
        .and_then(|name| name.rsplit_once('.'))
        .map(|(_, ext)| ext.to_ascii_lowercase())
        .unwrap_or_default();
    match extension.as_str() {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "webp" => "image/webp",
        "gif" => "image/gif",
        "bmp" => "image/bmp",
        "heic" => "image/heic",
        "heif" => "image/heif",
        "mp4" => "video/mp4",
        "m4v" => "video/x-m4v",
        "mkv" => "video/x-matroska",
        "mov" => "video/quicktime",
        "avi" => "video/x-msvideo",
        "webm" => "video/webm",
        "3gp" => "video/3gpp",
        "ts" | "m2ts" => "video/mp2t",
        "wmv" => "video/x-ms-wmv",
        "asf" => "video/x-ms-asf",
        _ => "application/octet-stream",
    }
}

async fn write_simple_response(
    writer: &mut (impl AsyncWriteExt + Unpin),
    status: &str,
    body: &str,
) -> Result<(), String> {
    let response = format!(
        "HTTP/1.1 {}\r\nContent-Type: text/plain\r\nContent-Length: {}\r\n\r\n{}",
        status,
        body.len(),
        body
    );
    writer
        .write_all(response.as_bytes())
        .await
        .map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};

    #[test]
    fn parse_range_table() {
        // open-ended
        assert_eq!(RangeOutcome::Partial(0, 99), parse_range("bytes=0-", 100));
        assert_eq!(RangeOutcome::Partial(90, 99), parse_range("bytes=90-", 100));
        // bounded (inclusive), end clamped to total-1
        assert_eq!(
            RangeOutcome::Partial(10, 19),
            parse_range("bytes=10-19", 100)
        );
        assert_eq!(
            RangeOutcome::Partial(10, 99),
            parse_range("bytes=10-1000", 100)
        );
        // suffix
        assert_eq!(RangeOutcome::Partial(90, 99), parse_range("bytes=-10", 100));
        assert_eq!(RangeOutcome::Partial(0, 99), parse_range("bytes=-500", 100));
        // unsatisfiable
        assert_eq!(RangeOutcome::Unsatisfiable, parse_range("bytes=100-", 100));
        assert_eq!(RangeOutcome::Unsatisfiable, parse_range("bytes=-0", 100));
        assert_eq!(RangeOutcome::Unsatisfiable, parse_range("bytes=0-", 0));
        // malformed -> full
        assert_eq!(RangeOutcome::Full, parse_range("bytes=abc-", 100));
        assert_eq!(RangeOutcome::Full, parse_range("bytes=5", 100));
        assert_eq!(RangeOutcome::Full, parse_range("items=0-10", 100));
        assert_eq!(RangeOutcome::Full, parse_range("bytes=20-10", 100));
        assert_eq!(RangeOutcome::Full, parse_range("bytes=0-10,20-30", 100));
    }

    #[test]
    fn authorize_and_decode_enforces_token_and_decodes_path() {
        let key = slot_key("smb://nas.lan/media", "user");
        assert_eq!(
            Some((key.clone(), "comics/ch 01/p1.jpg".to_string())),
            authorize_and_decode("/tok123/smb%3A%2F%2Fnas.lan%2Fmedia%0Auser/comics%2Fch%2001%2Fp1.jpg", "tok123")
        );
        assert_eq!(None, authorize_and_decode("/wrong/smb%3A%2F%2Fhost%0Au/a.jpg", "tok123"));
        assert_eq!(None, authorize_and_decode("/tok123/", "tok123"));
        assert_eq!(None, authorize_and_decode("/tok123", "tok123"));
        assert_eq!(
            None,
            authorize_and_decode("/tok123/smb%3A%2F%2Fhost%0Au/a/../../etc/passwd", "tok123")
        );
        // A path segment without a slot key is rejected.
        assert_eq!(None, authorize_and_decode("/tok123/a.jpg", "tok123"));
        // query strings are ignored
        let ftp_key = slot_key("ftp://host", "u");
        assert_eq!(
            Some((ftp_key, "a.mp4".to_string())),
            authorize_and_decode("/tok123/ftp%3A%2F%2Fhost%0Au/a.mp4?x=1", "tok123")
        );
    }

    #[test]
    fn content_type_covers_supported_media() {
        assert_eq!("image/jpeg", content_type_for("a/b/cover.JPG"));
        assert_eq!("video/x-matroska", content_type_for("movie.mkv"));
        assert_eq!("application/octet-stream", content_type_for("notes.txt"));
        assert_eq!("application/octet-stream", content_type_for("noext"));
    }

    struct TestClient {
        stream: std::net::TcpStream,
    }

    struct TestResponse {
        status: String,
        headers: Vec<(String, String)>,
        body: Vec<u8>,
    }

    impl TestClient {
        fn connect(port: u16) -> Self {
            let stream = std::net::TcpStream::connect(("127.0.0.1", port)).expect("connect");
            stream
                .set_read_timeout(Some(std::time::Duration::from_secs(10)))
                .unwrap();
            Self { stream }
        }

        fn request(&mut self, method: &str, target: &str, range: Option<&str>) -> TestResponse {
            let mut req = format!("{} {} HTTP/1.1\r\nHost: 127.0.0.1\r\n", method, target);
            if let Some(range) = range {
                req.push_str(&format!("Range: {}\r\n", range));
            }
            req.push_str("\r\n");
            self.stream.write_all(req.as_bytes()).expect("write");

            let mut head = Vec::new();
            let mut byte = [0u8; 1];
            while !head.ends_with(b"\r\n\r\n") {
                let n = self.stream.read(&mut byte).expect("read head");
                assert!(n > 0, "connection closed while reading head");
                head.push(byte[0]);
            }
            let head_text = String::from_utf8_lossy(&head).into_owned();
            let mut lines = head_text.lines();
            let status = lines.next().unwrap_or_default().to_string();
            let headers: Vec<(String, String)> = lines
                .filter_map(|l| l.split_once(':'))
                .map(|(k, v)| (k.trim().to_ascii_lowercase(), v.trim().to_string()))
                .collect();
            let content_length: usize = headers
                .iter()
                .find(|(k, _)| k == "content-length")
                .map(|(_, v)| v.parse().expect("content-length"))
                .unwrap_or(0);
            let mut body = vec![0u8; content_length];
            if content_length > 0 && method != "HEAD" {
                self.stream.read_exact(&mut body).expect("read body");
            } else {
                body.clear();
            }
            TestResponse {
                status,
                headers,
                body,
            }
        }
    }

    impl TestResponse {
        fn header(&self, name: &str) -> Option<&str> {
            self.headers
                .iter()
                .find(|(k, _)| k == name)
                .map(|(_, v)| v.as_str())
        }
    }

    /// Single end-to-end test: the proxy's byte service and info are process
    /// globals, so all scenarios run sequentially inside one test.
    #[test]
    fn proxy_serves_local_fs_end_to_end() {
        let dir = std::env::temp_dir().join(format!(
            "webdavtoon-proxy-test-{}",
            std::process::id()
        ));
        std::fs::create_dir_all(dir.join("sub dir")).expect("mkdir");
        let payload: Vec<u8> = (0..100_000u32).map(|i| (i % 251) as u8).collect();
        std::fs::write(dir.join("file.bin"), &payload).expect("write file");
        std::fs::write(dir.join("sub dir").join("pic.jpg"), b"jpegdata").expect("write pic");

        let service = RemoteService::new_local_fs_for_tests(
            dir.to_str().expect("utf8 temp dir"),
            "smb://testhost/share",
        )
        .expect("service");
        let key = slot_key("smb://testhost/share", "tester");
        SERVICE_REGISTRY
            .write()
            .unwrap()
            .insert(key.clone(), Arc::new(service));
        let info = ensure_media_proxy().expect("proxy");
        let token = info.token.as_str();
        let encoded_key = "smb%3A%2F%2Ftesthost%2Fshare%0Atester";
        let mut client = TestClient::connect(info.port);

        // Full GET
        let full = client.request("GET", &format!("/{}/{}/file.bin", token, encoded_key), None);
        assert!(full.status.contains("200"), "status: {}", full.status);
        assert_eq!(Some("bytes"), full.header("accept-ranges"));
        assert_eq!(payload, full.body);

        // Bounded range (keep-alive: same connection)
        let part = client.request(
            "GET",
            &format!("/{}/{}/file.bin", token, encoded_key),
            Some("bytes=10-19"),
        );
        assert!(part.status.contains("206"), "status: {}", part.status);
        assert_eq!(Some("bytes 10-19/100000"), part.header("content-range"));
        assert_eq!(&payload[10..20], &part.body[..]);

        // Open-ended range
        let tail = client.request(
            "GET",
            &format!("/{}/{}/file.bin", token, encoded_key),
            Some("bytes=99990-"),
        );
        assert!(tail.status.contains("206"));
        assert_eq!(&payload[99990..], &tail.body[..]);

        // Suffix range
        let suffix = client.request("GET", &format!("/{}/{}/file.bin", token, encoded_key), Some("bytes=-5"));
        assert!(suffix.status.contains("206"));
        assert_eq!(&payload[99995..], &suffix.body[..]);

        // Unsatisfiable range
        let bad_range = client.request(
            "GET",
            &format!("/{}/{}/file.bin", token, encoded_key),
            Some("bytes=100000-"),
        );
        assert!(bad_range.status.contains("416"), "status: {}", bad_range.status);
        assert_eq!(Some("bytes */100000"), bad_range.header("content-range"));

        // Percent-encoded path
        let pic = client.request("GET", &format!("/{}/{}/sub%20dir/pic.jpg", token, encoded_key), None);
        assert!(pic.status.contains("200"));
        assert_eq!(Some("image/jpeg"), pic.header("content-type"));
        assert_eq!(b"jpegdata", &pic.body[..]);

        // HEAD carries headers but no body
        let head = client.request("HEAD", &format!("/{}/{}/file.bin", token, encoded_key), None);
        assert!(head.status.contains("200"));
        assert_eq!(Some("100000"), head.header("content-length"));

        // Missing file and wrong token both 404 (fresh connection for HEAD-after
        // framing simplicity)
        let mut client2 = TestClient::connect(info.port);
        let missing = client2.request("GET", &format!("/{}/{}/nope.bin", token, encoded_key), None);
        assert!(missing.status.contains("404"));
        let wrong_token = client2.request("GET", "/deadbeef/smb%3A%2F%2Ftesthost%2Fshare%0Atester/file.bin", None);
        assert!(wrong_token.status.contains("404"));

        let _ = std::fs::remove_dir_all(&dir);
    }
}
