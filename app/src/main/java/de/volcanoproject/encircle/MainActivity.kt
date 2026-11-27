// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.volcanoproject.encircle.actions.SystemActions
import de.volcanoproject.encircle.assist.AssistService
import de.volcanoproject.encircle.setup.AssistantSetup
import de.volcanoproject.encircle.setup.SetupStatus
import de.volcanoproject.encircle.ui.EncircleTheme
import de.volcanoproject.encircle.ui.LogoRing
import de.volcanoproject.encircle.ui.Onboarding
import de.volcanoproject.encircle.ui.SetupSteps

class MainActivity : ComponentActivity() {
    private var setup by mutableStateOf<SetupStatus?>(null)
    private var onboardingDone by mutableStateOf(true)
    private var diagnostics by mutableStateOf(emptyList<Pair<String, String>>())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onboardingDone = AssistantSetup.onboardingDone(this)
        setup = AssistantSetup.status(this)
        setContent {
            EncircleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    // First start: the full-screen tutorial, then the start page.
                    Crossfade(targetState = onboardingDone, animationSpec = tween(450), label = "onboarding") { done ->
                        if (!done) {
                            setup?.let { status ->
                                Onboarding(
                                    status = status,
                                    onOpenAssistant = { SystemActions.openAssistSettings(this) },
                                    onFinish = {
                                        AssistantSetup.markOnboardingDone(this)
                                        onboardingDone = true
                                    },
                                )
                            }
                        } else {
                            SetupScreen(
                                setup = setup,
                                diagnostics = diagnostics,
                                onOpenAssistant = { SystemActions.openAssistSettings(this) },
                                onOpenSettings = { SystemActions.openSettings(this, it) },
                                onTry = { AssistService.launchOverlay() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }

    private fun refresh() {
        setup = AssistantSetup.status(this)
        isAssistant = AssistService.isActive(this)
        isDisconnected = AssistService.isRoleHeldButDisconnected(this)
        diagnostics = Diagnostics.read(this)
    }

    /**
     * Opens the overlay. If that fails, the status shown is out of date: refreshing it lets the card say
     * what to do, unless Encircle is still the assistant and Android just has not connected it yet.
     */
    private fun tryOverlay() {
        if (AssistService.launchOverlay()) return
        refreshStatus()
        val message = if (isAssistant) {
            "Encircle ist noch nicht bereit. Versuche es gleich noch einmal."
        } else {
            "Encircle ist nicht mehr als Assistent aktiv"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SetupScreen(
    setup: SetupStatus?,
    diagnostics: List<Pair<String, String>>,
    onOpenAssistant: () -> Unit,
    onOpenSettings: (actions: List<String>) -> Unit,
    onTry: () -> Unit,
) {
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
            "Encircle über einen Kurzbefehl starten, dann Text oder Codes auf dem Bildschirm markieren, kopieren und teilen.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        setup?.let { SetupSteps(it, onOpenAssistant, onOpenSettings, onTry) }

        InfoCard(
            "Bedienung",
            listOf(
                "Auf ein Wort tippen markiert das Wort. Erneut tippen erweitert auf Satz, dann Absatz.",
                "Lange drücken markiert den ganzen Absatz.",
                "Über Text wischen markiert wie ein Textmarker, quer über einen Absatz den ganzen Absatz.",
                "Einkreisen markiert alles im Kreis. Ohne Text wird der Bereich als Bild ausgewählt.",
                "Die Griffe an der Auswahl verschieben Anfang und Ende.",
                "Erkannte QR- und Barcodes leuchten auf. Antippen zeigt Inhalt und Aktionen.",
                "Die Leiste unten lässt sich nach oben schieben, um verdeckte Inhalte zu sehen. Ganz nach oben hinausschieben oder Zurück schließt Encircle.",
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

        if (diagnostics.isNotEmpty()) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            ) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Letzter Aufruf", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            diagnostics.forEach { (k, v) ->
                                Row {
                                    Text(k, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                                    Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Encircle ist freie Software unter der Apache-Lizenz 2.0, Quellcode: github.com/Maxi2555/encircle. " +
                "Barcodes: zxing-cpp (Apache-2.0). Texterkennung: Google ML Kit (proprietär, offline auf dem Gerät). " +
                "Icons: Material Design Icons (Apache-2.0).",
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
