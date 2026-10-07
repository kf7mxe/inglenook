package com.kf7mxe.inglenook.ebook

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.kiteui.models.ImageScaleType
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.Foundation.NSURL
import platform.UIKit.UIView
import platform.CoreGraphics.CGRectZero

/**
 * iOS implementation of ebook reader using WKWebView with epub.js.
 * Uses the epub.js 'selected' event for text-range highlights with a floating context menu.
 */
actual fun ViewWriter.ebookReader(
    bookId: String,
    downloadUrl: String,
    authHeader: String
) {
    image {
        scaleType = ImageScaleType.Fit

        val imageView = rView.native as? UIView
        val container = imageView?.superview

        if (container != null) {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
            webView.setAutoresizingMask(
                platform.UIKit.UIViewAutoresizingFlexibleWidth or
                platform.UIKit.UIViewAutoresizingFlexibleHeight
            )
            webView.setTranslatesAutoresizingMaskIntoConstraints(false)

            val escapedUrl = downloadUrl.replace("'", "\\'")
            val escapedAuth = authHeader.replace("'", "\\'")

            val readerHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                    <title>Reader</title>
                    <script src="https://cdn.jsdelivr.net/npm/epubjs/dist/epub.min.js"></script>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { height: 100%; overflow: hidden; background: #fafafa; }
                        #topbar {
                            display: flex; align-items: center; justify-content: space-between;
                            padding: 8px 12px; background: #f5f5f5; border-bottom: 1px solid #ddd;
                            height: 44px;
                        }
                        #topbar-title { font-size: 14px; font-weight: 600; color: #333; flex: 1; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
                        #topbar-btns { display: flex; gap: 8px; }
                        #topbar-btns button { padding: 4px 10px; cursor: pointer; font-size: 16px; background: none; border: none; border-radius: 4px; }
                        #reader { width: 100%; height: calc(100% - 104px); }
                        #loading {
                            display: flex; justify-content: center; align-items: center;
                            height: 100%; font-family: -apple-system, sans-serif;
                        }
                        #controls {
                            position: fixed; bottom: 0; left: 0; right: 0;
                            display: none; justify-content: center; gap: 20px;
                            padding: 15px; padding-bottom: env(safe-area-inset-bottom, 15px);
                            background: #fff; border-top: 1px solid #ddd;
                        }
                        #controls button {
                            padding: 12px 30px; font-size: 17px;
                            background: #007AFF; color: white; border: none; border-radius: 10px;
                        }
                        #highlight-menu {
                            display: none; position: fixed; background: white;
                            border: 1px solid #ccc; border-radius: 8px; padding: 10px;
                            box-shadow: 0 4px 16px rgba(0,0,0,0.2); z-index: 100; min-width: 200px;
                        }
                        #highlight-menu .menu-title { font-weight: bold; margin-bottom: 8px; font-size: 13px; }
                        #highlight-menu .color-row { display: flex; gap: 6px; margin-bottom: 8px; }
                        #highlight-menu .color-btn {
                            width: 28px; height: 28px; border: 2px solid #ccc; border-radius: 4px; cursor: pointer;
                        }
                        #highlight-menu .action-row { display: flex; gap: 6px; }
                        #highlight-menu .action-btn {
                            flex: 1; padding: 6px; font-size: 12px;
                            background: #f0f0f0; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;
                        }
                        #error {
                            display: none; padding: 20px; text-align: center;
                            font-family: -apple-system, sans-serif; color: #c00;
                        }
                    </style>
                </head>
                <body>
                    <div id="loading">Loading ebook...</div>
                    <div id="topbar">
                        <div id="topbar-title">Loading...</div>
                        <div id="topbar-btns">
                            <button id="btn-bookmark" style="opacity:0.5;">🔖</button>
                        </div>
                    </div>
                    <div id="reader"></div>
                    <div id="highlight-menu">
                        <div class="menu-title">Highlight</div>
                        <div class="color-row">
                            <button class="color-btn" data-color="#FFFF00" style="background:#FFFF00;"></button>
                            <button class="color-btn" data-color="#90EE90" style="background:#90EE90;"></button>
                            <button class="color-btn" data-color="#87CEEB" style="background:#87CEEB;"></button>
                            <button class="color-btn" data-color="#FFB6C1" style="background:#FFB6C1;"></button>
                            <button class="color-btn" data-color="#FFA500" style="background:#FFA500;"></button>
                        </div>
                        <div class="action-row">
                            <button class="action-btn" id="hl-note-btn">Add Note</button>
                            <button class="action-btn" id="hl-cancel-btn">Cancel</button>
                        </div>
                    </div>
                    <div id="controls">
                        <button id="prev">← Previous</button>
                        <button id="next">Next →</button>
                    </div>
                    <div id="error"></div>
                    <script>
                        (async function() {
                            const url = '$escapedUrl';
                            const authHeader = '$escapedAuth';
                            const bookId = '${bookId}';

                            try {
                                const response = await fetch(url, {
                                    headers: { 'Authorization': authHeader }
                                });
                                if (!response.ok) throw new Error('Failed: ' + response.status);

                                const contentType = response.headers.get('Content-Type') || '';
                                const blob = await response.blob();
                                const blobUrl = URL.createObjectURL(blob);

                                if (contentType.includes('pdf')) {
                                    document.getElementById('loading').style.display = 'none';
                                    const pdfViewer = document.createElement('iframe');
                                    pdfViewer.src = blobUrl;
                                    pdfViewer.style.cssText = 'width:100%;height:100%;border:none';
                                    document.getElementById('reader').appendChild(pdfViewer);
                                    document.getElementById('reader').style.height = '100%';
                                    return;
                                }

                                const book = ePub(blobUrl);
                                const rendition = book.renderTo('reader', {
                                    width: '100%', height: '100%', spread: 'none'
                                });
                                rendition.display();

                                /* Load existing highlights */
                                var storedHighlights = JSON.parse(localStorage.getItem('ebook_highlights_' + bookId) || '[]');
                                function reloadHighlights() {
                                    storedHighlights.forEach(function(h) {
                                        try { rendition.annotations.highlight(h.cfiRange, {}, function(){}, h.id, {'fill': h.color, 'fill-opacity': '0.3'}); } catch(e) {}
                                    });
                                }
                                book.ready.then(function() { reloadHighlights(); });

                                /* Load existing bookmarks */
                                var storedBookmarks = JSON.parse(localStorage.getItem('ebook_bookmarks_' + bookId) || '[]');
                                function isBookmarked(cfi) {
                                    var key = bookmarkKey(cfi);
                                    return storedBookmarks.some(function(b) { return b.cfi === key; });
                                }
                                function bookmarkKey(cfi) { return cfi.substring(0, 80); }

                                document.getElementById('loading').style.display = 'none';
                                document.getElementById('controls').style.display = 'flex';

                                document.getElementById('prev').onclick = () => rendition.prev();
                                document.getElementById('next').onclick = () => rendition.next();

                                /* Track position for progress and bookmarks */
                                book.ready.then(function() {
                                    return book.locations.generate(1024);
                                });

                                var lastCfi = null;
                                var bookmarkBtn = document.getElementById('btn-bookmark');
                                rendition.on('relocated', function(location) {
                                    if (location && location.start && location.start.cfi) {
                                        lastCfi = location.start.cfi;
                                        localStorage.setItem('ebook_pos_' + bookId, lastCfi);
                                        var bkey = bookmarkKey(lastCfi);
                                        if (bookmarkBtn) {
                                            bookmarkBtn.style.opacity = isBookmarked(bkey) ? '1.0' : '0.5';
                                        }
                                    }
                                });

                                /* Bookmark toggle */
                                if (bookmarkBtn) {
                                    bookmarkBtn.addEventListener('click', function() {
                                        if (!lastCfi) return;
                                        var bkey = bookmarkKey(lastCfi);
                                        var idx = storedBookmarks.findIndex(function(b) { return b.cfi === bkey; });
                                        if (idx >= 0) {
                                            storedBookmarks.splice(idx, 1);
                                        } else {
                                            storedBookmarks.push({ cfi: bkey, timestamp: Date.now() });
                                        }
                                        localStorage.setItem('ebook_bookmarks_' + bookId, JSON.stringify(storedBookmarks));
                                        bookmarkBtn.style.opacity = isBookmarked(bkey) ? '1.0' : '0.5';
                                    });
                                }

                                /* Highlight menu */
                                var highlightMenu = document.getElementById('highlight-menu');
                                var pendingCfiRange = null;
                                var pendingText = null;

                                /* Text selection triggers floating menu */
                                rendition.on('selected', function(cfiRange, contents) {
                                    pendingCfiRange = cfiRange;
                                    var sel = contents.window.getSelection();
                                    pendingText = sel ? sel.toString().substring(0, 200) : '';

                                    if (sel && sel.rangeCount > 0) {
                                        var range = sel.getRangeAt(0);
                                        var rect = range.getBoundingClientRect();
                                        highlightMenu.style.left = Math.min(rect.left, window.innerWidth - 220) + 'px';
                                        highlightMenu.style.top = (rect.top - 10) + 'px';
                                    } else {
                                        highlightMenu.style.left = '50%';
                                        highlightMenu.style.top = '50%';
                                    }
                                    highlightMenu.style.display = 'block';
                                });

                                /* Hide on tap outside */
                                document.addEventListener('mousedown', function(e) {
                                    if (!highlightMenu.contains(e.target)) {
                                        highlightMenu.style.display = 'none';
                                    }
                                });

                                /* Color buttons */
                                var colorBtns = highlightMenu.querySelectorAll('.color-btn');
                                for (var i = 0; i < colorBtns.length; i++) {
                                    colorBtns[i].addEventListener('click', function(e) {
                                        e.stopPropagation();
                                        var color = this.getAttribute('data-color');
                                        if (!pendingCfiRange) return;

                                        var hlId = 'hl-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6);
                                        rendition.annotations.highlight(pendingCfiRange, {}, function(){}, hlId, {'fill': color, 'fill-opacity': '0.3'});

                                        storedHighlights.push({ id: hlId, cfiRange: pendingCfiRange, color: color, text: pendingText, note: null, timestamp: Date.now() });
                                        localStorage.setItem('ebook_highlights_' + bookId, JSON.stringify(storedHighlights));

                                        highlightMenu.style.display = 'none';
                                        pendingCfiRange = null;
                                        pendingText = null;
                                    });
                                }

                                /* Note button */
                                document.getElementById('hl-note-btn').addEventListener('click', function(e) {
                                    e.stopPropagation();
                                    if (!pendingCfiRange) return;
                                    var note = prompt('Add a note:', '');
                                    if (note === null) return;

                                    var hlId2 = 'hl-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6);
                                    rendition.annotations.highlight(pendingCfiRange, {}, function(){}, hlId2, {'fill': '#FFFF00', 'fill-opacity': '0.3'});

                                    storedHighlights.push({ id: hlId2, cfiRange: pendingCfiRange, color: '#FFFF00', text: pendingText, note: note || null, timestamp: Date.now() });
                                    localStorage.setItem('ebook_highlights_' + bookId, JSON.stringify(storedHighlights));

                                    highlightMenu.style.display = 'none';
                                    pendingCfiRange = null;
                                    pendingText = null;
                                });

                                /* Cancel button */
                                document.getElementById('hl-cancel-btn').addEventListener('click', function(e) {
                                    e.stopPropagation();
                                    highlightMenu.style.display = 'none';
                                    pendingCfiRange = null;
                                    pendingText = null;
                                });

                            } catch (error) {
                                document.getElementById('loading').style.display = 'none';
                                document.getElementById('error').style.display = 'block';
                                document.getElementById('error').textContent = 'Error: ' + error.message;
                            }
                        })();
                    </script>
                </body>
                </html>
            """.trimIndent()

            webView.loadHTMLString(readerHtml, baseURL = NSURL.URLWithString("https://jellyfin.local"))

            imageView?.setHidden(true)
            container.addSubview(webView)

            webView.topAnchor.constraintEqualToAnchor(container.topAnchor).setActive(true)
            webView.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor).setActive(true)
            webView.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor).setActive(true)
            webView.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor).setActive(true)
        }
    }
}
