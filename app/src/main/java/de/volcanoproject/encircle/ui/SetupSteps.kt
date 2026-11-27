// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.volcanoproject.encircle.R
import de.volcanoproject.encircle.setup.AssistantSetup
import de.volcanoproject.encircle.setup.SetupStatus

private enum class StepState { Done, Current, Upcoming }

/**
 * Guided setup: choose Encircle as the assistant, allow screen content, set up the system shortcut.
 * Each step ticks itself off as soon as the system reports it done; the current one is expanded.
 */
@Composable
fun SetupSteps(
    status: SetupStatus,
    onOpenAssistant: () -> Unit,
    onOpenSettings: (actions: List<String>) -> Unit,
    onTry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val done = status.steps
    val current = done.indexOfFirst { !it }
    fun state(i: Int) = when {
        done[i] -> StepState.Done
        i == current -> StepState.Current
        else -> StepState.Upcoming
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (status.ready) scheme.primaryContainer else scheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (status.ready) R.drawable.ic_check else R.drawable.ic_settings),
                    null,
                    Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (status.ready) "Bereit" else "Einrichtung", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (status.stepsLeft) {
                            0 -> "Encircle startet über deinen Kurzbefehl."
                            1 -> "Noch ein Schritt"
                            else -> "Noch ${status.stepsLeft} Schritte"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Step(1, "Encircle als Assistent wählen", state(0)) {
                Text(
                    if (status.disconnected) {
                        "Encircle ist gewählt, aber Android hat es nach einem Neustart abgemeldet. " +
                            "Wähle in den Einstellungen kurz „Keine“ und dann wieder Encircle."
                    } else {
                        "Android startet nur den digitalen Assistenten über einen Kurzbefehl. " +
                            "Wähle in den Einstellungen Encircle aus."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAssistant) {
                    Text(if (status.disconnected) "Neu verbinden" else "Assistent auswählen")
                }
            }

            Step(2, "Bildschirminhalt erlauben", state(1)) {
                Text(
                    "Schalte auf derselben Seite „Bildschirm- und App-Daten verwenden“ und, falls vorhanden, " +
                        "„Screenshot verwenden“ ein. Ohne diese Freigabe sieht Encircle den Bildschirm nicht.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAssistant) { Text("Freigabe öffnen") }
            }

            Step(3, if (done[2]) status.plan.title else "Kurzbefehl einrichten", state(2)) {
                Text(status.plan.title, style = MaterialTheme.typography.titleSmall)
                Text(status.plan.howTo, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenSettings(status.plan.settingsActions) }) {
                        Text(status.plan.buttonLabel)
                    }
                    if (status.plan.offerButtonNavigation) {
                        TextButton(onClick = { onOpenSettings(listOf(AssistantSetup.ACTION_NAVIGATION_MODE)) }) {
                            Text("Lieber Tasten-Navigation")
                        }
                    }
                }
                Text(
                    "Probier den Kurzbefehl jetzt aus. Sobald Encircle darüber startet, ist dieser Schritt erledigt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (status.assistant) {
                FilledTonalButton(onClick = onTry) {
                    Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("In der App ausprobieren")
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, state: StepState, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.alpha(if (state == StepState.Upcoming) 0.5f else 1f)) {
        Surface(
            shape = CircleShape,
            color = if (state == StepState.Done) scheme.primary else scheme.secondaryContainer,
            contentColor = if (state == StepState.Done) scheme.onPrimary else scheme.onSecondaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == StepState.Done) {
                    Icon(painterResource(R.drawable.ic_check), "Erledigt", Modifier.size(18.dp))
                } else {
                    Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (state == StepState.Current) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 3.dp),
            )
            AnimatedVisibility(
                visible = state == StepState.Current,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
        }
    }
}
