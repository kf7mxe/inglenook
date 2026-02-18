# Inglenook - Feature Summary

A Kotlin Multiplatform audiobook and ebook reader app using Jellyfin as its media backend. Targets Android and Web (JS) with shared UI via KiteUI.

---

## App-Wide Features

- **Jellyfin Integration** — Connects to Jellyfin servers for library browsing, streaming, playback reporting, and Quick Connect authentication
- **Multi-Server Support** — Configure and switch between multiple Jellyfin servers; all local data (downloads, bookshelves, bookmarks) is scoped per server
- **Offline Mode** — Auto-detects connectivity loss and falls back to cached data; manual offline toggle available; periodic server reachability checks
- **Download Manager** — Download audiobooks for offline playback with progress tracking; Android uses a background service with notifications
- **API Caching** — In-memory cache with tiered TTLs (1 min / 5 min / 15 min); serves stale data on network errors
- **Image Caching** — Platform-specific image caching with blur support and preloading
- **Theming** — 9 presets (Cozy, Autumn Cabin, Midnight, Sunrise, Material, Hackerman, Clouds, Obsidian, Custom) with full color/layout customization, wallpaper support, and blurred book-cover backgrounds
- **Library Filtering** — Select which Jellyfin libraries to display across all views
- **Grid / List View Toggle** — Persisted preference for books, authors, series, and bookshelves
- **Bottom Navigation** — Home, Library, Bookshelf, Settings tabs with a global search button

---

## Pages & Per-Page Features

### Dashboard (`/`)
- Continue Listening section (in-progress audiobooks)
- Recommended For You (Jellyfin suggestions)
- Recently Added section
- Downloaded Books section (visible in offline mode)
- Server availability banner when manually offline
- Connection error handling with retry

### Jellyfin Setup (`/connect-jellyfin`)
- Server URL input
- Username/password login
- Quick Connect (6-digit code) login
- Loading states and error messages
- Cancel button when a server is already configured

### Library (`/library`)
- Tabbed view: Books, Authors, Series
- Tab selection preserved in URL query parameters

#### Books Tab
- Search bar (title, author, series)
- Book type filter (All / Audio / Ebooks)
- Grid/List view toggle
- Infinite scrolling
- Connection error handling

#### Authors Tab
- Search bar
- Grid/List view toggle
- Author cards with image or fallback icon

#### Series Tab
- Search bar
- Grid/List view toggle
- Series cards with cover image and book count

### Book Detail (`/book/{bookId}`)
- Cover image (cached)
- Title, clickable authors, narrator, series info
- Duration and progress indicator
- **Audiobooks:** Play/Continue button, Download button with progress, Add to Bookshelf
- **Ebooks:** Read button (launches reader), Open in Browser, Add to Bookshelf
- Expandable description
- Expandable chapters list (audiobooks)
- Expandable bookmarks section with playback jump
- Blurred background cover (when enabled in settings)

### Author Detail (`/authors-detail/{authorId}`)
- Author image or fallback icon
- Author name and overview/bio
- Book count
- Grid/List view of author's books

### Series Detail (`/series/{seriesName}`)
- Series cover image (first book)
- Book count and aggregated author names
- Grid/List view of books sorted by series index
- Play buttons on book cards

### Search (`/search`)
- Live search with 300ms debounce
- Offline search over downloaded books
- Results split into Books and Authors sections
- Book cards with cover, title, author, series
- Author cards with image
- Clear search button

### Bookshelf (`/bookshelf`)
- Create new bookshelf dialog
- Grid of bookshelves with book counts
- Empty state with create prompt

### Bookshelf Detail (`/bookshelf/{bookshelfId}`)
- Bookshelf name header
- Edit mode toggle (shows remove buttons per book)
- Grid/List view toggle

### Downloads (`/downloads`)
- Storage info card (total size, book count)
- Active downloads with progress bars, status, and cancel
- Downloaded books list with file size and delete option
- Click a downloaded book to play

### Settings (`/settings`)
- **Jellyfin Servers:** List configured servers, active indicator, switch/remove/add servers
- **Theme:** Quick preset selection, link to full theme customization
- **Connectivity:** Offline mode toggle, server reachable banner
- **Downloads:** Link to downloads management page
- **Libraries:** Filter which Jellyfin libraries are shown (select all / clear)
- **About:** App name and version

### Theme Settings (`/settings/theme`)
- Theme preset selector (9 presets)
- Background effects: wallpaper upload, blur intensity, use playing book cover as wallpaper, blurred cover background toggle
- Accent color picker (8 presets + hex input) for customizable presets
- Full custom theme editor: primary/background/outline colors, opacity sliders, layout sliders (corner radius, padding, gap, elevation, outline width)
- Live theme preview

### Ebook Reader (`/reader/{bookId}`)
- Full-screen ebook rendering
- **Web:** epub.js integration with navigation controls
- **Android:** Launches Readium-based ReaderActivity

---

## Now Playing Overlay

Displayed as a bottom sheet (Android) or dialog (Web), with a mini player preview in the bottom bar.

- Large cover image
- Book title (clickable to detail page) and authors (clickable)
- Chapter selector dialog
- Playback controls: skip back 15s, play/pause, skip forward 30s
- Progress slider with position/duration display
- Playback speed selector (0.5x - 2.0x)
- Sleep timer: time-based (15/30/45/60 min), end-of-chapter, countdown display
- Stop playback button
- Blurred background (when enabled)

---

## Platform-Specific Implementations

| Feature | Android | Web (JS) |
|---|---|---|
| Audio playback | ExoPlayer + foreground service | HTML5 Audio API |
| Downloads | Background service with notifications | Browser download |
| Ebook reading | Readium Kotlin Toolkit | epub.js |
| Image caching | Platform file cache | In-memory |

---

## Data Persistence

| Scope | Data |
|---|---|
| Per server | Downloads, bookshelves, bookmarks, playback positions, last played book |
| Global | Theme settings, view mode, offline mode, server configs, active server, selected libraries |
| Session only | API cache, image cache, active download progress, connectivity state |
