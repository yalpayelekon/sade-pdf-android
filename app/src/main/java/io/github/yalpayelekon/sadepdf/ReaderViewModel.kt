package io.github.yalpayelekon.sadepdf

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReaderState(
    val document: CachedPdf? = null,
    val pending: CachedPdf? = null,
    val importing: Boolean = false,
    val ready: Boolean = false,
    val displayError: Boolean = false,
) {
    val canUseDocument: Boolean
        get() = ready && pending == null && !importing
}

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
        discard(state.value.pending)
        mutableState.value = state.value.copy(pending = null, importing = true)
        importJob = viewModelScope.launch {
            var copied: CachedPdf? = null
            try {
                // Keep ownership of a completed copy even if cancellation wins the handoff to Main.
                withContext(Dispatchers.IO) { copied = files.importPdf(uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify(R.string.error_open)
            } finally {
                if (isActive) {
                    mutableState.value = state.value.copy(pending = copied, importing = false)
                } else {
                    copied?.let { document ->
                        withContext(NonCancellable + Dispatchers.IO) { files.discard(document) }
                    }
                }
            }
        }
    }

    fun documentOpened(id: String) {
        val current = state.value
        val pending = current.pending
        if (pending?.id == id) {
            savedState[DOCUMENT_ID] = pending.id
            savedState[DOCUMENT_NAME] = pending.displayName
            mutableState.value = ReaderState(document = pending, ready = true)
        } else if (current.document?.id == id) {
            mutableState.value = current.copy(ready = true, displayError = false)
        }
    }

    fun documentFailed(id: String, cancelled: Boolean) {
        val current = state.value
        val pending = current.pending
        if (pending?.id == id) {
            discard(pending)
            mutableState.value = current.copy(
                pending = null,
                displayError = current.displayError || (!cancelled && current.document == null),
            )
            if (!cancelled && current.document != null) notify(R.string.error_document)
        } else if (current.document?.id == id) {
            savedState.remove<String>(DOCUMENT_ID)
            savedState.remove<String>(DOCUMENT_NAME)
            mutableState.value = current.copy(document = null, ready = false, displayError = !cancelled)
        }
    }

    fun keepSharedFile(document: CachedPdf) {
        viewModelScope.launch(Dispatchers.IO) { files.touch(document) }
    }

    private fun discard(document: CachedPdf?) {
        if (document == null) return
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { files.discard(document) }
        }
    }

    fun notify(@StringRes message: Int) {
        messages.trySend(message)
    }

    private companion object {
        const val DOCUMENT_ID = "documentId"
        const val DOCUMENT_NAME = "documentName"
    }
}
