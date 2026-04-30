param(
  [string]$WorkspaceRoot = '.'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
Push-Location -LiteralPath $root

try {
  if ($env:PASEO_SOURCE_CHECKOUT_PATH) {
    $localProperties = Join-Path $env:PASEO_SOURCE_CHECKOUT_PATH 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
      Copy-Item -LiteralPath $localProperties -Destination '.\local.properties' -Force
    }
  }

  cargo fetch --manifest-path 'rust-core\Cargo.toml'
  .\gradlew.bat --no-daemon :app:dependencies --configuration debugCompileClasspath --console=plain
}
finally {
  Pop-Location
}
