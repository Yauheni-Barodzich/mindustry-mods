# Тег + GitHub Release (автобамп patch по умолчанию)
param(
    [string]$Version,
    [ValidateSet("patch", "minor", "major")]
    [string]$Increment = "patch"
)

$ErrorActionPreference = "Stop"
$repo = Join-Path $PSScriptRoot ".."
Set-Location $repo

if ($Version) {
    & (Join-Path $PSScriptRoot "bump-version.ps1") -Version $Version
} else {
    & (Join-Path $PSScriptRoot "bump-version.ps1") -Increment $Increment
    $Version = (Get-Content "VERSION" -Raw).Trim()
}

$tag = if ($Version -match '^v') { $Version } else { "v$Version" }

git add VERSION dev/
if (git status --porcelain VERSION dev/) {
    git commit -m "chore: release $tag"
    git push origin main
}

if (-not (git rev-parse $tag 2>$null)) {
    git tag $tag
}

git push origin $tag

Write-Host "Released $tag"
Write-Host "https://github.com/Yauheni-Barodzich/mindustry-mods/releases/tag/$tag"
