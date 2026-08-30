$ErrorActionPreference = "Stop"

$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = "9.5.0"
$Tools = Join-Path $Project ".local-tools"
$GradleHome = Join-Path $Tools "gradle-$GradleVersion"

if (-not $env:JAVA_HOME) {
    $StudioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $StudioJbr) { $env:JAVA_HOME = $StudioJbr }
}
if (-not $env:ANDROID_HOME) {
    $DefaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $DefaultSdk) { $env:ANDROID_HOME = $DefaultSdk }
}

if (-not (Test-Path $GradleHome)) {
    New-Item -ItemType Directory -Force -Path $Tools | Out-Null
    $Zip = Join-Path $Tools "gradle-$GradleVersion-bin.zip"
    Write-Host "Downloading Gradle $GradleVersion..."
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $Zip
    Expand-Archive $Zip -DestinationPath $Tools -Force
}

if (-not $env:ANDROID_HOME) {
    throw "Android SDK not found. Install Android Studio once, then run this script again."
}

$Gradle = Join-Path $GradleHome "bin\gradle.bat"
Push-Location $Project
try {
    & $Gradle :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
    Write-Host ""
    Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk"
} finally {
    Pop-Location
}
