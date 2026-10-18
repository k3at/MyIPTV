# Builds the MyIPTV APK / AAB. Usage:
#   .\build.ps1            -> debug APK
#   .\build.ps1 -Release   -> release APK (minified, signed with the upload key)
#   .\build.ps1 -Bundle    -> release AAB for Play Store upload
#   .\build.ps1 -Release -Bundle -> both
param(
    [switch]$Release,
    [switch]$Bundle
)

$ErrorActionPreference = "Stop"

# Prefer Android Studio's bundled JDK if JAVA_HOME is not already set.
if (-not $env:JAVA_HOME) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) {
        $env:JAVA_HOME = $jbr
        $env:PATH = "$jbr\bin;$env:PATH"
    }
}

$tasks = @()
if (-not $Release -and -not $Bundle) {
    $tasks += ":app:assembleDebug"
} else {
    if ($Release) { $tasks += ":app:assembleRelease" }
    if ($Bundle)  { $tasks += ":app:bundleRelease" }
}

# Gradle must run from the project root (where settings.gradle.kts lives).
Set-Location $PSScriptRoot

Write-Host "Building $($tasks -join ', ') ..." -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" @tasks

Write-Host ""
Write-Host "APKs are under: $PSScriptRoot\app\build\outputs\apk" -ForegroundColor Green
Write-Host "AAB  is  under: $PSScriptRoot\app\build\outputs\bundle" -ForegroundColor Green
