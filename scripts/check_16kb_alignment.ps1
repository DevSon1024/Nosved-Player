# Audit ELF 16KB Page Size Alignment for Nosved Player Native Libraries
param(
    [string]$TargetDir = "app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib",
    [string]$NdkPath = "$env:LOCALAPPDATA\Android\Sdk\ndk\28.2.13676358"
)

function Read-ElfAlignment([string]$filePath) {
    $stream = [System.IO.File]::OpenRead($filePath)
    $reader = New-Object System.IO.BinaryReader($stream)
    try {
        $magic = $reader.ReadBytes(4)
        if ($magic[0] -ne 0x7F -or $magic[1] -ne 0x45 -or $magic[2] -ne 0x4C -or $magic[3] -ne 0x46) {
            return -1
        }
        $elfClass = $reader.ReadByte() # 1 = 32-bit, 2 = 64-bit
        if ($elfClass -ne 2) { return 4096 } # 32-bit ELF doesn't use 16KB pages

        $stream.Seek(32, [System.IO.SeekOrigin]::Begin) | Out-Null
        $phOff = $reader.ReadInt64()
        $stream.Seek(54, [System.IO.SeekOrigin]::Begin) | Out-Null
        $phEntSize = $reader.ReadInt16()
        $phNum = $reader.ReadInt16()

        $minAlign = [Int64]::MaxValue
        for ($i = 0; $i -lt $phNum; $i++) {
            $entryOffset = $phOff + ($i * $phEntSize)
            $stream.Seek($entryOffset, [System.IO.SeekOrigin]::Begin) | Out-Null
            $pType = $reader.ReadInt32()
            if ($pType -eq 1) { # PT_LOAD
                $stream.Seek($entryOffset + 48, [System.IO.SeekOrigin]::Begin) | Out-Null
                $pAlign = $reader.ReadInt64()
                if ($pAlign -lt $minAlign -and $pAlign -gt 0) {
                    $minAlign = $pAlign
                }
            }
        }
        if ($minAlign -eq [Int64]::MaxValue) { return 4096 }
        return $minAlign
    } finally {
        $reader.Close()
        $stream.Close()
    }
}

if (-not (Test-Path $TargetDir)) {
    # Fallback to general intermediates or jniLibs if debug merge dir hasn't run yet
    $altDir = "app/src/main/jniLibs"
    if (Test-Path $altDir) {
        Write-Host "Target dir $TargetDir not found. Auditing $altDir instead."
        $TargetDir = $altDir
    } else {
        Write-Error "Directory not found: $TargetDir. Run 'gradlew mergeDebugNativeLibs' first."
        exit 1
    }
}

Write-Host "======================================================================"
Write-Host "  Nosved Player: 16KB Page Size Alignment Audit"
Write-Host "  Target: $TargetDir"
Write-Host "======================================================================"

$soFiles = Get-ChildItem -Path $TargetDir -Filter "*.so" -Recurse | Where-Object {
    $_.FullName -match "arm64|x86_64"
}

if ($soFiles.Count -eq 0) {
    Write-Warning "No 64-bit .so files found in $TargetDir."
    exit 0
}

$failCount = 0
$passCount = 0

foreach ($file in $soFiles) {
    $relPath = $file.FullName.Replace((Resolve-Path .).Path, "").TrimStart("\/")
    $align = Read-ElfAlignment $file.FullName
    $hexAlign = "0x" + [Convert]::ToString($align, 16)

    if ($align -ge 16384) {
        Write-Host ("[PASS] {0,-35} | Align: {1,7} ({2})" -f $file.Name, $hexAlign, $file.Directory.Name) -ForegroundColor Green
        $passCount++
    } else {
        Write-Host ("[FAIL] {0,-35} | Align: {1,7} ({2}) -> 4KB ALIGNED" -f $file.Name, $hexAlign, $file.Directory.Name) -ForegroundColor Red
        $failCount++
    }
}

Write-Host "======================================================================"
Write-Host "Results: $passCount passed, $failCount failed (out of $($soFiles.Count) 64-bit libraries)"
Write-Host "======================================================================"

if ($failCount -gt 0) {
    exit 1
} else {
    exit 0
}
