package hu.rsc.shelflife.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import hu.rsc.shelflife.ocr.DateCandidate
import hu.rsc.shelflife.ocr.DateParser

enum class ScanPhase { SCANNING_BARCODE, SCANNING_DATE, PAUSED }

/** Diagnosztikai adat egy datum-fazisu OCR futasrol (fejlesztoi nezethez). */
data class OcrDebugInfo(
    val variant: String,
    val frameSize: String,
    val rawText: String,
    val parsed: String?,
    val processedBitmap: Bitmap?
)

/**
 * Egyetlen ImageAnalysis.Analyzer, ami a UI aktualis fazisa alapjan vagy
 * vonalkod-olvasast, vagy szovegfelismerest (lejarati datum keresest) futtat
 * a kamera elo kepkockain. Minden feldolgozas a telefonon tortenik
 * (Google ML Kit on-device modellek), halozati hivas nincs.
 *
 * Datum-fazisban a kepkockak kozott varialunk (lasd [TextVariant]): a tiszta,
 * nyomdai szoveghez az eredeti kep a legjobb, a mintas fedelre tintasugarral
 * nyomott pontmatrix datumhoz viszont a kozepre vagott, kontrasztnyujtott es
 * enyhen elmosott (a pontokat osszeolvaszto) szurkearnyalatos kep. A tobbsegi
 * szavazas a UI-ban tortenik (ugyanaz a datum tobbszor).
 *
 * Az INK_ONLY variansok a szines mintara (pl. narancs "Tejfol" felirat) nyomott
 * fekete tintasugaras datumhoz valok: fenyessegben a fekete tinta (~15-40) joval
 * sotetebb a narancsnal (~80-110) es a feheznel (~170+), igy egy alacsony kuszobbel
 * csak a tinta marad meg, a minta eltunik. (Valodi Pilos tejfol-fotón kiprobalva.)
 *
 * Megj.: a pontmatrix nyomatot (pl. vodros tejfol fedele) az ML Kit tapasztalat szerint
 * semmilyen elofeldolgozassal nem detektalja szovegkent -- ott a termekenkent megjegyzett
 * szokasos eltarthatosag + gyors datumbevitel a megoldas (lasd ScannerScreen).
 */
class ScannerAnalyzer(
    context: Context,
    private val phaseProvider: () -> ScanPhase,
    private val onBarcodeDetected: (String) -> Unit,
    private val onDateCandidate: (DateCandidate) -> Unit
) : ImageAnalysis.Analyzer {

    private enum class TextVariant { FULL_FRAME, CROP_BLUR1, INK_ONLY_15 }

    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastTextAnalysisMs = 0L
    private val textAnalysisIntervalMs = 200L
    private var variantIndex = 0

    /** Ha nem null, minden datum-fazisu OCR futas utan meghivodik (fo szalon). */
    @Volatile
    var debugListener: ((OcrDebugInfo) -> Unit)? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        when (phaseProvider()) {
            ScanPhase.SCANNING_BARCODE -> {
                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner.process(input)
                    .addOnSuccessListener(mainExecutor) { barcodes ->
                        barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let(onBarcodeDetected)
                    }
                    .addOnCompleteListener(mainExecutor) { imageProxy.close() }
            }

            ScanPhase.SCANNING_DATE -> {
                val now = System.currentTimeMillis()
                if (now - lastTextAnalysisMs < textAnalysisIntervalMs) {
                    imageProxy.close()
                    return
                }
                lastTextAnalysisMs = now
                val rotation = imageProxy.imageInfo.rotationDegrees
                val variant = TextVariant.entries[variantIndex]
                variantIndex = (variantIndex + 1) % TextVariant.entries.size

                val frameSize = "${imageProxy.width}x${imageProxy.height} rot=$rotation"
                var processed: Bitmap? = null
                val input = if (variant == TextVariant.FULL_FRAME) {
                    InputImage.fromMediaImage(mediaImage, rotation)
                } else {
                    val bitmap = try {
                        buildPreprocessedCrop(imageProxy, variant)
                    } catch (e: Exception) {
                        debugListener?.let { l ->
                            mainExecutor.execute { l(OcrDebugInfo(variant.name, frameSize, "HIBA: $e", null, null)) }
                        }
                        null
                    }
                    // A bitmap mar a kep masolata, a kamerakocka elengedheto.
                    imageProxy.close()
                    if (bitmap == null) return
                    processed = bitmap
                    InputImage.fromBitmap(bitmap, rotation)
                }

                textRecognizer.process(input)
                    .addOnSuccessListener(mainExecutor) { visionText ->
                        val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                        val candidate = DateParser.findBestCandidate(lines)
                        candidate?.let(onDateCandidate)
                        debugListener?.invoke(
                            OcrDebugInfo(
                                variant = variant.name,
                                frameSize = frameSize,
                                rawText = lines.joinToString(" | "),
                                parsed = candidate?.let { "${it.date} ('${it.rawMatch}')" },
                                processedBitmap = processed?.let { rotateForDisplay(it, rotation) }
                            )
                        )
                    }
                    .addOnFailureListener(mainExecutor) { e ->
                        debugListener?.invoke(OcrDebugInfo(variant.name, frameSize, "OCR HIBA: $e", null, processed))
                    }
                    .addOnCompleteListener(mainExecutor) {
                        if (variant == TextVariant.FULL_FRAME) imageProxy.close()
                    }
            }

            ScanPhase.PAUSED -> imageProxy.close()
        }
    }

    /**
     * A kep kozepso savjat (allo helyzetben ~85% szeles, ~45% magas) kivagja a
     * Y (fenyesseg) sikbol, szurkearnyalatos Bitmap-et keszit belole, es a variansnak
     * megfeleloen elmossa + kontrasztot nyujt. A kivagas miatt a fedel mintaja kevesbe
     * zavarja a szovegdetektort, es relativan nagyobbak a karakterek.
     */
    private fun buildPreprocessedCrop(imageProxy: ImageProxy, variant: TextVariant): Bitmap {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val imgW = imageProxy.width
        val imgH = imageProxy.height

        // A szenzor-kep 90/270 fokkal el van forgatva az allo kijelzohoz kepest.
        val sideways = imageProxy.imageInfo.rotationDegrees % 180 != 0
        val fracX = if (sideways) 0.45f else 0.85f
        val fracY = if (sideways) 0.85f else 0.45f
        val cw = (imgW * fracX).toInt()
        val ch = (imgH * fracY).toInt()
        val x0 = (imgW - cw) / 2
        val y0 = (imgH - ch) / 2

        var gray = IntArray(cw * ch)
        for (y in 0 until ch) {
            val rowBase = (y0 + y) * rowStride
            for (x in 0 until cw) {
                gray[y * cw + x] = buffer.get(rowBase + (x0 + x) * pixelStride).toInt() and 0xFF
            }
        }

        when (variant) {
            TextVariant.CROP_BLUR1 -> {
                gray = boxBlur(gray, cw, ch, 1)
                contrastStretch(gray)
            }
            TextVariant.INK_ONLY_15 -> gray = boxBlur(keepDarkInk(gray, 0.15f), cw, ch, 1)
            TextVariant.FULL_FRAME -> Unit
        }

        val pixels = IntArray(gray.size) { i ->
            val v = gray[i]
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, cw, ch, Bitmap.Config.ARGB_8888)
    }

    /** Szeparalhato doboz-elmosas; a tintasugaras pontokat osszefuggo vonalakka olvasztja. */
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        val size = 2 * r + 1
        for (y in 0 until h) {
            val base = y * w
            var sum = 0
            for (k in -r..r) sum += src[base + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[base + x] = sum / size
                sum += src[base + (x + r + 1).coerceAtMost(w - 1)] - src[base + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / size
                sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    private fun rotateForDisplay(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Csak a legsotetebb (tinta) kepponteket tartja meg feketenek, minden mas feher.
     * Kuszob = p1 + frac * (p99 - p1), ahol p1/p99 a fenyesseg-percentilisek.
     */
    private fun keepDarkInk(px: IntArray, frac: Float): IntArray {
        val hist = IntArray(256)
        for (v in px) hist[v]++
        val cut = px.size / 100
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) { acc += hist[lo]; lo++ }
        var hi = 255
        acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) { acc += hist[hi]; hi-- }
        val threshold = lo + frac * (hi - lo)
        return IntArray(px.size) { i -> if (px[i] < threshold) 0 else 255 }
    }

    /** 1%-99% percentilis kozotti tartomanyt 0..255-re nyujtja (helyben). */
    private fun contrastStretch(px: IntArray) {
        val hist = IntArray(256)
        for (v in px) hist[v]++
        val cut = px.size / 100
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) { acc += hist[lo]; lo++ }
        var hi = 255
        acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) { acc += hist[hi]; hi-- }
        if (hi - lo < 10) return
        val range = (hi - lo).toFloat()
        for (i in px.indices) {
            px[i] = (((px[i] - lo) * 255f) / range).toInt().coerceIn(0, 255)
        }
    }
}
