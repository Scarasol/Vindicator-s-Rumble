param([switch]$Clean)

$ErrorActionPreference = 'Stop'

function Assert-OrdinaryDirectory([System.IO.DirectoryInfo]$Directory) {
    for ($item = $Directory; $null -ne $item; $item = $item.Parent) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Refusing to follow a junction or symlink: $($item.FullName)"
        }
    }
}

$temp = Get-Item -LiteralPath $env:TEMP
if (-not $temp.PSIsContainer -or $temp.PSDrive.Name -ne 'C') {
    throw 'Expected the original C: temporary directory.'
}
Assert-OrdinaryDirectory $temp
$expectedRoot = Join-Path $temp.FullName 'vr-limitless-validation'
if (-not (Test-Path -LiteralPath $expectedRoot)) {
    Write-Host 'The old Limitless validation directory is already absent.'
    exit 0
}
$root = Get-Item -LiteralPath $expectedRoot -Force
if (-not $root.PSIsContainer -or $root.FullName -ne $expectedRoot) {
    throw "Unexpected validation directory: $($root.FullName)"
}
Assert-OrdinaryDirectory $root

# Keep builds, logs, worlds, the baseline snapshot, and the Gradle distribution/JDKs.
$cachePaths = @(
    'cache'
    'cache-baseline'
    'cache-without-limitless'
    'gradle-home\.tmp'
    'gradle-home\caches'
    'gradle-home\native'
    'gradle-home\workers'
)
$targets = @(foreach ($relative in $cachePaths) {
    $expected = Join-Path $root.FullName $relative
    if (-not (Test-Path -LiteralPath $expected)) { continue }
    $target = Get-Item -LiteralPath $expected -Force
    if (-not $target.PSIsContainer -or $target.FullName -ne $expected -or
        -not $target.FullName.StartsWith($root.FullName + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unexpected cleanup target: $expected"
    }
    Assert-OrdinaryDirectory $target
    if (Get-ChildItem -LiteralPath $target.FullName -Recurse -Force -Attributes ReparsePoint | Select-Object -First 1) {
        throw "Refusing to clean a directory that contains a junction or symlink: $expected"
    }
    $target
})

if (-not $Clean) {
    Write-Host 'Preview only. The following cache directories are eligible for removal:'
    $targets | ForEach-Object { Write-Host "  $($_.FullName)" }
    Write-Host 'To remove only these directories, run cleanup-limitless-cache.bat /clean.'
    exit 0
}
if (-not $targets.Count) {
    Write-Host 'No matching cache directories remain.'
    exit 0
}
if (-not (Test-Path -LiteralPath 'D:\Library\.gradle\caches' -PathType Container)) {
    throw 'The normal Gradle cache on D: is unavailable. Nothing was removed.'
}
$running = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
    Where-Object { $_.CommandLine -like '*vr-limitless-validation*' }
if ($running) {
    throw 'A Java process is using the old validation directory. Stop that run and retry.'
}
foreach ($target in $targets) {
    Remove-Item -LiteralPath $target.FullName -Recurse -Force
    Write-Host "Removed $($target.FullName)"
}
