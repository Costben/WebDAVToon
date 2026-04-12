## Intent Contract

intent: Fix dark-mode contrast issues for the settings-page back icon and folder-card text so both day mode and dark mode render readable UI.
constraints:
- Keep the existing theme-selection and dynamic-color behavior unchanged
- Limit scope to the reported UI contrast regressions plus the closely related settings-page navigation-bar icon appearance
- Avoid broad theme palette refactors in this bug-fix pass
success_criteria:
- In dark mode, the settings-page back icon is no longer rendered as black-on-dark and remains readable
- In dark mode, folder name and folder info text use theme-aware colors instead of fixed light-theme hex values
- Day mode remains readable and functionally unchanged
- Real-device ADB verification on `192.168.31.216:5555` captures screenshot/UI/activity evidence for the affected screens
risk_level: low

## Verification Contract

verify_steps:
- run build: `.\gradlew.bat :app:compileDebugKotlin`
- run build: `.\gradlew.bat :app:assembleDebug`
- check: settings toolbar navigation icon and title use a theme-aware tint/color instead of a hardcoded dark icon path
- check: folder card primary/secondary text use theme-aware colors instead of fixed `#333333` / `#808080`
- check: `SettingsActivity` does not force light navigation-bar icons while dark mode is active
- confirm: ADB evidence on `192.168.31.216:5555` shows the settings page and folder list remain readable in dark mode

## Governance Contract

approval_gates:
- design approved by user before implementation
rollback:
- restore the previous settings toolbar drawable/layout/color wiring and folder item text colors
- remove any dark-mode-specific navigation-bar appearance change if it regresses other screens
ownership:
- Codex implementation + verification in this session

## Problem summary

The reported dark-mode regression comes from three narrow issues in the current UI wiring:

1. `ic_arrow_back.xml` hardcodes a black path fill, which can stay black when the settings toolbar does not explicitly tint it.
2. `activity_settings_md3.xml` does not explicitly bind the toolbar title/navigation icon to theme-aware colors.
3. `item_folder.xml` hardcodes folder title/info text to light-theme hex colors (`#333333`, `#808080`), so the text does not adapt when the app is shown in dark mode.

`SettingsActivity` also currently forces light navigation-bar icons unconditionally, which is visually inconsistent in dark mode and is safe to fix alongside the reported settings-page contrast issue.

## Recommended approach

1. Update the settings toolbar XML so its title and navigation icon explicitly use theme-aware color attributes.
2. Replace the hardcoded folder name/info text colors with theme-aware on-surface colors.
3. Make the settings-page system navigation-bar icon appearance conditional on the current night-mode state instead of always forcing light-mode icons.
4. Rebuild, export `webdavtoon-debug.apk`, and verify the affected screens on the preferred ADB device with saved artifacts.
