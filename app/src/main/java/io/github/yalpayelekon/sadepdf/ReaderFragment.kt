package io.github.yalpayelekon.sadepdf

import android.os.Bundle
import android.os.OperationCanceledException
import android.view.View
import androidx.core.os.bundleOf
import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment

class ReaderFragment : PdfViewerFragment() {
    internal val documentId: String
        get() = requireNotNull(requireArguments().getString(DOCUMENT_ID))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (documentUri == null) {
            documentUri = PdfFiles(requireContext()).viewerUri(CachedPdf(documentId, "document.pdf"))
        }
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        (activity as? ReaderActivity)?.onDocumentOpened(documentId)
    }

    override fun onLoadDocumentError(error: Throwable) {
        (activity as? ReaderActivity)?.onDocumentFailed(documentId, error is OperationCanceledException)
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        isToolboxVisible = false
    }

    internal companion object {
        private const val DOCUMENT_ID = "documentId"

        fun forDocument(id: String) = ReaderFragment().apply {
            arguments = bundleOf(DOCUMENT_ID to id)
        }
    }
}
