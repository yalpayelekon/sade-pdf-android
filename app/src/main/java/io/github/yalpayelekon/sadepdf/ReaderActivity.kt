package io.github.yalpayelekon.sadepdf

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.yalpayelekon.sadepdf.databinding.ActivityReaderBinding
import java.io.IOException
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private val reader: ReaderViewModel by viewModels()
    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) reader.open(uri)
    }
    private val viewer: ReaderFragment?
        get() = supportFragmentManager.findFragmentById(R.id.document_view) as? ReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }

        binding.openButton.setOnClickListener { choosePdf() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open -> choosePdf()
                R.id.action_search -> viewer?.let { it.isTextSearchActive = !it.isTextSearchActive }
                R.id.action_share -> sharePdf()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = viewer
                if (fragment?.isTextSearchActive == true) {
                    fragment.isTextSearchActive = false
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { reader.state.collect(::render) }
                launch {
                    reader.notices.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
                }
            }
        }
        if (savedInstanceState == null) openFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    internal fun onDocumentOpened(id: String) = reader.documentOpened(id)

    internal fun onDocumentFailed(id: String, cancelled: Boolean) = reader.documentFailed(id, cancelled)

    private fun openFromIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data
        if (uri == null) {
            reader.notify(R.string.error_open)
        } else {
            reader.open(uri)
        }
    }

    private fun choosePdf() {
        try {
            pickPdf.launch(arrayOf("application/pdf"))
        } catch (_: ActivityNotFoundException) {
            reader.notify(R.string.error_open)
        }
    }

    private fun sharePdf() {
        val state = reader.state.value
        val document = state.document ?: return
        if (!state.ready || state.importing) return
        try {
            val share = PdfFiles(this).shareIntent(document)
            startActivity(Intent.createChooser(share, getString(R.string.share_title)))
            reader.keepSharedFile(document)
        } catch (_: IOException) {
            reader.notify(R.string.error_share)
        } catch (_: ActivityNotFoundException) {
            reader.notify(R.string.error_share)
        }
    }

    private fun render(state: ReaderState) {
        val document = state.document
        val currentViewer = viewer
        if (document?.id != currentViewer?.documentId) {
            val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            if (document != null) {
                transaction.replace(R.id.document_view, ReaderFragment.forDocument(document.id))
            } else if (currentViewer != null) {
                transaction.remove(currentViewer)
            }
            transaction.commitNow()
        }

        binding.toolbar.title = document?.displayName ?: getString(R.string.app_name)
        val actions = binding.toolbar.menu
        actions.findItem(R.id.action_search).isVisible = state.ready && !state.importing
        actions.findItem(R.id.action_share).isVisible = state.ready && !state.importing
        binding.documentView.isVisible = document != null && !state.displayError
        binding.emptyPanel.isVisible = (document == null || state.displayError) && !state.importing
        binding.documentError.isVisible = state.displayError
        binding.importProgress.isVisible = state.importing
    }
}
