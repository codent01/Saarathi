package com.saarathi.app.ingest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class Attachment(
    val kind: String,          // image | pdf | text
    val name: String,
    val extractedText: String,
    val truncated: Boolean,
    val note: String = "",      // e.g. "OCR found no text"
) {
    fun toJson(): String = Json.encodeToString(this)
    companion object { fun fromJson(s: String): Attachment? = runCatching { Json.decodeFromString<Attachment>(s) }.getOrNull() }
}

/**
 * Turns a user-picked file into text, entirely on the device:
 *  - images: ML Kit text recognition (Latin + Devanagari models bundled in the APK),
 *  - PDFs: PdfBox-Android text extraction (first pages up to a size cap),
 *  - text files: read directly.
 * Personal attachments never enter the scripture database; they only ride along with one message.
 */
class AttachmentProcessor(private val context: Context) {
    private val maxChars = 9000
    private val maxPdfPages = 40

    suspend fun process(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: ""
        val name = displayName(uri)
        when {
            mime.startsWith("image/") -> ocrImage(uri, name)
            mime == "application/pdf" || name.endsWith(".pdf", true) -> extractPdf(uri, name)
            else -> readText(uri, name)
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
        }
        return uri.lastPathSegment ?: "attachment"
    }

    private suspend fun ocrImage(uri: Uri, name: String): Attachment {
        val image = InputImage.fromFilePath(context, uri)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val a = runCatching { recognize(latin, image) }.getOrDefault("")
        val b = runCatching { recognize(devanagari, image) }.getOrDefault("")
        latin.close(); devanagari.close()
        // Keep the richer result; if both found text in different scripts, join them.
        val text = when {
            a.isBlank() -> b
            b.isBlank() -> a
            b.any { it in 'ऀ'..'ॿ' } && a.length < b.length * 2 -> a + "\n" + b
            else -> a
        }.trim()
        val note = if (text.isBlank()) "No readable text was found in this image. Saarathi can only read text in images; it cannot see pictures." else "Text was read from the image with on-device OCR; visual details are not understood."
        return Attachment("image", name, text.take(maxChars), text.length > maxChars, note)
    }

    private suspend fun recognize(recognizer: TextRecognizer, image: InputImage): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun extractPdf(uri: Uri, name: String): Attachment {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                val pages = minOf(doc.numberOfPages, maxPdfPages)
                stripper.startPage = 1; stripper.endPage = pages
                val text = stripper.getText(doc).replace(Regex("[ \\t]+"), " ").trim()
                val truncated = text.length > maxChars || doc.numberOfPages > pages
                val note = if (text.isBlank()) "This PDF has no text layer (it may be scanned). Try a screenshot of the page instead." else "${doc.numberOfPages} pages; first $pages read."
                return Attachment("pdf", name, text.take(maxChars), truncated, note)
            }
        }
        return Attachment("pdf", name, "", false, "Could not open the PDF.")
    }

    private fun readText(uri: Uri, name: String): Attachment {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        return Attachment("text", name, text.take(maxChars), text.length > maxChars)
    }
}
