<p align="center">
  <img src="assets/apollolauncher-logo.svg" alt="ApolloLauncher" width="760" />
</p>

<p align="center">
  <a href="https://github.com/SANDRO00O/tvluncher/actions/workflows/build.yml"><img src="https://github.com/SANDRO00O/tvluncher/actions/workflows/build.yml/badge.svg" alt="Build status" /></a>
  <img src="https://img.shields.io/badge/Platform-Android%20TV-8FAFD2" alt="Android TV" />
  <img src="https://img.shields.io/badge/Android-API%2021%2B-8FAFD2" alt="Android API 21+" />
  <img src="https://img.shields.io/badge/Language-Java-8FAFD2" alt="Java" />
</p>

<p align="center">A clean, remote-first launcher designed for Android TV.</p>

---

## Overview

**ApolloLauncher** is a lightweight home screen for Android TV devices. It focuses on a clear layout, fast navigation with a TV remote, and a simple viewing experience.

The interface is built with Android framework views and supports Android 5.0 (API 21) and newer devices.

## Features

- **Remote-first navigation** — move through apps and controls using a D-pad, keyboard, or TV remote.
- **Recently used apps** — the most recently launched apps appear first.
- **Minimal menus** — text-only options with clear focus states.
- **Arabic and English** — locale-aware typography and right-to-left scrolling for Arabic.
- **Personalized backgrounds** — dynamic app-colour gradients, built-in wallpapers, and custom images.
- **At-a-glance information** — a large clock, date, and weather on compatible Zeasn-based TV boxes.
- **Branded startup** — a short ApolloLauncher wordmark splash screen.

## Compatibility

| Requirement | Details |
|---|---|
| Platform | Android TV / Leanback devices |
| Minimum Android | Android 5.0 (API 21) |
| Navigation | D-pad, remote control, keyboard |
| Languages | English and Arabic |

Weather information depends on the provider included in the TV firmware. Other launcher features do not require that provider.

## Install

1. Download the latest **ApolloLauncher-debug-apk** artifact from [GitHub Actions](https://github.com/SANDRO00O/tvluncher/actions/workflows/build.yml).
2. Install the APK on the TV. For ADB installation:

   ```bash
   adb install -r app-debug.apk
   ```

3. Press the **Home** button and choose **ApolloLauncher** as the default home app.

Keep the manufacturer launcher available until ApolloLauncher has been tested after a full reboot.

## Build from source

**Requirements:** JDK 17, Android SDK 33, and Gradle 7.6.4.

```bash
gradle assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow builds the debug APK on every push and publishes it as **ApolloLauncher-debug-apk**.

## Application ID

The Android application ID remains `com.karrar.tvlauncher` so existing installations can be updated in place without losing their app data or launcher preferences.

## Font licences

The bundled Inter Display and Noto Kufi Arabic fonts are distributed under the SIL Open Font License 1.1. See [NOTICE-FONTS.txt](NOTICE-FONTS.txt) and [LICENSE-OFL-1.1.txt](LICENSE-OFL-1.1.txt).
