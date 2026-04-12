## Intent Contract

intent: Make waterfall-mode video thumbnails use the extracted frame's real aspect ratio instead of a forced uniform square ratio.
constraints:
- Keep folder preview thumbnails square so folder cards remain visually stable
- Keep existing image waterfall behavior unchanged
- Keep local and WebDAV video thumbnail loading paths working
- Avoid increasing thumbnail memory/cached bitmap size without bound
success_criteria:
- In the media waterfall list, portrait and landscape videos render with heights that match their thumbnail frame aspect ratios
- Folder preview video tiles remain square
- Existing square-cropped video bitmap cache entries are invalidated so updated thumbnails appear after install
- Real-device ADB verification shows a waterfall page where portrait video thumbnails are no longer compressed/cropped into a uniform ratio
risk_level: low

## Verification Contract

verify_steps:
- run tests: `.\gradlew.bat :app:compileDebugKotlin`
- run build: `.\gradlew.bat :app:assembleDebug`
- check: `PhotoAdapter` no longer forces normal video items to `CENTER_CROP` with `adjustViewBounds=false`
- check: `WebDavImageLoader` preserves aspect ratio for non-folder video thumbnails and still square-crops folder previews
- confirm: ADB screenshot/log evidence on `192.168.31.216:5555` shows waterfall video cards with variable heights matching portrait/landscape content

## Governance Contract

approval_gates:
- none (low-risk UI/media thumbnail behavior fix)
rollback:
- restore square normalization for normal video thumbnails
- restore `PhotoAdapter` normal video item binding to the previous crop-based display mode
ownership:
- Codex implementation + verification in this session

## Problem summary

Current waterfall video thumbnails are normalized into a square bitmap and then displayed with `CENTER_CROP`. This combination makes portrait video previews look compressed/cropped into a nearly uniform tile ratio, unlike image items whose card heights follow the media aspect ratio.

## Recommended approach

1. Keep the existing square crop path only for folder preview thumbnails.
2. For normal video thumbnails, scale the extracted frame so its longest edge is bounded, but preserve the original width/height ratio.
3. Update the normal media item binding so video thumbnails use `adjustViewBounds=true` and a non-cropping scale type.
4. Bump the video thumbnail bitmap cache version so old square thumbnails are not reused after the fix.
