# Builds a standalone release APK (JS bundled via Hermes — no Metro needed) and
# copies it into releases/ with a distinguishable name: version + short git hash
# + timestamp. Re-run after each iteration; every build lands as its own file.
#
# Usage (from anywhere):  powershell -ExecutionPolicy Bypass -File scripts\release.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

Write-Host '==> Building release APK (./gradlew assembleRelease)...'
Push-Location "$root\android"
try {
    & cmd /c '.\gradlew.bat assembleRelease --console=plain'
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

$apk = Join-Path $root 'android\app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK not found at $apk" }

# Derive a distinguishable name.
$verMatch = Select-String -Path (Join-Path $root 'android\app\build.gradle') -Pattern 'versionName\s+"([^"]+)"'
$ver = if ($verMatch) { $verMatch.Matches[0].Groups[1].Value } else { 'x' }
try { $hash = (& git -C $root rev-parse --short HEAD).Trim() } catch { $hash = 'nogit' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$releases = Join-Path $root 'releases'
New-Item -ItemType Directory -Force -Path $releases | Out-Null
$dest = Join-Path $releases "cashkaro-smsparser-v$ver-$hash-$stamp.apk"
Copy-Item $apk $dest -Force

$sizeMb = '{0:N1}' -f ((Get-Item $dest).Length / 1MB)
Write-Host "==> Release APK ready: $dest ($sizeMb MB)"
Write-Host '    Share this file; the installer needs "Install unknown apps" enabled. It runs offline (no Metro/PC).'
