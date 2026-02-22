package com.kf7mxe.inglenook.ebook

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.kf7mxe.inglenook.R
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
/**
 * Activity that hosts Readium's EpubNavigatorFragment for reading ebooks.
 * Downloads the EPUB from Jellyfin, opens it with Readium, and provides
 * full reading features: TOC, font/theme customization, position tracking, bookmarks.
 */
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

    // Current reading preferences
    private var currentPreferences = EpubPreferences()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must set fragment factory BEFORE super.onCreate for configuration changes
        val factory = navigatorFactory
        if (factory != null) {
            supportFragmentManager.fragmentFactory =
                factory.createFragmentFactory(
                    initialLocator = null,
                    initialPreferences = currentPreferences
                )
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_reader)
        setupWindowInsets()
        // Extract intent extras
        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: run { finish(); return }
        authHeader = intent.getStringExtra(EXTRA_AUTH_HEADER) ?: run { finish(); return }

        // Fetch book info from Jellyfin for title and duration
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

        // Load saved preferences
        loadPreferences()

        // Set up toolbar
        setupToolbar()

        if (savedInstanceState == null) {
            // First launch: download and open the book
            downloadAndOpenBook()
        } else {
            // Restored from config change: re-find the navigator
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            if (navigator != null) {
                showReaderContent()
                startPositionTracking()
            } else {
                downloadAndOpenBook()
            }
        }
    }

    private fun setupWindowInsets() {
        // Target the root view of the Activity
        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply the insets as padding to the view
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            // Return CONSUMED so the window doesn't try to apply them again
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

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showReaderSettings()
        }
    }

    private fun downloadAndOpenBook() {
        lifecycleScope.launch {
            try {
                updateLoadingText("Downloading ebook...")

                // Download the EPUB to cache
                val epubFile = downloadEpub()

                updateLoadingText("Opening ebook...")

                // Open with Readium
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

        // Use cached file if available
        if (epubFile.exists() && epubFile.length() > 0) {
            return@withContext epubFile
        }

        // Download from Jellyfin
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

        // Create navigator factory
        val factory = EpubNavigatorFactory(
            publication = pub,
            configuration = EpubNavigatorFactory.Configuration(
                defaults = EpubDefaults(
                    pageMargins = 1.5
                )
            )
        )
        navigatorFactory = factory

        // Calculate initial locator from Jellyfin position
        val initialLocator = restoreSavedLocator(pub)

        withContext(Dispatchers.Main) {
            // Set fragment factory on the activity's fragment manager
            supportFragmentManager.fragmentFactory =
                factory.createFragmentFactory(
                    initialLocator = initialLocator,
                    initialPreferences = currentPreferences
                )

            // Add the navigator fragment
            supportFragmentManager.commitNow {
                add(R.id.navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }

            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment

            // Add tap-to-navigate support
            navigator?.let { nav ->
                nav.addInputListener(DirectionalNavigationAdapter(nav))
            }

            showReaderContent()
            startPositionTracking()

            // Report playback start to Jellyfin
            reportPlaybackStart()
        }
    }

    private fun restoreSavedLocator(pub: Publication): Locator? {
        // Restore saved locator from SharedPreferences
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        val locatorJson = prefs.getString("ebook_locator_$bookId", null) ?: return null
        return try {
            Locator.fromJSON(org.json.JSONObject(locatorJson))
        } catch (_: Exception) {
            null
        }
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
                    if (locator != null) {
                        navigator?.go(locator)
                    }
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

        // Show confirmation
        android.widget.Toast.makeText(this, "Bookmark added", android.widget.Toast.LENGTH_SHORT).show()
    }

    // --- Reader Settings ---

    private fun showReaderSettings() {
        val nav = navigator ?: return
        val pub = publication ?: return

        val editor = navigatorFactory?.createPreferencesEditor(currentPreferences) ?: return

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)

            // Font size
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

            // Theme
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

            // Scroll mode toggle
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
            // Track locator changes
            nav.currentLocator
                .onEach { locator ->
                    lastReportedLocator = locator
                    // Save position for restore on next open
                    saveLocator(locator)
                    // Update toolbar with current chapter title
                    val chapterTitle = locator.title
                    if (!chapterTitle.isNullOrBlank()) {
                        findViewById<TextView>(R.id.toolbar_title)?.text = chapterTitle
                    }
                }
                .launchIn(this)

            // Periodic position reporting to Jellyfin
            while (isActive) {
                delay(30_000) // every 30 seconds
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
            } catch (e: Exception) {
                // Ignore reporting errors
            }
        }
    }

    private fun reportProgress() {
        val locator = lastReportedLocator ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ticks = locatorToTicks(locator)
                jellyfinClient.value?.reportPlaybackProgress(bookId, ticks, false)
            } catch (e: Exception) {
                // Ignore reporting errors
            }
        }
    }

    private fun reportPlaybackStopped() {
        val locator = lastReportedLocator ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ticks = locatorToTicks(locator)
                jellyfinClient.value?.reportPlaybackStopped(bookId, ticks)
            } catch (e: Exception) {
                // Ignore reporting errors
            }
        }
    }

    private fun locatorToTicks(locator: Locator): Long {
        val progression = locator.locations.totalProgression ?: 0.0
        return if (bookDuration > 0) {
            (progression * bookDuration).toLong()
        } else {
            // Fallback: use progression as a percentage-based tick value
            (progression * 10_000_000_000L).toLong() // 1000 seconds worth of ticks
        }
    }

    // --- Preferences Persistence ---

    private fun loadPreferences() {
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        val json = prefs.getString("epub_preferences_$bookId", null)
            ?: prefs.getString("epub_preferences_default", null)
        if (json != null) {
            try {
                // For now, use defaults; serialization can be added later
            } catch (e: Exception) {
                // Use defaults
            }
        }
    }

    private fun savePreferences() {
        // Save for this specific book and as default
        val prefs = getSharedPreferences("reader_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            // Preferences serialization would go here
            // For now, we rely on the in-memory currentPreferences
            apply()
        }
    }

    // --- Lifecycle ---

    override fun onStop() {
        super.onStop()
        // Report progress when leaving
        reportProgress()
    }

    override fun onDestroy() {
        positionReportingJob?.cancel()
        reportPlaybackStopped()
        super.onDestroy()
    }
}
