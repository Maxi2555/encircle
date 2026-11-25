package io.github.maxi2555.encircle.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.Patterns
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.maxi2555.encircle.R
import io.github.maxi2555.encircle.actions.SystemActions
import io.github.maxi2555.encircle.analysis.CodeContent
import io.github.maxi2555.encircle.analysis.DetectedCode
import io.github.maxi2555.encircle.analysis.ScreenAnalyzer
import io.github.maxi2555.encircle.analysis.TextLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock
import io.github.maxi2555.encircle.Diagnostics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface Selection {
    data object None : Selection

    class Words(val ids: IntArray) : Selection {
        val first get() = ids.first()
        val last get() = ids.last()
        val isRange get() = last - first + 1 == ids.size
        operator fun contains(i: Int) = ids.binarySearch(i) >= 0
    }

    class Code(val code: DetectedCode) : Selection
    class Region(val rect: Rect) : Selection
}

enum class Handle { Start, End }

class OverlayAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val primary: Boolean = false,
    val run: () -> Unit,
)

/**
 * State and interaction logic of one overlay session.
 * Pointer input arrives in view coordinates; everything else lives in screenshot pixels.
 */
@Stable
class OverlayController(private val context: Context, private val finish: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var analysis: Job? = null
    private var timeout: Job? = null
    private var feedbackJob: Job? = null
    private var pixels: Bitmap? = null

    var sessionId by mutableIntStateOf(0); private set
    var image by mutableStateOf<ImageBitmap?>(null); private set
    var screenshotMissing by mutableStateOf(false); private set
    var layout by mutableStateOf(TextLayout.EMPTY); private set
    var textReady by mutableStateOf(false); private set
    var codes by mutableStateOf<List<DetectedCode>>(emptyList()); private set
    var codesReady by mutableStateOf(false); private set
    var selection by mutableStateOf<Selection>(Selection.None); private set
    var feedback by mutableStateOf<String?>(null); private set
    var exiting by mutableStateOf(false); private set

    /** Finger trail of the current or last stroke, in screenshot pixels. */
    val trail = mutableStateListOf<Offset>()
    var strokeActive by mutableStateOf(false); private set

    // Mapping between view and screenshot coordinates, kept current by the UI.
    var origin by mutableStateOf(Offset.Zero)
    var scale by mutableFloatStateOf(1f)
    var density = 1f
    var haptics: (Int) -> Unit = {}

    val analyzing get() = image != null && !(textReady && codesReady)

    private fun dp(v: Float) = v * density / scale
    fun toImage(p: Offset) = (p - origin) / scale
    fun toView(p: Offset) = p * scale + origin

    // ---------------------------------------------------------------- session lifecycle

    fun begin(showFlags: Int, screenshotExpected: Boolean) {
        sessionId++
        exiting = false
        Diagnostics.start(context, "Flags 0x" + Integer.toHexString(showFlags) + if (screenshotExpected) " · Screenshot angefordert" else " · ohne Screenshot")
        if (!screenshotExpected) {
            screenshotMissing = image == null
            Diagnostics.put(context, "Screenshot", "vom System nicht angefordert")
            return
        }
        timeout?.cancel()
        timeout = scope.launch {
            delay(2000)
            if (image == null) {
                screenshotMissing = true
                Diagnostics.put(context, "Screenshot", "nach 2 s nicht angekommen")
            }
        }
    }

    fun onScreenshot(bitmap: Bitmap?) {
        timeout?.cancel()
        if (bitmap == null) {
            screenshotMissing = true
            Diagnostics.put(context, "Screenshot", "leer (null) vom System")
            return
        }
        Diagnostics.put(context, "Screenshot", "${bitmap.width}×${bitmap.height} ${bitmap.config}")
        screenshotMissing = false
        image = bitmap.asImageBitmap()
        haptics(HapticFeedbackConstants.VIRTUAL_KEY)
        analysis?.cancel()
        analysis = scope.launch {
            val soft = withContext(Dispatchers.Default) { ScreenAnalyzer.softwareCopy(bitmap) }
            pixels = soft
            launch {
                val t0 = SystemClock.elapsedRealtime()
                codes = try {
                    ScreenAnalyzer.scanCodes(soft).also {
                        Diagnostics.put(context, "Codes", "${it.size} in ${SystemClock.elapsedRealtime() - t0} ms")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "scan failed", e)
                    Diagnostics.put(context, "Codes", "Fehler: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                    emptyList()
                }
                codesReady = true
            }
            launch {
                val t0 = SystemClock.elapsedRealtime()
                layout = try {
                    val result = withTimeoutOrNull(OCR_TIMEOUT_MS) { ScreenAnalyzer.recognizeText(soft) }
                    val ms = SystemClock.elapsedRealtime() - t0
                    if (result == null) {
                        Diagnostics.put(context, "Texterkennung", "Zeitüberschreitung nach $ms ms")
                        TextLayout.EMPTY
                    } else {
                        Diagnostics.put(context, "Texterkennung", "${result.words.size} Wörter in $ms ms")
                        result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ocr failed", e)
                    Diagnostics.put(context, "Texterkennung", "Fehler: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                    TextLayout.EMPTY
                }
                textReady = true
            }
        }
    }

    fun reset() {
        analysis?.cancel(); timeout?.cancel(); feedbackJob?.cancel()
        image = null; pixels = null
        screenshotMissing = false
        layout = TextLayout.EMPTY; textReady = false
        codes = emptyList(); codesReady = false
        selection = Selection.None
        feedback = null
        trail.clear(); strokeActive = false
        exiting = false
    }

    fun dispose() {
        reset()
        scope.cancel()
    }

    /** Animated close; the UI calls [finishNow] when the exit animation is done. */
    fun dismiss() {
        exiting = true
    }

    fun finishNow() = finish()

    // ---------------------------------------------------------------- gestures

    fun onTap(view: Offset) {
        val p = toImage(view)
        codeAt(p)?.let { selectCode(it); return }
        val w = layout.wordAt(p, dp(10f))
        if (w == null) {
            if (selection != Selection.None) {
                selection = Selection.None
                haptics(HapticFeedbackConstants.CLOCK_TICK)
            }
            return
        }
        val cur = selection
        val word = w..w
        // Tapping inside the current selection grows it: word, sentence, paragraph, then back to the word.
        val next = if (cur is Selection.Words && w in cur && cur.isRange) {
            val now = cur.first..cur.last
            val sentence = layout.sentenceRange(w)
            val block = layout.blockRange(w)
            when {
                now == word && sentence != word -> sentence
                (now == word || now == sentence) && block != now -> block
                else -> word
            }
        } else {
            word
        }
        selectRange(next)
    }

    fun onLongPress(view: Offset) {
        val p = toImage(view)
        codeAt(p)?.let { selectCode(it); return }
        val w = layout.wordAt(p, dp(14f)) ?: return
        selectRange(layout.blockRange(w), HapticFeedbackConstants.LONG_PRESS)
    }

    private var strokeAnchor: Int? = null

    fun onStrokeStart(view: Offset) {
        val p = toImage(view)
        trail.clear()
        trail.add(p)
        strokeActive = true
        strokeAnchor = layout.wordAt(p, dp(6f))
        if (strokeAnchor != null) haptics(gestureStart())
    }

    fun onStrokeMove(view: Offset) {
        val p = toImage(view)
        trail.add(p)
        // Starting on a word means "highlight": follow the finger live, like a text marker.
        val anchor = strokeAnchor ?: return
        val end = layout.nearestWord(p, dp(40f)) ?: return
        val range = min(anchor, end)..max(anchor, end)
        val cur = selection
        if (cur !is Selection.Words || cur.first != range.first || cur.last != range.last || !cur.isRange) {
            selectRange(range, HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        }
    }

    fun onStrokeEnd() {
        strokeActive = false
        val pts = trail.toList()
        if (pts.size < 2) return
        val shape = StrokeShape(pts, density / scale)
        if (shape.isLasso) {
            lassoSelect(pts, shape.bounds)
            return
        }
        val s = pts.first()
        val e = pts.last()
        // A diagonal swipe across most of one paragraph selects the whole paragraph.
        layout.blockContaining(s, e, dp(28f))?.let { b ->
            if (b.lastLine > b.firstLine && abs(e.x - s.x) >= 0.6f * b.box.width && abs(e.y - s.y) >= 0.5f * b.box.height) {
                selectRange(b.first..b.last, gestureEnd())
                return
            }
        }
        var lo = Int.MAX_VALUE
        var hi = Int.MIN_VALUE
        fun take(i: Int?) { if (i != null) { lo = min(lo, i); hi = max(hi, i) } }
        take(strokeAnchor)
        if (strokeAnchor != null) take(layout.nearestWord(e, dp(40f)))
        wordsAlong(pts, dp(8f)).forEach { take(it) }
        if (lo <= hi) {
            selectRange(lo..hi, gestureEnd())
        } else if (strokeAnchor == null) {
            // Swiping over a code highlights it as well.
            codes.firstOrNull { c -> pts.any { c.bounds.inflate(dp(8f)).contains(it) } }?.let { selectCode(it) }
        }
    }

    private fun lassoSelect(poly: List<Offset>, bounds: Rect) {
        val words = layout.words.filter { pointInPolygon(it.box.center, poly) }.map { it.index }
        val codesInside = codes.filter { pointInPolygon(it.center, poly) }
        when {
            codesInside.size == 1 && words.size < 4 -> selectCode(codesInside.first())
            words.isNotEmpty() -> {
                selection = Selection.Words(words.toIntArray())
                haptics(gestureEnd())
            }
            codesInside.isNotEmpty() -> selectCode(codesInside.first())
            else -> {
                val img = image ?: return
                val r = Rect(
                    max(0f, bounds.left), max(0f, bounds.top),
                    min(img.width.toFloat(), bounds.right), min(img.height.toFloat(), bounds.bottom),
                )
                if (r.width > dp(24f) && r.height > dp(24f)) {
                    selection = Selection.Region(r)
                    haptics(gestureEnd())
                }
            }
        }
    }

    private fun wordsAlong(pts: List<Offset>, tolerance: Float): List<Int> {
        val samples = resample(pts, dp(4f))
        val minX = samples.minOf { it.x } - tolerance
        val maxX = samples.maxOf { it.x } + tolerance
        val minY = samples.minOf { it.y } - tolerance
        val maxY = samples.maxOf { it.y } + tolerance
        return layout.words.filter { w ->
            val b = w.box
            if (b.right < minX || b.left > maxX || b.bottom < minY || b.top > maxY) return@filter false
            val r = b.inflate(tolerance)
            samples.any { r.contains(it) }
        }.map { it.index }
    }

    // ---------------------------------------------------------------- selection handles

    /** Handle circle centres in view coordinates. */
    fun handleCenters(): Pair<Offset, Offset>? {
        val sel = selection as? Selection.Words ?: return null
        val a = layout.words.getOrNull(sel.first) ?: return null
        val b = layout.words.getOrNull(sel.last) ?: return null
        val r = 9f * density
        val start = toView(Offset(a.box.left, a.box.bottom)) + Offset(-r, r)
        val end = toView(Offset(b.box.right, b.box.bottom)) + Offset(r, r)
        return start to end
    }

    fun handleAt(view: Offset): Handle? {
        val (s, e) = handleCenters() ?: return null
        val ds = (view - s).getDistance()
        val de = (view - e).getDistance()
        val reach = 30f * density
        return when {
            ds > reach && de > reach -> null
            ds <= de -> Handle.Start
            else -> Handle.End
        }
    }

    private var fixedEnd = -1

    fun onHandleDragStart(handle: Handle) {
        val sel = selection as? Selection.Words ?: return
        fixedEnd = if (handle == Handle.Start) sel.last else sel.first
        haptics(gestureStart())
    }

    fun onHandleDrag(view: Offset) {
        if (fixedEnd < 0) return
        // The handle sits below the text, so aim a little above the finger.
        val p = toImage(view - Offset(0f, 22f * density))
        val w = layout.nearestWord(p, dp(80f)) ?: return
        val range = min(w, fixedEnd)..max(w, fixedEnd)
        val cur = selection as? Selection.Words
        if (cur == null || cur.first != range.first || cur.last != range.last || !cur.isRange) {
            selectRange(range, HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        }
    }

    fun onHandleDragEnd() {
        fixedEnd = -1
    }

    // ---------------------------------------------------------------- selection helpers

    private fun codeAt(p: Offset): DetectedCode? =
        codes.filter { c -> pointInPolygon(p, expandPolygon(c.corners, dp(12f))) }.minByOrNull { it.bounds.width * it.bounds.height }

    private fun selectRange(r: IntRange, haptic: Int = HapticFeedbackConstants.CLOCK_TICK) {
        if (r.isEmpty()) return
        selection = Selection.Words(IntArray(r.last - r.first + 1) { r.first + it })
        haptics(haptic)
    }

    fun selectCode(c: DetectedCode) {
        selection = Selection.Code(c)
        haptics(confirm())
    }

    fun selectAllText() {
        if (!layout.isEmpty) selectRange(0..layout.words.lastIndex, confirm())
    }

    fun clearSelection() {
        selection = Selection.None
    }

    fun selectedText(): String? = when (val s = selection) {
        is Selection.Words -> layout.textOf(s.ids)
        is Selection.Code -> s.code.raw
        else -> null
    }

    // ---------------------------------------------------------------- actions

    fun actions(): List<OverlayAction> = when (val s = selection) {
        Selection.None -> idleActions()
        is Selection.Words -> textActions(layout.textOf(s.ids), allSelected = s.ids.size == layout.words.size)
        is Selection.Code -> codeActions(s.code)
        is Selection.Region -> regionActions(s.rect)
    }

    private fun idleActions(): List<OverlayAction> = buildList {
        if (codes.size == 1) {
            val c = codes.first()
            add(OverlayAction(c.formatName + " anzeigen", R.drawable.ic_qr, primary = true) { selectCode(c) })
        }
        if (!layout.isEmpty) add(OverlayAction("Gesamten Text", R.drawable.ic_select_all, primary = isEmpty()) { selectAllText() })
        if (image != null) add(OverlayAction("Screenshot teilen", R.drawable.ic_image) { shareImage(null) })
    }

    private fun textActions(text: String, allSelected: Boolean): List<OverlayAction> = buildList {
        val t = text.trim()
        val url = findUrl(t)
        val compact = !t.contains('\n') && t.length <= 64
        when {
            url != null -> add(OverlayAction("Link öffnen", R.drawable.ic_open, primary = true) { launchExternal { SystemActions.openUri(context, url) } })
            compact && Patterns.EMAIL_ADDRESS.matcher(t).matches() ->
                add(OverlayAction("E-Mail schreiben", R.drawable.ic_mail, primary = true) { launchExternal { SystemActions.email(context, t) } })
            compact && t.count { it.isDigit() } >= 6 && Patterns.PHONE.matcher(t).matches() ->
                add(OverlayAction("Anrufen", R.drawable.ic_call, primary = true) { launchExternal { SystemActions.dial(context, t) } })
        }
        add(OverlayAction("Kopieren", R.drawable.ic_copy, primary = isEmpty()) { copy(text) })
        add(OverlayAction("Teilen", R.drawable.ic_share) { launchExternal { SystemActions.shareText(context, text) } })
        if (!allSelected) add(OverlayAction("Alles auswählen", R.drawable.ic_select_all) { selectAllText() })
    }

    private fun codeActions(c: DetectedCode): List<OverlayAction> = buildList {
        when (val k = c.content) {
            is CodeContent.Link -> {
                add(OverlayAction("Öffnen", R.drawable.ic_open, true) { launchExternal { SystemActions.openUri(context, k.url) } })
                add(OverlayAction("Link kopieren", R.drawable.ic_copy) { copy(k.url) })
            }
            is CodeContent.Wifi -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    add(OverlayAction("Verbinden", R.drawable.ic_wifi, true) {
                        launchExternal {
                            SystemActions.connectWifi(context, k) || run {
                                k.password?.let { SystemActions.copyText(context, it, "WLAN-Passwort", sensitive = true) }
                                SystemActions.openWifiSettings(context)
                            }
                        }
                    })
                }
                k.password?.let { pw ->
                    add(OverlayAction("Passwort kopieren", R.drawable.ic_key, isEmpty()) { copy(pw, sensitive = true) })
                }
                add(OverlayAction("Netzwerkname kopieren", R.drawable.ic_copy) { copy(k.ssid) })
            }
            is CodeContent.Contact -> {
                add(OverlayAction("Kontakt speichern", R.drawable.ic_person_add, true) { launchExternal { SystemActions.addContact(context, k.vcard) } })
                add(OverlayAction("Kopieren", R.drawable.ic_copy) { copy(c.raw) })
            }
            is CodeContent.Email -> {
                add(OverlayAction("E-Mail schreiben", R.drawable.ic_mail, true) { launchExternal { SystemActions.email(context, k.to, k.subject, k.body) } })
                add(OverlayAction("Adresse kopieren", R.drawable.ic_copy) { copy(k.to) })
            }
            is CodeContent.Phone -> {
                add(OverlayAction("Anrufen", R.drawable.ic_call, true) { launchExternal { SystemActions.dial(context, k.number) } })
                add(OverlayAction("Nummer kopieren", R.drawable.ic_copy) { copy(k.number) })
            }
            is CodeContent.Sms -> {
                add(OverlayAction("SMS schreiben", R.drawable.ic_sms, true) { launchExternal { SystemActions.sms(context, k.number, k.body) } })
                add(OverlayAction("Kopieren", R.drawable.ic_copy) { copy(c.raw) })
            }
            is CodeContent.Geo -> {
                add(OverlayAction("In Karten öffnen", R.drawable.ic_place, true) { launchExternal { SystemActions.openUri(context, k.uri) } })
                add(OverlayAction("Kopieren", R.drawable.ic_copy) { copy(c.raw) })
            }
            is CodeContent.Event -> {
                add(OverlayAction("In Kalender eintragen", R.drawable.ic_event, true) { launchExternal { SystemActions.addEvent(context, k) } })
                add(OverlayAction("Kopieren", R.drawable.ic_copy) { copy(c.raw) })
            }
            is CodeContent.OtherUri -> {
                add(OverlayAction("Öffnen", R.drawable.ic_open, true) { launchExternal { SystemActions.openUri(context, k.uri) } })
                add(OverlayAction("Kopieren", R.drawable.ic_copy) { copy(c.raw) })
            }
            is CodeContent.Product -> add(OverlayAction("Nummer kopieren", R.drawable.ic_copy, true) { copy(k.code) })
            is CodeContent.Text -> {
                findUrl(k.text)?.let { url ->
                    add(OverlayAction("Link öffnen", R.drawable.ic_open, true) { launchExternal { SystemActions.openUri(context, url) } })
                }
                add(OverlayAction("Kopieren", R.drawable.ic_copy, isEmpty()) { copy(k.text) })
            }
        }
        add(OverlayAction("Teilen", R.drawable.ic_share) { launchExternal { SystemActions.shareText(context, c.raw) } })
    }

    private fun regionActions(r: Rect): List<OverlayAction> = listOf(
        OverlayAction("Bild teilen", R.drawable.ic_share, true) { shareImage(r) },
        OverlayAction("Bild kopieren", R.drawable.ic_image) { copyImage(r) },
    )

    private fun copy(text: String, sensitive: Boolean = false) {
        SystemActions.copyText(context, text, sensitive = sensitive)
        showFeedback("In die Zwischenablage kopiert")
        haptics(confirm())
    }

    /** Starts something in another app, then closes the overlay right away. */
    private fun launchExternal(block: () -> Boolean) {
        if (block()) finish() else showFeedback("Keine passende App gefunden")
    }

    private fun crop(r: Rect?): Bitmap? {
        val src = pixels ?: return null
        if (r == null) return src
        val x = r.left.roundToInt().coerceIn(0, src.width - 1)
        val y = r.top.roundToInt().coerceIn(0, src.height - 1)
        val w = r.width.roundToInt().coerceIn(1, src.width - x)
        val h = r.height.roundToInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    private fun shareImage(r: Rect?) = scope.launch {
        val uri = withContext(Dispatchers.IO) { crop(r)?.let { SystemActions.saveImage(context, it) } }
        if (uri != null) launchExternal { SystemActions.shareImage(context, uri) } else showFeedback("Bild nicht verfügbar")
    }

    private fun copyImage(r: Rect) = scope.launch {
        val uri = withContext(Dispatchers.IO) { crop(r)?.let { SystemActions.saveImage(context, it) } }
        if (uri != null) {
            SystemActions.copyImage(context, uri)
            showFeedback("Bild kopiert")
            haptics(confirm())
        } else {
            showFeedback("Bild nicht verfügbar")
        }
    }

    private fun showFeedback(msg: String) {
        feedback = msg
        feedbackJob?.cancel()
        feedbackJob = scope.launch {
            delay(1800)
            feedback = null
        }
    }

    private fun confirm() = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    private fun gestureStart() = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CLOCK_TICK
    private fun gestureEnd() = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK

    companion object {
        private const val TAG = "Overlay"
        private const val OCR_TIMEOUT_MS = 6000L

        /** First real-looking link in [text]; plain words with a dot are not enough. */
        fun findUrl(text: String): String? {
            val m = Patterns.WEB_URL.matcher(text)
            while (m.find()) {
                val u = m.group().trimEnd('.', ',', ';', ':', ')', '!', '?')
                val lower = u.lowercase()
                val looksReal = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") ||
                    (u == text.trim() && Regex("""^[\w-]+(\.[\w-]+)*\.[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE).matches(u))
                if (looksReal) return if (lower.startsWith("http")) u else "https://$u"
            }
            return null
        }
    }
}
