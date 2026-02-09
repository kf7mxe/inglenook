// --- CHANGE THIS ---
// Old (Broken in Webpack):
// @JsModule("epubjs/dist/epub.js")
// external val EpubModule: dynamic

// New (Correct):
@JsModule("epubjs")
@JsNonModule
external val EpubModule: dynamic

@JsModule("jszip")
@JsNonModule
external val JsZipModule: dynamic