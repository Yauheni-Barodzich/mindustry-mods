# Синхронизирует VERSION во все mod.hjson / plugin.hjson и gradle
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$version = $Version -replace '^v', ''
if ($version -notmatch '^\d+\.\d+\.\d+') {
    throw "Invalid semver: $Version (expected X.Y.Z)"
}

$repo = Join-Path $PSScriptRoot ".."
Set-Location $repo

Set-Content -Path "VERSION" -Value $version -NoNewline
Write-Host "VERSION -> $version"

$metaFiles = @(
    "dev/dune-start/mod.hjson",
    "dev/server-content-sync/client/mod.hjson",
    "dev/server-content-sync/admin-client/mod.hjson",
    "dev/server-content-sync/plugin/plugin.hjson",
    "dev/server-content-sync/admin-plugin/plugin.hjson"
)

foreach ($file in $metaFiles) {
    $content = Get-Content $file -Raw
    $updated = $content -replace 'version:\s*"[^"]*"', "version: `"$version`""
    if ($updated -eq $content) {
        throw "version field not updated in $file"
    }
    Set-Content -Path $file -Value $updated -NoNewline
    Write-Host "Updated $file"
}

$gradle = "dev/server-content-sync/build.gradle"
$g = Get-Content $gradle -Raw
$g2 = $g -replace "version = '[^']*'", "version = '$version'"
if ($g2 -eq $g) {
    throw "gradle version not updated"
}
Set-Content -Path $gradle -Value $g2 -NoNewline
Write-Host "Updated $gradle"
