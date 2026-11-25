# Privacy

Encircle is built to read your screen without anything leaving your phone.

## What Encircle can see

When you invoke Encircle, Android hands it a **screenshot** of the current screen. Encircle declares that it uses screenshots and basic assist data, but it does not request the text structure of other apps (`usesAssistStructureScreenContent` is not set). It reads text only from the screenshot, with on-device recognition.

Android does not give Encircle a screenshot of apps that block screenshots (for example banking apps or private browser tabs). In that case Encircle shows a message instead.

## What happens with it

- The screenshot and everything recognized in it stay in memory for the current session only and are discarded when the overlay closes.
- Text and barcode recognition run entirely on the device: ML Kit's bundled model for text, zxing-cpp for codes.
- **Encircle has no internet permission.** ML Kit normally declares network access for usage statistics; Encircle removes it in its manifest, so no data can be sent.
- There is no account, no analytics, no crash reporting and no advertising.

## What is stored

- **Diagnostics of the last invocation** (time, invocation flags, screenshot size, number of recognized words and codes, timing) are stored in the app's private storage so they can be shown on the start screen. They contain no screen content.
- When you **share or copy an image**, the cropped picture is written to the app's private cache and handed to the app you choose through a temporary content URI.
- When you **copy** text, it goes to the system clipboard. Wi-Fi passwords are marked as sensitive, so Android hides them in clipboard previews.

## Actions

Encircle only acts when you tap an action. Opening a link, writing an e-mail, dialling, connecting to Wi-Fi or saving a contact hands the data to the respective app, and that app's own privacy policy applies from there.
