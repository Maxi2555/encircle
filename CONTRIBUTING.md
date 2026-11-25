# Contributing to Encircle

Thanks for helping. Encircle is a small project that wants to grow into a solid, community-run tool, and every kind of contribution counts: bug reports, recognition samples, translations, design ideas, documentation and code.

You can write in English or German.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug.** Use the [bug report form](https://github.com/Maxi2555/encircle/issues/new?template=bug_report.yml).
- **Report a recognition problem.** Text, a link or a code was not recognized, or was recognized wrongly. Use the [recognition form](https://github.com/Maxi2555/encircle/issues/new?template=recognition.yml) and attach a screenshot. These reports are the fastest way to improve Encircle, because every rule in the recognition code started as one of them.
- **Suggest a feature.** Use the [feature request form](https://github.com/Maxi2555/encircle/issues/new?template=feature_request.yml). Explain the problem first, the solution second.
- **Ask a question or share an idea.** Use [Discussions](https://github.com/Maxi2555/encircle/discussions) instead of issues.
- **Translate.** The UI is German only so far. Moving the strings to resources and adding languages is one of the most useful contributions right now.
- **Pick up an issue.** Issues labelled [`good first issue`](https://github.com/Maxi2555/encircle/labels/good%20first%20issue) or [`help wanted`](https://github.com/Maxi2555/encircle/labels/help%20wanted) are ready to work on. Comment that you are taking one, so nobody duplicates the work.

Before opening an issue, search the existing ones. Please report security problems privately as described in [SECURITY](SECURITY.md).

## Development setup

You need JDK 17 or newer (21 recommended) and the Android SDK. Android Studio works, but any editor plus the command line is fine.

```sh
git clone https://github.com/Maxi2555/encircle.git
cd encircle
./gradlew assembleRelease testDebugUnitTest lintDebug
```

To try your build, install it and choose Encircle as the digital assistant (see the README). Over adb:

```sh
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
adb shell cmd role add-role-holder android.app.role.ASSISTANT de.volcanoproject.encircle
adb shell input keyevent KEYCODE_ASSIST
```

Use release builds to judge animations and speed. Debug builds of Compose are much slower.

The app's start screen shows diagnostics of the last invocation (flags, screenshot size, recognition time). Include them in bug reports.

## Project layout

```
app/src/main/java/de/volcanoproject/encircle/
  assist/     VoiceInteractionService, session and window handling
  analysis/   OCR and barcode engines, layout rebuilding, link and code parsing
  ui/         Overlay state (OverlayController), drawing and gestures (OverlayScreen), theme
  actions/    Intents to other apps: share, open, dial, Wi-Fi, contacts, calendar
app/src/test/ Unit tests for selection, layout and recognition logic
docs/         Architecture, privacy, release process
```

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains how the pieces fit together.

## Making changes

1. Fork the repository and create a branch from `main`, for example `fix/wrapped-links` or `feat/spanish`.
2. Keep each pull request to one topic. Small pull requests get reviewed faster.
3. Add or update unit tests when you change logic in `analysis/` or the selection rules in `ui/`. Recognition fixes should include the failing input as a test case; the raw OCR fragments from a real device are ideal (see `TextUnderstandingTest`).
4. Run `./gradlew testDebugUnitTest lintDebug assembleRelease` before pushing. CI runs the same.
5. Update [CHANGELOG.md](CHANGELOG.md) under *Unreleased* if users will notice the change.
6. Open the pull request and fill in the template. Screenshots or short screen recordings help a lot for anything visual.

### Code style

- Kotlin, following the [official Kotlin style](https://kotlinlang.org/docs/coding-conventions.html). Four spaces, trailing commas, lines up to about 140 characters. [.editorconfig](.editorconfig) sets the basics.
- New source files start with `// SPDX-License-Identifier: Apache-2.0`.
- Comments explain *why*, not *what*. Platform quirks (Android versions, OEM behaviour, OCR oddities) deserve a comment, because they are not obvious from the code.
- No new dependency without a reason in the pull request. Prefer well-maintained open-source libraries with a license compatible with Apache-2.0. Proprietary dependencies are not accepted.
- Encircle must stay offline. Do not add network access, analytics or crash reporting that sends data off the device.
- Keep the overlay fast: no work on the main thread that scales with screen content, and no noticeable delay before the frozen screen appears.

### Commit messages

Write a short summary line in the imperative ("Fix wrapped links in German text"), optionally followed by a blank line and a body that explains why. Reference issues with `Fixes #123`.

## Licensing of contributions

Encircle is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you agree that it is licensed under the same terms (Apache-2.0, section 5), and you confirm that you have the right to submit it. Do not copy code, icons or assets from sources with incompatible licenses.

## Reviews and releases

Maintainers review pull requests as time permits; a friendly ping after a week is fine. Releases are cut from `main` by pushing a version tag, see [docs/RELEASING.md](docs/RELEASING.md).
