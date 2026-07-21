package com.kf7mxe.inglenook.ebook

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.kf7mxe.inglenook.R
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.HighlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class ReaderActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_DOWNLOAD_URL = "download_url"
        private const val EXTRA_AUTH_HEADER = "auth_header"
        private const val NAVIGATOR_TAG = "epub_navigator"

        fun createIntent(
            context: Context,
            bookId: String,
            downloadUrl: String,
            authHeader: String
        ): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                putExtra(EXTRA_AUTH_HEADER, authHeader)
            }
        }
    }

    private lateinit var bookId: String
    private lateinit var downloadUrl: String
    private lateinit var authHeader: String
    private var bookTitle: String = "Book"
    private var bookDuration: Long = 0L

    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var navigatorFactory: EpubNavigatorFactory? = null
    private var positionReportingJob: Job? = null
    private var lastReportedLocator: Locator? = null
    private var currentPreferences = EpubPreferences()

    private var selectionActionMode: ActionMode? = null

    private val highlightColors = listOf(
        "#FFFFEB3B" to Color.parseColor("#FFFFEB3B"),
        "#4CAF50" to Color.parseColor("#4CAF50"),
        "#2196F3" to Color.parseColor("#2196F3"),
        "#E91E63" to Color.parseColor("#E91E63"),
        "#FF9800" to Color.parseColor("#FF9800")
    )

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
            menu.add(0, 1, 0, "Copy")
            menu.add(0, 2, 1, "Select All")
            menu.add(0, 3, 2, "Highlight")
            menu.add(0, 4, 3, "Note")
            selectionActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                1 -> { navigator?.lifecycleScope?.launch { navigator?.evaluateJavascript("document.execCommand('copy')") }; mode.finish(); return true }
                2 -> { navigator?.lifecycleScope?.launch { navigator?.evaluateJavascript("document.execCommand('selectAll')") }; mode.finish(); return true }
                3 -> { mode.finish(); onHighlightAction(); return true }
                4 -> { mode.finish(); onNoteAction(); return true }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectionActionMode = null
        }
    }

    private val highlightListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val highlightId = event.decoration.extras["highlightId"] as? String ?: return false
            showHighlightTapDialog(highlightId)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val factory = navigatorFactory
        if (factory != null) {
            val fragmentConfiguration = EpubNavigatorFragment.Configuration(
                selectionActionModeCallback = selectionActionModeCallback
            )
            supportFragmentManager.fragmentFactory =
                factory.createFragmentFactory(
                    initialLocator = null,
                    initialPreferences = currentPreferences,
                    configuration = fragmentConfiguration
                )
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_reader)
        setupWindowInsets()

        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: run { finish(); return }
        authHeader = intent.getStringExtra(EXTRA_AUTH_HEADER) ?: run { finish(); return }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val book = jellyfinClient.value?.getBook(bookId)
                if (book != null) {
                    bookTitle = book.title
                    bookDuration = book.duration
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.toolbar_title)?.text = bookTitle
                    }
                }
            } catch (_: Exception) { }
        }

        loadPreferences()
        setupToolbar()

        if (savedInstanceState == null) {
            downloadAndOpenBook()
        } else {
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            if (navigator != null) {
                showReaderContent()
                startPositionTracking()
                registerHighlightListener()
                loadHighlightsForBook()
            } else {
                downloadAndOpenBook()
            }
        }
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        findViewById<TextView>(R.id.toolbar_title).text = bookTitle

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btn_toc).setOnClickListener {
            showTableOfContents()
        }

        findViewById<ImageButton>(R.id.btn_bookmark).setOnClickListener {
            addBookmark()
        }

        findViewById<ImageButton>(R.id.btn_highlight).setOnClickListener {
            onHighlightAction()
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showReaderSettings()
        }
    }

    private fun downloadAndOpenBook() {
        lifecycleScope.launch {
            try {
                updateLoadingText("Downloading ebook...")
                val epubFile = downloadEpub()
                updateLoadingText("Opening ebook...")
                openWithReadium(epubFile)
            } catch (e: Exception) {
                updateLoadingText("Error: ${e.message}")
            }
        }
    }

    private suspend fun downloadEpub(): File = withContext(Dispatchers.IO) {
        val booksDir = File(cacheDir, "books")
        booksDir.mkdirs()
        val epubFile = File(booksDir, "$bookId.epub")
        if (epubFile.exists() && epubFile.length() > 0) return@withContext epubFile

        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("X-Emby-Authorization", authHeader)
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(epubFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        } finally {
            connection.disconnect()
        }
        epubFile
    }

    private suspend fun openWithReadium(epubFile: File) {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(contentResolver, httpClient)
        val publicationParser = DefaultPublicationParser(
            this@ReaderActivity,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
        val publicationOpener = PublicationOpener(publicationParser)

        val url = epubFile.toURI().toURL().let {
            org.readium.r2.shared.util.AbsoluteUrl(it.toString())
        }
        val asset = assetRetriever.retrieve(url!!)
            .getOrElse { throw Exception("Failed to retrieve asset: $it") }

        val pub = publicationOpener.open(asset, allowUserInteraction = true)
            .getOrElse { throw Exception("Failed to open publication: $it") }

        publication = pub

        val factory = EpubNavigatorFactory(
            publication = pub,
            configuration = EpubNavigatorFactory.Configuration(
                defaults = EpubDefaults(pageMargins = 1.5)
            )
        )
        navigatorFactory = factory

        val initialLocator = restoreSavedLocator(pub)

        val fragmentConfiguration = EpubNavigatorFragment.Configuration(
            selectionActionModeCallback = selectionActionModeCallback
        )

        withContext(Dispatchers.Main) {
            supportFragmentManager.fragmentFactory =
                factory.createFragmentFactory(
                    initialLocator = initialLocator,
                    initialPreferences = currentPreferences,
                    configuration = fragmentConfiguration
                )

            supportFragmentManager.commitNow {
                add(R.id.navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }

            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment

            navigator?.let { nav ->
                nav.addInputListener(DirectionalNavigationAdapter(nav))
            }

            showReaderContent()
            startPositionTracking()
            registerHighlightListener()
            loadHighlightsForBook()
            reportPlaybackStart()
        }
    }

    private fun registerHighlightListener() {
        navigator?.addDecorationListener("highlights", highlightListener)
    }

    private fun restoreSavedLocator(pub: Publication): Locator? {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        val locatorJson = prefs.getString("ebook_locator_$bookId", null) ?: return null
        return try {
            Locator.fromJSON(org.json.JSONObject(locatorJson))
        } catch (_: Exception) { null }
    }

    private fun showReaderContent() {
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.reader_content).visibility = View.VISIBLE
    }

    private fun updateLoadingText(text: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            findViewById<TextView>(R.id.loading_text)?.text = text
        }
    }

    // --- Table of Contents ---

    private fun showTableOfContents() {
        val pub = publication ?: return
        val toc = pub.tableOfContents
        if (toc.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Table of Contents")
                .setMessage("No table of contents available.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val titles = toc.map { it.title ?: "Untitled" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Table of Contents")
            .setItems(titles) { _, which ->
                val link = toc[which]
                lifecycleScope.launch {
                    val locator = pub.locatorFromLink(link)
                    if (locator != null) navigator?.go(locator)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Bookmarks ---

    private fun addBookmark() {
        val nav = navigator ?: return
        val locator = nav.currentLocator.value
        val positionTicks = locatorToTicks(locator)
        val chapterTitle = locator.title

        BookmarkRepository.createBookmark(
            bookId = bookId,
            positionTicks = positionTicks,
            chapterName = chapterTitle
        )
        Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
    }

    // --- Highlights (text selection based) ---

    fun onHighlightAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val locator = nav.currentLocator.value
                val selText = nav.evaluateJavascript(
                    """(function(){var s=window.getSelection();if(!s||s.isCollapsed||!s.rangeCount)return null;return s.toString()})()"""
                )?.trim('"')?.takeIf { it.isNotBlank() }
                if (selText != null) {
                    val selection = Selection(
                        locator = locator.copy(text = org.readium.r2.shared.publication.Locator.Text(highlight = selText)),
                        rect = null
                    )
                    showHighlightDialog(selection)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReaderActivity, "Select some text first", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onNoteAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val locator = nav.currentLocator.value
                val selText = nav.evaluateJavascript(
                    """(function(){var s=window.getSelection();if(!s||s.isCollapsed||!s.rangeCount)return null;return s.toString()})()"""
                )?.trim('"')?.takeIf { it.isNotBlank() }
                if (selText != null) {
                    val selection = Selection(
                        locator = locator.copy(text = org.readium.r2.shared.publication.Locator.Text(highlight = selText)),
                        rect = null
                    )
                    showHighlightDialog(selection, showNoteInput = true)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReaderActivity, "Select some text first", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun showHighlightDialog(selection: Selection, showNoteInput: Boolean = false) {
        val colors = highlightColors
        val colorNames = colors.map { (hex, _) -> hex }.toTypedArray()

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
        }

        for ((hex, colorInt) in colors) {
            val circle = View(this).apply {
                val size = dpToPx(40)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = dpToPx(6)
                    marginEnd = dpToPx(6)
                }
                setBackgroundResource(R.drawable.color_circle)
                backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                setOnClickListener {
                    showNoteDialog(selection, colorInt, hex)
                }
            }
            dialogView.addView(circle)
        }

        val noteBtn = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).apply {
                marginStart = dpToPx(12)
            }
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(Color.DKGRAY)
            setOnClickListener {
                showNoteDialog(selection, colors.first().second, colors.first().first)
            }
        }
        dialogView.addView(noteBtn)

        if (showNoteInput) {
            showNoteDialog(selection, colors.first().second, colors.first().first)
        } else {
            AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Cancel") { _, _ ->
                    (navigator as? SelectableNavigator)?.clearSelection()
                }
                .setOnCancelListener {
                    (navigator as? SelectableNavigator)?.clearSelection()
                }
                .show()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun showNoteDialog(selection: Selection, colorInt: Int, colorHex: String) {
        val input = EditText(this).apply {
            hint = "Add a note (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(48, 32, 48, 32)
            minLines = 2
        }

        AlertDialog.Builder(this)
            .setTitle("Add Note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val note = input.text.toString().takeIf { it.isNotBlank() }
                createHighlight(selection, colorInt, colorHex, note)
            }
            .setNegativeButton("Skip") { _, _ ->
                createHighlight(selection, colorInt, colorHex, null)
            }
            .setNeutralButton("Cancel") { _, _ ->
                (navigator as? SelectableNavigator)?.clearSelection()
            }
            .setOnCancelListener {
                (navigator as? SelectableNavigator)?.clearSelection()
            }
            .show()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createHighlight(selection: Selection, colorInt: Int, colorHex: String, note: String?) {
        val nav = navigator ?: return
        val selectNav = nav as? SelectableNavigator ?: return
        val decorNav = nav as? DecorableNavigator ?: return

        val locator = selection.locator
        val locatorJson = locator.toJSON().toString()

        HighlightRepository.createHighlight(
            bookId = bookId,
            locator = locatorJson,
            color = colorHex,
            note = note,
            chapterName = locator.title
        )

        applyAllHighlights(decorNav)
        selectNav.clearSelection()
        Toast.makeText(this, "Highlight added", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun applyAllHighlights(decorNav: DecorableNavigator) {
        lifecycleScope.launch(Dispatchers.IO) {
            val highlights = HighlightRepository.getHighlightsForBook(bookId)
            val decorations = highlights.mapNotNull { highlight ->
                try {
                    val locator = Locator.fromJSON(org.json.JSONObject(highlight.locator))
                    locator?.let {
                        Decoration(
                            id = highlight._id.toString(),
                            locator = it,
                            style = Decoration.Style.Highlight(
                                tint = android.graphics.Color.parseColor(highlight.color)
                            ),
                            extras = mapOf("highlightId" to highlight._id.toString())
                        )
                    }
                } catch (e: Exception) { null }
            }

            withContext(Dispatchers.Main) {
                decorNav.applyDecorations(decorations, "highlights")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun loadHighlightsForBook() {
        val nav = navigator ?: return
        val decorNav = nav as? DecorableNavigator ?: return
        applyAllHighlights(decorNav)
    }

    private fun showHighlightTapDialog(highlightId: String) {
        val highlight = try {
            HighlightRepository.getHighlight(kotlin.uuid.Uuid.parse(highlightId))
        } catch (e: Exception) { null }

        if (highlight == null) {
            Toast.makeText(this, "Highlight not found", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        if (highlight.note != null) options.add("View Note")
        options.add("Edit Note")
        options.add("Change Color")
        options.add("Delete")

        AlertDialog.Builder(this)
            .setTitle("Highlight")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "View Note" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Note")
                            .setMessage(highlight.note ?: "No note")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    "Edit Note" -> showEditNoteDialog(highlight)
                    "Change Color" -> showChangeColorDialog(highlight)
                    "Delete" -> {
                        HighlightRepository.deleteHighlight(highlight._id)
                        loadHighlightsForBook()
                        Toast.makeText(this, "Highlight deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun showEditNoteDialog(highlight: com.kf7mxe.inglenook.Highlight) {
        val input = EditText(this).apply {
            setText(highlight.note ?: "")
            hint = "Add a note"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(48, 32, 48, 32)
            minLines = 2
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val note = input.text.toString().takeIf { it.isNotBlank() }
                HighlightRepository.updateHighlight(highlight.copy(note = note))
                Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun showChangeColorDialog(highlight: com.kf7mxe.inglenook.Highlight) {
        val colors = arrayOf("Yellow", "Green", "Blue", "Pink", "Orange")
        val colorHexValues = arrayOf("#FFFF00", "#00FF00", "#0000FF", "#FF00FF", "#FFA500")

        AlertDialog.Builder(this)
            .setTitle("Change Color")
            .setItems(colors) { _, which ->
                HighlightRepository.updateHighlight(highlight.copy(color = colorHexValues[which]))
                loadHighlightsForBook()
                Toast.makeText(this, "Color updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // --- Reader Settings ---

    private fun showReaderSettings() {
        val nav = navigator ?: return
        val editor = navigatorFactory?.createPreferencesEditor(currentPreferences) ?: return

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)

            addView(TextView(context).apply {
                text = "Font Size"
                textSize = 16f
                setPadding(0, 16, 0, 8)
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(android.widget.Button(context).apply {
                    text = "A-"
                    setOnClickListener {
                        editor.fontSize.decrement()
                        currentPreferences = editor.preferences
                        nav.submitPreferences(currentPreferences)
                        savePreferences()
                    }
                })
                addView(android.widget.Button(context).apply {
                    text = "A+"
                    setOnClickListener {
                        editor.fontSize.increment()
                        currentPreferences = editor.preferences
                        nav.submitPreferences(currentPreferences)
                        savePreferences()
                    }
                })
            })

            addView(TextView(context).apply {
                text = "Theme"
                textSize = 16f
                setPadding(0, 24, 0, 8)
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for (theme in listOf(
                    "Light" to org.readium.r2.navigator.preferences.Theme.LIGHT,
                    "Dark" to org.readium.r2.navigator.preferences.Theme.DARK,
                    "Sepia" to org.readium.r2.navigator.preferences.Theme.SEPIA
                )) {
                    addView(android.widget.Button(context).apply {
                        text = theme.first
                        setOnClickListener {
                            editor.theme.set(theme.second)
                            currentPreferences = editor.preferences
                            nav.submitPreferences(currentPreferences)
                            savePreferences()
                        }
                    })
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 24, 0, 0)
                addView(TextView(context).apply {
                    text = "Scroll Mode"
                    textSize = 16f
                })
                addView(android.widget.Switch(context).apply {
                    isChecked = editor.scroll.value ?: editor.scroll.effectiveValue
                    setOnCheckedChangeListener { _, isChecked ->
                        editor.scroll.set(isChecked)
                        currentPreferences = editor.preferences
                        nav.submitPreferences(currentPreferences)
                        savePreferences()
                    }
                })
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Reader Settings")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }

    // --- Position Tracking ---

    private fun saveLocator(locator: Locator) {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        prefs.edit().putString("ebook_locator_$bookId", locator.toJSON().toString()).apply()
    }

    private fun startPositionTracking() {
        val nav = navigator ?: return
        positionReportingJob?.cancel()
        positionReportingJob = lifecycleScope.launch {
            nav.currentLocator
                .onEach { locator ->
                    lastReportedLocator = locator
                    saveLocator(locator)
                    val chapterTitle = locator.title
                    if (!chapterTitle.isNullOrBlank()) {
                        findViewById<TextView>(R.id.toolbar_title)?.text = chapterTitle
                    }
                }
                .launchIn(this)

            while (isActive) {
                delay(30_000)
                reportProgress()
            }
        }
    }

    private fun reportPlaybackStart() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val locator = lastReportedLocator
                val ticks = if (locator != null) locatorToTicks(locator) else 0L
                jellyfinClient.value?.reportPlaybackStart(bookId, ticks)
            } catch (_: Exception) { }
        }
    }

    private fun reportProgress() {
        val locator = lastReportedLocator ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ticks = locatorToTicks(locator)
                jellyfinClient.value?.reportPlaybackProgress(bookId, ticks, false)
            } catch (_: Exception) { }
        }
    }

    private fun reportPlaybackStopped() {
        val locator = lastReportedLocator ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ticks = locatorToTicks(locator)
                jellyfinClient.value?.reportPlaybackStopped(bookId, ticks)
            } catch (_: Exception) { }
        }
    }

    private fun locatorToTicks(locator: Locator): Long {
        val progression = locator.locations.totalProgression ?: 0.0
        return if (bookDuration > 0) {
            (progression * bookDuration).toLong()
        } else {
            (progression * 10_000_000_000L).toLong()
        }
    }

    // --- Preferences Persistence ---

    private fun loadPreferences() {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        val json = prefs.getString("epub_preferences_$bookId", null)
            ?: prefs.getString("epub_preferences_default", null)
        if (json != null) {
            try { } catch (e: Exception) { }
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        prefs.edit().apply { apply() }
    }

    // --- Lifecycle ---

    override fun onStop() {
        super.onStop()
        reportProgress()
    }

    override fun onDestroy() {
        positionReportingJob?.cancel()
        navigator?.removeDecorationListener(highlightListener)
        reportPlaybackStopped()
        super.onDestroy()
    }
}
