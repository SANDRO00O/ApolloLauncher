<p align="center">
  <img src="assets/apollolauncher-logo.svg" alt="ApolloLauncher" width="760" />
</p>

<p align="center">
  <a href="https://github.com/SANDRO00O/ApolloLauncher/actions/workflows/release.yml"><img src="https://github.com/SANDRO00O/ApolloLauncher/actions/workflows/release.yml/badge.svg" alt="Release workflow" /></a>
  <a href="https://github.com/SANDRO00O/ApolloLauncher/releases/latest"><img src="https://img.shields.io/github/v/release/SANDRO00O/ApolloLauncher?color=8FAFD2" alt="Latest release" /></a>
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

## Download and install

Download the latest signed APK from [GitHub Releases](https://github.com/SANDRO00O/ApolloLauncher/releases/latest).

For installation through ADB:

```bash
adb install -r ApolloLauncher-2.0.0.apk
```

The first installation of a release-signed APK may require uninstalling a previously installed CI debug build because the signing certificates differ. See [Release setup](RELEASE-SETUP.md) before switching signing keys.

## Build from source

**Requirements:** JDK 17, Android SDK 33, and Gradle 7.6.4.

Build a debug APK:

```bash
gradle assembleDebug
```

Build a locally unsigned release APK (the GitHub release workflow adds the configured signing key):

```bash
gradle assembleRelease
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`; the release APK is generated at `app/build/outputs/apk/release/app-release.apk`.

## Publish a release

1. Configure the one-time signing secrets described in [RELEASE-SETUP.md](RELEASE-SETUP.md).
2. Push a version tag such as `v2.0.0`, or run **Actions → ApolloLauncher Release → Run workflow** and enter the version.
3. The workflow builds and signs the release APK, creates a GitHub Release with generated notes, and attaches the APK and its SHA-256 checksum.

The regular **ApolloLauncher Build** workflow continues to publish debug APK artifacts for testing.

## Application ID

The Android application ID remains `com.karrar.tvlauncher` so existing installations retain the same package identity.

## Font licences

The bundled Inter Display and Noto Kufi Arabic fonts are distributed under the SIL Open Font License 1.1. See [NOTICE-FONTS.txt](NOTICE-FONTS.txt) and [LICENSE-OFL-1.1.txt](LICENSE-OFL-1.1.txt).
