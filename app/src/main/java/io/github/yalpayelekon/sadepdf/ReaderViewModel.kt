package io.github.yalpayelekon.sadepdf

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReaderState(
    val document: CachedPdf? = null,
    val importing: Boolean = false,
    val ready: Boolean = false,
    val displayError: Boolean = false,
)

internal class ReaderViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val files = PdfFiles(application)
    private val restoredDocument = savedState.get<String>(DOCUMENT_ID)
        ?.takeIf(PdfFiles::validId)
        ?.let { CachedPdf(it, savedState.get<String>(DOCUMENT_NAME) ?: "document.pdf") }
    private val mutableState = MutableStateFlow(ReaderState(document = restoredDocument))
    private val messages = Channel<Int>(Channel.BUFFERED)
    private var importJob: Job? = null

    val state = mutableState.asStateFlow()
    val notices = messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { files.prune(restoredDocument?.id) }
    }

    fun open(uri: Uri) {
        importJob?.cancel()
        mutableState.value = mutableState.value.copy(importing = true)
        importJob = viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) { files.importPdf(uri) }
                savedState[DOCUMENT_ID] = document.id
                savedState[DOCUMENT_NAME] = document.displayName
                mutableState.value = ReaderState(document = document)
            } catch (_: IOException) {
                importFailed()
            } catch (_: SecurityException) {
                importFailed()
            } catch (_: IllegalArgumentException) {
                importFailed()
            }
        }
    }

    fun documentOpened(id: String) {
        if (state.value.document?.id == id) {
            mutableState.value = state.value.copy(ready = true, displayError = false)
        }
    }

    fun documentFailed(id: String, cancelled: Boolean) {
        if (state.value.document?.id != id) return
        if (cancelled) {
            savedState.remove<String>(DOCUMENT_ID)
            savedState.remove<String>(DOCUMENT_NAME)
            mutableState.value = ReaderState(importing = state.value.importing)
        } else {
            mutableState.value = state.value.copy(ready = false, displayError = true)
        }
    }

    fun keepSharedFile(document: CachedPdf) {
        viewModelScope.launch(Dispatchers.IO) { files.touch(document) }
    }

    private fun importFailed() {
        mutableState.value = mutableState.value.copy(importing = false)
        notify(R.string.error_open)
    }

    fun notify(@StringRes message: Int) {
        messages.trySend(message)
    }

    private companion object {
        const val DOCUMENT_ID = "documentId"
        const val DOCUMENT_NAME = "documentName"
    }
}
