# Third-party notices

Encircle's own source code is licensed under the [Apache License 2.0](LICENSE). The APK also contains the components below, each under its own license. Versions are defined in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Component | Used for | License |
| --- | --- | --- |
| [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) (`io.github.zxing-cpp:android`) | Barcode and QR code detection | Apache-2.0 |
| [Google ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) (`com.google.mlkit:text-recognition`) | On-device text recognition (OCR) | Proprietary, [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) |
| Google Play services support libraries (`com.google.android.gms:*`), Firebase components and encoders (`com.google.firebase:*`), Google Data Transport (`com.google.android.datatransport:*`), ODML image (`com.google.android.odml:image`) | Pulled in by ML Kit | Proprietary or Apache-2.0, distributed under the ML Kit terms |
| [AndroidX](https://developer.android.com/jetpack/androidx) (Core, Activity, Lifecycle) and [Jetpack Compose](https://developer.android.com/compose) (UI, Foundation, Material 3) | User interface | Apache-2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), kotlinx.serialization | Language runtime | Apache-2.0 |
| [Material Design icons](https://github.com/google/material-design-icons) | Icons in `app/src/main/res/drawable` | Apache-2.0 |
| Guava ListenableFuture, AutoValue annotations | Pulled in by AndroidX and ML Kit | Apache-2.0 |

Build and test tooling (Android Gradle Plugin, Gradle, JUnit) is not shipped in the APK.

## About Google ML Kit

ML Kit is free to use but not open source. Encircle uses the bundled text model, which runs entirely on the device and needs neither Google Play services nor a network connection. The app removes the network permissions that ML Kit declares, so ML Kit cannot send usage statistics.

Because ML Kit is proprietary, Encircle does not qualify for F-Droid's main repository. A build flavour with an open-source OCR engine would change that, and contributions in that direction are welcome.

## Trademarks

"Circle to Search", "Android", "Google Play" and "Material You" are trademarks of Google LLC. Encircle is not affiliated with or endorsed by Google.
