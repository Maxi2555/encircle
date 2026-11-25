# Architecture

Encircle is a single Android app module written in Kotlin with Jetpack Compose. This page explains how a long-press turns into a selectable, frozen screen.

## Entry point: the assistant role

Android lets third-party apps react to the home long-press (or the assistant gesture) only through the **digital assistant role**. Encircle therefore implements the voice interaction stack:

| Class | Role |
| --- | --- |
| `assist/AssistService` | `VoiceInteractionService`. Bound by the system while Encircle is the assistant. Also starts a session from the "try it" button. |
| `assist/AssistSessionService` | Creates one `OverlaySession` per invocation. |
| `assist/OverlaySession` | `VoiceInteractionSession`. Owns the full-screen, edge-to-edge window and hosts Compose in it (with its own lifecycle owner, since there is no Activity). Receives the screenshot in `onHandleScreenshot`. |
| `assist/StubRecognitionService` | A placeholder `RecognitionService`. The platform only accepts assistants that declare one. |

`res/xml/assist_service.xml` declares `usesAssistData` and `usesAssistScreenshots`. Since Android 17 (API 37) the system only delivers a screenshot when both are declared. Encircle deliberately does not declare `usesAssistStructureScreenContent`: it never reads other apps' view hierarchy.

### Platform quirks worth knowing

- On AOSP-based systems such as GrapheneOS, Launcher3 sends a long-press on the gesture handle to Contextual Search only. Encircle is reached there through the corner swipe or the three-button home long-press.
- AOSP drops a non-system assistant at user start when no speech recognizer is configured. `AssistService.isRoleHeldButDisconnected` detects that state so the setup screen can guide the user.

## Pipeline per invocation

```text
screenshot (hardware bitmap)
   │  copy to ARGB_8888
   ├──────────────► zxing-cpp ──► DetectedCode list ──► CodeParser (Wi-Fi, vCard, event, URL, …)
   └──────────────► ML Kit OCR ──► raw lines ──► LayoutBuilder ──► TextLayout ──► EntityFinder
                                                                    (words, lines,      (links, e-mail,
                                                                     paragraphs)         phone, IBAN)
```

Both engines are created and warmed up when the process starts (`EncircleApp`), and the process stays alive while Encircle is the assistant, so there is no loading delay. Barcode scanning and OCR run in parallel; the frozen screenshot is shown immediately and results appear as they arrive.

### `analysis/LayoutBuilder`

OCR engines report text in their own structure, which often does not match what a reader sees: one visual line comes back in pieces, one paragraph in several blocks, and links are cut after dots and slashes. The engine's box gaps are too noisy to tell a real space from a split. `LayoutBuilder` rebuilds the structure:

1. **Glue** pieces that belong to one string, decided by their text (`https://example.` + `org`, `hallo@` + `example.org`, `example.org` + `/path`).
2. **Merge rows**: pieces on the same baseline with similar height become one line.
3. **Group paragraphs**: a line joins the paragraph above it if the gap matches normal line spacing (and the paragraph's own rhythm), the text size is similar and the left edges line up or the lines overlap horizontally. Columns stay separate.

### `analysis/EntityFinder`

Searches each paragraph's text for links (with scheme, `www.` or a bare domain with a known TLD), e-mail addresses (including `(at)`/`(dot)` spelling), phone numbers (not dates or amounts) and IBANs (with checksum). Links that wrap at a line end are joined. Matches map back to word indices, so the UI can underline them and select them as one unit.

### `analysis/TextLayout`

The selection model: words in reading order with line and paragraph indices, plus helpers for word hit-testing, sentence detection (abbreviation-aware) and turning a selection back into clean text (reflowing lines, removing end-of-line hyphenation, keeping list breaks).

## UI

| File | Responsibility |
| --- | --- |
| `ui/OverlayController` | All state of one session: screenshot, layout, codes, entities, current selection. Gesture handlers (tap, long-press, highlight stroke, lasso, handle drag), progressive selection (word or entity → sentence → paragraph) and the action list for each selection type. |
| `ui/OverlayScreen` | Drawing and input. One `Canvas` paints the screenshot, scrim, selection, entity underlines, code outlines and animations in screenshot coordinates. One pointer detector handles every gesture so they never conflict. Also the code badges and the bottom bar with its swipe-to-dismiss. |
| `ui/Geometry` | Stroke analysis (lasso detection by closure and turning angle), polygon helpers, path smoothing. |
| `ui/Theme` | Material You theme and `GlowPalette`, a vivid palette derived from the wallpaper's accent colour for all light effects. |

Coordinates: everything in the controller is in screenshot pixels. The UI keeps `origin` and `scale` up to date to map between view and screenshot space.

## Actions

`actions/SystemActions` wraps every hand-off to other apps: share, open, e-mail, dial, SMS, adding a Wi-Fi network, contact insert (vCard through `FileProvider`), calendar insert, clipboard. Each returns whether an app handled it, so the overlay can close or show "no suitable app".

## Tests

Logic that does not need Android lives in plain Kotlin and is covered by JVM unit tests in `app/src/test`: selection rules, text reflow, layout rebuilding, entity detection and code parsing. Recognition bugs should be reproduced there with the raw OCR fragments from a real device.
