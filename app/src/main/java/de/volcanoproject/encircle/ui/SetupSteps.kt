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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    onCopy: (String) -> Unit,
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
                    Text(stringResource(if (status.ready) R.string.setup_ready else R.string.setup_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (status.ready) {
                            stringResource(R.string.setup_ready_summary)
                        } else {
                            pluralStringResource(R.plurals.setup_steps_left, status.stepsLeft, status.stepsLeft)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Step(1, stringResource(R.string.setup_assistant_title), state(0)) {
                Text(
                    stringResource(if (status.disconnected) R.string.setup_assistant_disconnected else R.string.setup_assistant_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAssistant) {
                    Text(stringResource(if (status.disconnected) R.string.setup_reconnect else R.string.setup_choose_assistant))
                }
                if (status.disconnected && status.recognizerCommand.isNotEmpty()) {
                    PermanentFix(status.recognizerCommand, onCopy)
                }
            }

            Step(2, stringResource(R.string.setup_screen_title), state(1)) {
                Text(
                    stringResource(R.string.setup_screen_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAssistant) { Text(stringResource(R.string.setup_open_access)) }
            }

            Step(3, stringResource(if (done[2]) status.plan.title else R.string.setup_shortcut_title), state(2)) {
                Text(stringResource(status.plan.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(status.plan.howTo), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenSettings(status.plan.settingsActions) }) {
                        Text(stringResource(status.plan.buttonLabel))
                    }
                    if (status.plan.offerButtonNavigation) {
                        TextButton(onClick = { onOpenSettings(listOf(AssistantSetup.ACTION_NAVIGATION_MODE)) }) {
                            Text(stringResource(R.string.setup_prefer_buttons))
                        }
                    }
                }
                Text(
                    stringResource(R.string.setup_try_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (status.assistant) {
                FilledTonalButton(onClick = onTry) {
                    Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_try_in_app))
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
                    Icon(painterResource(R.drawable.ic_check), stringResource(R.string.setup_done), Modifier.size(18.dp))
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

/** Explains the one-time adb fix for the sign-out at every start and offers the command to copy. */
@Composable
private fun PermanentFix(command: String, onCopy: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(stringResource(R.string.setup_fix_permanently))
    }
    AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_fix_text), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                SelectionContainer {
                    Text(
                        command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            FilledTonalButton(onClick = { onCopy(command) }) { Text(stringResource(R.string.setup_copy_command)) }
        }
    }
}
