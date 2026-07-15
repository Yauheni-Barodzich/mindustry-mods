# Один шаг: тег + GitHub Release (сборка в CI/CD)
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$repo = Join-Path $PSScriptRoot ".."
$tag = if ($Version -match '^v') { $Version } else { "v$Version" }

Set-Location $repo

Write-Host "==> Push main"
git push origin main

if (git rev-parse $tag 2>$null) {
    Write-Host "Tag $tag already exists locally"
} else {
    Write-Host "==> Tag $tag"
    git tag $tag
}

Write-Host "==> Push tag (triggers GitHub Actions -> Release)"
git push origin $tag

Write-Host ""
Write-Host "Done. Open: https://github.com/Yauheni-Barodzich/mindustry-mods/releases/tag/$tag"
Write-Host "Or watch: https://github.com/Yauheni-Barodzich/mindustry-mods/actions"
