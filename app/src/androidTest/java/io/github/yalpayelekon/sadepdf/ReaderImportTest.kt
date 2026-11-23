package io.github.yalpayelekon.sadepdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application
    private val savedState = SavedStateHandle()
    private val reader = ReaderViewModel(application, savedState)
    private val files = PdfFiles(application)
    private val fixtures = mutableListOf<CachedPdf>()

    @After
    fun removeFixtures() {
        fixtures.forEach(files::discard)
    }

    @Test
    fun cancellingPasswordKeepsPreviousDocumentAndDeletesCandidate() = runBlocking {
        val previous = openCandidate("previous.pdf")
        onMain { reader.documentOpened(previous.id) }
        val locked = openCandidate("locked.pdf")

        assertEquals(previous.id, savedState.get<String>("documentId"))
        assertFalse(reader.state.value.canUseDocument)
        assertTrue(cacheFile(locked).isFile)

        onMain {
            reader.documentFailed(locked.id, cancelled = true)
            reader.documentOpened(locked.id)
        }

        val state = reader.state.value
        assertEquals(previous, state.document)
        assertEquals(previous.id, savedState.get<String>("documentId"))
        assertNull(state.pending)
        assertTrue(state.canUseDocument)
        assertTrue(cacheFile(previous).isFile)
        withTimeout(5_000) {
            while (cacheFile(locked).parentFile!!.exists()) delay(10)
        }
    }

    @Test
    fun unexpectedProviderExceptionEndsImportAndKeepsPreviousDocument() = runBlocking {
        val previous = openCandidate("previous.pdf")
        onMain { reader.documentOpened(previous.id) }

        onMain { reader.open(fixture("failure.pdf")) }

        assertEquals(R.string.error_open, withTimeout(5_000) { reader.notices.first() })
        val state = withTimeout(5_000) { reader.state.first { !it.importing } }
        assertEquals(previous, state.document)
        assertNull(state.pending)
        assertTrue(state.canUseDocument)
        assertTrue(cacheFile(previous).isFile)
    }

    private suspend fun openCandidate(name: String): CachedPdf {
        onMain { reader.open(fixture(name)) }
        val state = withTimeout(5_000) { reader.state.first { it.pending != null } }
        return requireNotNull(state.pending).also(fixtures::add)
    }

    private fun onMain(action: () -> Unit) {
        instrumentation.runOnMainSync { action() }
    }

    private fun fixture(name: String): Uri =
        Uri.parse("content://io.github.yalpayelekon.sadepdf.test.fixtures/$name")

    private fun cacheFile(document: CachedPdf): File = File(requireNotNull(files.viewerUri(document).path))
}
