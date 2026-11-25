# Releasing

Releases are built, signed and published by the [release workflow](../.github/workflows/release.yml) when a version tag is pushed.

## One-time setup: signing key

Android only accepts an update if it is signed with the same key as the installed app, so the release key must stay the same forever. Create it once and keep a backup somewhere safe (a password manager, not the repository):

```sh
keytool -genkeypair -v -keystore encircle-release.jks -alias encircle \
  -keyalg RSA -keysize 4096 -validity 10000
```

Add these [repository secrets](https://github.com/Maxi2555/encircle/settings/secrets/actions):

| Secret | Value |
| --- | --- |
| `ENCIRCLE_KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 encircle-release.jks` |
| `ENCIRCLE_KEYSTORE_PASSWORD` | Keystore password |
| `ENCIRCLE_KEY_ALIAS` | Key alias, e.g. `encircle` |
| `ENCIRCLE_KEY_PASSWORD` | Key password |

The release workflow stops with an error if any of them is missing, so an unsigned or debug-signed build can never be published as a release.

Local builds read the same values from environment variables (`ENCIRCLE_KEYSTORE_FILE`, `ENCIRCLE_KEYSTORE_PASSWORD`, `ENCIRCLE_KEY_ALIAS`, `ENCIRCLE_KEY_PASSWORD`). Without them, release builds are signed with the local debug key.

## Cutting a release

1. Make sure `main` is green in CI.
2. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (semantic versioning, e.g. `0.2.0`).
3. In `CHANGELOG.md`, rename *Unreleased* to the new version with today's date and start a fresh *Unreleased* section. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (and `de-DE`) with a short summary.
4. Commit: `Release 0.2.0`.
5. Tag and push:
   ```sh
   git tag -a v0.2.0 -m "Encircle 0.2.0"
   git push origin main v0.2.0
   ```

The workflow checks that the tag matches `versionName`, builds the APKs, signs them, and publishes a GitHub release with the APKs, SHA-256 checksums and the changelog section as release notes. Versions below 1.0.0 are marked as pre-releases.
