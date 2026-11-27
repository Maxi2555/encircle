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
import androidx.compose.ui.res.stringResource
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
    private var diagnostics by mutableStateOf(emptyList<Pair<Diagnostics.Key, String>>())

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
                                onTry = ::tryOverlay,
                                onCopy = { SystemActions.copyText(this, it) },
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
        diagnostics = Diagnostics.read(this)
    }

    /**
     * Opens the overlay. If that fails, the status shown is out of date: refreshing it lets the card say
     * what to do, unless Encircle is still the assistant and Android just has not connected it yet.
     */
    private fun tryOverlay() {
        if (AssistService.launchOverlay()) return
        refresh()
        val message = getString(if (setup?.assistant == true) R.string.try_failed_not_ready else R.string.try_failed_no_assistant)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SetupScreen(
    setup: SetupStatus?,
    diagnostics: List<Pair<Diagnostics.Key, String>>,
    onOpenAssistant: () -> Unit,
    onOpenSettings: (actions: List<String>) -> Unit,
    onTry: () -> Unit,
    onCopy: (String) -> Unit,
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
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        setup?.let { SetupSteps(it, onOpenAssistant, onOpenSettings, onTry, onCopy) }

        InfoCard(
            stringResource(R.string.home_usage_title),
            listOf(
                R.string.home_usage_1,
                R.string.home_usage_2,
                R.string.home_usage_3,
                R.string.home_usage_4,
                R.string.home_usage_5,
                R.string.home_usage_6,
                R.string.home_usage_7,
            ).map { stringResource(it) },
        )

        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_try_title), style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        stringResource(R.string.home_try_text),
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
                    Text(stringResource(R.string.home_diagnostics_title), style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            diagnostics.forEach { (k, v) ->
                                Row {
                                    Text(stringResource(k.label), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                                    Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            stringResource(R.string.home_licenses),
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
