package io.github.maxi2555.encircle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.maxi2555.encircle.actions.SystemActions
import io.github.maxi2555.encircle.assist.AssistService
import io.github.maxi2555.encircle.ui.EncircleTheme
import io.github.maxi2555.encircle.ui.LogoRing

class MainActivity : ComponentActivity() {
    private var isAssistant by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EncircleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    SetupScreen(
                        isAssistant = isAssistant,
                        onOpenSettings = { SystemActions.openAssistSettings(this) },
                        onTry = { AssistService.launchOverlay() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAssistant = AssistService.isActive(this)
    }
}

@Composable
private fun SetupScreen(isAssistant: Boolean, onOpenSettings: () -> Unit, onTry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        LogoRing(Modifier.size(56.dp))
        Text("Encircle", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(
            "Home-Taste oder Navigationsleiste gedrückt halten, dann Text oder Codes auf dem Bildschirm markieren, kopieren und teilen.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAssistant) scheme.primaryContainer else scheme.tertiaryContainer,
            ),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (isAssistant) R.drawable.ic_check else R.drawable.ic_settings),
                        null,
                        Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isAssistant) "Bereit" else "Noch nicht eingerichtet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    if (isAssistant) {
                        "Encircle ist dein digitaler Assistent. Halte Home oder die Navigationsleiste gedrückt."
                    } else {
                        "Wähle Encircle als App für den digitalen Assistenten. Nur so startet Android die App per langem Druck auf Home."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAssistant) {
                        Button(onClick = onTry) {
                            Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ausprobieren")
                        }
                        FilledTonalButton(onClick = onOpenSettings) { Text("Einstellungen") }
                    } else {
                        Button(onClick = onOpenSettings) { Text("Assistent auswählen") }
                    }
                }
            }
        }

        InfoCard(
            "Einrichtung",
            listOf(
                "In den Einstellungen unter Standard-Apps › Digitaler Assistent Encircle wählen.",
                "Dort „Text auf dem Bildschirm verwenden“ und „Screenshot verwenden“ einschalten.",
                "Pixel: Circle to Search unter System › Navigationsmodus ausschalten, damit der lange Druck den Assistenten startet.",
                "Samsung: Circle to Search unter Anzeige › Navigationsleiste ausschalten.",
            ),
        )

        InfoCard(
            "Bedienung",
            listOf(
                "Auf ein Wort tippen markiert das Wort. Erneut tippen erweitert auf Satz, dann Absatz.",
                "Lange drücken markiert den ganzen Absatz.",
                "Über Text wischen markiert wie ein Textmarker, quer über einen Absatz den ganzen Absatz.",
                "Einkreisen markiert alles im Kreis. Ohne Text wird der Bereich als Bild ausgewählt.",
                "Die Griffe an der Auswahl verschieben Anfang und Ende.",
                "Erkannte QR- und Barcodes leuchten auf. Antippen zeigt Inhalt und Aktionen.",
                "Die Leiste unten nach unten wischen oder Zurück schließt alles.",
            ),
        )

        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zum Testen", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        "Encircle läuft komplett auf dem Gerät. Es sucht nichts im Netz und schickt keine Daten weg. " +
                            "Probier es an diesem Absatz aus: Tippe auf ein Wort, wische quer darüber oder kreise ihn ein. " +
                            "Links wie https://example.org und Adressen wie hallo@example.org werden erkannt.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Text(
            "Barcodes: zxing-cpp (Apache-2.0). Texterkennung: Google ML Kit, offline auf dem Gerät. Icons: Material Design Icons (Apache-2.0).",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InfoCard(title: String, items: List<String>) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { i, item ->
                Row {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(24.dp)) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 3.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
