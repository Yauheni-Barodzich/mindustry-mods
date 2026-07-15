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

[System.IO.File]::WriteAllText((Join-Path (Get-Location) "VERSION"), $version)
Write-Host "VERSION -> $version"

$metaFiles = @(
    "dev/dune-start/mod.hjson",
    "dev/server-content-sync/unified/mod.hjson",
    "dev/server-content-sync/unified/plugin.hjson"
)

foreach ($file in $metaFiles) {
    $content = Get-Content $file -Raw
    # Только поле version: в начале строки, не minGameVersion
    $updated = $content -replace '(?m)^version:\s*"[^"]*"', "version: `"$version`""
    if ($updated -eq $content) {
        Write-Host "Already $version in $file"
        continue
    }
    Set-Content -Path $file -Value $updated -NoNewline
    Write-Host "Updated $file"
}

$gradle = "dev/server-content-sync/build.gradle"
$g = Get-Content $gradle -Raw
# Только project version, не mindustryVersion
$g2 = $g -replace "(?m)^    version = '[^']*'", "    version = '$version'"
if ($g2 -eq $g) {
    Write-Host "Gradle version already $version"
} else {
    Set-Content -Path $gradle -Value $g2 -NoNewline
    Write-Host "Updated $gradle"
}
