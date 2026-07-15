# Синхронизирует VERSION во все mod.hjson / plugin.hjson и gradle
param(
    [string]$Version,
    [ValidateSet("patch", "minor", "major")]
    [string]$Increment = "patch"
)

$ErrorActionPreference = "Stop"
$repo = Join-Path $PSScriptRoot ".."
Set-Location $repo

function Get-NextSemver {
    param([string]$Current, [string]$Kind)
    if ($Current -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "Invalid VERSION: $Current"
    }
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3]
    switch ($Kind) {
        "major" { return "{0}.0.0" -f ($major + 1) }
        "minor" { return "{0}.{1}.0" -f $major, ($minor + 1) }
        default { return "{0}.{1}.{2}" -f $major, $minor, ($patch + 1) }
    }
}

if ([string]::IsNullOrWhiteSpace($Version)) {
    $current = (Get-Content "VERSION" -Raw).Trim()
    $version = Get-NextSemver $current $Increment
    Write-Host "Auto-increment ($Increment): $current -> $version"
} else {
    $version = $Version -replace '^v', ''
    if ($version -notmatch '^\d+\.\d+\.\d+$') {
        throw "Invalid semver: $Version (expected X.Y.Z)"
    }
}

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
