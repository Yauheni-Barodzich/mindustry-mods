# Сборка dune-start.zip
param(
    [string]$Out
)

if (-not $Out) {
    $Out = Join-Path $PSScriptRoot "..\..\release\client\CONTENT-dune-start.zip"
}

& (Join-Path $PSScriptRoot "..\pack-content-mod.ps1") -ModDir $PSScriptRoot -Out $Out
