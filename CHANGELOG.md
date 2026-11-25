# Changelog

All notable changes to Encircle are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Phone numbers, e-mail addresses and links are recognized across line breaks, including long links broken mid-word.
- Store listing graphics and texts in German and English (`fastlane/metadata`).

### Changed

- Code badges appear together with the main animation and are placed so they never cover recognized text.
- IBANs are shown in groups of four in the bottom bar.
- Refreshed app icon: the ring now opens towards the spark.

### Fixed

- IBANs that wrap over two lines were not recognized.
- A line of digits was split from its paragraph because its text box is shorter than a line with ascenders and descenders.

## [0.1.0] - 2026-11-25

First public release.

### Added

- Invocation as the digital assistant: home long-press, assistant gesture, or the corner swipe on AOSP-based systems such as GrapheneOS.
- Frozen-screen overlay with smart text selection: tap for word, sentence and paragraph, long-press, highlighter swipe, diagonal paragraph swipe, lasso, and drag handles.
- Clean copying that reflows lines and removes end-of-line hyphenation.
- Detection of links, e-mail addresses, phone numbers and IBANs anywhere on screen, including links wrapped over two lines.
- Detection of all common barcode and 2D code formats with actions for links, Wi-Fi, contacts, events, e-mail, phone, SMS and locations.
- Region selection to share or copy part of the screen as an image.
- Material You design with a vivid palette derived from the wallpaper and animations for activation, results and selections.
- Setup screen with step-by-step guidance and diagnostics of the last invocation.
- Support for Android 17 assistant data declarations.

### Security

- Removed the network permissions that ML Kit declares, so screen content can never leave the device.

[Unreleased]: https://github.com/Maxi2555/encircle/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Maxi2555/encircle/releases/tag/v0.1.0
