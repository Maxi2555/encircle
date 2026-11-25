# Encircle

Encircle imitiert die Bedienung von Circle to Search auf Android, sucht aber nichts. Home-Taste oder Navigationsleiste gedrückt halten, dann Text oder Codes auf dem eingefrorenen Bildschirm markieren und kopieren, teilen oder öffnen. Alles läuft offline auf dem Gerät.

## Funktionen

- **Start per langem Druck** auf Home oder die Navigationsleiste, auf AOSP-Systemen mit Gestennavigation per Wisch aus der unteren Ecke. Encircle ist dafür als digitaler Assistent eingetragen, genau wie Circle to Search.
- **Textauswahl wie bei Circle to Search:**
  - Tippen markiert ein Wort. Erneutes Tippen erweitert auf den Satz, dann auf den Absatz.
  - Langes Drücken markiert den Absatz.
  - Wischen über Text markiert wie ein Textmarker, live unter dem Finger.
  - Diagonal von Ecke zu Ecke eines Absatzes markiert den ganzen Absatz.
  - Einkreisen markiert alles im Kreis. Ohne Text darin wird der Bereich als Bild ausgewählt.
  - Griffe an Anfang und Ende passen die Auswahl an.
- **Links, E-Mail-Adressen, Telefonnummern und IBANs** werden im erkannten Text gefunden und farbig unterstrichen, auch wenn sie über zwei Zeilen umbrechen oder als „name (at) firma (dot) de“ geschrieben sind. Ein Tippen markiert sie vollständig und bietet die passende Aktion an.
- **Absätze wie auf dem Bildschirm:** Encircle setzt Wörter, Zeilen und Absätze selbst aus der Lage der erkannten Zeilen zusammen. Die Texterkennung zerteilt Absätze und Links oft. Encircle fügt sie anhand von Zeilenabstand, Schriftgröße, linker Kante und Inhalt wieder zusammen.
- **Sauberer Text beim Kopieren:** Zeilen innerhalb eines Absatzes werden zusammengeführt, Silbentrennung am Zeilenende entfernt, Listen und kurze Zeilen behalten ihre Umbrüche.
- **Alle gängigen Codes** werden erkannt und hervorgehoben: QR, Micro-QR, rMQR, Data Matrix, Aztec, PDF417, MaxiCode, EAN, UPC, Code 128/39/93, ITF, Codabar, GS1 DataBar.
- **Passende Aktionen je Inhalt:** Link öffnen, WLAN verbinden, Kontakt speichern, Termin eintragen, anrufen, E-Mail oder SMS schreiben, Ort in Karten öffnen, kopieren, teilen.
- **Leiste unten** im Stil der Suchleiste. Nach unten wischen oder Zurück schließt alles.
- Material You mit dynamischen Farben, hell und dunkel. Die Leuchteffekte nutzen kräftigere Farben, die aus der Akzentfarbe des Hintergrundbilds abgeleitet sind: ein farbiger Rand während der Erkennung, eine Farbwelle über den Text, und wenn die Welle über QR- und Barcodes läuft, leuchten deren dunkle Module in diesen Farben auf.

## Einrichtung auf dem Gerät

1. App installieren und öffnen.
2. **Assistent auswählen** antippen und Encircle als App für den digitalen Assistenten wählen.
3. In denselben Einstellungen **Bildschirm- und App-Daten verwenden** einschalten, je nach System auch **Screenshot verwenden**.
4. Den Einstieg passend zum Gerät einrichten:
   - Drei-Tasten-Navigation: Home gedrückt halten startet Encircle.
   - Gestennavigation auf GrapheneOS und anderen AOSP-Systemen: unter Einstellungen › System › Navigationsmodus › Zahnrad das Wischen zum Aufrufen des Assistenten einschalten. Ein Wisch schräg nach oben aus einer unteren Ecke startet dann Encircle. Langes Drücken auf die Leiste ist dort fest mit Circle to Search verbunden.
   - Pixel mit Google-Launcher: Circle to Search unter Einstellungen › System › Navigationsmodus ausschalten.
   - Samsung: Circle to Search unter Einstellungen › Anzeige › Navigationsleiste ausschalten.

Android erlaubt Drittanbieter-Apps diesen Einstieg nur über die Assistenten-Rolle. Encircle ersetzt deshalb den bisherigen Assistenten, solange es ausgewählt ist.

### GrapheneOS: Assistent nach Neustart verschwunden

AOSP verwirft beim Start eines Nutzers oder Profils jeden Assistenten, der keine System-App ist, wenn keine Spracherkennung eingetragen ist. Auf GrapheneOS ist das der Normalfall. Encircle bleibt dann zwar ausgewählt, Android verbindet es aber nicht mehr. Die Startseite der App zeigt das als **Neu verbinden** an. Kurz **Keine** und dann wieder Encircle wählen behebt es bis zum nächsten Neustart.

Dauerhaft lässt es sich einmalig per adb beheben. Dafür wird Encircles Platzhalter-Spracherkennung eingetragen. `<user>` ist die Nutzer-ID des Profils, `adb shell pm list users` zeigt sie an:

```sh
adb shell settings --user <user> put secure voice_recognition_service io.github.maxi2555.encircle/.assist.StubRecognitionService
```

### Android 17

Ab Android 17 liefert das System einem Assistenten mit Ziel-SDK 37 nur noch, was er in seiner Dienstbeschreibung erklärt. Encircle erklärt in `res/xml/assist_service.xml` `usesAssistData` und `usesAssistScreenshots`. Android macht den Screenshot nur zusammen mit den Assistenzdaten. Ohne `usesAssistStructureScreenContent` enthalten diese Daten aber keinen Bildschirmtext der Apps. Encircle liest den Text ausschließlich per Texterkennung aus dem Screenshot.

## Bauen

Voraussetzungen: JDK 17 oder neuer und das Android SDK.

```sh
./gradlew assembleRelease
```

Für aktuelle Handys die APK `app/build/outputs/apk/release/app-arm64-v8a-release.apk` nehmen. Daneben entstehen APKs für ältere 32-Bit-Geräte, für x86_64-Emulatoren und eine universelle APK. Der Release-Build ist mit dem lokalen Debug-Schlüssel signiert, damit er sich direkt installieren lässt. Für eine Veröffentlichung einen eigenen Signaturschlüssel eintragen.

Für flüssige Animationen den Release-Build verwenden. Compose läuft im Debug-Build deutlich langsamer.

```sh
./gradlew testDebugUnitTest   # Auswahl-Logik, Absätze, Link-Erkennung, Code-Parser
```

## Aufbau

| Bereich | Datei |
| --- | --- |
| Assistenten-Dienst und Sitzung | `assist/AssistService.kt`, `assist/OverlaySession.kt` |
| Texterkennung und Barcode-Scan | `analysis/ScreenAnalyzer.kt` |
| Wörter, Zeilen, Absätze, Textzusammenbau | `analysis/LayoutBuilder.kt`, `analysis/TextLayout.kt` |
| Links, Adressen, Nummern, IBANs im Text | `analysis/Entities.kt` |
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
