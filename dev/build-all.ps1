# Сборка всех наших модов в .zip
# dist/        -> локальный выход сборки (в git не хранится)
# mods/        -> локальный клиент Mindustry
# server-mods/ -> локальная папка для заливки на сервер
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$repo = Join-Path $root ".."
$dist = Join-Path $repo "dist"
$clientMods = Join-Path $repo "mods"
$serverMods = Join-Path $repo "server-mods"
$scs = Join-Path $root "server-content-sync"

$artifactNames = @(
    "dune-start.jar", "dune-start.zip",
    "CONTENT-dune-start.zip",
    "sync-admin.zip",
    "CONTENT-sync-admin.zip",
    "CONTENT-scs.zip",
    "server-content-sync-plugin.jar", "server-content-sync-plugin.zip",
    "server-content-sync-client.jar", "server-content-sync-client.zip",
    "server-admin-plugin.jar", "server-admin-plugin.zip",
    "server-admin-client.jar", "server-admin-client.zip",
    "SERVER-sync-content.zip", "CLIENT-sync-content.zip",
    "SERVER-admin.zip", "CLIENT-admin.zip"
)

foreach ($dir in @($dist, $clientMods, $serverMods)) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

foreach ($name in $artifactNames) {
    foreach ($dir in @($dist, $clientMods, $serverMods)) {
        $old = Join-Path $dir $name
        if (Test-Path $old) {
            try {
                Remove-Item $old -Force -ErrorAction Stop
                Write-Host "Removed $old"
            } catch {
                if ($dir -eq $dist) {
                    throw
                }
                Write-Host "Skip locked: $old"
            }
        }
    }
}

Write-Host "==> dune-start"
& (Join-Path $root "pack-content-mod.ps1") -ModDir (Join-Path $root "dune-start") -Out (Join-Path $dist "CONTENT-dune-start.zip")

Write-Host "==> Синхронизация и админка (gradle)"
Push-Location $scs
try {
    & .\gradlew.bat :unified:jar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

function Publish-Artifact($srcPath, $dstName, $targets) {
    if (-not (Test-Path $srcPath)) { throw "Artifact not found: $srcPath" }
    foreach ($dir in $targets) {
        $dst = Join-Path $dir $dstName
        Copy-Item $srcPath $dst -Force
        Write-Host "-> $dst"
    }
}

$contentSrc = Join-Path $dist "CONTENT-dune-start.zip"
$scsSrc = Join-Path $scs "unified\build\libs\sync-admin.zip"

Publish-Artifact $scsSrc "sync-admin.zip" @($dist, $clientMods, $serverMods)
Publish-Artifact $contentSrc "CONTENT-dune-start.zip" @($dist, $clientMods, $serverMods)

Write-Host "Done. Build: dist\ | Client: mods\ | Server deploy: server-mods\ | Publish: GitHub Releases"
