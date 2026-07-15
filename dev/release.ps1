# Тег + GitHub Release (бамп версий и сборка в CI/CD)
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$repo = Join-Path $PSScriptRoot ".."
$tag = if ($Version -match '^v') { $Version } else { "v$Version" }

Set-Location $repo

Write-Host "==> Optional local bump (CI сделает то же при релизе)"
& (Join-Path $PSScriptRoot "bump-version.ps1") -Version $Version

git add VERSION dev/
if (git status --porcelain VERSION dev/) {
    git commit -m "chore: release $tag"
    git push origin main
}

if (-not (git rev-parse $tag 2>$null)) {
    git tag $tag
}

git push origin $tag

Write-Host "Done: https://github.com/Yauheni-Barodzich/mindustry-mods/releases/tag/$tag"
