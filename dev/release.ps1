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
}

$pkg = Get-Content "package.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$Version = [string]$pkg.version
$tag = if ($Version -match '^v') { $Version } else { "v$Version" }

git add package.json dev/
if (git status --porcelain package.json dev/) {
    git commit -m "chore: release $tag"
    git push origin main
}

$tagExists = $false
try {
    git rev-parse --verify "$tag^{commit}" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $tagExists = $true }
} catch {}

if (-not $tagExists) {
    git tag $tag
}

git push origin $tag

Write-Host "Released $tag"
Write-Host "https://github.com/Yauheni-Barodzich/mindustry-mods/releases/tag/$tag"
