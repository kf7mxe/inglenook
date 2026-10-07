package com.kf7mxe.inglenook.ebook



import EpubModule
import JsZipModule
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


        val settingsPanel = createDiv("$readerId-settings-panel").apply {
            style.cssText = "display:none; position:absolute; top:50px; right:10px; background:white; border:1px solid #ccc; padding:15px; border-radius:4px; box-shadow:0 4px 12px rgba(0,0,0,0.15); z-index:50;"
            innerHtmlUnsafe = """
                <div style="margin-bottom:10px; font-weight:bold;">Theme</div>
                <div style="display:flex; gap:5px;">
                    <button id="$readerId-theme-light" style="padding:5px 10px; background:#fff; border:1px solid #ccc;">Light</button>
                    <button id="$readerId-theme-dark" style="padding:5px 10px; background:#333; color:#fff; border:1px solid #333;">Dark</button>
                    <button id="$readerId-theme-sepia" style="padding:5px 10px; background:#f4ecd8; border:1px solid #dcb;">Sepia</button>
                </div>
            """.trimIndent()
        }
        wrapper.appendChild(settingsPanel)

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
