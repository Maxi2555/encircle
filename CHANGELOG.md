# Changelog

All notable changes to Encircle are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
