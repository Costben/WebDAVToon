## Intent Contract

intent: Remove in-app video playback from normal user flows so all videos open through an external player, and add a setting that chooses between the system default external player and the chooser dialog.
constraints:
- Keep existing image browsing behavior unchanged
- Keep WebDAV/local video opening working for both local and remote URIs
- Do not break existing external-player authentication URI handling for WebDAV
- Remove or hide settings that only apply to the internal player so the UI does not expose dead controls
success_criteria:
- Tapping any video from the library no longer launches the in-app player
- Settings exposes an external-player launch mode with “default external player” and “ask every time”
- The default behavior uses the system default external player
- Real-device ADB verification confirms a video tap routes to an external player or chooser instead of `VideoPlayerActivity`
risk_level: low

## Verification Contract

verify_steps:
- run tests: `.\gradlew.bat :app:compileDebugKotlin`
- run build: `.\gradlew.bat :app:assembleDebug`
- check: settings screen shows the new external-player mode option and no longer exposes internal-player-only controls
- check: tapping a video on device no longer leaves `VideoPlayerActivity` focused
- confirm: ADB evidence shows an external app chooser/default handler is invoked

## Governance Contract

approval_gates:
- none (low-risk UI/behavior change)
rollback:
- restore MainActivity video routing to `VideoPlayerActivity`
- restore previous settings items for internal-player controls
ownership:
- Codex implementation + verification in this session

## Recommended approach

1. Keep `ExternalVideoOpener` as the single video launch path.
2. Add a persisted launch-mode setting:
   - `system_default` (recommended default)
   - `chooser`
3. Route all video taps in `MainActivity` to `ExternalVideoOpener`.
4. Make `VideoPlayerActivity` immediately hand off to the external player and finish, so any legacy/deep link path also behaves consistently.
5. Hide internal-player-only settings and add one external-player mode setting row.
