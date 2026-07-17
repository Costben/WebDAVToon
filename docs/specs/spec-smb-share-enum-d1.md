## Objective

Add SMB share enumeration as a new Rust UniFFI export for stage D1 so Android can later build UI on top of it in D2.

## Scope

- Add `SmbShare` UniFFI record.
- Add Rust SMB share enumeration logic with shared SMB client config.
- Export `list_smb_shares(...)` from Rust.
- Regenerate Kotlin bindings.
- Add pure Rust unit tests for username formatting and share filtering/mapping.

## Constraints

- Keep existing WebDAV code paths unchanged.
- Do not modify Kotlin app code beyond generated `app/src/main/java/uniffi/rust_core/rust_core.kt`.
- Prefer the exact SMB auth/dialect config required by Android.
- No live SMB integration test in this stage.

## Affected Files

- `docs/specs/spec-smb-share-enum-d1.md`
- `docs/hotl-workflow-smb-share-enum-d1.md`
- `rust-core/Cargo.toml`
- `rust-core/Cargo.lock`
- `rust-core/src/models.rs`
- `rust-core/src/smb_fs.rs`
- `rust-core/src/lib.rs`
- `app/src/main/java/uniffi/rust_core/rust_core.kt`

## Acceptance Checks

- `cargo test --manifest-path rust-core/Cargo.toml`
- `cargo build --manifest-path rust-core/Cargo.toml`
- `./gradlew.bat :app:compileDebugKotlin --console=plain`
- Conventional git commit on this branch

## Verification Artifacts

- Passing command output for the three gates above
- Generated Kotlin binding containing `SmbShare` and `listSmbShares`

## Risks / Open Questions

- The branch state does not currently include the SMB Rust module described by the task, so a minimal manifest/module restoration may be required.
- SMB crate error taxonomy may not expose stable auth/SRVSVC-specific variants; user-facing error strings may need to rely on message inspection plus known call boundaries.
