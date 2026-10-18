# Strip unused desktop, testing, and legacy modules from python313.zip for Nosved Player
param(
    [string]$ZipPath = "app/src/main/assets/ytdl/python313.zip"
)

if (-not (Test-Path $ZipPath)) {
    Write-Error "Zip file not found: $ZipPath"
    exit 1
}

$tempZipPath = "$ZipPath.tmp"
if (Test-Path $tempZipPath) {
    Remove-Item $tempZipPath -Force
}

$removablePatterns = @(
    '^asyncio/windows_',
    '^_pyrepl/windows_',
    '^_pyrepl/(curses|_minimal_curses|trace)\.pyc$',
    '^curses/',
    '^wsgiref/',
    '^(turtle|antigravity|pydoc|bdb|doctest|_osx_support|_aix_support)\.pyc$',
    '^ctypes/_aix\.pyc$',
    '^(imaplib|poplib|smtplib|mailbox|ftplib)\.pyc$',
    '^(tabnanny|wave|colorsys|timeit|cProfile|profile|pstats|trace)\.pyc$',
    '^(tkinter|unittest|idlelib)/'
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$srcZip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ZipPath).Path)
$destZip = [System.IO.Compression.ZipFile]::Open($tempZipPath, [System.IO.Compression.ZipArchiveMode]::Create)

$removedCount = 0
foreach ($entry in $srcZip.Entries) {
    $name = $entry.FullName
    $shouldRemove = $false
    foreach ($pat in $removablePatterns) {
        if ($name -match $pat) {
            $shouldRemove = $true
            break
        }
    }
    if ($shouldRemove) {
        Write-Host "Stripping: $name"
        $removedCount++
    } else {
        $newEntry = $destZip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
        $srcStream = $entry.Open()
        $destStream = $newEntry.Open()
        $srcStream.CopyTo($destStream)
        $destStream.Dispose()
        $srcStream.Dispose()
    }
}
$destZip.Dispose()
$srcZip.Dispose()

$origSize = (Get-Item $ZipPath).Length
$newSize = (Get-Item $tempZipPath).Length

Remove-Item $ZipPath -Force
Move-Item $tempZipPath $ZipPath

Write-Host "Successfully stripped $removedCount modules."
Write-Host "Original size: $([math]::Round($origSize / 1KB, 2)) KB -> New size: $([math]::Round($newSize / 1KB, 2)) KB"
