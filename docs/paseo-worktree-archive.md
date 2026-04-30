# Paseo Worktree Archive Notes

On Windows, do not configure a project teardown command for `paseo archive`.
This project keeps `worktree.teardown` as an empty array:

```json
"teardown": []
```

Archive has previously failed when `worktree.teardown` invoked either PowerShell
or `cmd` cleanup scripts, with `FullyQualifiedErrorId : SetConsoleWindowTitle`.
Keep cleanup separate from archive.

## Active Worktrees

If an active Paseo worktree still contains an older `paseo.json`, copy the current
root `paseo.json` into that worktree before archiving it. The important archive
setting is the empty `worktree.teardown` array.

## Setup

`worktree.setup` should call `scripts\paseo-worktree-setup.ps1`. Do not expand it
back into complex inline PowerShell in `paseo.json`.

## Manual Cleanup

Do not test teardown from the real project root. If cleanup behavior needs to be
tested, use a temporary directory as `-WorkspaceRoot`.

Manual cleanup is available through `scripts\paseo-teardown.ps1` only. Treat it
as a manual cleanup tool, not as `worktree.teardown`.

The cleanup script removes worktree-local generated state, including:

- `dist`
- `data\generated`
- `data\diagnostics`
- `logs`

It also removes local build/cache outputs such as `.gradle`, `.kotlin`,
`.artifacts`, `build`, `app\build`, `app\src\main\jniLibs`, `rust-core\target`,
`tmp`, exported APKs, and generated UniFFI/Rust library artifacts.

Do not add `scripts\paseo-teardown.cmd` back to `worktree.teardown`; it is not
part of the archive path.
