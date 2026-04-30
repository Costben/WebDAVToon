[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$WorkspaceRoot
)

$ErrorActionPreference = 'Stop'

# Manual cleanup only. Do not configure this script as worktree.teardown.
$root = (Resolve-Path -LiteralPath $WorkspaceRoot).Path

function Remove-WorkspaceItem {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RelativePath,

    [switch]$Recurse
  )

  $target = Join-Path $root $RelativePath
  if (-not (Test-Path -LiteralPath $target)) {
    return
  }

  $resolved = (Resolve-Path -LiteralPath $target).Path
  $rootPrefix = $root.TrimEnd('\') + '\'

  if (($resolved -ne $root) -and (-not $resolved.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "Refusing to remove path outside workspace: $resolved"
  }

  Remove-Item -LiteralPath $resolved -Force -ErrorAction SilentlyContinue -Recurse:$Recurse
}

$currentPid = $PID

$processNames = @(
  'node.exe',
  'npm.cmd',
  'npx.cmd',
  'pnpm.exe',
  'pnpm.cmd',
  'yarn.cmd',
  'python.exe',
  'uv.exe',
  'java.exe',
  'javaw.exe',
  'dotnet.exe',
  'go.exe',
  'cargo.exe',
  'rustc.exe',
  'esbuild.exe'
)

Get-CimInstance Win32_Process |
  Where-Object {
    $_.ProcessId -ne $currentPid -and
    $_.CommandLine -and
    $_.CommandLine.IndexOf($root, [System.StringComparison]::OrdinalIgnoreCase) -ge 0 -and
    $processNames -contains $_.Name
  } |
  ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
  }

Start-Sleep -Milliseconds 500

Remove-WorkspaceItem '.gradle' -Recurse
Remove-WorkspaceItem '.kotlin' -Recurse
Remove-WorkspaceItem '.artifacts' -Recurse
Remove-WorkspaceItem 'build' -Recurse
Remove-WorkspaceItem 'app\build' -Recurse
Remove-WorkspaceItem 'app\src\main\jniLibs' -Recurse
Remove-WorkspaceItem 'rust-core\target' -Recurse
Remove-WorkspaceItem 'dist' -Recurse
Remove-WorkspaceItem 'data\generated' -Recurse
Remove-WorkspaceItem 'data\diagnostics' -Recurse
Remove-WorkspaceItem 'logs' -Recurse
Remove-WorkspaceItem 'tmp' -Recurse

Remove-WorkspaceItem 'webdavtoon-debug.apk'
Remove-WorkspaceItem 'webdavtoon-release.apk'
Remove-WorkspaceItem 'tmp_x86_64_librust_core.so'
Remove-WorkspaceItem 'rust-core\uniffi-bindgen.exe'
Remove-WorkspaceItem 'rust-core\uniffi-bindgen'
Remove-WorkspaceItem 'rust-core\src\uniffi_rust_core.kt'
Remove-WorkspaceItem 'rust-core\src\librust_core.so'
