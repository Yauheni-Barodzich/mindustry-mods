# Читает/пишет version в package.json и синхронизирует в mod.hjson / plugin.hjson / gradle
param(
    [string]$Version,
    [ValidateSet("patch", "minor", "major")]
    [string]$Increment = "patch"
)

$ErrorActionPreference = "Stop"
$repo = Join-Path $PSScriptRoot ".."
Set-Location $repo

$packagePath = Join-Path (Get-Location) "package.json"
if (-not (Test-Path $packagePath)) {
    throw "package.json not found"
}

function Get-NextSemver {
    param([string]$Current, [string]$Kind)
    if ($Current -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "Invalid version: $Current"
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

function Get-PackageVersion {
    $pkg = Get-Content $packagePath -Raw -Encoding UTF8 | ConvertFrom-Json
    return [string]$pkg.version
}

function Set-PackageVersion([string]$NewVersion) {
    $raw = [System.IO.File]::ReadAllText($packagePath)
    $updated = [regex]::Replace($raw, '("version"\s*:\s*")[^"]*(")', "`${1}$NewVersion`${2}")
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($packagePath, $updated, $utf8)
}

if ([string]::IsNullOrWhiteSpace($Version)) {
    $current = Get-PackageVersion
    $version = Get-NextSemver $current $Increment
    Write-Host "Auto-increment ($Increment): $current -> $version"
} else {
    $version = $Version -replace '^v', ''
    if ($version -notmatch '^\d+\.\d+\.\d+$') {
        throw "Invalid semver: $Version (expected X.Y.Z)"
    }
}

Set-PackageVersion $version
Write-Host "package.json version -> $version"

$metaFiles = @(
    "dev/dune-start/mod.hjson",
    "dev/server-content-sync/unified/mod.hjson",
    "dev/server-content-sync/unified/plugin.hjson"
)

foreach ($file in $metaFiles) {
    $path = Join-Path (Get-Location) $file
    # Важно: без -Encoding UTF8 PowerShell на Windows портит кириллицу (кракозябры в списке модов).
    $content = Get-Content $path -Raw -Encoding UTF8
    $updated = $content -replace '(?m)^version:\s*"[^"]*"', "version: `"$version`""
    if ($updated -eq $content) {
        Write-Host "Already $version in $file"
        continue
    }
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $updated, $utf8)
    Write-Host "Updated $file"
}

$gradle = "dev/server-content-sync/build.gradle"
$gradlePath = Join-Path (Get-Location) $gradle
$g = Get-Content $gradlePath -Raw -Encoding UTF8
$g2 = $g -replace "(?m)^    version = '[^']*'", "    version = '$version'"
if ($g2 -eq $g) {
    Write-Host "Gradle version already $version"
} else {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($gradlePath, $g2, $utf8)
    Write-Host "Updated $gradle"
}
