package com.kf7mxe.inglenook.ebook



import EpubModule
import JsZipModule
import com.kf7mxe.inglenook.components.ExportedHighlight
import com.kf7mxe.inglenook.components.highlightsToMarkdown
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.HighlightRepository
import com.lightningkite.kiteui.views.FutureElement
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.cssText
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.reactive.core.AppScope
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
actual fun ViewWriter.ebookReader(
    bookId: String,
    downloadUrl: String,
    authHeader: String
) {
    col {
        val container = this.native as? FutureElement ?: return@col

        container.style.cssText = "width:100%; height:100%; min-height:500px; position:relative; overflow:hidden; display:flex; flex-direction:column;"

        val readerId = "reader-${bookId.hashCode()}"

        val wrapper = createDiv("$readerId-wrapper").apply {
            style.cssText = "display:flex; flex-direction:column; width:100%; height:100%; font-family:system-ui,-apple-system,sans-serif;"
        }
        container.appendChild(wrapper)

        val topBar = createDiv("$readerId-topbar").apply {
            style.cssText = "display:flex; align-items:center; justify-content:space-between; padding:8px 12px; background:#f5f5f5; border-bottom:1px solid #ddd; min-height:40px; z-index:20;"
        }
        wrapper.appendChild(topBar)

        val titleEl = FutureElement().also {
            it.tag = "span"
            it.id = "$readerId-title"
            it.content = "Loading..."
            it.style.cssText = "font-size:14px; font-weight:600; color:#333;"
        }
        topBar.appendChild(titleEl)

        val topBtnContainer = createDiv("$readerId-top-btns").apply {
            style.cssText = "display:flex; gap:8px;"
        }
        topBar.appendChild(topBtnContainer)

        val tocBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-toc"
            it.content = "TOC"
            it.style.cssText = "padding:4px 10px; cursor:pointer;"
        }
        topBtnContainer.appendChild(tocBtn)

        val searchBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-search"
            it.content = "Search"
            it.style.cssText = "padding:4px 10px; cursor:pointer;"
        }
        topBtnContainer.appendChild(searchBtn)

        val settingsBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-settings"
            it.content = "Aa"
            it.style.cssText = "padding:4px 10px; cursor:pointer;"
        }
        topBtnContainer.appendChild(settingsBtn)

        val bookmarkBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-bookmark"
            it.content = "🔖"
            it.style.cssText = "padding:4px 10px; cursor:pointer; font-size:16px; opacity:0.5;"
        }
        topBtnContainer.appendChild(bookmarkBtn)


        val readerArea = createDiv("$readerId-content").apply {
            style.cssText = "flex:1; position:relative; overflow:hidden;"
        }
        wrapper.appendChild(readerArea)


        val bottomBar = createDiv("$readerId-bottombar").apply {
            style.cssText = "display:flex; align-items:center; justify-content:center; gap:20px; padding:10px; background:#f5f5f5; border-top:1px solid #ddd; min-height:50px; z-index:20;"
        }
        wrapper.appendChild(bottomBar)

        val prevBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-prev"
            it.content = "← Prev"
            it.style.cssText = "padding:8px 20px; cursor:pointer; background:#333; color:white; border:none; border-radius:4px;"
        }
        bottomBar.appendChild(prevBtn)

        val progressEl = FutureElement().also {
            it.tag = "span"
            it.id = "$readerId-progress"
            it.content = ""
            it.style.cssText = "font-size:12px; color:#666; min-width:50px; text-align:center;"
        }
        bottomBar.appendChild(progressEl)

        val nextBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-next"
            it.content = "Next →"
            it.style.cssText = "padding:8px 20px; cursor:pointer; background:#333; color:white; border:none; border-radius:4px;"
        }
        bottomBar.appendChild(nextBtn)


        val engineButtonsHtml = SearchEngine.entries.joinToString("\n                    ") { engine ->
            """<button id="$readerId-engine-${engine.name.lowercase()}" style="padding:5px 10px; background:#fff; border:1px solid #ccc; cursor:pointer;">${engine.displayName}</button>"""
        }

        val settingsPanel = createDiv("$readerId-settings-panel").apply {
            style.cssText = "display:none; position:absolute; top:50px; right:10px; background:white; border:1px solid #ccc; padding:15px; border-radius:4px; box-shadow:0 4px 12px rgba(0,0,0,0.15); z-index:50;"
            innerHtmlUnsafe = """
                <div style="margin-bottom:10px; font-weight:bold;">Theme</div>
                <div style="display:flex; gap:5px;">
                    <button id="$readerId-theme-light" style="padding:5px 10px; background:#fff; border:1px solid #ccc;">Light</button>
                    <button id="$readerId-theme-dark" style="padding:5px 10px; background:#333; color:#fff; border:1px solid #333;">Dark</button>
                    <button id="$readerId-theme-sepia" style="padding:5px 10px; background:#f4ecd8; border:1px solid #dcb;">Sepia</button>
                </div>
                <div style="margin-bottom:10px; margin-top:12px; font-weight:bold;">Search Engine for Dictionary lookups</div>
                <div style="display:flex; gap:5px; flex-wrap:wrap;">
                    $engineButtonsHtml
                </div>
                <div style="margin-bottom:10px; margin-top:12px; font-weight:bold;">Highlights</div>
                <button id="$readerId-export-highlights" style="padding:5px 10px; background:#fff; border:1px solid #ccc; cursor:pointer;">Export as Markdown</button>
            """.trimIndent()
        }
        wrapper.appendChild(settingsPanel)

        // Floating results panel for "Search in book"
        val searchPanel = createDiv("$readerId-search-panel").apply {
            style.cssText = "display:none; position:absolute; top:50px; left:10px; right:10px; max-height:60vh; overflow:auto; background:white; border:1px solid #ccc; border-radius:4px; box-shadow:0 4px 12px rgba(0,0,0,0.15); z-index:60;"
        }
        wrapper.appendChild(searchPanel)

        val loadingOverlay = createDiv("$readerId-loading").apply {
            style.cssText = "position:absolute; top:0; left:0; right:0; bottom:0; display:flex; justify-content:center; align-items:center; background:rgba(255,255,255,0.9); z-index:30; font-size:16px; color:#333;"
            content = "Initializing..."
        }
        wrapper.appendChild(loadingOverlay)

        // Floating highlight context menu (hidden by default)
        val highlightMenu = createDiv("$readerId-highlight-menu").apply {
            style.cssText = "display:none; position:fixed; background:white; border:1px solid #ccc; border-radius:8px; padding:10px; box-shadow:0 4px 16px rgba(0,0,0,0.2); z-index:100; min-width:200px;"
            innerHtmlUnsafe = """
                <div style="font-weight:bold; margin-bottom:8px; font-size:13px;">Highlight</div>
                <div style="display:flex; gap:6px; margin-bottom:8px;">
                    <button data-color="#FFFF00" style="width:28px; height:28px; background:#FFFF00; border:2px solid #ccc; border-radius:4px; cursor:pointer;"></button>
                    <button data-color="#90EE90" style="width:28px; height:28px; background:#90EE90; border:2px solid #ccc; border-radius:4px; cursor:pointer;"></button>
                    <button data-color="#87CEEB" style="width:28px; height:28px; background:#87CEEB; border:2px solid #ccc; border-radius:4px; cursor:pointer;"></button>
                    <button data-color="#FFB6C1" style="width:28px; height:28px; background:#FFB6C1; border:2px solid #ccc; border-radius:4px; cursor:pointer;"></button>
                    <button data-color="#FFA500" style="width:28px; height:28px; background:#FFA500; border:2px solid #ccc; border-radius:4px; cursor:pointer;"></button>
                </div>
                <div style="display:flex; gap:6px;">
                    <button id="$readerId-highlight-define" style="flex:1; padding:6px; font-size:12px; background:#f0f0f0; border:1px solid #ccc; border-radius:4px; cursor:pointer;">Define</button>
                    <button id="$readerId-highlight-note-btn" style="flex:1; padding:6px; font-size:12px; background:#f0f0f0; border:1px solid #ccc; border-radius:4px; cursor:pointer;">Add Note</button>
                    <button id="$readerId-highlight-cancel" style="flex:1; padding:6px; font-size:12px; background:#f0f0f0; border:1px solid #ccc; border-radius:4px; cursor:pointer;">Cancel</button>
                </div>
            """.trimIndent()
        }
        wrapper.appendChild(highlightMenu)


        // --- 7. Bridge NPM Modules ---
        try {
            val zip = JsZipModule
            window.asDynamic().JSZip = zip

            val epubLib = EpubModule
            val epubFunc = if (epubLib.default != undefined) epubLib.default else epubLib
            window.asDynamic().ePub = epubFunc
        } catch (e: Exception) {
            console.error("Module Loading Error: ", e)
        }

        // --- 8. Pass Config ---
        val config = js("{}")
        config.downloadUrl = downloadUrl
        config.authHeader = authHeader
        config.bookId = bookId

        window.asDynamic()["__activeReaderId"] = readerId
        window.asDynamic()["__readerConfig_$readerId"] = config
        window.asDynamic()["__readerRendition_$readerId"] = null

        // --- Bookmark Bridge ---
        val bookmarksState = js("""{"currentTicks": "0", "currentChapter": "", "currentCfi": ""}""")
        window.asDynamic()["__bookmarkState_$readerId"] = bookmarksState
        window.asDynamic()["__bookmarkIds_$readerId"] = js("{}")

        window.asDynamic()["__toggleBookmark_$readerId"] = { ticksStr: String, chapter: String, cfi: String ->
            GlobalScope.launch {
                val ticks = ticksStr.toLongOrNull() ?: return@launch
                val idsObj = window.asDynamic()["__bookmarkIds_$readerId"]
                val existingId = idsObj[ticksStr]
                if (existingId != undefined) {
                    BookmarkRepository.deleteBookmark(kotlin.uuid.Uuid.parse(existingId as String))
                    js("delete idsObj[ticksStr]")
                } else {
                    val metaObj = js("{}")
                    metaObj.cfi = cfi
                    metaObj.chapter = chapter
                    val metaJson: String = js("JSON.stringify(metaObj)")
                    val bm = BookmarkRepository.createBookmark(bookId, ticks, metaJson, chapter)
                    js("idsObj[ticksStr] = bm._id.toString()")
                }
            }
        }

        window.asDynamic()["__isBookmarked_$readerId"] = { ticksStr: String ->
            val idsObj = window.asDynamic()["__bookmarkIds_$readerId"]
            idsObj[ticksStr] != undefined
        }

        // URL building stays in commonMain so web and Android search URLs can't drift.
        window.asDynamic()["__searchUrl_$readerId"] = { engineName: String, customTemplate: String, query: String ->
            val engine = SearchEngine.entries.firstOrNull { it.name == engineName } ?: SearchEngine.Google
            searchUrl(engine, customTemplate, query)
        }

        // The web reader stores highlights in localStorage (not HighlightRepository),
        // so export reads them from there and downloads the Markdown directly.
        window.asDynamic()["__exportHighlights_$readerId"] = {
            try {
                val stored = window.localStorage.getItem("ebook_highlights_$bookId")
                val elements = try {
                    Json.parseToJsonElement(stored ?: "[]").jsonArray
                } catch (e: Exception) {
                    emptyList<kotlinx.serialization.json.JsonElement>()
                }
                val exported = elements.mapNotNull { element ->
                    val obj = element.jsonObject
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull
                    if (text.isNullOrBlank()) null
                    else ExportedHighlight(
                        chapter = null,
                        text = text,
                        note = obj["note"]?.jsonPrimitive?.contentOrNull
                    )
                }
                if (exported.isEmpty()) {
                    window.alert("No highlights to export")
                } else {
                    val title = (document.getElementById("$readerId-title") as? HTMLElement)
                        ?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: "Book"
                    val markdown = highlightsToMarkdown(title, exported)
                    val blob = js("new Blob([markdown], { type: 'text/markdown' })")
                    val url = js("URL.createObjectURL(blob)")
                    val anchor = document.createElement("a")
                    anchor.asDynamic().href = url
                    anchor.asDynamic().download = title.replace(Regex("[^A-Za-z0-9 .-]"), "_") + " - highlights.md"
                    anchor.asDynamic().click()
                    js("URL.revokeObjectURL(url)")
                }
            } catch (e: Exception) {
                window.alert("Couldn't export highlights: ${e.message}")
            }
        }

        GlobalScope.launch {
            val bookmarks = BookmarkRepository.getBookmarksForBook(bookId)
            val idsObj = js("{}")
            for (b in bookmarks) {
                js("idsObj[b.positionTicks.toString()] = b._id.toString()")
            }
            window.asDynamic()["__bookmarkIds_$readerId"] = idsObj
        }

        // --- 9. Initialize & Wire Up Events ---
        js("""
            setTimeout(function() {
                var rid = window['__activeReaderId'];
                var loadingEl = document.getElementById(rid + '-loading');
                var readerArea = document.getElementById(rid + '-content');
                var titleEl = document.getElementById(rid + '-title');

                var nextBtn = document.getElementById(rid + '-next');
                var prevBtn = document.getElementById(rid + '-prev');
                var tocBtn = document.getElementById(rid + '-btn-toc');
                var settingsBtn = document.getElementById(rid + '-btn-settings');
                var settingsPanel = document.getElementById(rid + '-settings-panel');

                var tLight = document.getElementById(rid + '-theme-light');
                var tDark = document.getElementById(rid + '-theme-dark');
                var tSepia = document.getElementById(rid + '-theme-sepia');

                var highlightMenu = document.getElementById(rid + '-highlight-menu');
                var highlightNoteBtn = document.getElementById(rid + '-highlight-note-btn');
                var highlightCancel = document.getElementById(rid + '-highlight-cancel');
                var bookmarkBtn = document.getElementById(rid + '-btn-bookmark');

                if (!window.ePub || !readerArea) {
                    if(loadingEl) loadingEl.textContent = "Error: Library or UI missing.";
                    return;
                }

                var ePub = window.ePub;
                var cfg = window['__readerConfig_' + rid];

                if (loadingEl) loadingEl.textContent = "Downloading book...";

                fetch(cfg.downloadUrl, {
                    headers: { 'Authorization': cfg.authHeader }
                })
                .then(function(r) {
                    if (!r.ok) throw new Error(r.status);
                    return r.arrayBuffer();
                })
                .then(function(data) {
                    if (loadingEl) loadingEl.textContent = "Rendering...";

                    var book = ePub(data);

                    var rendition = book.renderTo(readerArea, {
                        width: '100%',
                        height: '100%',
                        flow: 'paginated',
                        allowScriptedContent: true
                    });

                    window['__readerRendition_' + rid] = rendition;

                    var savedCfi = localStorage.getItem('ebook_pos_' + cfg.bookId);
                    if (savedCfi) {
                        rendition.display(savedCfi);
                    } else {
                        rendition.display();
                    }

                    rendition.themes.register('light', { 'body': { 'background': '#ffffff', 'color': '#333333' } });
                    rendition.themes.register('dark', { 'body': { 'background': '#1a1a2e', 'color': '#ccc' } });
                    rendition.themes.register('sepia', { 'body': { 'background': '#f4ecd8', 'color': '#5b4636' } });
                    rendition.themes.select('light');

                    /* --- Load existing highlights --- */
                    var storedHighlights = JSON.parse(localStorage.getItem('ebook_highlights_' + cfg.bookId) || '[]');
                    function reloadHighlights() {
                        storedHighlights.forEach(function(h) {
                            try { rendition.annotations.highlight(h.cfiRange, {}, function(){}, h.id, {'fill': h.color, 'fill-opacity': '0.3'}); } catch(e) {}
                        });
                    }
                    book.ready.then(function() {
                        reloadHighlights();
                    });

                    book.ready.then(function() {
                        console.log("Book Ready");
                        if(loadingEl) loadingEl.style.display = 'none';
                        book.loaded.metadata.then(function(meta) {
                            if(titleEl) titleEl.textContent = meta.title;
                        });
                    });

                    var progressEl = document.getElementById(rid + '-progress');
                    rendition.on('relocated', function(location) {
                        if (location && location.start && location.start.cfi) {
                            localStorage.setItem('ebook_pos_' + cfg.bookId, location.start.cfi);
                        }
                        if (progressEl && book.locations && book.locations.length()) {
                            var pct = book.locations.percentageFromCfi(location.start.cfi);
                            progressEl.textContent = Math.round(pct * 100) + '%';
                            var ticks = Math.round(pct * 10000000000).toString();
                            var bs = window['__bookmarkState_' + rid];
                            bs.currentTicks = ticks;
                            bs.currentCfi = location.start.cfi;
                            bs.currentChapter = location.start.href || '';
                            if (bookmarkBtn) {
                                var isBm = window['__isBookmarked_' + rid](ticks);
                                bookmarkBtn.style.opacity = isBm ? '1.0' : '0.5';
                            }
                        }
                    });

                    book.ready.then(function() {
                        return book.locations.generate(1024);
                    });

                    /* --- Track pending highlight info --- */
                    var pendingHighlightCfiRange = null;
                    var pendingHighlightText = null;

                    /* --- Text Selection Handler (shows floating menu) --- */
                    rendition.on('selected', function(cfiRange, contents) {
                        pendingHighlightCfiRange = cfiRange;
                        var selection = contents.window.getSelection();
                        pendingHighlightText = selection ? selection.toString().substring(0, 200) : '';

                        /* Position the menu near the selection */
                        if (selection && selection.rangeCount > 0) {
                            var range = selection.getRangeAt(0);
                            var rect = range.getBoundingClientRect();
                            /* Find the reader container's position */
                            var readerRect = readerArea.getBoundingClientRect();
                            highlightMenu.style.left = Math.min(rect.left, readerRect.right - 220) + 'px';
                            highlightMenu.style.top = (rect.top + readerRect.top - 10) + 'px';
                        } else {
                            highlightMenu.style.left = '50%';
                            highlightMenu.style.top = '50%';
                        }
                        highlightMenu.style.display = 'block';
                    });

                    /* Hide menu on tap outside */
                    document.addEventListener('mousedown', function(e) {
                        if (!highlightMenu.contains(e.target)) {
                            highlightMenu.style.display = 'none';
                        }
                    });

                    /* --- Highlight Color Buttons --- */
                    var colorBtns = highlightMenu.querySelectorAll('button[data-color]');
                    for (var i = 0; i < colorBtns.length; i++) {
                        colorBtns[i].addEventListener('click', function(e) {
                            e.stopPropagation();
                            var color = this.getAttribute('data-color');
                            if (!pendingHighlightCfiRange) return;

                            var highlightId = 'hl-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6);
                            rendition.annotations.highlight(pendingHighlightCfiRange, {}, function(){}, highlightId, {'fill': color, 'fill-opacity': '0.3'});

                            var highlight = {
                                id: highlightId,
                                cfiRange: pendingHighlightCfiRange,
                                color: color,
                                text: pendingHighlightText,
                                note: null,
                                timestamp: Date.now()
                            };
                            storedHighlights.push(highlight);
                            localStorage.setItem('ebook_highlights_' + cfg.bookId, JSON.stringify(storedHighlights));

                            highlightMenu.style.display = 'none';
                            pendingHighlightCfiRange = null;
                            pendingHighlightText = null;
                        });
                    }

                    /* --- Add Note Button --- */
                    if (highlightNoteBtn) {
                        highlightNoteBtn.addEventListener('click', function(e) {
                            e.stopPropagation();
                            if (!pendingHighlightCfiRange) return;
                            var note = prompt('Add a note:', '');
                            if (note === null) return;

                            var color = '#FFFF00';
                            var colorBtns2 = highlightMenu.querySelectorAll('button[data-color]');
                            for (var j = 0; j < colorBtns2.length; j++) {
                                if (colorBtns2[j].matches(':hover') || colorBtns2[j].getAttribute('data-color') === '#FFFF00') {
                                    color = colorBtns2[j].getAttribute('data-color');
                                    break;
                                }
                            }

                            var highlightId2 = 'hl-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6);
                            rendition.annotations.highlight(pendingHighlightCfiRange, {}, function(){}, highlightId2, {'fill': color, 'fill-opacity': '0.3'});

                            storedHighlights.push({
                                id: highlightId2,
                                cfiRange: pendingHighlightCfiRange,
                                color: color,
                                text: pendingHighlightText,
                                note: note || null,
                                timestamp: Date.now()
                            });
                            localStorage.setItem('ebook_highlights_' + cfg.bookId, JSON.stringify(storedHighlights));

                            highlightMenu.style.display = 'none';
                            pendingHighlightCfiRange = null;
                            pendingHighlightText = null;
                        });
                    }

                    /* --- Cancel Button --- */
                    if (highlightCancel) {
                        highlightCancel.addEventListener('click', function(e) {
                            e.stopPropagation();
                            highlightMenu.style.display = 'none';
                            pendingHighlightCfiRange = null;
                            pendingHighlightText = null;
                        });
                    }

                    /* --- Define Button (opens the chosen search engine in a new tab) --- */
                    var highlightDefine = document.getElementById(rid + '-highlight-define');
                    if (highlightDefine) {
                        highlightDefine.addEventListener('click', function(e) {
                            e.stopPropagation();
                            var text = (pendingHighlightText || '').trim();
                            highlightMenu.style.display = 'none';
                            pendingHighlightCfiRange = null;
                            pendingHighlightText = null;
                            if (!text) return;
                            var engine = localStorage.getItem('ebook_search_engine') || 'Google';
                            var custom = localStorage.getItem('ebook_search_engine_custom') || '';
                            window.open(window['__searchUrl_' + rid](engine, custom, 'define ' + text), '_blank');
                        });
                    }

                    /* --- Bookmark Button --- */
                    if (bookmarkBtn) {
                        bookmarkBtn.addEventListener('click', function() {
                            var bs = window['__bookmarkState_' + rid];
                            window['__toggleBookmark_' + rid](bs.currentTicks, bs.currentChapter, bs.currentCfi);
                            var isBm = window['__isBookmarked_' + rid](bs.currentTicks);
                            bookmarkBtn.style.opacity = isBm ? '1.0' : '0.5';
                        });
                    }

                    /* --- Navigation & Settings --- */
                    if (nextBtn) nextBtn.addEventListener('click', function() { rendition.next(); });
                    if (prevBtn) prevBtn.addEventListener('click', function() { rendition.prev(); });

                    if (settingsBtn && settingsPanel) {
                        settingsBtn.addEventListener('click', function() {
                            settingsPanel.style.display = settingsPanel.style.display === 'none' ? 'block' : 'none';
                        });
                    }

                    if(tLight) tLight.addEventListener('click', function() { rendition.themes.select('light'); });
                    if(tDark) tDark.addEventListener('click', function() { rendition.themes.select('dark'); });
                    if(tSepia) tSepia.addEventListener('click', function() { rendition.themes.select('sepia'); });

                    /* --- Search engine selection --- */
                    var engineKeys = ['google', 'duckduckgo', 'bing', 'custom'];
                    var engineNames = { 'google': 'Google', 'duckduckgo': 'DuckDuckGo', 'bing': 'Bing', 'custom': 'Custom' };
                    function refreshEngineButtons() {
                        var current = localStorage.getItem('ebook_search_engine') || 'Google';
                        var template = localStorage.getItem('ebook_search_engine_custom') || '';
                        if (current === 'Custom' && template.indexOf('http') !== 0) current = 'Google';
                        for (var i = 0; i < engineKeys.length; i++) {
                            var b = document.getElementById(rid + '-engine-' + engineKeys[i]);
                            if (!b) continue;
                            var active = engineNames[engineKeys[i]] === current;
                            b.style.background = active ? '#333' : '#fff';
                            b.style.color = active ? '#fff' : '#000';
                        }
                    }
                    for (var ei = 0; ei < engineKeys.length; ei++) {
                        (function(key, name) {
                            var b = document.getElementById(rid + '-engine-' + key);
                            if (!b) return;
                            b.addEventListener('click', function(e) {
                                e.stopPropagation();
                                if (name === 'Custom') {
                                    var current = localStorage.getItem('ebook_search_engine_custom') || '';
                                    var input = prompt('Enter the search URL.\nUse %s where the query goes (appended if omitted):', current);
                                    if (input === null) return;
                                    input = input.trim();
                                    if (input.indexOf('http://') !== 0 && input.indexOf('https://') !== 0) {
                                        alert('URL must start with http:// or https://');
                                        return;
                                    }
                                    localStorage.setItem('ebook_search_engine_custom', input);
                                }
                                localStorage.setItem('ebook_search_engine', name);
                                refreshEngineButtons();
                            });
                        })(engineKeys[ei], engineNames[engineKeys[ei]]);
                    }
                    refreshEngineButtons();

                    /* --- Export highlights button --- */
                    var exportHighlightsBtn = document.getElementById(rid + '-export-highlights');
                    if (exportHighlightsBtn) {
                        exportHighlightsBtn.addEventListener('click', function(e) {
                            e.stopPropagation();
                            window['__exportHighlights_' + rid]();
                        });
                    }

                    /* --- Search in book (walks the spine, epub.js section.find) --- */
                    var searchBtn = document.getElementById(rid + '-btn-search');
                    var searchPanel = document.getElementById(rid + '-search-panel');
                    if (searchBtn && searchPanel) {
                        searchBtn.addEventListener('click', function() {
                            var query = prompt('Search in this book:');
                            if (!query) return;
                            query = query.trim();
                            if (!query) return;
                            searchPanel.style.display = 'block';
                            searchPanel.innerHTML = '';
                            var searching = document.createElement('div');
                            searching.style.cssText = 'padding:10px; font-size:13px; color:#666;';
                            searching.textContent = 'Searching\u2026';
                            searchPanel.appendChild(searching);
                            var items = book.spine.spineItems;
                            var found = [];
                            var index = 0;
                            function showResults() {
                                searchPanel.innerHTML = '';
                                if (!found.length) {
                                    var none = document.createElement('div');
                                    none.style.cssText = 'padding:10px; font-size:13px; color:#666;';
                                    none.textContent = 'No results for "' + query + '"';
                                    searchPanel.appendChild(none);
                                    return;
                                }
                                var header = document.createElement('div');
                                header.style.cssText = 'padding:8px 10px; font-size:12px; font-weight:bold; border-bottom:1px solid #eee;';
                                header.textContent = 'Results (' + found.length + (found.length >= 50 ? '+' : '') + ')';
                                searchPanel.appendChild(header);
                                found.forEach(function(r) {
                                    var b = document.createElement('button');
                                    b.style.cssText = 'display:block; width:100%; text-align:left; padding:8px 10px; font-size:12px; border:none; border-bottom:1px solid #eee; background:#fff; cursor:pointer;';
                                    b.textContent = r.excerpt || r.cfi;
                                    b.addEventListener('click', function() {
                                        rendition.display(r.cfi);
                                        searchPanel.style.display = 'none';
                                    });
                                    searchPanel.appendChild(b);
                                });
                            }
                            function step() {
                                if (index >= items.length || found.length >= 50) { showResults(); return; }
                                var item = items[index++];
                                item.load(book.load.bind(book)).then(function(section) {
                                    var matches = section.find(query) || [];
                                    for (var m = 0; m < matches.length; m++) {
                                        found.push(matches[m]);
                                        if (found.length >= 50) break;
                                    }
                                    try { item.unload(); } catch (err) {}
                                    step();
                                }).catch(function() { step(); });
                            }
                            step();
                        });
                    }

                    /* --- Double-click to toggle fullscreen (hide/show chrome bars) --- */
                    var topBarEl = document.getElementById(rid + '-topbar');
                    var bottomBarEl = document.getElementById(rid + '-bottombar');
                    var chromeVisible = true;
                    var lastClickTime = 0;
                    var lastClickX = 0;
                    var lastClickY = 0;
                    rendition.on('click', function(e) {
                        var now = Date.now();
                        var x = e.pageX || e.clientX || 0;
                        var y = e.pageY || e.clientY || 0;
                        var isDoubleClick = now - lastClickTime < 300 &&
                            Math.abs(x - lastClickX) < 100 &&
                            Math.abs(y - lastClickY) < 100;
                        lastClickTime = now;
                        lastClickX = x;
                        lastClickY = y;
                        if (isDoubleClick) {
                            chromeVisible = !chromeVisible;
                            var barDisplay = chromeVisible ? 'flex' : 'none';
                            if (topBarEl) topBarEl.style.display = barDisplay;
                            if (bottomBarEl) bottomBarEl.style.display = barDisplay;
                        }
                    });

                    document.addEventListener('keyup', function(e) {
                        if ((e.keyCode || e.which) == 37) rendition.prev();
                        if ((e.keyCode || e.which) == 39) rendition.next();
                    });

                })
                .catch(function(err) {
                    console.error("Book Error:", err);
                    if (loadingEl) loadingEl.textContent = "Error: " + err.message;
                });
            }, 100);
        """)
    }
}

private fun createDiv(id: String): FutureElement {
    val div = FutureElement().also {
        it.tag = "div"
    }
    div.id = id
    return div
}
