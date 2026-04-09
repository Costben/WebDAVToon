---
intent: Bring WebDAVToon to a releasable and maintainable baseline by clearing current lint blockers, hardening WebDAV credential handling, reducing synchronous settings bottlenecks, and establishing minimum automated quality gates.
success_criteria: Debug build, lint, Android unit tests, and Rust tests all pass; WebDAV credentials are no longer stored in plain JSON preferences; remember-password behavior works end-to-end; cleartext HTTP is limited to explicit safe scenarios; and CI enforces the new quality gates.
risk_level: high
auto_approve: false
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Fix XML and resource lint blockers**
action: Update `app/src/main/res/layout/activity_photo_view.xml`, `app/src/main/res/layout/activity_folder_view.xml`, `app/src/main/res/layout/activity_settings.xml`, `app/src/main/res/layout/item_server.xml`, `app/src/main/res/layout/item_server_swipe.xml`, `app/src/main/res/layout/layout_drawer_content.xml`, `app/src/main/res/values/strings.xml`, and `app/src/main/res/values-zh/strings.xml` to remove the current `BottomAppBar`, `MissingConstraints`, `UseAppTint`, and `MissingTranslation` errors; if `activity_settings.xml` is legacy-only, either fix its constraints or delete it after confirming it is unused.
loop: until the XML/resource lint blockers are gone from the lint report
max_iterations: 4
verify: powershell -NoProfile -Command "& { ./gradlew.bat :app:lintDebug --console=plain *> $null; $report='app/build/intermediates/lint_intermediate_text_report/debug/lint-results-debug.txt'; if (Select-String -Path $report -Pattern 'BottomAppBar|MissingConstraints|UseAppTint|MissingTranslation') { Write-Error 'XML/resource lint blockers remain'; exit 1 } }"

- [x] **Step 2: Fix Kotlin lint blockers in theme and image loading code**
action: Update `app/src/main/java/erl/webdavtoon/ThemeHelper.kt` to use typed-array access in a lint-safe way, and update `app/src/main/java/erl/webdavtoon/WebDavImageLoader.kt` so `RequestOptions` transformations are applied immutably instead of discarding return values.
loop: until the targeted Kotlin lint blockers are gone from the lint report
max_iterations: 4
verify: powershell -NoProfile -Command "& { ./gradlew.bat :app:lintDebug --console=plain *> $null; $report='app/build/intermediates/lint_intermediate_text_report/debug/lint-results-debug.txt'; if (Select-String -Path $report -Pattern 'ThemeHelper.kt:.*ResourceType|WebDavImageLoader.kt:.*CheckResult') { Write-Error 'ThemeHelper/WebDavImageLoader lint blockers remain'; exit 1 } }"

- [x] **Step 3: Resolve the UniFFI-generated API 33 cleaner issue**
action: Remove the `NewApi` lint errors in `app/src/main/java/uniffi/rust_core/rust_core.kt` by choosing a durable fix: regenerate bindings with an Android-safe cleaner strategy, patch the generated binding with an API-safe fallback, or move generated sources out of lint scope with a documented regeneration path; document the chosen approach in the code comments or build logic so it is repeatable.
loop: until the generated-binding `NewApi` errors disappear from the lint report
max_iterations: 4
verify: powershell -NoProfile -Command "& { ./gradlew.bat :app:lintDebug --console=plain *> $null; $report='app/build/intermediates/lint_intermediate_text_report/debug/lint-results-debug.txt'; if (Select-String -Path $report -Pattern 'rust_core.kt:.*NewApi') { Write-Error 'UniFFI NewApi lint blockers remain'; exit 1 } }"

- [x] **Step 4: Make `lintDebug` fully pass**
action: Re-run lint, clear any remaining errors introduced or exposed by the previous steps, and keep the fixes localized to the files already touched unless a newly exposed blocker requires a small follow-up edit.
loop: until `:app:lintDebug` passes cleanly
max_iterations: 4
verify: ./gradlew.bat :app:lintDebug --console=plain

- [x] **Step 5: Write the credential-hardening design note**
action: Create `docs/plans/webdav-credential-hardening.md` describing the target storage model for WebDAV usernames/passwords, the migration path from `webdav_slots_json`, the exact semantics of “remember password”, and how non-remembered credentials should behave across app restarts and Glide/OkHttp requests.
loop: false
verify:
  type: artifact
  path: docs/plans/webdav-credential-hardening.md
  assert:
    kind: exists
gate: human

- [x] **Step 6: Extract testable WebDAV config and credential policy helpers**
action: Introduce focused helper classes under `app/src/main/java/erl/webdavtoon/` for WebDAV endpoint normalization and credential-retention policy so URL building, remember-password rules, and migration behavior are no longer buried inside monolithic `SettingsManager.kt` logic.
loop: false
verify:
  type: artifact
  path: app/src/main/java/erl/webdavtoon
  assert:
    kind: matches-glob
    value: "*WebDav*Policy*.kt"

- [x] **Step 7: Add Android unit tests for URL and credential policy behavior**
action: Add tests under `app/src/test/java/erl/webdavtoon/` that cover URL normalization edge cases, slot migration behavior, and remember-password on/off behavior for the helpers introduced in the previous step.
loop: until the new Android unit tests pass
max_iterations: 4
verify: ./gradlew.bat :app:testDebugUnitTest --console=plain

- [x] **Step 8: Implement secure credential storage and wire remember-password end-to-end**
action: Replace plain JSON password persistence in `app/src/main/java/erl/webdavtoon/SettingsManager.kt`, `app/src/main/java/erl/webdavtoon/AppSettingsStore.kt`, `app/src/main/java/erl/webdavtoon/ConfigMigration.kt`, `app/src/main/java/erl/webdavtoon/SettingsActivity.kt`, and `app/src/main/java/erl/webdavtoon/WebDAVToonApplication.kt` with the designed secure-storage flow; remembered passwords should survive restarts, non-remembered passwords should not be persisted, and runtime auth should still work for the current session.
loop: until build and unit tests both pass with the secure-storage changes
max_iterations: 4
verify: powershell -NoProfile -Command "& { ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain }"
gate: human

- [x] **Step 9: Restrict cleartext network traffic to explicit safe scenarios**
action: Replace broad `android:usesCleartextTraffic=\"true\"` usage in `app/src/main/AndroidManifest.xml` with a safer policy such as debug-only cleartext, a network security config, or an explicit per-host/per-build opt-in that matches the credential-hardening design.
loop: until the manifest/network policy matches the approved design and the app still builds
max_iterations: 3
verify: ./gradlew.bat :app:assembleDebug --console=plain
gate: human

- [x] **Step 10: Remove `runBlocking` hot paths from settings access**
action: Refactor `app/src/main/java/erl/webdavtoon/SettingsManager.kt` and its callers so UI/runtime code no longer relies on many synchronous `runBlocking` wrappers for DataStore access; prefer suspend APIs, cached state, or Flow-backed snapshots where appropriate.
loop: until `SettingsManager.kt` no longer contains `runBlocking` and the app still builds/tests
max_iterations: 4
verify: powershell -NoProfile -Command "& { if (Select-String -Path 'app/src/main/java/erl/webdavtoon/SettingsManager.kt' -Pattern '\brunBlocking\b') { Write-Error 'runBlocking still present in SettingsManager'; exit 1 }; ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain }"

- [x] **Step 11: Add Rust regression tests for repository and storage behavior**
action: Add tests under `rust-core/tests/` or inline Rust test modules covering repository sorting/caching behavior, database interactions that can be exercised without Android, and any path/metadata helpers touched while stabilizing the app.
loop: until Rust tests pass
max_iterations: 4
verify: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 12: Expand CI to enforce the new quality gates**
action: Update `.github/workflows/build-and-release.yml` so non-release builds run at least `:app:testDebugUnitTest`, `:app:lintDebug`, and `cargo test --manifest-path rust-core/Cargo.toml` before publishing artifacts; keep the existing release-signing flow intact.
loop: false
verify: powershell -NoProfile -Command "& { $wf='.github/workflows/build-and-release.yml'; if (-not (Select-String -Path $wf -Pattern 'testDebugUnitTest')) { Write-Error 'Workflow missing testDebugUnitTest'; exit 1 }; if (-not (Select-String -Path $wf -Pattern 'lintDebug')) { Write-Error 'Workflow missing lintDebug'; exit 1 }; if (-not (Select-String -Path $wf -Pattern 'cargo test')) { Write-Error 'Workflow missing cargo test'; exit 1 } }"

- [x] **Step 13: Run the release-readiness verification matrix**
action: Run the final verification matrix locally: `./gradlew.bat :app:assembleDebug`, `./gradlew.bat :app:testDebugUnitTest`, `./gradlew.bat :app:lintDebug`, and `cargo test --manifest-path rust-core/Cargo.toml`; if any command fails, loop back to the responsible step instead of papering over the result.
loop: until the full verification matrix passes
max_iterations: 3
verify: powershell -NoProfile -Command "& { ./gradlew.bat :app:assembleDebug --console=plain; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./gradlew.bat :app:testDebugUnitTest --console=plain; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./gradlew.bat :app:lintDebug --console=plain; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cargo test --manifest-path rust-core/Cargo.toml }"
gate: human
