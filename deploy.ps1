# Builds Quick Launcher, installs it on the TV and re-enables the Home-button service.
# Usage: .\deploy.ps1            (uses the default TV address)
#        .\deploy.ps1 -Tv 192.168.88.6:5555
param([string]$Tv = "192.168.88.6:5555")
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$service = "com.nick.tvlauncher/com.nick.tvlauncher.HomeWatcherService"

Set-Location $PSScriptRoot
.\gradlew.bat assembleDebug -q --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

& $adb connect $Tv | Out-Null
& $adb -s $Tv install -r app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw "Install failed (is the TV on and authorized? try: adb devices)" }

# Reinstalling unbinds the accessibility service; toggling the setting rebinds it.
# Other enabled services are kept so deploying never turns off the user's own.
$current = "$(& $adb -s $Tv shell settings get secure enabled_accessibility_services)".Trim()
$others = @($current -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $service })
$without = if ($others.Count) { $others -join ':' } else { 'null' }
& $adb -s $Tv shell settings put secure enabled_accessibility_services $without
& $adb -s $Tv shell settings put secure enabled_accessibility_services (@($others) + $service -join ':')
& $adb -s $Tv shell settings put secure accessibility_enabled 1
& $adb -s $Tv shell am start -n com.nick.tvlauncher/.LauncherActivity | Out-Null
Write-Host "Deployed to $Tv"
