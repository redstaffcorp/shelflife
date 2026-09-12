package hu.rsc.shelflife.scanner

import android.annotation.SuppressLint
import android.content.Context
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

/**
 * Egyetlen ImageAnalysis.Analyzer, ami a UI aktualis fazisa alapjan vagy
 * vonalkod-olvasast, vagy szovegfelismerest (lejarati datum keresest) futtat
 * a kamera elo kepkockain. Minden feldolgozas a telefonon tortenik
 * (Google ML Kit on-device modellek), halozati hivas nincs.
 */
class ScannerAnalyzer(
    context: Context,
    private val phaseProvider: () -> ScanPhase,
    private val onBarcodeDetected: (String) -> Unit,
    private val onDateCandidate: (DateCandidate) -> Unit
) : ImageAnalysis.Analyzer {

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
    private val textAnalysisIntervalMs = 300L

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
                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                textRecognizer.process(input)
                    .addOnSuccessListener(mainExecutor) { visionText ->
                        val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                        DateParser.findBestCandidate(lines)?.let(onDateCandidate)
                    }
                    .addOnCompleteListener(mainExecutor) { imageProxy.close() }
            }

            ScanPhase.PAUSED -> imageProxy.close()
        }
    }
}
