package io.github.yalpayelekon.sadepdf

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfSharingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = PdfFiles(context)

    @Test
    fun shareServesOriginalBytesAndNameWithReadOnlyGrant() {
        val document = CachedPdf(UUID.randomUUID().toString(), "Original document.pdf")
        val stored = File(context.cacheDir, "pdfs/${document.id}/original.pdf")
        stored.parentFile!!.mkdirs()
        try {
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
                page.canvas.drawText("An original PDF", 30f, 70f, Paint())
                pdf.finishPage(page)
                stored.outputStream().use(pdf::writeTo)
            } finally {
                pdf.close()
            }
            val intent = files.shareIntent(document)
            val uri = requireNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals("application/pdf", intent.type)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)

            val resolver = context.contentResolver
            resolver.openInputStream(uri)!!.use { received ->
                assertArrayEquals(stored.readBytes(), received.readBytes())
            }
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                it.moveToFirst()
                assertEquals(document.displayName, it.getString(0))
            }
        } finally {
            stored.delete()
            stored.parentFile?.delete()
        }
    }

    @Test
    fun evictedFileCannotBeShared() {
        val document = CachedPdf(UUID.randomUUID().toString(), "Missing.pdf")
        assertThrows(IOException::class.java) { files.shareIntent(document) }
    }
}
