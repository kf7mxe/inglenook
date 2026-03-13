# Inglenook

**A cozy audiobook and ebook client for Jellyfin.**

*Inglenook* — a warm nook by the fireplace — captures the feeling of curling up with a good book. Escape on an adventure from the comfort of your favorite reading spot.

---

## Why Inglenook?

Jellyfin self-hosted media server has worked great for me, but its support for books and audiobooks has always been limited. Rather than splitting your library across multiple applications, Inglenook lets you keep all your media managed in one place and simply use a purpose-built client for your reading and listening.

Inglenook is designed from the ground up for audiobooks and ebooks, providing a polished experience that Jellyfin's other clients don't offer.

## Companion Plugin

For the best experience, pair Inglenook with the **[Inglenook Jellyfin Plugin](https://github.com/kf7mxe/Inglenook-jellyfin-plugin)**, which enhances Jellyfin's book metadata handling and library organization.

> **Required:** The [Jellyfin Bookshelf Plugin](https://github.com/jellyfin/jellyfin-plugin-bookshelf) is needed for Inglenook to work properly. The Inglenook plugin is optional but recommended, as it adds additional features on top of Bookshelf.

## Features

### Audiobook Player
- Full playback controls with play/pause, skip forward (30s), and skip backward (15s)
- Adjustable playback speed (0.5x – 2.0x)
- Chapter navigation with chapter-aware progress tracking
- Sleep timer with multiple modes — timed (15/30/45/60 min) or end-of-chapter
- Background playback with media notification controls (Android)
- Automatic position syncing back to your Jellyfin server

### Ebook Reader
- EPUB support with reading position persistence
- Native rendering via Readium (Android) and epub.js (Web)
- Seamless resume — pick up right where you left off

### Library Management
- Browse by books, authors, or series
- Search with live results
- Filter by audiobook or ebook
- Toggle between grid and list views
- Create personal bookshelves to organize your collection

### Bookmarks
- Save bookmarks at any position with optional notes
- Jump to any bookmark instantly
- Manage bookmarks per book

### Offline & Downloads
- Download audiobooks and ebooks for offline listening and reading
- Background download service with progress notifications (Android)
- Manual offline mode toggle with automatic fallback to cached data
- Smart API caching for a responsive experience even on slow connections

### Multi-Server Support
- Connect to multiple Jellyfin servers
- Authenticate with username/password or Quick Connect
- Per-server scoped data (downloads, bookshelves, bookmarks, playback positions)

### Theming
- 9+ built-in theme presets including Cozy, Autumn Cabin, Midnight, and more
- Full color customization — primary, secondary, accent, background, and surface colors
- Adjustable corner radius, padding, elevation, and opacity
- Custom wallpaper support with blur effects
- Dynamic backgrounds from currently playing book covers

### Demo Mode
- Try the app without a Jellyfin server via the built-in demo mode on web deployments

## Platform Support

| Platform | Status |
|----------|--------|
| Android  | Supported |
| Web      | Supported |
| iOS      | Planned — KiteUI fully supports iOS; Apple hardware needed for building and testing platform-specific implementations |
| Desktop  | Planned — awaiting KiteUI desktop target stabilization |

## Getting Started

### Prerequisites

- JDK 17+
- Android SDK (for Android builds)
- A running [Jellyfin](https://jellyfin.org/) server with audiobooks or ebooks in your library

### Building

```bash
# Build all modules
./gradlew build

# Android debug APK
./gradlew :apps:installDebug

# Web development server with hot reload
./gradlew :apps:jsViteDev

# Production web build
./gradlew :apps:viteBuild
```

## Tech Stack

- **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** — shared business logic across all platforms
- **[KiteUI](https://github.com/lightningkite/kiteui)** — declarative cross-platform UI framework (see [KiteUI note](#kiteui-experimental-branch) below)
- **[Media3 / ExoPlayer](https://developer.android.com/media/media3)** — audio playback on Android
- **[Readium](https://readium.org/)** — ebook rendering on Android
- **[epub.js](https://github.com/futurepress/epub.js)** — ebook rendering on the web
- **[Ktor](https://ktor.io/)** — HTTP client for Jellyfin API communication
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** — JSON serialization

## KiteUI Experimental Branch

Inglenook currently uses an experimental build of KiteUI (`kf7mxe-experimental` branch) to support the neumorphism theme and Lottie animation color overrides. The neumorphism theme is experimental and may exhibit visual quirks.

**To build from source:**

1. Clone [KiteUI](https://github.com/lightningkite/kiteui)
2. Check out the `kf7mxe-experimental` branch
3. Publish to Maven Local via the Gradle `publishToMavenLocal` task
4. Build Inglenook as normal

**To build with mainline KiteUI instead:** remove the neumorphism theme preset and the Lottie animation color overrides from the source, then build using the standard KiteUI release.


## Roadmap

### Phase 1 — Audiobook & Ebook Client (Complete)
A polished, full-featured client for consuming audiobooks and ebooks from your Jellyfin library. Focus on a clean, enjoyable user experience with robust playback, offline support, and library management.

### Phase 2 — Media Conversion
Convert between audiobook and ebook formats:

- **Audiobook to Ebook** — Transcribe audiobooks to text using speech-to-text models. A known challenge is handling the unique character and place names common in fiction. A planned approach involves using an LLM to detect likely transcription errors, then presenting an interface where users can review flagged names alongside short audio clips for verification.

- **Ebook to Audiobook** — Generate audiobook narration from ebook text using modern open-weight text-to-speech models. Features under consideration include:
  - Voice cloning from a reference audio clip, allowing books to be read in a voice of your choosing
  - LLM-assisted extraction of contextual cues (character emotion, tone, dialogue attribution) to guide expressive speech synthesis with natural intonation and emotional variation

## Contributing

Contributions are welcome! Whether it's bug reports, feature requests, or pull requests — all input is appreciated.

### AI-Assisted Contributions

This project was built with the assistance of [Claude Code](https://claude.ai/code) as an experiment in AI-assisted development. The majority of AI-generated code has been reviewed, adjusted, and rewritten. AI output was primarily used as tracer code — an initial approximation of the desired implementation that was then refined by hand.

The use of AI tools in contributions is welcomed, provided the following standards are met:

- All AI-generated code must be thoroughly reviewed and tested before submission.
- Code must be readable, and easy for a human to understand and maintain.
- AI tools often produce approximate but incomplete implementations. Only finished, well-written code will be accepted — treat AI output as a starting point, not production ready.

## License

See [LICENSE](LICENSE) for details.
