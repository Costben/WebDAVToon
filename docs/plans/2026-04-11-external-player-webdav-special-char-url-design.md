## Intent Contract

intent: Fix external-player launching for remote WebDAV videos whose paths contain spaces, Chinese characters, or symbols so VLC receives a correctly encoded and authenticated URL.
constraints:
- Keep local video external-player launching unchanged
- Keep the existing external-player mode behavior unchanged (system default vs chooser)
- Do not break remote WebDAV videos whose names are already simple ASCII
- Avoid logging raw credentials while collecting verification evidence
success_criteria:
- External-player URL building no longer falls back to the raw unencoded WebDAV URL for files like `Videos/PMV/b站同衣服91v/XXR ❤要和姐姐一起啪啪吗.mp4`
- Remote launch URLs preserve path encoding and still include usable authentication
- A targeted unit test covers a WebDAV video path with Chinese characters, spaces, and `❤`
- Real-device ADB verification shows the user-reported style of path can launch out to VLC instead of failing inside the handoff
risk_level: low

## Verification Contract

verify_steps:
- run tests: `.\gradlew.bat :app:testDebugUnitTest --tests erl.webdavtoon.ExternalVideoOpenerTest`
- run build: `.\gradlew.bat :app:compileDebugKotlin`
- run build: `.\gradlew.bat :app:assembleDebug`
- check: `ExternalVideoOpener` encodes the remote WebDAV URL before rebuilding it with credentials
- check: logs redact or avoid raw password exposure
- confirm: ADB evidence on `192.168.31.216:5555` shows the special-character WebDAV video path launches to VLC (or the configured external player) instead of failing during handoff

## Governance Contract

approval_gates:
- none (low-risk bug fix in external launch path)
rollback:
- restore the previous external WebDAV launch URI construction
- remove the targeted special-character remote URL test if rollback is required
ownership:
- Codex implementation + verification in this session

## Problem summary

The current remote external-player handoff builds credentials by first parsing the raw WebDAV media URL with `java.net.URI(mediaUri)`. That fails for unencoded paths containing spaces, Chinese characters, or symbols such as `❤`. On failure, the code falls back to the original raw URL, which means the external player can receive an unencoded path and may also miss injected authentication.

## Recommended approach

1. Encode the remote WebDAV path first using the existing WebDAV URL encoder.
2. Rebuild the final launch URL from the encoded form, then inject credentials and emit an ASCII-safe URL string for the external player.
3. Add a focused unit test for a special-character remote video path.
4. Validate on device with the reported VLC scenario.
