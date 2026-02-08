package com.kf7mxe.inglenook.ebook

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.kiteui.models.ImageScaleType

/**
 * Android implementation of ebook reader using WebView with epub.js.
 */
@SuppressLint("SetJavaScriptEnabled")
actual fun ViewWriter.ebookReader(
    downloadUrl: String,
    authHeader: String
) {
    // Use image as container since it provides rView access
    image {
        // Make the image invisible - we just need the container
        scaleType = ImageScaleType.Fit

        // Get the parent container from the image's native view
        val imageView = rView.native
        val parent = imageView?.parent as? ViewGroup

        if (parent != null) {
            // Create WebView
            val webView = WebView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
            }

            // Create the reader HTML
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
                        #reader { width: 100%; height: calc(100% - 60px); }
                        #loading {
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100%;
                            font-family: system-ui, sans-serif;
                            color: #333;
                        }
                        #controls {
                            position: fixed;
                            bottom: 0;
                            left: 0;
                            right: 0;
                            display: none;
                            justify-content: center;
                            gap: 20px;
                            padding: 10px;
                            background: #fff;
                            border-top: 1px solid #ddd;
                            z-index: 1000;
                        }
                        #controls button {
                            padding: 12px 30px;
                            font-size: 16px;
                            cursor: pointer;
                            background: #333;
                            color: white;
                            border: none;
                            border-radius: 8px;
                            touch-action: manipulation;
                        }
                        #error {
                            display: none;
                            padding: 20px;
                            text-align: center;
                            font-family: system-ui, sans-serif;
                            color: #c00;
                        }
                        #pdf-viewer {
                            width: 100%;
                            height: 100%;
                            border: none;
                        }
                    </style>
                </head>
                <body>
                    <div id="loading">Loading ebook...</div>
                    <div id="reader"></div>
                    <div id="controls">
                        <button id="prev">← Previous</button>
                        <button id="next">Next →</button>
                    </div>
                    <div id="error"></div>
                    <script>
                        (async function() {
                            const url = '$escapedUrl';
                            const authHeader = '$escapedAuth';

                            try {
                                const response = await fetch(url, {
                                    headers: { 'X-Emby-Authorization': authHeader }
                                });

                                if (!response.ok) {
                                    throw new Error('Failed to download: ' + response.status);
                                }

                                const contentType = response.headers.get('Content-Type') || '';
                                const blob = await response.blob();
                                const blobUrl = URL.createObjectURL(blob);

                                // Check if it's a PDF
                                if (contentType.includes('pdf') || url.toLowerCase().includes('.pdf')) {
                                    document.getElementById('loading').style.display = 'none';
                                    document.getElementById('reader').style.display = 'none';
                                    const pdfViewer = document.createElement('iframe');
                                    pdfViewer.id = 'pdf-viewer';
                                    pdfViewer.src = blobUrl;
                                    document.body.appendChild(pdfViewer);
                                    return;
                                }

                                // Assume it's an ePub
                                const book = ePub(blobUrl);
                                const rendition = book.renderTo('reader', {
                                    width: '100%',
                                    height: '100%',
                                    spread: 'none'
                                });

                                rendition.display();

                                document.getElementById('loading').style.display = 'none';
                                document.getElementById('controls').style.display = 'flex';

                                document.getElementById('prev').addEventListener('click', () => rendition.prev());
                                document.getElementById('next').addEventListener('click', () => rendition.next());

                                // Touch swipe navigation
                                let touchStartX = 0;
                                document.addEventListener('touchstart', (e) => {
                                    touchStartX = e.touches[0].clientX;
                                }, { passive: true });

                                document.addEventListener('touchend', (e) => {
                                    const touchEndX = e.changedTouches[0].clientX;
                                    const diff = touchStartX - touchEndX;
                                    if (Math.abs(diff) > 50) {
                                        if (diff > 0) rendition.next();
                                        else rendition.prev();
                                    }
                                }, { passive: true });

                            } catch (error) {
                                console.error('Error loading ebook:', error);
                                document.getElementById('loading').style.display = 'none';
                                document.getElementById('error').style.display = 'block';
                                document.getElementById('error').textContent = 'Failed to load ebook: ' + error.message;
                            }
                        })();
                    </script>
                </body>
                </html>
            """.trimIndent()

            // Load the HTML
            webView.loadDataWithBaseURL(
                "https://jellyfin.local",
                readerHtml,
                "text/html",
                "UTF-8",
                null
            )

            // Hide the image and add WebView to parent
            imageView?.visibility = android.view.View.GONE
            parent.addView(webView)
        }
    }
}
