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
 */
actual fun ViewWriter.ebookReader(
    downloadUrl: String,
    authHeader: String
) {
    // Use image as container since it provides rView access
    image {
        scaleType = ImageScaleType.Fit

        val imageView = rView.native as? UIView
        val container = imageView?.superview

        if (container != null) {
            // Create WKWebView configuration
            val config = WKWebViewConfiguration()

            // Create WKWebView
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
            webView.setAutoresizingMask(
                platform.UIKit.UIViewAutoresizingFlexibleWidth or
                platform.UIKit.UIViewAutoresizingFlexibleHeight
            )
            webView.setTranslatesAutoresizingMaskIntoConstraints(false)

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
                            font-family: -apple-system, sans-serif;
                        }
                        #controls {
                            position: fixed;
                            bottom: 0;
                            left: 0;
                            right: 0;
                            display: none;
                            justify-content: center;
                            gap: 20px;
                            padding: 15px;
                            padding-bottom: env(safe-area-inset-bottom, 15px);
                            background: #fff;
                            border-top: 1px solid #ddd;
                        }
                        #controls button {
                            padding: 12px 30px;
                            font-size: 17px;
                            background: #007AFF;
                            color: white;
                            border: none;
                            border-radius: 10px;
                        }
                        #error {
                            display: none;
                            padding: 20px;
                            text-align: center;
                            font-family: -apple-system, sans-serif;
                            color: #c00;
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

                                document.getElementById('loading').style.display = 'none';
                                document.getElementById('controls').style.display = 'flex';

                                document.getElementById('prev').onclick = () => rendition.prev();
                                document.getElementById('next').onclick = () => rendition.next();

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

            // Load the HTML
            webView.loadHTMLString(readerHtml, baseURL = NSURL.URLWithString("https://jellyfin.local"))

            // Hide image and add WebView to container
            imageView?.setHidden(true)
            container.addSubview(webView)

            // Set up constraints to fill the container
            webView.topAnchor.constraintEqualToAnchor(container.topAnchor).setActive(true)
            webView.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor).setActive(true)
            webView.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor).setActive(true)
            webView.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor).setActive(true)
        }
    }
}
