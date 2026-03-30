# WebDAVToon Technical Documentation

This document describes the current technical architecture of WebDAVToon, focusing on the **Rust Core Integration** and **Remote Filesystem Implementation**.

> **Note**: This document supersedes the outdated `PROJECT_ARCHITECTURE.md`.

## 🏗️ Architecture Overview

WebDAVToon follows a hybrid architecture combining Kotlin (Android UI/Business Logic) and Rust (Core Logic/Networking).

### 1. Kotlin Layer (Android App)
Responsible for:
- **UI Rendering**: `MainActivity`, `SettingsActivity`, `PhotoViewActivity`.
- **Image Loading**: Custom `Glide` integration (`WebDavImageLoader`) that fetches image streams.
- **State Management**: `SettingsManager`, `PhotoRepository`.
- **Rust Integration**: Uses UniFFI-generated bindings (`rust_core.kt`) to call native functions.

### 2. Rust Layer (`rust-core`)
Responsible for:
- **Networking**: Handling WebDAV and SMB protocols.
- **File System Abstraction**: Unified `RemoteService` interface.
- **Concurrency**: `tokio` runtime for async operations.
- **Cross-Platform Logic**: Shared logic compiled into a single `.so` library.

---

## 🔧 Core Implementation Details

### Rust Core (`rust-core/src/remote_fs.rs`)

The `RemoteService` struct is the central point for all remote operations.

#### Protocol Support
- **WebDAV**: Uses the `opendal` crate.
  - Supports standard WebDAV operations (LIST, READ, PROPFIND).
- **SMB (Samba)**: Uses the `smb` crate (version 0.11+).
  - **SMB Configuration**:
    - **Dialect**: Enforced `Smb030` (3.0) to `Smb0311` (3.1.1) for modern security.
    - **Authentication**: Explicitly enables **NTLM** and disables **Kerberos** to fix Android `Sspi: NoCredentials` errors.
    - **Endpoint Normalization**: Custom logic strips `smb://` prefixes and handles `host:port` parsing correctly.

#### Key Functions
- `new_smb(endpoint, share, username, password, workgroup)`: Initializes the SMB client with the optimized configuration.
- `list_root_names()`: Returns a list of filenames in the root directory. Used by the "Test Connection" feature to prove connectivity.
- `list_photos(path, sort_order, recursive)`: Efficiently traverses directories to find image files.
- `read_file(path)`: Streams file content for image loading.

### Android Integration (`app/src/main/java/erl/webdavtoon/`)

#### SettingsActivity.kt
- Manages user input for server configuration.
- Calls `rust_core.test_webdav` or `test_smb` directly via UniFFI.
- **Test Connection Logic**:
  - Upon success, displays a `MaterialAlertDialog` containing the file list returned by Rust.
  - This provides visual confirmation that the connection is working and readable.

#### RustWebDavPhotoRepository.kt
- Implements the `PhotoRepository` interface.
- Delegates calls to the Rust `RemoteService` instance.
- Handles the conversion between Rust structs (`Photo`, `Folder`) and Kotlin data classes.

### Build System & UniFFI

1. **Rust Build**:
   - `cargo build --target aarch64-linux-android --release`
   - `cargo build --target x86_64-linux-android --release`
   - Generates `librust_core.so`.

2. **UniFFI Bindings**:
   - `uniffi-bindgen` generates `rust_core.kt` (Kotlin interface) and the C-compatible scaffolding.
   - The Kotlin bindings are placed in `app/src/main/java/uniffi/rust_core/`.

3. **Android Build (Gradle)**:
   - Copies the `.so` files to `app/src/main/jniLibs/` (or strictly uses the cargo output path).
   - **Crucial Note**: If you encounter `UnsatisfiedLinkError` or missing symbols, ensure you clean the `jniLibs` folder and rebuild, as Gradle might cache old libraries.

---

## 🐛 Troubleshooting & Known Issues

### "Rust Core Init Failed" / UnsatisfiedLinkError
- **Cause**: Old `.so` files in `jniLibs` shadowing new builds.
- **Fix**: Delete `app/src/main/jniLibs`, run `cargo clean`, and `./gradlew clean`.

### SMB "Sspi error: NoCredentials"
- **Cause**: The `smb` crate defaults to SPN/Kerberos which fails on Android.
- **Fix**: The current code forces `auth_methods.ntlm = true` and `kerberos = false`.

### Connection Timeouts
- **Cause**: Network unreachable or firewall blocking SMB port (445).
- **Check**: Verify server is reachable from the device.

---

## 🔮 Future Improvements

- **Streaming Optimization**: Implement chunked streaming for large images to reduce memory pressure.
- **Caching**: Add a local disk cache for thumbnails generated from remote files.
- **Upload Support**: Add ability to upload files to WebDAV/SMB shares.
