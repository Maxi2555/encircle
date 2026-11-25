<div align="center">

# Encircle

**Circle to Search, without the search.**
Select text, links and codes anywhere on your Android screen, then copy, share or open them. Everything stays on your device.

[![CI](https://github.com/Maxi2555/encircle/actions/workflows/ci.yml/badge.svg?branch=beta)](https://github.com/Maxi2555/encircle/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/Maxi2555/encircle?include_prereleases&sort=semver)](https://github.com/Maxi2555/encircle/releases)

[Deutsch](README.de.md)

</div>

---

Encircle registers as your phone's digital assistant. When you invoke it (long-press home, or the assistant gesture), it freezes the current screen and lets you pick what you need, the way Circle to Search does. It never sends anything anywhere: there is no search, no account and no network permission.

## Features

- **Smart text selection.** Tap a word, tap again for the sentence, a third time for the paragraph. Long-press selects the paragraph. Swipe over text like a highlighter, swipe diagonally across a paragraph to take all of it, or draw a circle around anything. Handles adjust the selection.
- **Links, addresses, numbers.** Links, e-mail addresses, phone numbers and IBANs are found anywhere on screen and underlined, even when they wrap over two lines or are written as `name (at) example (dot) org`. One tap selects the whole thing and offers the right action.
- **Every common barcode.** QR, Micro QR, rMQR, Data Matrix, Aztec, PDF417, MaxiCode, EAN/UPC, Code 128/39/93, ITF, Codabar and GS1 DataBar are detected and outlined. Wi-Fi codes connect, contact cards save, events go to the calendar, links open.
- **Clean copies.** Lines are reflowed into paragraphs, hyphenation at line ends is removed, and lists keep their line breaks.
- **Material You.** Dynamic colours from your wallpaper, light and dark theme, and lively but quick animations.
- **Private by design.** Recognition runs entirely on the device and the app has no internet permission. See [PRIVACY](docs/PRIVACY.md).

The user interface is currently in German. Translations are very welcome, see [CONTRIBUTING](CONTRIBUTING.md).

## Install

Download the APK for your device from the [latest release](https://github.com/Maxi2555/encircle/releases):

| File | For |
| --- | --- |
| `encircle-<version>-arm64-v8a.apk` | almost all current phones |
| `encircle-<version>-armeabi-v7a.apk` | older 32-bit phones |
| `encircle-<version>-x86_64.apk` | emulators, x86 tablets |
| `encircle-<version>-universal.apk` | any device, larger download |

Each release lists SHA-256 checksums. Requires Android 10 or newer.

### Release channels

| Channel | Where | For |
| --- | --- | --- |
| **Stable** | [Latest release](https://github.com/Maxi2555/encircle/releases/latest) | Everyday use |
| **Beta** | [Pre-releases](https://github.com/Maxi2555/encircle/releases), e.g. `0.2.0-beta.3` | Trying new features early and reporting problems |

Both are signed with the same key, so you can switch between them without reinstalling. Beta versions always have a higher version code than the stable release before them.

## Set up

1. Open Encircle and tap **Assistent auswählen** (choose assistant). Pick Encircle as the digital assistant app.
2. In the same settings, enable **Use screen content** and, where offered, **Use screenshot**.
3. Choose how to invoke it:
   - **Three-button navigation:** long-press home.
   - **Gesture navigation on GrapheneOS and other AOSP-based systems:** enable the assistant swipe under *Settings › System › Navigation mode › ⚙*, then swipe diagonally up from a bottom corner. On these systems a long-press on the gesture handle is wired to Circle to Search and cannot reach other assistants.
   - **Pixel with the Google launcher:** turn off Circle to Search under *Settings › System › Navigation mode*, then long-press the handle.
   - **Samsung:** turn off Circle to Search under *Settings › Display › Navigation bar*.

Android only gives this entry point to the assistant role, so Encircle replaces your current assistant while it is selected.

<details>
<summary>GrapheneOS: Encircle stops working after a reboot</summary>

AOSP drops any non-system assistant when a user or profile starts without a speech recognizer configured, which is the default on GrapheneOS. Encircle then shows **Neu verbinden** (reconnect): select *None* and then Encircle again in the assistant settings.

To fix it permanently, register Encircle's placeholder recognizer once over adb (`<user>` is the profile's user id from `adb shell pm list users`):

```sh
adb shell settings --user <user> put secure voice_recognition_service de.volcanoproject.encircle/.assist.StubRecognitionService
```

</details>

## Build

Requirements: JDK 17 or newer and the Android SDK. The build downloads the platform it needs.

```sh
./gradlew assembleRelease      # APKs in app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android lint
```

Without signing credentials, release builds are signed with your local debug key so they install directly. Official releases are built, versioned and signed by GitHub Actions, see [RELEASING](docs/RELEASING.md). Judge performance on release builds; Compose is much slower in debug builds.

## How it works

Encircle is a `VoiceInteractionService`. The system hands its session a screenshot, which Encircle shows frozen while two engines run in parallel: [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) for barcodes and Google ML Kit for text. The raw OCR output is rebuilt into words, lines and paragraphs from geometry, and links and similar strings are detected on top of that. More in [ARCHITECTURE](docs/ARCHITECTURE.md).

## Contributing

Bug reports, recognition problems, ideas, translations and code are all welcome. Development happens on the `beta` branch; please read [CONTRIBUTING](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Report security issues as described in [SECURITY](SECURITY.md), not in public issues.

## License

Encircle is licensed under the [Apache License 2.0](LICENSE).

It bundles third-party components under their own terms, listed in [NOTICE](NOTICE) and [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md). Text recognition uses **Google ML Kit**, which is free to use but **not open source**. Because of that, Encircle cannot be published on F-Droid yet. Replacing it with an open OCR engine is on the roadmap.

Encircle is an independent project and is not affiliated with Google. "Circle to Search" is a trademark of Google LLC and is used here only to describe the interaction style.
