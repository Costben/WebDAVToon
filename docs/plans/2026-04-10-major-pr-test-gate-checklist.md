# 2026-04-10 Major PR Test Gate Checklist

Use this checklist when running **Major PR Gate v1**. Detailed thresholds/specs live in `docs/plans/2026-04-10-major-pr-test-gate-design.md`.

---

## 0. Trigger check

- [ ] This PR is a **major / large PR**
- [ ] The changes are **user-perceptible** (UI / thumbnails / media / playback / navigation / caching / WebDAV / visible performance)
- [ ] Therefore the **full Major PR Gate v1** is required

If any item above is false, use targeted verification instead of this checklist.

---

## 1. Preflight

- [ ] Build latest debug APK: `.\gradlew.bat :app:assembleDebug --console=plain`
- [ ] Run Android unit tests: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- [ ] Run Rust tests: `cargo test --manifest-path rust-core/Cargo.toml`
- [ ] Confirm root APK exists: `webdavtoon-debug.apk`
- [ ] Confirm device connected: `adb -s 192.168.31.216:5555 get-state`
- [ ] Create artifact directory: `.artifacts/adb/<date>/major-pr-<slug>/`
- [ ] Install APK: `adb -s 192.168.31.216:5555 install -r .\webdavtoon-debug.apk`
- [ ] Wake/unlock device if needed

---

## 2. WebDAV thumbnail chain

### Run 1
- [ ] Open WebDAV root folder page
- [ ] Check root-folder thumbnails
- [ ] Enter level 1 image-dense child folder
- [ ] Check level 1 thumbnails
- [ ] Enter level 2 image-dense child folder
- [ ] Check level 2 thumbnails
- [ ] Enter level 3 image-dense child folder
- [ ] Check level 3 thumbnails

### Run 2
- [ ] Return and re-enter the same root/level chain
- [ ] Confirm run 2 is not slower than run 1 by more than `1.0s`
- [ ] Confirm no blank / wrong / stale thumbnails on re-entry

### Evidence
- [ ] Save screenshots
- [ ] Save UI dumps
- [ ] Save `dumpsys activity activities`
- [ ] Save `dumpsys gfxinfo ... framestats`
- [ ] Save `logcat`

---

## 3. Local thumbnail chain

### Run 1
- [ ] Open Local Photos root
- [ ] Check local root thumbnails
- [ ] Enter level 1 image-dense child folder
- [ ] Check level 1 thumbnails
- [ ] Enter level 2 image-dense child folder
- [ ] Check level 2 thumbnails

### Run 2
- [ ] Return and re-enter the same local chain
- [ ] Confirm run 2 is not slower than run 1 by more than `1.0s`
- [ ] Confirm no blank / wrong / stale thumbnails on re-entry

### Evidence
- [ ] Save screenshots
- [ ] Save UI dumps
- [ ] Save `dumpsys activity activities`
- [ ] Save `dumpsys gfxinfo ... framestats`
- [ ] Save `logcat`

---

## 4. Remote video list + playback

### Video list
- [ ] Open remote video list
- [ ] Confirm list becomes usable within threshold
- [ ] Confirm a video card is visible with video indicator / usable thumbnail

### Handoff route
- [ ] Tap remote video card
- [ ] Confirm route does **not** go to `PhotoViewActivity`
- [ ] Confirm route validly hands off to either:
  - an external player activity
  - `ResolverActivity` when chooser mode is expected

### Handoff quality
- [ ] Confirm handoff completes within threshold
- [ ] Confirm the external target/chooser remains stable for at least `0.5s`
- [ ] If chooser mode is expected, confirm at least one capable external handler is listed
- [ ] If app is resumed later, confirm the originating list is still reachable without obvious corruption

### Run 2
- [ ] Re-enter the same remote video path
- [ ] Confirm run 2 is not slower than run 1 by more than `1.0s`

### Evidence
- [ ] Save screenshots
- [ ] Save UI dumps
- [ ] Save `dumpsys activity activities`
- [ ] Save `dumpsys gfxinfo ... framestats`
- [ ] Save `logcat`
- [ ] Save `dumpsys window windows`
- [ ] Save `dumpsys audio`

---

## 5. Local video list + playback

### Video list
- [ ] Open local video list
- [ ] Confirm list becomes usable within threshold
- [ ] Confirm a video card is visible with video indicator / usable thumbnail

### Handoff route
- [ ] Tap local video card
- [ ] Confirm route does **not** go to `PhotoViewActivity`
- [ ] Confirm route validly hands off to either:
  - an external player activity
  - `ResolverActivity` when chooser mode is expected

### Handoff quality
- [ ] Confirm handoff completes within threshold
- [ ] Confirm the external target/chooser remains stable for at least `0.5s`
- [ ] If chooser mode is expected, confirm at least one capable external handler is listed
- [ ] If app is resumed later, confirm the originating list is still reachable without obvious corruption

### Run 2
- [ ] Re-enter the same local video path
- [ ] Confirm run 2 is not slower than run 1 by more than `1.0s`

### Evidence
- [ ] Save screenshots
- [ ] Save UI dumps
- [ ] Save `dumpsys activity activities`
- [ ] Save `dumpsys gfxinfo ... framestats`
- [ ] Save `logcat`
- [ ] Save `dumpsys window windows`
- [ ] Save `dumpsys audio`

---

## 6. Error budget review

- [ ] No crash observed
- [ ] No ANR observed
- [ ] No fatal handoff error observed
- [ ] No unexpected bounce-back to the app without chooser/player handoff observed
- [ ] No repeated remote-auth failure loop observed

---

## 7. Final report

- [ ] Summarize each gate item as PASS / FAIL / INVALID
- [ ] Mark Overall result as PASS / FAIL / INVALID
- [ ] If any item failed, block the PR
- [ ] Include artifact directory path in the report
