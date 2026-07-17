# Упаковка контент-мода в .zip с путями "/" (Mindustry не читает записи с "\").
param(
    [Parameter(Mandatory = $true)]
    [string]$ModDir,

    [string]$Out
)

$src = [System.IO.Path]::GetFullPath($ModDir)
if (-not (Test-Path (Join-Path $src "mod.hjson"))) {
    throw "mod.hjson not found in $src"
}

$modName = (Get-Content (Join-Path $src "mod.hjson") -Raw) -match 'name:\s*"(.+)"' | Out-Null
$modName = if ($Matches) { $Matches[1] } else { Split-Path $src -Leaf }

if (-not $Out) {
    $Out = Join-Path $src "..\..\dist\$modName.zip"
}

$outPath = [System.IO.Path]::GetFullPath($Out)
$outDir = Split-Path $outPath -Parent

if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

if (Test-Path $outPath) {
    Remove-Item $outPath -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::Open($outPath, [System.IO.Compression.ZipArchiveMode]::Create)

try {
    Get-ChildItem -Path $src -Recurse -File | Where-Object {
        $_.Name -notin @("build.ps1", ".gitignore", "README.md", ".gitkeep") -and
        $_.DirectoryName -notmatch "\\\.git($|\\)" -and
        $_.DirectoryName -notmatch "\\dist($|\\)" -and
        $_.Extension -notin @(".jar", ".zip")
    } | ForEach-Object {
        $relative = $_.FullName.Substring($src.Length + 1).Replace("\", "/")
        $entry = $zip.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
        $entryStream = $entry.Open()
        try {
            $fileStream = [System.IO.File]::OpenRead($_.FullName)
            try {
                $fileStream.CopyTo($entryStream)
            } finally {
                $fileStream.Close()
            }
        } finally {
            $entryStream.Close()
        }
        Write-Host "  + $relative"
    }
} finally {
    $zip.Dispose()
}

Write-Host "Built: $outPath"
