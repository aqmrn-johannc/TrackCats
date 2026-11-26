package com.trackcats.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trackcats.app.core.Game
import com.trackcats.app.core.Pool
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads cat names out of screenshots (beta). It only understands screenshots where the cat's name is
 * written on screen, such as the reveal screen after each roll; icon-only summary screens have no text to read.
 */
object ScreenshotImport {
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Cats named in [lines], in reading order. Exact line matches win; longer names may also match inside a line. */
    fun match(lines: List<String>, names: Map<Int, String>): List<Int> {
        val byName = names.entries.map { normalize(it.value) to it.key }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.length }
        val out = ArrayList<Int>()
        for (line in lines) {
            val text = normalize(line)
            if (text.isEmpty()) continue
            val exact = byName.firstOrNull { it.first == text }
            val hit = exact ?: byName.firstOrNull { it.first.length >= 6 && text.contains(it.first) }
            if (hit != null && out.lastOrNull() != hit.second) out.add(hit.second)
        }
        return out
    }

    suspend fun read(context: Context, uris: List<Uri>, pool: Pool, game: Game): List<Int> {
        val names = pool.slots.flatMap { it.asIterable() }.distinct().associateWith { game.name(it) }
        val ordered = uris.sortedBy { displayName(context, it) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val out = ArrayList<Int>()
        try {
            for (uri in ordered) {
                val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() ?: continue
                val lines: List<String> = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result -> cont.resume(result.textBlocks.flatMap { b -> b.lines.map { it.text } }) }
                        .addOnFailureListener { cont.resume(emptyList()) }
                }
                out.addAll(match(lines, names))
            }
        } finally {
            recognizer.close()
        }
        return out
    }

    /** Screenshot file names carry their time, so sorting by name puts them in the order they were taken. */
    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.toString()
}
