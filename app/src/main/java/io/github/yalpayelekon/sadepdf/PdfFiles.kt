package io.github.yalpayelekon.sadepdf

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class CachedPdf(val id: String, val displayName: String)

class PdfFileProvider : FileProvider()

internal class PdfFiles(private val context: Context) {
    private val directory = File(context.cacheDir, "pdfs")
    private val authority = "${context.packageName}.files"

    suspend fun importPdf(uri: Uri): CachedPdf {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority == authority) {
            throw IOException("Unsupported document source")
        }

        val document = CachedPdf(UUID.randomUUID().toString(), displayName(uri))
        val file = fileFor(document)
        if (!file.parentFile!!.mkdirs()) throw IOException("Couldn't create document cache")

        var completed = false
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Couldn't open document stream")
            input.use { source ->
                file.outputStream().buffered().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count == -1) break
                        target.write(buffer, 0, count)
                    }
                }
            }
            coroutineContext.ensureActive()
            completed = true
            return document
        } finally {
            if (!completed) {
                file.delete()
                file.parentFile?.delete()
            }
        }
    }

    fun viewerUri(document: CachedPdf): Uri = Uri.fromFile(fileFor(document))

    fun shareIntent(document: CachedPdf): Intent {
        val file = fileFor(document)
        if (!file.isFile) throw IOException("Cached document is missing")
        val uri = FileProvider.getUriForFile(context, authority, file, document.displayName)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(document.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun touch(document: CachedPdf) {
        fileFor(document).parentFile?.setLastModified(System.currentTimeMillis())
    }

    fun prune(activeId: String?) {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        directory.listFiles()?.forEach { entry ->
            if (entry.name != activeId && validId(entry.name) && entry.lastModified() < cutoff) {
                File(entry, "original.pdf").delete()
                entry.delete()
            }
        }
    }

    private fun fileFor(document: CachedPdf): File {
        require(validId(document.id))
        return File(File(directory, document.id), "original.pdf")
    }

    private fun displayName(uri: Uri): String {
        val column = OpenableColumns.DISPLAY_NAME
        val name = try {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
        return name?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.filterNot { Character.isISOControl(it) }
            ?.trim()?.takeIf { it.isNotEmpty() } ?: "document.pdf"
    }

    companion object {
        fun validId(id: String): Boolean = UUID_PATTERN.matches(id)

        private val UUID_PATTERN =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
