package com.kf7mxe.inglenook.ebook

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kf7mxe.inglenook.R
import com.kf7mxe.inglenook.components.highlightsToMarkdown
import com.kf7mxe.inglenook.components.toExportedHighlight
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
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.StringSearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.publication.services.search.searchServiceFactory
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_DOWNLOAD_URL = "download_url"
        private const val EXTRA_AUTH_HEADER = "auth_header"
        private const val NAVIGATOR_TAG = "epub_navigator"
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
        private const val DOUBLE_TAP_SLOP_PX = 100f

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

    private var currentPositionTicks: Long = 0L
    private var bookmarksForBook: List<com.kf7mxe.inglenook.Bookmark> = emptyList()

    private var isFullScreen = false
    private var readerForegroundColor: Int = Color.BLACK
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    // Toggles fullscreen on a double tap in the center of the viewport. Registered after
    // DirectionalNavigationAdapter, so edge taps (which turn pages) never reach this listener.
    private val doubleTapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            val now = SystemClock.uptimeMillis()
            val isDoubleTap = now - lastTapTime < DOUBLE_TAP_TIMEOUT_MS &&
                abs(event.point.x - lastTapX) < DOUBLE_TAP_SLOP_PX &&
                abs(event.point.y - lastTapY) < DOUBLE_TAP_SLOP_PX
            lastTapTime = now
            lastTapX = event.point.x
            lastTapY = event.point.y
            if (isDoubleTap) {
                toggleFullScreen()
                return true
            }
            return false
        }
    }

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
            menu.add(0, 1, 0, "Copy")
            menu.add(0, 2, 1, "Select All")
            menu.add(0, 3, 2, "Highlight")
            menu.add(0, 4, 3, "Note")
            menu.add(0, 5, 4, "Define")
            menu.add(0, 6, 5, "Edit")
            selectionActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                1 -> {
                    navigator?.lifecycleScope?.launch {
                        navigator?.evaluateJavascript("document.execCommand('copy')")
                        // Selection is no longer needed; a lingering one blocks later taps.
                        (navigator as? SelectableNavigator)?.clearSelection()
                    }
                    mode.finish()
                    return true
                }
                2 -> { navigator?.lifecycleScope?.launch { navigator?.evaluateJavascript("document.execCommand('selectAll')") }; mode.finish(); return true }
                3 -> { mode.finish(); onHighlightAction(); return true }
                4 -> { mode.finish(); onNoteAction(); return true }
                5 -> { mode.finish(); onDefineAction(); return true }
                6 -> { mode.finish(); onEditAction(); return true }
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
            println("DEBUG decoration activated: $highlightId")
            showHighlightDetailDialog(highlightId)
            return true
        }
    }

    // Installs an idempotent capture-phase click handler inside the WebView that
    // hit-tests decorations in CSS pixels (the same space as clientX/clientY — unlike
    // TapEvent.point, which is physical pixels) and forwards hits through Readium's
    // decoration bridge, bypassing its selection gates and the page-turn zones.
    private val highlightTapHandlerScript = """
        (function() {
            if (window.__hlTapInstalled) return 'already';
            window.__hlTapInstalled = true;
            document.addEventListener('click', function(e) {
                if (!window.getSelection().isCollapsed) return;
                var h = window.readium && window.readium.getDecorations ? window.readium.getDecorations('highlights') : null;
                if (!h || !h.items) return;
                for (var i = 0; i < h.items.length; i++) {
                    var item = h.items[i];
                    var extras = item.decoration && item.decoration.extras;
                    var id = (item.decoration && item.decoration.id) || (extras && extras.highlightId);
                    if (!id) continue;
                    var r = null;
                    var els = item.clickableElements || (item.container ? item.container.children : []);
                    for (var j = 0; j < els.length; j++) {
                        var b = els[j].getBoundingClientRect();
                        if (e.clientX >= b.left && e.clientX <= b.right && e.clientY >= b.top && e.clientY <= b.bottom) { r = b; break; }
                    }
                    if (!r && item.range) {
                        var rb = item.range.getBoundingClientRect();
                        if (e.clientX >= rb.left && e.clientX <= rb.right && e.clientY >= rb.top && e.clientY <= rb.bottom) r = rb;
                    }
                    if (!r) continue;
                    var handled = Android.onDecorationActivated(JSON.stringify({
                        id: id,
                        group: 'highlights',
                        rect: { left: r.left, top: r.top, right: r.right, bottom: r.bottom },
                        click: { defaultPrevented: false, x: e.clientX, y: e.clientY, targetElement: '' }
                    }));
                    if (handled) { e.stopPropagation(); e.preventDefault(); }
                    return;
                }
            }, true);
            return 'installed';
        })()
    """.trimIndent()

    // (Re)installs the handler on the current page — every page load creates a fresh
    // document without the listener. Runs twice (immediately, then after a short delay)
    // to cover navigation timing and reflows settling async.
    private fun installHighlightTapHandler() {
        val nav = navigator ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            for (attempt in 0..1) {
                if (attempt == 1) delay(400)
                try {
                    val result = nav.evaluateJavascript(highlightTapHandlerScript)
                    println("DEBUG highlight tap handler: $result")
                } catch (e: Exception) {
                    println("DEBUG highlight tap handler failed: ${e.message}")
                }
            }
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
        applyReaderChrome()

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
                registerDoubleTapListener()
                loadHighlightsForBook()
                installHighlightTapHandler()
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

    // System bars stay visible in fullscreen; setupWindowInsets() keeps the content padded
    // below them, so text never slides under the status bar camera or the navigation bar.
    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        findViewById<View>(R.id.reader_toolbar).visibility =
            if (isFullScreen) View.GONE else View.VISIBLE
    }

    // Paints the (always transparent on Android 15+) system bar strips and the toolbar with
    // the current reader theme colors, and keeps their icons readable on them.
    private fun applyReaderChrome() {
        val theme = currentPreferences.theme ?: Theme.LIGHT
        val background = when (theme) {
            Theme.DARK -> Color.BLACK
            Theme.SEPIA -> Color.parseColor("#FAF4E8")
            else -> Color.WHITE
        }
        // Readium's own text colors per theme, so the toolbar blends with the page.
        readerForegroundColor = when (theme) {
            Theme.DARK -> Color.parseColor("#FEFEFE")
            Theme.SEPIA -> Color.parseColor("#121212")
            else -> Color.BLACK
        }
        findViewById<View>(android.R.id.content).setBackgroundColor(background)
        findViewById<MaterialCardView>(R.id.reader_toolbar).setCardBackgroundColor(background)
        findViewById<TextView>(R.id.toolbar_title).setTextColor(readerForegroundColor)
        for (id in listOf(R.id.btn_back, R.id.btn_toc, R.id.btn_highlight, R.id.btn_settings, R.id.btn_search)) {
            findViewById<ImageButton>(id).setColorFilter(readerForegroundColor, PorterDuff.Mode.SRC_IN)
        }
        updateBookmarkIcon()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val darkIcons = theme != Theme.DARK
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
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
            toggleBookmark()
        }
        findViewById<ImageButton>(R.id.btn_bookmark).setOnLongClickListener {
            showBookmarksList()
            true
        }

        findViewById<ImageButton>(R.id.btn_highlight).setOnClickListener {
            showNotesListDialog()
        }
        findViewById<ImageButton>(R.id.btn_highlight).setOnLongClickListener {
            showHighlightsList()
            true
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showReaderSettings()
        }

        findViewById<ImageButton>(R.id.btn_search).setOnClickListener {
            showSearchDialog()
        }

        updateBookmarkIcon()
        loadBookmarksForCurrentBook()
    }

    private fun loadBookmarksForCurrentBook() {
        bookmarksForBook = BookmarkRepository.getBookmarksForBook(bookId)
    }

    private fun toggleBookmark() {
        val nav = navigator ?: return
        val locator = nav.currentLocator.value
        val ticks = locatorToTicks(locator)
        val chapterTitle = locator.title

        val existing = bookmarksForBook.find { it.positionTicks == ticks }
        if (existing != null) {
            BookmarkRepository.deleteBookmark(existing._id)
            bookmarksForBook = bookmarksForBook.filter { it._id != existing._id }
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            val bookmark = BookmarkRepository.createBookmark(
                bookId = bookId,
                positionTicks = ticks,
                chapterName = chapterTitle
            )
            bookmarksForBook = (bookmarksForBook + bookmark).sortedBy { it.positionTicks }
            Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
        }
        updateBookmarkIcon()
    }

    private fun updateBookmarkIcon() {
        val btnBookmark = findViewById<ImageButton>(R.id.btn_bookmark)
        val isBookmarked = bookmarksForBook.any { it.positionTicks == currentPositionTicks }
        val tintColor = if (isBookmarked) Color.parseColor("#FFD700") else readerForegroundColor
        btnBookmark.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
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
        connection.setRequestProperty("Authorization", authHeader)
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

        val pub = publicationOpener.open(
            asset,
            allowUserInteraction = true,
            onCreatePublication = {
                // Register Readium's default search service so "Search in book" works.
                servicesBuilder.searchServiceFactory = StringSearchService.createDefaultFactory()
            }
        ).getOrElse { throw Exception("Failed to open publication: $it") }

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
            registerDoubleTapListener()
            loadHighlightsForBook()
            installHighlightTapHandler()
            reportPlaybackStart()
        }
    }

    private fun registerHighlightListener() {
        navigator?.addDecorationListener("highlights", highlightListener)
    }

    private fun registerDoubleTapListener() {
        navigator?.addInputListener(doubleTapListener)
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
            MaterialAlertDialogBuilder(this)
                .setTitle("Table of Contents")
                .setMessage("No table of contents available.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val titles = toc.map { it.title ?: "Untitled" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
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

    // --- Highlights ---

    // Reads the current WebView selection, or null when nothing is selected.
    private suspend fun selectedText(nav: EpubNavigatorFragment): String? =
        nav.evaluateJavascript(
            """(function(){var s=window.getSelection();if(!s||s.isCollapsed||!s.rangeCount)return null;return s.toString()})()"""
        )?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }

    private fun lastHighlightColor(): String =
        getSharedPreferences("reader_prefs", MODE_PRIVATE)
            .getString("last_highlight_color", null) ?: highlightColors.first().first

    private fun saveLastHighlightColor(hex: String) {
        getSharedPreferences("reader_prefs", MODE_PRIVATE)
            .edit().putString("last_highlight_color", hex).apply()
    }

    private fun highlightQuotedText(highlight: com.kf7mxe.inglenook.Highlight): String = try {
        Locator.fromJSON(org.json.JSONObject(highlight.locator))?.text?.highlight ?: ""
    } catch (_: Exception) { "" }

    // Finds an existing highlight whose quoted text matches the given selection.
    private fun findHighlightByText(text: String): com.kf7mxe.inglenook.Highlight? =
        HighlightRepository.getHighlightsForBook(bookId).firstOrNull {
            highlightQuotedText(it).trim() == text.trim()
        }

    private fun selectionFor(nav: EpubNavigatorFragment, text: String): Selection = Selection(
        locator = nav.currentLocator.value.copy(
            text = Locator.Text(highlight = text)
        ),
        rect = null
    )

    // Instantly highlights the selection with the last-used color — no dialogs.
    // If the text is already highlighted, opens its detail dialog instead of duplicating.
    fun onHighlightAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val selText = selectedText(nav)
                if (selText == null) {
                    Toast.makeText(this@ReaderActivity, "Select some text first", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                (nav as? SelectableNavigator)?.clearSelection()
                val existing = findHighlightByText(selText)
                if (existing != null) {
                    showHighlightDetailDialog(existing._id.toString())
                    return@launch
                }
                val color = lastHighlightColor()
                createHighlight(selectionFor(nav, selText), Color.parseColor(color), color, null)
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Ensures the selection is highlighted (last-used color), then opens the shared
    // detail dialog where the note can be added or edited.
    fun onNoteAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val selText = selectedText(nav)
                if (selText == null) {
                    Toast.makeText(this@ReaderActivity, "Select some text first", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                (nav as? SelectableNavigator)?.clearSelection()
                val existing = findHighlightByText(selText)
                    ?: createHighlight(selectionFor(nav, selText), Color.parseColor(lastHighlightColor()), lastHighlightColor(), null)
                if (existing != null) {
                    showHighlightDetailDialog(existing._id.toString())
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Opens the shared detail dialog for a selection that is already highlighted.
    fun onEditAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val selText = selectedText(nav)
                if (selText == null) {
                    Toast.makeText(this@ReaderActivity, "Select some text first", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                (nav as? SelectableNavigator)?.clearSelection()
                val existing = findHighlightByText(selText)
                if (existing != null) {
                    showHighlightDetailDialog(existing._id.toString())
                } else {
                    Toast.makeText(this@ReaderActivity, "This text isn't highlighted yet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onDefineAction() {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val selected = selectedText(nav)
                if (selected != null) {
                    // Selection is no longer needed; a lingering one blocks later taps.
                    (nav as? SelectableNavigator)?.clearSelection()
                    showDefineDialog(selected)
                } else {
                    Toast.makeText(this@ReaderActivity, "Select a word first", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDefineDialog(word: String) {
        val loading = MaterialAlertDialogBuilder(this)
            .setTitle("Define")
            .setMessage("Looking up \"$word\"…")
            .setNegativeButton("Cancel", null)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            val senses = try {
                Dictionary.lookup(this@ReaderActivity, word)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (!loading.isShowing) return@withContext
                loading.dismiss()
                when (senses) {
                    null -> MaterialAlertDialogBuilder(this@ReaderActivity)
                        .setTitle("Define")
                        .setMessage("Couldn't read the dictionary data.")
                        .setPositiveButton("OK", null)
                        .show()
                    else -> if (senses.isEmpty()) {
                        webSearch(word)
                    } else {
                        MaterialAlertDialogBuilder(this@ReaderActivity)
                            .setTitle(word)
                            .setMessage(formatSenses(senses))
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    // Groups senses under part-of-speech headings: "noun\n1. definition\n   "example"".
    private fun formatSenses(senses: List<Dictionary.Sense>): String = buildString {
        senses.firstOrNull { it.pronunciation.isNotBlank() }?.let {
            append('/').append(it.pronunciation).append('\n')
        }
        var currentPos = ""
        var number = 0
        for (sense in senses) {
            if (sense.pos != currentPos) {
                if (isNotEmpty()) append('\n')
                append(sense.pos).append('\n')
                currentPos = sense.pos
                number = 0
            }
            number++
            append(number).append(". ").append(sense.definition)
            sense.example?.let { append("\n   \"").append(it).append('"') }
            append('\n')
        }
    }.trimEnd()

    // Opens "define <word>" in the user's chosen search engine (browser or search app).
    private fun webSearch(word: String) {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        val engine = SearchEngine.entries.firstOrNull { it.name == prefs.getString("search_engine", "Google") }
            ?: SearchEngine.Google
        val custom = prefs.getString("search_engine_custom", "") ?: ""
        val url = searchUrl(engine, custom, "define $word")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to open the search", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForCustomSearchUrl(current: String, onSaved: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(current)
            hint = "https://example.com/search?q=%s"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Search Engine")
            .setMessage("Use %s where the query goes. If omitted, the query is appended.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (isValidSearchTemplate(url)) {
                    onSaved(url)
                } else {
                    Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createHighlight(selection: Selection, colorInt: Int, colorHex: String, note: String?): com.kf7mxe.inglenook.Highlight? {
        val nav = navigator ?: return null
        val selectNav = nav as? SelectableNavigator ?: return null
        val decorNav = nav as? DecorableNavigator ?: return null

        val locator = selection.locator
        val locatorJson = locator.toJSON().toString()

        val created = HighlightRepository.createHighlight(
            bookId = bookId,
            locator = locatorJson,
            color = colorHex,
            note = note,
            chapterName = locator.title
        )

        applyAllHighlights(decorNav)
        selectNav.clearSelection()
        Toast.makeText(this, "Highlight added", Toast.LENGTH_SHORT).show()
        return created
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
                installHighlightTapHandler()
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun loadHighlightsForBook() {
        val nav = navigator ?: return
        val decorNav = nav as? DecorableNavigator ?: return
        applyAllHighlights(decorNav)
    }

    // Shared view/edit dialog for a highlight: quoted text, editable note field and
    // color swatches with Save/Cancel/Delete. Opened by tapping a highlight and by the
    // Note/Edit selection actions.
    @OptIn(ExperimentalUuidApi::class)
    private fun showHighlightDetailDialog(highlightId: String) {
        // Reached from WebView's JavaBridge thread via @JavascriptInterface — dialogs
        // must be shown on the main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { showHighlightDetailDialog(highlightId) }
            return
        }
        val highlight = try {
            HighlightRepository.getHighlight(kotlin.uuid.Uuid.parse(highlightId))
        } catch (e: Exception) { null }
        if (highlight == null) {
            Toast.makeText(this, "Highlight not found", Toast.LENGTH_SHORT).show()
            return
        }

        val quoted = highlightQuotedText(highlight)
        var selectedColor = highlight.color

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8))
        }
        if (quoted.isNotBlank()) {
            view.addView(TextView(this).apply {
                text = "\"$quoted\""
                textSize = 14f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
            })
        }
        view.addView(TextView(this).apply {
            text = "Note"
            textSize = 14f
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        })
        val noteInput = EditText(this).apply {
            setText(highlight.note ?: "")
            hint = "Add a note"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 14f
            minLines = 2
            maxLines = 5
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        view.addView(
            noteInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        view.addView(TextView(this).apply {
            text = "Color"
            textSize = 14f
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        })

        // Swatch row: the checkmark marks the selection; colors only commit on Save.
        val circleSize = dpToPx(42)
        val checks = LinkedHashMap<String, TextView>()
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, dpToPx(4), 0, 0)
            for ((hex, colorInt) in highlightColors) {
                val check = TextView(this@ReaderActivity).apply {
                    text = "✓"
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(contrastingTextColor(colorInt))
                    visibility = if (hex.equals(selectedColor, ignoreCase = true)) View.VISIBLE else View.INVISIBLE
                }
                checks[hex] = check
                addView(android.widget.FrameLayout(this@ReaderActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                        marginStart = dpToPx(6)
                        marginEnd = dpToPx(6)
                    }
                    addView(View(this@ReaderActivity).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundResource(R.drawable.color_circle)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                    })
                    addView(
                        check,
                        android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER
                        )
                    )
                    setOnClickListener {
                        selectedColor = hex
                        checks.forEach { (h, c) ->
                            c.visibility = if (h.equals(hex, ignoreCase = true)) View.VISIBLE else View.INVISIBLE
                        }
                    }
                })
            }
        }
        view.addView(swatchRow)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (highlight.note.isNullOrBlank()) "Highlight" else "Note")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val note = noteInput.text.toString().trim().ifBlank { null }
                HighlightRepository.updateHighlight(highlight.copy(note = note, color = selectedColor))
                saveLastHighlightColor(selectedColor)
                loadHighlightsForBook()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete")
                    .setMessage(
                        if (highlight.note.isNullOrBlank()) "Delete this highlight?"
                        else "Delete this highlight and its note?"
                    )
                    .setPositiveButton("Delete") { _, _ ->
                        deleteHighlight(highlight._id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    // Black or white — whichever reads on [colorInt]; used for the swatch checkmark.
    private fun contrastingTextColor(colorInt: Int): Int {
        val luminance = Color.red(colorInt) * 0.299 + Color.green(colorInt) * 0.588 + Color.blue(colorInt) * 0.114
        return if (luminance > 150) Color.BLACK else Color.WHITE
    }

    // --- Notes List ---

    // All notes in the book, nearest to the current reading position first, with the
    // list opened scrolled to the closest one. Tapping a row opens the detail dialog.
    private fun showNotesListDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val notes = HighlightRepository.getHighlightsForBook(bookId)
                .filter { !it.note.isNullOrBlank() }
            withContext(Dispatchers.Main) {
                if (notes.isEmpty()) {
                    MaterialAlertDialogBuilder(this@ReaderActivity)
                        .setTitle("Notes")
                        .setMessage("No notes yet.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@withContext
                }
                val (ordered, nearestIndex) = notesNearestFirst(notes)

                val rowViews = ArrayList<View>()
                val container = LinearLayout(this@ReaderActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                for (highlight in ordered) {
                    val row = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                        isClickable = true
                        setOnClickListener { navigateToHighlight(highlight) }
                    }
                    val textColumn = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val snippet = highlightQuotedText(highlight).replace('\n', ' ')
                    textColumn.addView(TextView(this@ReaderActivity).apply {
                        text = if (snippet.isBlank()) (highlight.note ?: "") else "“$snippet”"
                        textSize = 14f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    textColumn.addView(TextView(this@ReaderActivity).apply {
                        text = highlight.note ?: ""
                        textSize = 13f
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, dpToPx(2), 0, 0)
                    })
                    highlight.chapterName?.takeIf { it.isNotBlank() }?.let { chapter ->
                        textColumn.addView(TextView(this@ReaderActivity).apply {
                            text = chapter
                            textSize = 11f
                            setTextColor(Color.parseColor("#999999"))
                            maxLines = 1
                            setPadding(0, dpToPx(2), 0, 0)
                        })
                    }
                    row.addView(textColumn)
                    row.addView(android.widget.Button(this@ReaderActivity).apply {
                        text = "Edit"
                        setOnClickListener {
                            showHighlightDetailDialog(highlight._id.toString())
                        }
                    })
                    container.addView(row)
                    container.addView(View(this@ReaderActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(Color.parseColor("#E0E0E0"))
                    })
                    rowViews.add(row)
                }

                val scrollView = ScrollView(this@ReaderActivity).apply { addView(container) }

                MaterialAlertDialogBuilder(this@ReaderActivity)
                    .setTitle("Notes (${ordered.size})")
                    .setMessage("Tap a note to jump to it.")
                    .setView(scrollView)
                    .setPositiveButton("Close", null)
                    .show()

                container.post {
                    // Cap the height so a long list can't push the Close button off-screen.
                    val max = dpToPx(480)
                    if (container.height > max) {
                        scrollView.layoutParams?.let {
                            it.height = max
                            scrollView.layoutParams = it
                        }
                    }
                    rowViews.getOrNull(nearestIndex)?.let { scrollView.scrollTo(0, it.top) }
                }
            }
        }
    }

    // Sorts notes by distance from the current reading position — locator progression
    // when available, current-chapter match as fallback — returning the nearest index.
    private fun notesNearestFirst(notes: List<com.kf7mxe.inglenook.Highlight>): Pair<List<com.kf7mxe.inglenook.Highlight>, Int> {
        val currentProgression =
            if (bookDuration > 0) currentPositionTicks.toDouble() / bookDuration else null
        val currentChapter = lastReportedLocator?.title

        fun distance(highlight: com.kf7mxe.inglenook.Highlight): Double {
            val progression = try {
                Locator.fromJSON(org.json.JSONObject(highlight.locator))?.locations?.totalProgression
            } catch (e: Exception) { null }
            if (progression != null && currentProgression != null) return abs(progression - currentProgression)
            if (currentChapter != null && highlight.chapterName == currentChapter) return 0.0
            return 1.0
        }

        val sorted = notes.sortedBy { distance(it) }
        val nearest = sorted.indexOfFirst { distance(it) < 1.0 }.let { if (it >= 0) it else 0 }
        return sorted to nearest
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun deleteHighlight(highlightId: kotlin.uuid.Uuid) {
        HighlightRepository.deleteHighlight(highlightId)
        loadHighlightsForBook()
        Toast.makeText(this, "Highlight deleted", Toast.LENGTH_SHORT).show()
    }

    // --- Highlights List ---

    @OptIn(ExperimentalUuidApi::class)
    private fun showHighlightsList() {
        val nav = navigator ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val highlights = HighlightRepository.getHighlightsForBook(bookId)
            withContext(Dispatchers.Main) {
                if (highlights.isEmpty()) {
                    MaterialAlertDialogBuilder(this@ReaderActivity)
                        .setTitle("Highlights")
                        .setMessage("No highlights yet. Select text and tap Highlight to add one.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@withContext
                }

                var dialog: androidx.appcompat.app.AlertDialog? = null

                val container = LinearLayout(this@ReaderActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }

                for (highlight in highlights) {
                    val snippet = try {
                        val loc = Locator.fromJSON(org.json.JSONObject(highlight.locator))
                        loc?.text?.highlight?.take(120) ?: "(no text)"
                    } catch (_: Exception) { "(no text)" }

                    val colorInt = try { Color.parseColor(highlight.color) } catch (_: Exception) { Color.YELLOW }
                    val chapter = highlight.chapterName ?: ""

                    val row = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                        setOnClickListener {
                            dialog?.dismiss()
                            navigateToHighlight(highlight)
                        }
                    }

                    row.addView(View(this@ReaderActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply {
                            marginEnd = dpToPx(10)
                        }
                        setBackgroundResource(R.drawable.color_circle)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                    })

                    val textColumn = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    textColumn.addView(TextView(this@ReaderActivity).apply {
                        text = snippet
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        textSize = 14f
                    })

                    if (chapter.isNotBlank()) {
                        textColumn.addView(TextView(this@ReaderActivity).apply {
                            text = chapter
                            textSize = 11f
                            setTextColor(Color.parseColor("#999999"))
                            maxLines = 1
                        })
                    }

                    row.addView(textColumn)

                    if (highlight.note != null) {
                        row.addView(TextView(this@ReaderActivity).apply {
                            text = "📝"
                            textSize = 14f
                            setPadding(dpToPx(6), 0, 0, 0)
                        })
                    }

                    row.addView(TextView(this@ReaderActivity).apply {
                        text = "✕"
                        textSize = 16f
                        setTextColor(Color.parseColor("#999999"))
                        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                        setOnClickListener {
                            MaterialAlertDialogBuilder(this@ReaderActivity)
                                .setTitle("Delete Highlight")
                                .setMessage("Delete this highlight?")
                                .setPositiveButton("Delete") { _, _ ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        HighlightRepository.deleteHighlight(highlight._id)
                                        withContext(Dispatchers.Main) {
                                            loadHighlightsForBook()
                                            Toast.makeText(this@ReaderActivity, "Highlight deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    })

                    container.addView(row)
                    container.addView(View(this@ReaderActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(Color.parseColor("#E0E0E0"))
                    })
                }

                dialog = MaterialAlertDialogBuilder(this@ReaderActivity)
                    .setTitle("Highlights (${highlights.size})")
                    .setView(container)
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Export") { _, _ -> exportHighlights() }
                    .setNeutralButton("Clear All") { _, _ ->
                        MaterialAlertDialogBuilder(this@ReaderActivity)
                            .setTitle("Clear All Highlights")
                            .setMessage("Are you sure you want to delete all highlights for this book?")
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    HighlightRepository.deleteHighlightsForBook(bookId)
                                    withContext(Dispatchers.Main) {
                                        loadHighlightsForBook()
                                        Toast.makeText(this@ReaderActivity, "All highlights deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .show()
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun navigateToHighlight(highlight: com.kf7mxe.inglenook.Highlight) {
        val nav = navigator ?: return
        lifecycleScope.launch {
            try {
                val locator = Locator.fromJSON(org.json.JSONObject(highlight.locator))
                if (locator != null) {
                    nav.go(locator)
                } else {
                    Toast.makeText(this@ReaderActivity, "Could not navigate to highlight", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Bookmarks List ---

    private fun showBookmarksList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarks = BookmarkRepository.getBookmarksForBook(bookId)
            withContext(Dispatchers.Main) {
                if (bookmarks.isEmpty()) {
                    MaterialAlertDialogBuilder(this@ReaderActivity)
                        .setTitle("Bookmarks")
                        .setMessage("No bookmarks yet. Tap the bookmark icon to add one.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@withContext
                }

                var dialog: androidx.appcompat.app.AlertDialog? = null

                val container = LinearLayout(this@ReaderActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }

                for (bookmark in bookmarks) {
                    val row = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                    }

                    row.addView(TextView(this@ReaderActivity).apply {
                        text = "🔖"
                        textSize = 18f
                        setPadding(0, 0, dpToPx(12), 0)
                    })

                    val textColumn = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    textColumn.addView(TextView(this@ReaderActivity).apply {
                        text = bookmark.chapterName ?: "Position ${bookmark.positionTicks}"
                        textSize = 14f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })

                    textColumn.addView(TextView(this@ReaderActivity).apply {
                        text = if (bookmark.note != null) bookmark.note else ""
                        textSize = 11f
                        setTextColor(Color.parseColor("#999999"))
                        maxLines = 1
                    })

                    row.addView(textColumn)

                    row.addView(TextView(this@ReaderActivity).apply {
                        text = "✕"
                        textSize = 16f
                        setTextColor(Color.parseColor("#999999"))
                        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                        setOnClickListener {
                            MaterialAlertDialogBuilder(this@ReaderActivity)
                                .setTitle("Delete Bookmark")
                                .setMessage("Delete this bookmark?")
                                .setPositiveButton("Delete") { _, _ ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        BookmarkRepository.deleteBookmark(bookmark._id)
                                        withContext(Dispatchers.Main) {
                                            bookmarksForBook = bookmarks.filter { it._id != bookmark._id }
                                            updateBookmarkIcon()
                                            Toast.makeText(this@ReaderActivity, "Bookmark deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    })

                    container.addView(row)
                    container.addView(View(this@ReaderActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(Color.parseColor("#E0E0E0"))
                    })
                }

                dialog = MaterialAlertDialogBuilder(this@ReaderActivity)
                    .setTitle("Bookmarks (${bookmarks.size})")
                    .setView(container)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Clear All") { _, _ ->
                        MaterialAlertDialogBuilder(this@ReaderActivity)
                            .setTitle("Clear All Bookmarks")
                            .setMessage("Are you sure you want to delete all bookmarks for this book?")
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    BookmarkRepository.deleteBookmarksForBook(bookId)
                                    withContext(Dispatchers.Main) {
                                        bookmarksForBook = emptyList()
                                        updateBookmarkIcon()
                                        Toast.makeText(this@ReaderActivity, "All bookmarks deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .show()
            }
        }
    }

    // Shares this book's highlights and notes as Markdown via the system share sheet.
    private fun exportHighlights() {
        val title = bookTitle
        val highlights = HighlightRepository.getHighlightsForBook(bookId)
            .mapNotNull { it.toExportedHighlight() }
        if (highlights.isEmpty()) {
            Toast.makeText(this, "No highlights to export", Toast.LENGTH_SHORT).show()
            return
        }
        val markdown = highlightsToMarkdown(title, highlights)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$title — highlights")
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        try {
            startActivity(Intent.createChooser(send, "Export highlights"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Search in book ---

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Search in this book"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Search")
            .setView(input)
            .setPositiveButton("Search") { _, _ -> runBookSearch(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Searches the whole book via Readium's SearchService and shows up to 50 matches.
    private fun runBookSearch(query: String) {
        val pub = publication ?: return
        if (query.isBlank()) return
        val loading = MaterialAlertDialogBuilder(this)
            .setTitle("Search")
            .setMessage("Searching for \"$query\"…")
            .setNegativeButton("Cancel", null)
            .show()
        lifecycleScope.launch(Dispatchers.IO) {
            val results = ArrayList<Locator>()
            val iterator = pub.search(query)
            if (iterator == null) {
                withContext(Dispatchers.Main) {
                    if (loading.isShowing) {
                        loading.dismiss()
                        Toast.makeText(this@ReaderActivity, "Search is not available for this book", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }
            try {
                while (results.size < 50) {
                    val page = iterator.next().getOrNull() ?: break
                    results.addAll(page.locators)
                }
            } finally {
                iterator.close()
            }
            withContext(Dispatchers.Main) {
                // Skip if the user cancelled while the search was running.
                if (!loading.isShowing) return@withContext
                loading.dismiss()
                showSearchResults(results)
            }
        }
    }

    private fun showSearchResults(results: List<Locator>) {
        if (results.isEmpty()) {
            Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = results.map { locator ->
            listOfNotNull(locator.title, locator.text?.highlight)
                .joinToString(" — ")
                .ifBlank { "Result" }
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Results (${results.size})")
            .setItems(labels) { _, which ->
                val locator = results[which]
                lifecycleScope.launch { navigator?.go(locator) }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // --- Reader Settings ---

    private fun showReaderSettings() {
        val nav = navigator ?: return
        val editor = navigatorFactory?.createPreferencesEditor(currentPreferences) ?: return

        var settingsDialog: androidx.appcompat.app.AlertDialog? = null

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
                        installHighlightTapHandler()
                    }
                })
                addView(android.widget.Button(context).apply {
                    text = "A+"
                    setOnClickListener {
                        editor.fontSize.increment()
                        currentPreferences = editor.preferences
                        nav.submitPreferences(currentPreferences)
                        savePreferences()
                        installHighlightTapHandler()
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
                            applyReaderChrome()
                            installHighlightTapHandler()
                        }
                    })
                }
            })

            addView(TextView(context).apply {
                text = "Search Engine for Dictionary lookups"
                textSize = 16f
                setPadding(0, 24, 0, 8)
            })

            run {
                val enginePrefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
                var selected = enginePrefs.getString("search_engine", SearchEngine.Google.name)
                    ?: SearchEngine.Google.name
                val storedCustom = enginePrefs.getString("search_engine_custom", "") ?: ""
                if (selected == SearchEngine.Custom.name && !isValidSearchTemplate(storedCustom)) {
                    selected = SearchEngine.Google.name
                }
                val engineButtons = ArrayList<Pair<SearchEngine, android.widget.Button>>()
                fun renderEngineButtons() {
                    for ((engine, button) in engineButtons) {
                        button.alpha = if (engine.name == selected) 1.0f else 0.4f
                    }
                }
                fun addButton(row: LinearLayout, engine: SearchEngine) {
                    val button = android.widget.Button(context).apply {
                        text = engine.displayName
                        setOnClickListener {
                            if (engine == SearchEngine.Custom) {
                                val currentTemplate =
                                    enginePrefs.getString("search_engine_custom", "") ?: ""
                                promptForCustomSearchUrl(currentTemplate) { url ->
                                    enginePrefs.edit()
                                        .putString("search_engine_custom", url)
                                        .putString("search_engine", SearchEngine.Custom.name)
                                        .apply()
                                    selected = SearchEngine.Custom.name
                                    renderEngineButtons()
                                }
                            } else {
                                selected = engine.name
                                enginePrefs.edit().putString("search_engine", engine.name).apply()
                                renderEngineButtons()
                            }
                        }
                    }
                    engineButtons.add(engine to button)
                    row.addView(button)
                }

                val presetRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                for (engine in SearchEngine.entries.filter { it != SearchEngine.Custom }) {
                    addButton(presetRow, engine)
                }
                addView(presetRow)

                val customRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                addButton(customRow, SearchEngine.Custom)
                addView(customRow)
                renderEngineButtons()
            }

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
                        installHighlightTapHandler()
                    }
                })
            })

            addView(android.widget.Button(context).apply {
                text = "Highlights & Notes"
                setOnClickListener {
                    settingsDialog?.dismiss()
                    showHighlightsList()
                }
            })
        }

        settingsDialog = MaterialAlertDialogBuilder(this)
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
                    currentPositionTicks = locatorToTicks(locator)
                    val chapterTitle = locator.title
                    if (!chapterTitle.isNullOrBlank()) {
                        findViewById<TextView>(R.id.toolbar_title)?.text = chapterTitle
                    }
                    updateBookmarkIcon()
                    installHighlightTapHandler()
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
