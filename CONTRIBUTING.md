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

```text
app/src/main/java/de/volcanoproject/encircle/
  assist/     VoiceInteractionService, session and window handling
  analysis/   OCR and barcode engines, layout rebuilding, link and code parsing
  ui/         Overlay state (OverlayController), drawing and gestures (OverlayScreen), theme
  actions/    Intents to other apps: share, open, dial, Wi-Fi, contacts, calendar
app/src/test/ Unit tests for selection, layout and recognition logic
docs/         Architecture, privacy, release process
scripts/      Version numbers, release notes and coverage summary used by the workflows
```

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains how the pieces fit together.

## Branches

```text
feature branch ──PR──▶ beta ──release PR──▶ stable
 (fix/…, feat/…)        │                     │
                        ▼                     ▼
                  pre-release           stable release
                  v0.2.0-beta.3         v0.2.0
```

| Branch | Purpose |
| --- | --- |
| `beta` | Default branch. All work lands here first, through pull requests. Every merge with user-facing changes publishes a pre-release. |
| `stable` | What users run. Only accepts the release pull request from `beta`. Every merge publishes a stable release. |
| `feat/…`, `fix/…`, `docs/…` | Your working branches, created from `beta` and deleted after the merge. |

Both `beta` and `stable` are protected for everyone, maintainers included: no direct pushes, no force pushes, and a pull request can only be merged when all required checks are green.

## Making changes

1. Fork the repository (or, with write access, just branch) and create a branch **from `beta`**, for example `fix/wrapped-iban` or `feat/spanish`.
2. Keep each pull request to one topic. Small pull requests get reviewed faster.
3. Add or update unit tests when you change logic in `analysis/` or the selection rules in `ui/`. Recognition fixes should include the failing input as a test case; the raw OCR fragments from a real device are ideal (see `TextUnderstandingTest`).
4. Run `./gradlew testDebugUnitTest lintDebug assembleRelease` before pushing. CI runs the same.
5. Open the pull request **against `beta`** with a title in the format below, and fill in the template. Screenshots or short screen recordings help a lot for anything visual.
6. Once the checks are green, a maintainer merges it with **Squash and merge**. The pull request title becomes the commit on `beta`.

### Pull request titles

Titles follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/): `type(optional scope): description`. A check enforces it, because the titles drive version numbers and release notes.

| Type | Use for | Effect on the next version |
| --- | --- | --- |
| `feat` | A new capability users notice | minor, e.g. 0.2.0 → 0.3.0 |
| `fix` | A bug fix | patch, e.g. 0.2.0 → 0.2.1 |
| `perf` | Faster or lighter without new behaviour | patch |
| `refactor` | Code change without visible effect | patch |
| `revert` | Undoing an earlier change | patch |
| `docs` | Documentation only | none |
| `test` | Tests only | none |
| `build` | Gradle, dependencies (`build(deps): …` counts as patch) | none |
| `ci` | Workflows and scripts | none |
| `chore` | Anything else that users do not see | none |
| `style` | Formatting only | none |

Add `!` after the type for breaking changes, e.g. `feat(assist)!: require Android 12`. That bumps the major version (the minor version while Encircle is below 1.0).

Good titles: `fix(ocr): keep wrapped IBANs together`, `feat(i18n): add Spanish translation`, `docs: explain the corner swipe`. Write the description in lower case, in the imperative, without a final period. Scopes are free text; common ones are `ocr`, `codes`, `ui`, `assist`, `i18n`, `deps`.

### What the checks do

| Check | What it does |
| --- | --- |
| Test, lint and build | Unit tests, Android lint, coverage and a release build. Test failures and lint findings appear as annotations in the diff, coverage per package as a comment. |
| Branch rules | Makes sure only `beta` is merged into `stable`. |
| Conventional title | Checks the pull request title. Fix it by editing the title; no new commit needed. |
| Docs | Markdown style and links, when documentation changes. |
| Labels | Adds area labels (`recognition`, `ui`, `platform`, …) and a size label automatically. |

Coverage is reported, not enforced. Logic in `analysis/` should stay well covered; UI and Android glue are tested on devices.

### Code style

- Kotlin, following the [official Kotlin style](https://kotlinlang.org/docs/coding-conventions.html). Four spaces, trailing commas, lines up to about 140 characters. [.editorconfig](.editorconfig) sets the basics.
- New source files start with `// SPDX-License-Identifier: Apache-2.0`.
- Comments explain *why*, not *what*. Platform quirks (Android versions, OEM behaviour, OCR oddities) deserve a comment, because they are not obvious from the code.
- No new dependency without a reason in the pull request. Prefer well-maintained open-source libraries with a license compatible with Apache-2.0. Proprietary dependencies are not accepted.
- GitHub Actions are pinned to a full commit SHA with the version as a comment. Dependabot keeps them current.
- Encircle must stay offline. Do not add network access, analytics or crash reporting that sends data off the device.
- Keep the overlay fast: no work on the main thread that scales with screen content, and no noticeable delay before the frozen screen appears.

### Changelog

There is no changelog file to edit. Release notes are generated from the pull request titles, grouped by type and sorted alphabetically, and published with each GitHub release. A clear title is therefore the changelog entry.

## Licensing of contributions

Encircle is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you agree that it is licensed under the same terms (Apache-2.0, section 5), and you confirm that you have the right to submit it. Do not copy code, icons or assets from sources with incompatible licenses.

## Reviews and releases

Maintainers review pull requests as time permits; a friendly ping after a week is fine.

Pre-releases appear automatically after merges into `beta`. When `beta` is ready for users, a maintainer starts the *Prepare stable release* workflow, which opens the release pull request into `stable`; merging it publishes the stable release. Details for maintainers are in [docs/RELEASING.md](docs/RELEASING.md).
