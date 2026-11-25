<!--
Target branch: beta.
Title format: type(scope): description, e.g. "fix(ocr): keep wrapped IBANs together".
The title becomes the release note entry. See CONTRIBUTING.md, section "Pull request titles".
-->

## What and why

<!-- What does this change, and why is it needed? Link the issue: "Fixes #123". -->

## How it was tested

<!-- Device and Android version, steps you tried. For visual changes, add screenshots or a short recording. -->

## Checklist

- [ ] The pull request targets `beta` and the title follows the Conventional Commits format
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` passes locally
- [ ] New or changed logic in `analysis/` or the selection rules has unit tests
- [ ] No network access, analytics or proprietary dependencies added
- [ ] New source files carry the `SPDX-License-Identifier: Apache-2.0` header
