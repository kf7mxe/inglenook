package com.kf7mxe.inglenook.ebook



import EpubModule
import JsZipModule
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.lightningkite.kiteui.views.FutureElement
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.cssText
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.reactive.core.AppScope
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

actual fun ViewWriter.ebookReader(
    bookId: String,
    downloadUrl: String,
    authHeader: String
) {
    col {
        val container = this.native as? FutureElement ?: return@col

        // --- 1. Main Container Styling ---
        container.style.cssText = "width:100%; height:100%; min-height:500px; position:relative; overflow:hidden; display:flex; flex-direction:column;"

        val readerId = "reader-${bookId.hashCode()}"

        // --- 2. Create Layout Wrapper ---
        val wrapper = createDiv("$readerId-wrapper").apply {
            style.cssText = "display:flex; flex-direction:column; width:100%; height:100%; font-family:system-ui,-apple-system,sans-serif;"
        }
        container.appendChild(wrapper)

        // --- 3. Top Bar (Menus) ---
        val topBar = createDiv("$readerId-topbar").apply {
            style.cssText = "display:flex; align-items:center; justify-content:space-between; padding:8px 12px; background:#f5f5f5; border-bottom:1px solid #ddd; min-height:40px; z-index:20;"
        }
        wrapper.appendChild(topBar)

        // Title Placeholder
        val titleEl = FutureElement().also {
            it.tag = "span"
            it.id = "$readerId-title"
            it.content = "Loading..."
            it.style.cssText = "font-size:14px; font-weight:600; color:#333;"
        }
        topBar.appendChild(titleEl)

        // Top Buttons Container
        val topBtnContainer = createDiv("$readerId-top-btns").apply {
            style.cssText = "display:flex; gap:8px;"
        }
        topBar.appendChild(topBtnContainer)

        // TOC Button
        val tocBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-toc"
            it.content = "TOC"
            it.style.cssText = "padding:4px 10px; cursor:pointer;"
        }
        topBtnContainer.appendChild(tocBtn)

        // Settings Button
        val settingsBtn = FutureElement().also {
            it.tag = "button"
            it.id = "$readerId-btn-settings"
            it.content = "Aa"
            it.style.cssText = "padding:4px 10px; cursor:pointer;"
        }
        topBtnContainer.appendChild(settingsBtn)


        // --- 4. Content Area (The Book) ---
        val readerArea = createDiv("$readerId-content").apply {
            style.cssText = "flex:1; position:relative; overflow:hidden;"
        }
        wrapper.appendChild(readerArea)


        // --- 5. Bottom Bar (Navigation) ---
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


        // --- 6. Hidden Panels (TOC & Settings) ---
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

        // --- 9. Initialize & Wire Up Events ---
        js("""
            setTimeout(function() {
                var rid = window['__activeReaderId'];
                var loadingEl = document.getElementById(rid + '-loading');
                var readerArea = document.getElementById(rid + '-content');
                var titleEl = document.getElementById(rid + '-title');
                
                /* Controls */
                var nextBtn = document.getElementById(rid + '-next');
                var prevBtn = document.getElementById(rid + '-prev');
                var tocBtn = document.getElementById(rid + '-btn-toc');
                var settingsBtn = document.getElementById(rid + '-btn-settings');
                var settingsPanel = document.getElementById(rid + '-settings-panel');
                
                /* Theme Buttons */
                var tLight = document.getElementById(rid + '-theme-light');
                var tDark = document.getElementById(rid + '-theme-dark');
                var tSepia = document.getElementById(rid + '-theme-sepia');

                if (!window.ePub || !readerArea) {
                    if(loadingEl) loadingEl.textContent = "Error: Library or UI missing.";
                    return;
                }

                var ePub = window.ePub;
                var cfg = window['__readerConfig_' + rid];

                if (loadingEl) loadingEl.textContent = "Downloading book...";
                
                fetch(cfg.downloadUrl, {
                    headers: { 'X-Emby-Authorization': cfg.authHeader }
                })
                .then(function(r) {
                    if (!r.ok) throw new Error(r.status);
                    return r.arrayBuffer();
                })
                .then(function(data) {
                    if (loadingEl) loadingEl.textContent = "Rendering...";
                    
                    var book = ePub(data);
                    
                    /* Render Book */
                    var rendition = book.renderTo(readerArea, {
                        width: '100%', 
                        height: '100%', 
                        flow: 'paginated',
                        allowScriptedContent: true
                    });
                    
                    /* Restore saved position or start from beginning */
                    var savedCfi = localStorage.getItem('ebook_pos_' + cfg.bookId);
                    if (savedCfi) {
                        rendition.display(savedCfi);
                    } else {
                        rendition.display();
                    }

                    /* Set Default Theme */
                    rendition.themes.register('light', { 'body': { 'background': '#ffffff', 'color': '#333333' } });
                    rendition.themes.register('dark', { 'body': { 'background': '#1a1a2e', 'color': '#ccc' } });
                    rendition.themes.register('sepia', { 'body': { 'background': '#f4ecd8', 'color': '#5b4636' } });
                    rendition.themes.select('light');
                    
                    book.ready.then(function() {
                        console.log("Book Ready");
                        if(loadingEl) loadingEl.style.display = 'none';
                        
                        /* Update Title */
                        book.loaded.metadata.then(function(meta) {
                            if(titleEl) titleEl.textContent = meta.title;
                        });
                    });

                    /* Save position on page change */
                    var progressEl = document.getElementById(rid + '-progress');
                    rendition.on('relocated', function(location) {
                        if (location && location.start && location.start.cfi) {
                            localStorage.setItem('ebook_pos_' + cfg.bookId, location.start.cfi);
                        }
                        /* Update progress display */
                        if (progressEl && book.locations && book.locations.length()) {
                            var pct = book.locations.percentageFromCfi(location.start.cfi);
                            progressEl.textContent = Math.round(pct * 100) + '%';
                        }
                    });

                    /* Generate locations for progress tracking */
                    book.ready.then(function() {
                        return book.locations.generate(1024);
                    });

                    /* --- EVENT LISTENERS --- */
                    
                    if (nextBtn) {
                        nextBtn.addEventListener('click', function() {
                            rendition.next();
                        });
                    }
                    
                    if (prevBtn) {
                        prevBtn.addEventListener('click', function() {
                            rendition.prev();
                        });
                    }

                    /* Settings Toggle */
                    if (settingsBtn && settingsPanel) {
                        settingsBtn.addEventListener('click', function() {
                            if (settingsPanel.style.display === 'none') {
                                settingsPanel.style.display = 'block';
                            } else {
                                settingsPanel.style.display = 'none';
                            }
                        });
                    }

                    /* Theme Listeners */
                    if(tLight) tLight.addEventListener('click', function() { rendition.themes.select('light'); });
                    if(tDark) tDark.addEventListener('click', function() { rendition.themes.select('dark'); });
                    if(tSepia) tSepia.addEventListener('click', function() { rendition.themes.select('sepia'); });

                    /* Keyboard Support */
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
//    val div = document.createElement("div") as HTMLDivElement

    val div = FutureElement().also {
        it.tag = "div"
    }

    div.id = id
    return div
}