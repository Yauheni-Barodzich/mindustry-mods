# Один шаг: сборка + коммит артефактов + тег + GitHub Release (через Actions)
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$repo = Join-Path $root ".."
$tag = if ($Version -match '^v') { $Version } else { "v$Version" }

Write-Host "==> Build"
& (Join-Path $root "build-all.ps1")

Set-Location $repo

Write-Host "==> Commit release/"
git add release/
$status = git status --porcelain release/
if ($status) {
    git commit -m "build: release $tag"
} else {
    Write-Host "release/ unchanged, skip commit"
}

Write-Host "==> Push main"
git push origin main

if (git rev-parse $tag 2>$null) {
    Write-Host "Tag $tag already exists locally, pushing..."
} else {
    Write-Host "==> Tag $tag"
    git tag $tag
}

Write-Host "==> Push tag (triggers GitHub Actions -> Release)"
git push origin $tag

Write-Host ""
Write-Host "Done. Open: https://github.com/Yauheni-Barodzich/mindustry-mods/releases/tag/$tag"
Write-Host "Or watch: https://github.com/Yauheni-Barodzich/mindustry-mods/actions"
