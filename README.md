# Quick Launcher for Fire TV

A fast, ad-free home screen for Fire TV smart TVs (built and tested on a 4K Insignia Fire TV, Fire OS 8).

- **Favorites, Inputs and All apps** sections, with brand-colored cards for apps that have no TV banner
- **HDMI inputs** open directly in a built-in full-screen player
- **Home button redirect**: an accessibility service sends the Home button to Quick Launcher instead of the Amazon home screen
- Options panel (≡ / long-press Select): favorite, reorder, hide, app info

## Build and install

Requirements: JDK 17, Android SDK (platform 35), and ADB debugging enabled on the TV.

```powershell
.\deploy.ps1 -Tv <tv-ip>:5555
```

This builds the debug APK, installs it, enables the Home-button service and opens the launcher.

To enable the Home-button service manually:

```
adb shell settings put secure enabled_accessibility_services com.nick.tvlauncher/com.nick.tvlauncher.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
```

## Versions

- `v1-grid`: original simple grid launcher
- `v2-modern`: redesigned home with sections, colored cards and options panel
