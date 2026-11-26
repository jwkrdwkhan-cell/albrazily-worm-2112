package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AttachmentKind
import com.example.model.PendingAttachment
import com.example.model.QueuedBookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

object AttachmentProcessor {

    private const val MAX_INLINE_IMAGE_DIM = 1280
    private const val MAX_FILE_READ_BYTES = 8 * 1024 * 1024 // 8MB safe memory cap per file

    suspend fun processImageUri(
        context: Context,
        uri: Uri,
        kind: AttachmentKind = AttachmentKind.IMAGE
    ): PendingAttachment = withContext(Dispatchers.IO) {
        val name = queryDisplayName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bitmap = decodeScaledBitmap(context, uri, MAX_INLINE_IMAGE_DIM)
        val sizeBytes = queryFileSize(context, uri)
        PendingAttachment(
            id = UUID.randomUUID().toString(),
            displayName = name,
            mimeType = mime,
            kind = kind,
            uri = uri,
            bitmap = bitmap,
            sizeBytes = sizeBytes
        )
    }

    fun processCapturedBitmap(bitmap: Bitmap): PendingAttachment {
        val scaled = scaleBitmapIfNeeded(bitmap, MAX_INLINE_IMAGE_DIM)
        return PendingAttachment(
            id = UUID.randomUUID().toString(),
            displayName = "camera_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            kind = AttachmentKind.CAMERA_PHOTO,
            bitmap = scaled,
            sizeBytes = (scaled.byteCount).toLong()
        )
    }

    suspend fun processDocumentUri(context: Context, uri: Uri): PendingAttachment =
        withContext(Dispatchers.IO) {
            val name = queryDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(name)
            val sizeBytes = queryFileSize(context, uri)
            val isImage = mime.startsWith("image/")
            val bitmap = if (isImage) decodeScaledBitmap(context, uri, MAX_INLINE_IMAGE_DIM) else null
            val extracted = if (!isImage) extractTextFromUri(context, uri, name, mime) else null

            PendingAttachment(
                id = UUID.randomUUID().toString(),
                displayName = name,
                mimeType = mime,
                kind = if (isImage) AttachmentKind.IMAGE else AttachmentKind.FILE,
                uri = uri,
                bitmap = bitmap,
                extractedText = extracted,
                sizeBytes = sizeBytes
            )
        }

    suspend fun prepareBookForTraining(context: Context, uri: Uri): QueuedBookFile =
        withContext(Dispatchers.IO) {
            val name = queryDisplayName(context, uri) ?: "Book_${System.currentTimeMillis()}.txt"
            val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(name)
            val extracted = extractTextFromUri(context, uri, name, mime)
            val cleanText = extracted.ifBlank {
                "Document '$name' ($mime) uploaded for structural & conceptual mastery."
            }
            val chunks = chunkBookText(cleanText, chunkSize = 3500, overlap = 250)
            QueuedBookFile(
                id = "book_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                title = name,
                mimeType = mime,
                fullText = cleanText,
                chunks = chunks
            )
        }

    fun chunkBookText(text: String, chunkSize: Int = 3500, overlap: Int = 250): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return listOf("")
        if (normalized.length <= chunkSize) return listOf(normalized)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length && chunks.size < 120) {
            val end = (start + chunkSize).coerceAtMost(normalized.length)
            chunks.add(normalized.substring(start, end))
            if (end >= normalized.length) break
            start = (end - overlap).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun extractTextFromUri(
        context: Context,
        uri: Uri,
        name: String,
        mime: String
    ): String {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(8192)
                var total = 0
                while (total < MAX_FILE_READ_BYTES) {
                    val read = input.read(data)
                    if (read == -1) break
                    buffer.write(data, 0, read)
                    total += read
                }
                buffer.toByteArray()
            } ?: return ""

            val lowerName = name.lowercase()
            when {
                lowerName.endsWith(".docx") || mime.contains("wordprocessingml") -> {
                    extractDocxText(rawBytes)
                }
                lowerName.endsWith(".pdf") || mime.contains("pdf") -> {
                    extractReadablePdfText(rawBytes)
                }
                else -> {
                    val text = rawBytes.toString(Charsets.UTF_8)
                    val printableRatio = text.take(1000).count {
                        !it.isISOControl() || it == '\n' || it == '\r' || it == '\t'
                    }.toFloat() / (text.take(1000).length.coerceAtLeast(1))
                    if (printableRatio > 0.72f) {
                        text.take(250_000)
                    } else {
                        extractReadableAsciiAndArabicRuns(rawBytes)
                    }
                }
            }
        } catch (e: Exception) {
            "File '$name' ($mime) attached (error reading raw bytes: ${e.message})."
        }
    }

    private fun extractDocxText(bytes: ByteArray): String {
        val sb = StringBuilder()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    val stripped = xml.replace(Regex("<w:p[^>]*>"), "\n")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                    sb.append(stripped)
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim().take(250_000)
    }

    private fun extractReadablePdfText(bytes: ByteArray): String {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val regex = Regex("\\(([^()]{2,500})\\)")
        val parts = regex.findAll(raw)
            .map { it.groupValues[1] }
            .filter { s -> s.any { it.isLetterOrDigit() } }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (parts.length > 80) {
            parts.take(250_000)
        } else {
            extractReadableAsciiAndArabicRuns(bytes)
        }
    }

    private fun extractReadableAsciiAndArabicRuns(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        val runs = Regex("[\\p{L}\\p{N}\\p{Punct}\\s]{6,}")
            .findAll(text)
            .map { it.value.trim() }
            .filter { it.length >= 6 }
            .take(400)
            .joinToString("\n")
        return runs.take(80_000)
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null
            scaleBitmapIfNeeded(bmp, maxDim)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun guessMimeFromName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".kt") -> "text/x-kotlin"
            lower.endsWith(".py") -> "text/x-python"
            lower.endsWith(".js") -> "application/javascript"
            lower.endsWith(".csv") -> "text/csv"
            else -> "application/octet-stream"
        }
    }
}
