// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.volcanoproject.encircle.R
import de.volcanoproject.encircle.actions.SystemActions
import de.volcanoproject.encircle.setup.AssistantSetup
import de.volcanoproject.encircle.setup.SetupStatus
import kotlinx.coroutines.launch

/** The pages of the first-run tutorial, in order. */
private enum class Page { Welcome, Assistant, ScreenContent, Shortcut, Practice, Done }

/** Whether the user may move past [page]: the setup pages wait until the system reports them done. */
private fun Page.passed(status: SetupStatus) = when (this) {
    Page.Assistant -> status.assistant
    Page.ScreenContent -> status.screenContent
    Page.Practice -> status.shortcutUsed
    else -> true
}

/**
 * Full-screen tutorial on first start: the logo draws itself, then the setup is done step by step and
 * practised on a sample page. Swiping only reaches pages up to the first unfinished step, so nobody slides
 * past a step by accident; "Später einrichten" leaves at any time and the start page keeps the remaining
 * steps.
 */
@Composable
fun Onboarding(status: SetupStatus, onOpenAssistant: () -> Unit, onFinish: () -> Unit) {
    val pages = Page.entries
    // Pages up to and including the first one that is not passed yet.
    val reachable = (pages.indexOfFirst { !it.passed(status) }.takeIf { it >= 0 } ?: pages.lastIndex) + 1
    val pager = rememberPagerState { reachable }
    val scope = rememberCoroutineScope()
    val page = pages[pager.currentPage.coerceAtMost(pages.lastIndex)]

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
    ) {
        // Top bar: leave for later, except on the last page where finishing is the main action.
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            if (page != Page.Done) {
                TextButton(onClick = onFinish) { Text("Später einrichten") }
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
        ) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                when (pages[index]) {
                    Page.Welcome -> WelcomePage()
                    Page.Assistant -> AssistantPage(status, onOpenAssistant)
                    Page.ScreenContent -> ScreenContentPage(status, onOpenAssistant)
                    Page.Shortcut -> ShortcutPage(status)
                    Page.Practice -> PracticePage(status)
                    Page.Done -> DonePage(status)
                }
            }
        }

        // Bottom bar: progress dots and navigation.
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PageDots(count = pages.size, current = pager.currentPage)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Zurück") }
                }
                Spacer(Modifier.weight(1f))
                val last = page == Page.Done
                Button(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    enabled = last || page.passed(status),
                ) {
                    AnimatedContent(targetState = if (last) "Los geht's" else "Weiter", label = "next") { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// -------------------------------------------------------------------- pages

@Composable
private fun WelcomePage() {
    var revealed by rememberSaveable { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LogoReveal(Modifier.size(156.dp), onRevealed = { revealed = true })
        Spacer(Modifier.height(36.dp))
        // The name and tagline fade and rise in once the star has flashed. Their space is reserved from the
        // start, so the logo does not move.
        val shown by animateFloatAsState(if (revealed) 1f else 0f, tween(700), label = "name")
        // The name opens up from tight to wide letter spacing as it appears.
        val spacing by animateFloatAsState(if (revealed) 0.32f else 0.05f, tween(1100), label = "tracking")
        Column(
            Modifier.graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 40.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ENCIRCLE",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = spacing.em,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Text, Links und Codes auf deinem Bildschirm markieren, kopieren und teilen. Komplett offline.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AssistantPage(status: SetupStatus, onOpenAssistant: () -> Unit) {
    StepPage(
        icon = R.drawable.ic_settings,
        step = "Schritt 1 von 3",
        title = "Encircle als Assistent wählen",
        text = if (status.disconnected) {
            "Encircle ist gewählt, aber Android hat es nach einem Neustart abgemeldet. " +
                "Wähle in den Einstellungen kurz „Keine“ und dann wieder Encircle."
        } else {
            "Android startet nur den digitalen Assistenten über einen Kurzbefehl wie langes Drücken auf Home. " +
                "Wähle dafür Encircle als Assistenten aus und bestätige den Hinweis."
        },
        done = status.assistant,
    ) {
        Button(onClick = onOpenAssistant) { Text(if (status.disconnected) "Neu verbinden" else "Assistent auswählen") }
    }
}

@Composable
private fun ScreenContentPage(status: SetupStatus, onOpenAssistant: () -> Unit) {
    StepPage(
        icon = R.drawable.ic_image,
        step = "Schritt 2 von 3",
        title = "Bildschirminhalt erlauben",
        text = "Schalte auf derselben Seite „Bildschirm- und App-Daten verwenden“ und, falls vorhanden, " +
            "„Screenshot verwenden“ ein. Encircle liest den Bildschirm nur, wenn du es aufrufst, und alles " +
            "bleibt auf dem Gerät.",
        done = status.screenContent,
    ) {
        Button(onClick = onOpenAssistant) { Text("Freigabe öffnen") }
    }
}

@Composable
private fun ShortcutPage(status: SetupStatus) {
    val context = LocalContext.current
    StepPage(
        icon = R.drawable.ic_play,
        step = "Schritt 3 von 3",
        title = status.plan.title,
        text = status.plan.howTo + " Im nächsten Schritt probierst du ihn aus.",
        done = null,
    ) {
        Button(onClick = { SystemActions.openSettings(context, status.plan.settingsActions) }) { Text(status.plan.buttonLabel) }
        if (status.plan.offerButtonNavigation) {
            TextButton(onClick = { SystemActions.openSettings(context, listOf(AssistantSetup.ACTION_NAVIGATION_MODE)) }) {
                Text("Lieber Tasten-Navigation")
            }
        }
    }
}

/** Sample content to try Encircle on: a sentence with a link and an address, and a QR code. */
@Composable
private fun PracticePage(status: SetupStatus) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Probier es aus", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Starte Encircle jetzt über deinen Kurzbefehl: ${status.plan.title}. Markiere dann den Satz unten " +
                "oder tippe auf den QR-Code.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Mehr über Encircle steht auf encircle.example.org. Fragen gern an hallo@example.org.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                // White behind the code, so it scans in dark mode too.
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                ) {
                    QrCode(Modifier.fillMaxSize())
                }
            }
        }
        AnimatedContent(
            targetState = status.shortcutUsed,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "practice",
        ) { worked ->
            if (worked) {
                StatusChip(done = true, text = "Geklappt! Encircle startet über deinen Kurzbefehl.")
            } else {
                WaitingChip()
            }
        }
    }
}

@Composable
private fun DonePage(status: SetupStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        LogoRing(Modifier.size(96.dp))
        Text("Alles bereit", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (status.shortcutUsed) {
                "Starte Encircle jederzeit so: ${status.plan.title}. Auf der Startseite findest du alle Gesten zum Markieren."
            } else {
                "Auf der Startseite findest du die Einrichtung und alle Gesten zum Markieren."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// -------------------------------------------------------------------- building blocks

/** One setup step: an icon, what to do, the button that gets there and whether it is done. */
@Composable
private fun StepPage(
    @DrawableRes icon: Int,
    step: String,
    title: String,
    text: String,
    done: Boolean?,
    actions: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(32.dp)) }
        }
        Text(step, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
        if (done != null) {
            AnimatedContent(targetState = done, label = "status") { isDone ->
                StatusChip(done = isDone, text = if (isDone) "Erledigt" else "Noch offen. Weiter geht es, sobald es erledigt ist.")
            }
        }
    }
}

@Composable
private fun StatusChip(done: Boolean, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (done) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (done) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A gently pulsing dot while waiting for the first start through the shortcut. */
@Composable
private fun WaitingChip() {
    val pulse by rememberInfiniteTransition(label = "wait").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHigh, contentColor = scheme.onSurfaceVariant) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(10.dp))
            Text("Warte auf den ersten Start über den Kurzbefehl …", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * QR code for https://github.com/Maxi2555/encircle (generated with ZXing), one string per module row,
 * '#' dark. Drawn directly so it stays crisp at any size without a very long vector path.
 */
private val PRACTICE_QR = listOf(
    "#######.#.#.##.#.####.#######",
    "#.....#..#####..##..#.#.....#",
    "#.###.#.###.......##..#.###.#",
    "#.###.#.###.#.##.####.#.###.#",
    "#.###.#.#..##.######..#.###.#",
    "#.....#...##.#..##.##.#.....#",
    "#######.#.#.#.#.#.#.#.#######",
    "..........#.#...##..#........",
    "####..#.#.####.###.#.#..###.#",
    "##.#......#.###.#..##.###...#",
    "##.##.#.######..###..#.#..##.",
    "#..##....##....##.#####.#...#",
    "##..#.##.##.#.##.###.....##..",
    "#.#......#.##.#####...#...###",
    ".###.###.###.#..#.###.#...###",
    ".#.###.#####..#....######..#.",
    "#.###.#..#..#.#.#..##...##.#.",
    "..##.#.#..#...##..#....#.###.",
    "#.###.##.##..####...#.###.#..",
    "...#.#.....#.##..##...###.#..",
    ".#..###.#....#####..#######..",
    "........###.....###.#...#####",
    "#######..#....#.#.###.#.##.#.",
    "#.....#...#######.#.#...##..#",
    "#.###.#..#.#...#.#..#####.#..",
    "#.###.#.##.#.#.###.#.#.###..#",
    "#.###.#.#...#.#.#.#.#..#..#.#",
    "#.....#.##..##..#...##.#.#.#.",
    "#######.####...#..#####....#.",
)

@Composable
private fun QrCode(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "QR-Code zum Encircle-Projekt" }) {
        val n = PRACTICE_QR.size
        val cell = size.minDimension / n
        PRACTICE_QR.forEachIndexed { y, row ->
            var x = 0
            while (x < n) {
                if (row[x] != '#') { x++; continue }
                val start = x
                while (x < n && row[x] == '#') x++
                // One rectangle per run, slightly overlapping so no hairlines show between modules.
                drawRect(QR_INK, Offset(start * cell, y * cell), Size((x - start) * cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private val QR_INK = Color(0xFF1B1B1F)
