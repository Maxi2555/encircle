# Encircle

Encircle imitiert die Bedienung von Circle to Search auf Android, sucht aber nichts. Home-Taste oder Navigationsleiste gedrückt halten, dann Text oder Codes auf dem eingefrorenen Bildschirm markieren und kopieren, teilen oder öffnen. Alles läuft offline auf dem Gerät.

## Funktionen

- **Start per langem Druck** auf Home oder die Navigationsleiste. Encircle ist dafür als digitaler Assistent eingetragen, genau wie Circle to Search.
- **Textauswahl wie bei Circle to Search:**
  - Tippen markiert ein Wort. Erneutes Tippen erweitert auf den Satz, dann auf den Absatz.
  - Langes Drücken markiert den Absatz.
  - Wischen über Text markiert wie ein Textmarker, live unter dem Finger.
  - Diagonal von Ecke zu Ecke eines Absatzes markiert den ganzen Absatz.
  - Einkreisen markiert alles im Kreis. Ohne Text darin wird der Bereich als Bild ausgewählt.
  - Griffe an Anfang und Ende passen die Auswahl an.
- **Sauberer Text beim Kopieren:** Zeilen innerhalb eines Absatzes werden zusammengeführt, Silbentrennung am Zeilenende entfernt, Listen und kurze Zeilen behalten ihre Umbrüche.
- **Alle gängigen Codes** werden erkannt und hervorgehoben: QR, Micro-QR, rMQR, Data Matrix, Aztec, PDF417, MaxiCode, EAN, UPC, Code 128/39/93, ITF, Codabar, GS1 DataBar.
- **Passende Aktionen je Inhalt:** Link öffnen, WLAN verbinden, Kontakt speichern, Termin eintragen, anrufen, E-Mail oder SMS schreiben, Ort in Karten öffnen, kopieren, teilen.
- **Leiste unten** im Stil der Suchleiste. Nach unten wischen oder Zurück schließt alles.
- Material You mit dynamischen Farben, hell und dunkel.

## Einrichtung auf dem Gerät

1. App installieren und öffnen.
2. **Assistent auswählen** antippen und Encircle als App für den digitalen Assistenten wählen.
3. In denselben Einstellungen **Text auf dem Bildschirm verwenden** und **Screenshot verwenden** einschalten.
4. Falls das Gerät Circle to Search hat, dort ausschalten, damit der lange Druck den Assistenten startet:
   - Pixel: Einstellungen › System › Navigationsmodus.
   - Samsung: Einstellungen › Anzeige › Navigationsleiste.

Android erlaubt Drittanbieter-Apps diesen Einstieg nur über die Assistenten-Rolle. Encircle ersetzt deshalb den bisherigen Assistenten, solange es ausgewählt ist.

## Bauen

Voraussetzungen: JDK 17 oder neuer und das Android SDK.

```sh
./gradlew assembleRelease
```

Die APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`. Der Release-Build ist mit dem lokalen Debug-Schlüssel signiert, damit er sich direkt installieren lässt. Für eine Veröffentlichung einen eigenen Signaturschlüssel eintragen.

Für flüssige Animationen den Release-Build verwenden. Compose läuft im Debug-Build deutlich langsamer.

```sh
./gradlew testDebugUnitTest   # Auswahl-Logik und Code-Parser
```

## Aufbau

| Bereich | Datei |
| --- | --- |
| Assistenten-Dienst und Sitzung | `assist/AssistService.kt`, `assist/OverlaySession.kt` |
| Texterkennung und Barcode-Scan | `analysis/ScreenAnalyzer.kt` |
| Wörter, Zeilen, Absätze, Textzusammenbau | `analysis/TextLayout.kt` |
| Auswertung von Code-Inhalten | `analysis/Codes.kt` |
| Auswahl-Logik und Aktionen | `ui/OverlayController.kt` |
| Oberfläche, Effekte, Gesten | `ui/OverlayScreen.kt` |

Beide Erkennungs-Engines werden beim Prozessstart vorgewärmt. Der Prozess bleibt aktiv, solange Encircle der Assistent ist, daher gibt es beim langen Druck keine Ladezeit. Text und Codes werden parallel erkannt, der eingefrorene Bildschirm erscheint sofort.

## Lizenzen der Bausteine

- [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp), Apache-2.0: Barcode-Erkennung.
- [Google ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), kostenlos nutzbar, aber nicht quelloffen: Texterkennung, komplett offline.
- [Material Design Icons](https://github.com/google/material-design-icons), Apache-2.0.
- Jetpack Compose und AndroidX, Apache-2.0.

ML Kit ist die einzige nicht quelloffene Komponente. Quelloffene Alternativen wie Tesseract sind auf ganzen Bildschirmen um ein Vielfaches langsamer und hätten die verzögerungsfreie Bedienung verhindert.
