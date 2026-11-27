// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.analysis

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.google.android.gms.tasks.Task
import de.volcanoproject.encircle.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.graphics.Rect as AndroidRect

/**
 * On-device screen understanding: ML Kit for text, zxing-cpp for every barcode symbology.
 * Both engines are created once per process and kept warm.
 */
object ScreenAnalyzer {
    private const val TAG = "ScreenAnalyzer"

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val barcodeReader by lazy {
        BarcodeReader(
            BarcodeReader.Options().apply {
                // An empty format set means "all formats".
                formats = emptySet()
                tryHarder = true
                tryRotate = true
                tryInvert = true
                tryDownscale = true
                maxNumberOfSymbols = 32
            },
        )
    }
    private val barcodeLock = Mutex()

    private val productFormats = setOf(
        BarcodeReader.Format.EAN_13, BarcodeReader.Format.EAN_8, BarcodeReader.Format.UPC_A,
        BarcodeReader.Format.UPC_E, BarcodeReader.Format.ISBN, BarcodeReader.Format.EAN_UPC,
    )
    private val matrixFormats = setOf(
        BarcodeReader.Format.QR_CODE, BarcodeReader.Format.QR_CODE_MODEL_1, BarcodeReader.Format.QR_CODE_MODEL_2,
        BarcodeReader.Format.MICRO_QR_CODE, BarcodeReader.Format.RMQR_CODE, BarcodeReader.Format.DATA_MATRIX,
        BarcodeReader.Format.AZTEC, BarcodeReader.Format.AZTEC_CODE, BarcodeReader.Format.AZTEC_RUNE,
        BarcodeReader.Format.MAXI_CODE, BarcodeReader.Format.PDF_417, BarcodeReader.Format.COMPACT_PDF_417,
        BarcodeReader.Format.MICRO_PDF_417,
    )

    fun warmUp() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val tiny = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            runCatching { recognizeText(tiny) }.onFailure { Log.w(TAG, "OCR warm-up failed", it) }
            runCatching { scanCodes(tiny) }.onFailure { Log.w(TAG, "Barcode warm-up failed", it) }
            tiny.recycle()
        }
    }

    /** Both engines need CPU-readable pixels; screenshots usually arrive as hardware bitmaps. */
    fun softwareCopy(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)

    suspend fun recognizeText(bitmap: Bitmap): TextLayout {
        val text = textRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return withContext(Dispatchers.Default) { text.toLayout() }
    }

    /** [res] translates format names such as "QR code"; the warm-up passes none and gets English. */
    suspend fun scanCodes(bitmap: Bitmap, res: Resources? = null): List<DetectedCode> = withContext(Dispatchers.Default) {
        val results = barcodeLock.withLock { barcodeReader.read(bitmap, AndroidRect(), 0) }
        results.mapIndexedNotNull { i, r ->
            val pos = r.position
            val corners = listOf(pos.topLeft, pos.topRight, pos.bottomRight, pos.bottomLeft)
                .map { Offset(it.x.toFloat(), it.y.toFloat()) }
            val raw = r.text?.takeIf { it.isNotBlank() }
                ?: r.bytes?.takeIf { it.isNotEmpty() }?.joinToString(" ") { "%02X".format(it) }
                ?: return@mapIndexedNotNull null
            val matrix = r.format in matrixFormats
            DetectedCode(
                id = i,
                formatName = formatName(r.format, res),
                isMatrix = matrix,
                raw = raw,
                corners = if (matrix) corners else padLinear(corners),
                content = CodeParser.parse(raw, r.format in productFormats),
            )
        }.distinctBy { Triple(it.raw, (it.center.x / 48).toInt(), (it.center.y / 48).toInt()) }
    }

    /** Linear barcodes are reported as a thin scan line; give them a usable height. */
    private fun padLinear(c: List<Offset>): List<Offset> {
        val h = maxOf(c[3].y - c[0].y, c[2].y - c[1].y)
        val w = maxOf(c[1].x - c[0].x, c[2].x - c[3].x)
        if (h >= w * 0.25f) return c
        val grow = (w * 0.25f - h) / 2f
        return listOf(
            c[0].copy(y = c[0].y - grow), c[1].copy(y = c[1].y - grow),
            c[2].copy(y = c[2].y + grow), c[3].copy(y = c[3].y + grow),
        )
    }

    private fun label(res: Resources?, @StringRes id: Int, fallback: String) = res?.getString(id) ?: fallback

    private fun formatName(f: BarcodeReader.Format, res: Resources?): String = when (f) {
        BarcodeReader.Format.QR_CODE, BarcodeReader.Format.QR_CODE_MODEL_1, BarcodeReader.Format.QR_CODE_MODEL_2 -> label(res, R.string.format_qr, "QR code")
        BarcodeReader.Format.MICRO_QR_CODE -> label(res, R.string.format_micro_qr, "Micro QR")
        BarcodeReader.Format.RMQR_CODE -> label(res, R.string.format_rmqr, "rMQR code")
        BarcodeReader.Format.DATA_MATRIX -> "Data Matrix"
        BarcodeReader.Format.AZTEC, BarcodeReader.Format.AZTEC_CODE, BarcodeReader.Format.AZTEC_RUNE -> label(res, R.string.format_aztec, "Aztec code")
        BarcodeReader.Format.PDF_417, BarcodeReader.Format.COMPACT_PDF_417 -> "PDF417"
        BarcodeReader.Format.MICRO_PDF_417 -> "MicroPDF417"
        BarcodeReader.Format.MAXI_CODE -> "MaxiCode"
        BarcodeReader.Format.EAN_13 -> "EAN-13"
        BarcodeReader.Format.EAN_8 -> "EAN-8"
        BarcodeReader.Format.UPC_A -> "UPC-A"
        BarcodeReader.Format.UPC_E -> "UPC-E"
        BarcodeReader.Format.ISBN -> "ISBN"
        BarcodeReader.Format.CODE_128 -> "Code 128"
        BarcodeReader.Format.CODE_39, BarcodeReader.Format.CODE_39_STD, BarcodeReader.Format.CODE_39_EXT -> "Code 39"
        BarcodeReader.Format.CODE_93 -> "Code 93"
        BarcodeReader.Format.ITF, BarcodeReader.Format.ITF_14 -> "ITF"
        BarcodeReader.Format.CODABAR -> "Codabar"
        else -> if (f.name.startsWith("DATA_BAR")) "GS1 DataBar" else label(res, R.string.format_barcode, "Barcode")
    }

    private fun Text.toLayout(): TextLayout = LayoutBuilder.build(
        textBlocks.flatMapIndexed { bi, b ->
            b.lines.map { l ->
                RawLine(l.elements.mapNotNull { e -> e.boundingBox?.let { RawElement(e.text, it.toRect()) } }, bi)
            }
        },
    )

    private fun AndroidRect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener(Runnable::run) { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
        }
    }
}
