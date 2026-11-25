# Releasing

Releases are fully automated. Nobody edits version numbers or changelogs by hand, and nobody pushes tags: the workflows derive everything from the pull request titles on `beta`.

## Overview

| Event | Workflow | Result |
| --- | --- | --- |
| Pull request merged into `beta` | [Beta release](../.github/workflows/beta-release.yml) | Pre-release such as `v0.2.0-beta.3`, if the merge contains user-facing changes |
| Maintainer runs *Prepare stable release* | [Prepare stable release](../.github/workflows/prepare-stable.yml) | Opens or updates the release pull request `beta` → `stable` |
| Release pull request merged into `stable` | [Stable release](../.github/workflows/stable-release.yml) | Stable release such as `v0.2.0`, marked as latest |

Both release workflows build through the reusable [Build signed release](../.github/workflows/build-release.yml) workflow: unit tests, per-ABI APKs, a universal APK and the app bundle for Google Play, all signed with the release key, plus SHA-256 checksums and the certificate fingerprint in the notes.

## Version numbers

[`scripts/version.sh`](../scripts/version.sh) looks at the squash commits since the last stable tag:

- any `feat` → next minor version
- otherwise any `fix`, `perf`, `refactor`, `revert` or `build(deps)` → next patch version
- `type!:` or `BREAKING CHANGE` → next major version (next minor while below 1.0)
- only `docs`, `test`, `ci`, `chore`, `style`, `build` → no release

Betas count up per version: `0.2.0-beta.1`, `0.2.0-beta.2`, … The Android `versionCode` is `major × 1 000 000 + minor × 10 000 + patch × 100 + n`, where `n` is the beta number (1–98) and 99 for the stable release. That keeps every build installable over the previous one, betas included.

Run the script locally to see what the next release would be:

```sh
bash scripts/version.sh beta     # next pre-release
bash scripts/version.sh stable   # next stable release
bash scripts/release-notes.sh v0.1.1   # notes since v0.1.1
```

## Release notes

[`scripts/release-notes.sh`](../scripts/release-notes.sh) groups the pull request titles into *Breaking changes*, *Features*, *Fixes*, *Performance* and *Other changes*, each sorted alphabetically, with docs, CI and chores folded into *Maintenance*. Pre-release notes list the changes since the previous pre-release and, folded, everything since the last stable release.

`CHANGELOG.md` covers the releases before this process. Newer changes are documented in the [GitHub releases](https://github.com/Maxi2555/encircle/releases).

## Cutting a stable release

1. Check that the latest pre-release works well. Testers install it from the releases page.
2. Open *Actions › Prepare stable release › Run workflow*. It opens the pull request `chore(release): vX.Y.Z` from `beta` into `stable` with the release notes. If one is already open, it updates it.
3. Review the notes and merge with **Create a merge commit** (not squash), so `beta` and `stable` keep a shared history.
4. The *Stable release* workflow publishes the release. Upload the `.aab` from the release to Google Play.

Hotfixes take the same path: a `fix` pull request into `beta`, then the release pull request.

## Branch protection

`beta` and `stable` are protected for everyone, administrators included:

- changes only through pull requests, no direct or force pushes, no deletion
- required checks on `beta`: *Test, lint and build*, *Branch rules*, *Conventional title*
- required checks on `stable`: *Test, lint and build*, *Branch rules* (which rejects any head branch other than `beta`)
- open review conversations must be resolved

Merge settings: *Squash and merge* uses the pull request title as the commit, *Create a merge commit* is allowed for the release pull request, rebase merging is off.

## Signing key

Android only accepts an update signed with the same key as the installed app, so the release key must never change. It is stored as repository secrets:

| Secret | Value |
| --- | --- |
| `ENCIRCLE_KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 encircle-release.jks` |
| `ENCIRCLE_KEYSTORE_PASSWORD` | Keystore password |
| `ENCIRCLE_KEY_ALIAS` | Key alias, `encircle` |
| `ENCIRCLE_KEY_PASSWORD` | Key password |

The build workflow stops with an error if the key is missing, so a debug-signed build can never be published. Keep a backup of the keystore and its password outside GitHub; without it, no update can ever be published again.

Local release builds read the same values from environment variables (`ENCIRCLE_KEYSTORE_FILE`, `ENCIRCLE_KEYSTORE_PASSWORD`, `ENCIRCLE_KEY_ALIAS`, `ENCIRCLE_KEY_PASSWORD`). Without them they are signed with the local debug key and get the version `0.0.0-dev`.
