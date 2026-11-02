# TV Launcher — tvOS-inspired redesign

This archive is a **source overlay** for `SANDRO00O/tvluncher`, not a complete standalone repository. Extract the contents of the `tvluncher-redesign/` folder over the root of your existing clone; matching files are replacements and the new assets are added.

A lightweight, remote-first Android TV launcher for Android 5.1+ (API 21+).

## What changed

- A large, vertically stretched centre clock with a custom Inter Display face and a locale-aware date.
- A floating glass-style app dock with artwork tiles, focus lift, and a selected-app title.
- Original line icons for TV inputs, sources, settings, network, browser, media, cast, and wallpaper controls.
- Custom English and Arabic typefaces (Inter Display and Noto Kufi Arabic).
- Four bundled cinematic wallpaper presets: Aurora, Ocean, Violet Dusk, and Desert Dusk.
- A wallpaper picker for choosing an image from the TV's file picker. The selected image is copied into the launcher's private storage so it remains available after reboot.
- Dynamic artwork background by default: the selected app's icon/banner is composited over a colour glow, heavily blurred, and used as the full-screen backdrop. A chosen preset or custom wallpaper overrides this behaviour.
- Framework-only UI and RenderScript blur to preserve Android 5.1 compatibility without adding third-party runtime dependencies.

## Build

After applying the overlay, commit and push the changes. The repository's existing GitHub Actions workflow runs on push and uploads the debug APK artifact. You can also build locally with Android Studio or the command below.

Open the repository in Android Studio or run the existing GitHub Actions workflow (`Build APK`). The debug artifact is uploaded as `TvLauncher-debug-apk`.

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV

```bash
adb install -r app-debug.apk
adb shell am start -n com.karrar.tvlauncher/.MainActivity
```

Press Home and choose **TV Launcher**. Keep the stock launcher installed so you can switch back at any time.

## Wallpaper behaviour

Open **Wallpaper** in the top-right corner. Select Dynamic to restore the selected-app glow, choose one of the four built-in scenes, or choose a custom photo. Custom photos are resized to a TV-friendly resolution and stored inside the app. Selecting **Remove custom wallpaper** returns to Dynamic mode.

## Font licences

The bundled Inter Display and Noto Kufi Arabic fonts are distributed under SIL OFL 1.1. See `NOTICE-FONTS.txt` and `LICENSE-OFL-1.1.txt`.
