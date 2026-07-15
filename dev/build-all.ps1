# Сборка всех наших модов в .zip
# release/     -> локально (zip в .gitignore, публикуется через CI/CD)
# mods/        -> локальный клиент Mindustry
# server-mods/ -> локальная папка для заливки на сервер
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$repo = Join-Path $root ".."
$releaseClient = Join-Path $repo "release\client"
$releaseServer = Join-Path $repo "release\server"
$clientMods = Join-Path $repo "mods"
$serverMods = Join-Path $repo "server-mods"
$scs = Join-Path $root "server-content-sync"

$artifactNames = @(
    "dune-start.jar", "dune-start.zip",
    "CONTENT-dune-start.zip",
    "server-content-sync-plugin.jar", "server-content-sync-plugin.zip",
    "server-content-sync-client.jar", "server-content-sync-client.zip",
    "server-admin-plugin.jar", "server-admin-plugin.zip",
    "server-admin-client.jar", "server-admin-client.zip",
    "SERVER-sync-content.zip", "CLIENT-sync-content.zip",
    "SERVER-admin.zip", "CLIENT-admin.zip"
)

foreach ($dir in @($releaseClient, $releaseServer, $clientMods, $serverMods)) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

foreach ($name in $artifactNames) {
    foreach ($dir in @($releaseClient, $releaseServer, $clientMods, $serverMods)) {
        $old = Join-Path $dir $name
        if (Test-Path $old) {
            try {
                Remove-Item $old -Force -ErrorAction Stop
                Write-Host "Removed $old"
            } catch {
                if ($dir -eq $releaseClient -or $dir -eq $releaseServer) {
                    throw
                }
                Write-Host "Skip locked: $old"
            }
        }
    }
}

Write-Host "==> dune-start"
& (Join-Path $root "pack-content-mod.ps1") -ModDir (Join-Path $root "dune-start") -Out (Join-Path $releaseClient "CONTENT-dune-start.zip")

Write-Host "==> server-content-sync (gradle)"
Push-Location $scs
try {
    & .\gradlew.bat :plugin:jar :client:jar :admin-plugin:jar :admin-client:jar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$clientArtifacts = @(
    @{ Src = "server-content-sync\client\build\libs\CLIENT-sync-content.zip"; Dst = "CLIENT-sync-content.zip" }
    @{ Src = "server-content-sync\admin-client\build\libs\CLIENT-admin.zip"; Dst = "CLIENT-admin.zip" }
)

$serverArtifacts = @(
    @{ Src = "server-content-sync\plugin\build\libs\SERVER-sync-content.zip"; Dst = "SERVER-sync-content.zip" }
    @{ Src = "server-content-sync\admin-plugin\build\libs\SERVER-admin.zip"; Dst = "SERVER-admin.zip" }
)

function Publish-Artifact($srcPath, $dstName, $targets) {
    if (-not (Test-Path $srcPath)) { throw "Artifact not found: $srcPath" }
    foreach ($dir in $targets) {
        $dst = Join-Path $dir $dstName
        Copy-Item $srcPath $dst -Force
        Write-Host "-> $dst"
    }
}

$contentSrc = Join-Path $releaseClient "CONTENT-dune-start.zip"

foreach ($a in $clientArtifacts) {
    Publish-Artifact (Join-Path $root $a.Src) $a.Dst @($releaseClient, $clientMods)
}

Publish-Artifact $contentSrc "CONTENT-dune-start.zip" @($releaseServer, $serverMods)

foreach ($a in $serverArtifacts) {
    Publish-Artifact (Join-Path $root $a.Src) $a.Dst @($releaseServer, $serverMods)
}

Write-Host "Done. Local: release\ | Client: mods\ | Server deploy: server-mods\ | Publish: GitHub Releases"
