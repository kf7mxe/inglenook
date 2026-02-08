package com.kf7mxe.inglenook.ebook

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.kiteui.models.ImageScaleType
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement

/**
 * JS implementation of ebook reader using an iframe with epub.js for ePub files.
 */
actual fun ViewWriter.ebookReader(
    downloadUrl: String,
    authHeader: String
) {
    // Use image as container since it provides rView access
    image {
        scaleType = ImageScaleType.Fit

        val imageElement = rView.native as? HTMLElement
        val parent = imageElement?.parentElement as? HTMLElement

        if (parent != null && imageElement != null) {
            // Hide the image element
            imageElement.style.display = "none"

            // Style the parent container
            parent.style.width = "100%"
            parent.style.height = "100%"
            parent.style.minHeight = "500px"
            parent.style.position = "relative"

            // Create an iframe for rendering
            val iframe = document.createElement("iframe") as HTMLIFrameElement
            iframe.style.apply {
                width = "100%"
                height = "100%"
                border = "none"
                minHeight = "500px"
            }

            // Create an HTML document that loads epub.js and renders the book
            val escapedUrl = downloadUrl.replace("'", "\\'").replace("\"", "\\\"")
            val escapedAuth = authHeader.replace("'", "\\'").replace("\"", "\\\"")

            val readerHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Reader</title>
                    <script src="https://cdn.jsdelivr.net/npm/epubjs/dist/epub.min.js"></script>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { height: 100%; overflow: hidden; background: #fafafa; }
                        #reader { width: 100%; height: calc(100% - 50px); }
                        #loading {
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100%;
                            font-family: system-ui, sans-serif;
                        }
                        #controls {
                            position: fixed;
                            bottom: 0;
                            left: 0;
                            right: 0;
                            display: none;
                            justify-content: center;
                            gap: 10px;
                            padding: 10px;
                            background: #fff;
                            border-top: 1px solid #ddd;
                            z-index: 1000;
                        }
                        #controls button {
                            padding: 10px 20px;
                            font-size: 16px;
                            cursor: pointer;
                            background: #333;
                            color: white;
                            border: none;
                            border-radius: 5px;
                        }
                        #controls button:hover { background: #555; }
                        #error {
                            display: none;
                            padding: 20px;
                            text-align: center;
                            font-family: system-ui, sans-serif;
                            color: #c00;
                        }
                        #pdf-container { width: 100%; height: 100%; }
                        #pdf-container iframe { width: 100%; height: 100%; border: none; }
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
                    <div id="pdf-container" style="display:none;"></div>
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
                                    const pdfContainer = document.getElementById('pdf-container');
                                    pdfContainer.style.display = 'block';
                                    pdfContainer.style.height = '100%';
                                    const pdfViewer = document.createElement('iframe');
                                    pdfViewer.src = blobUrl;
                                    pdfContainer.appendChild(pdfViewer);
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

                                // Keyboard navigation
                                document.addEventListener('keydown', (e) => {
                                    if (e.key === 'ArrowLeft') rendition.prev();
                                    if (e.key === 'ArrowRight') rendition.next();
                                });

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

            // Set the iframe content
            iframe.srcdoc = readerHtml

            // Add iframe to parent
            parent.appendChild(iframe)
        }
    }
}
