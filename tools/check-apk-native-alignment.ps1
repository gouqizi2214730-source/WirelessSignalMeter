param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$ReadElfPath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
if (!$ReadElfPath) {
    $sdk = $env:ANDROID_SDK_ROOT
    if (!$sdk) { $sdk = $env:ANDROID_HOME }
    if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $ReadElfPath = Get-ChildItem -LiteralPath (Join-Path $sdk 'ndk') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
}
if (!$ReadElfPath -or !(Test-Path -LiteralPath $ReadElfPath)) {
    throw 'Set -ReadElfPath to the NDK llvm-readelf executable.'
}

$scratch = Join-Path ([IO.Path]::GetTempPath()) ('wsm-native-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
$archive = $null
$extracted = [Collections.Generic.List[string]]::new()
$failures = [Collections.Generic.List[string]]::new()
$count = 0
try {
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '^lib/(arm64-v8a|x86_64)/[^/]+\.so$') { continue }
        $count++
        $destination = Join-Path $scratch ($entry.FullName.Replace('/', '_'))
        $extracted.Add($destination)
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination)
        $headers = & $ReadElfPath -lW $destination
        if ($LASTEXITCODE) { throw "Cannot inspect $($entry.FullName)" }
        $loads = @($headers | Where-Object { $_ -match '^\s*LOAD\s' })
        if (!$loads.Count) { throw "No ELF LOAD segments in $($entry.FullName)" }
        $valid = $true
        foreach ($load in $loads) {
            $fields = $load.Trim() -split '\s+'
            $alignment = [Convert]::ToInt64($fields[-1], 16)
            $offset = [Convert]::ToInt64($fields[1], 16)
            $address = [Convert]::ToInt64($fields[2], 16)
            if ($alignment -lt 16384 -or ($offset % 16384) -ne ($address % 16384)) {
                $valid = $false
                $failures.Add("$($entry.FullName): $($load.Trim())")
            }
        }
        $result = if ($valid) { 'PASS' } else { 'FAIL' }
        Write-Output "$result $($entry.FullName) ($($loads.Count) LOAD segments)"
    }
    if (!$count) { throw 'No arm64/x86_64 libraries found; APK was not checked.' }
    if ($failures.Count) {
        throw "ELF 16 KB alignment failed:`n$($failures -join "`n")"
    }
    Write-Output "All $count native libraries have 16 KB compatible ELF LOAD segments."
} finally {
    if ($archive) { $archive.Dispose() }
    # Only exact files created by this invocation are removed.
    foreach ($path in $extracted) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
    Remove-Item -LiteralPath $scratch -Force
}
