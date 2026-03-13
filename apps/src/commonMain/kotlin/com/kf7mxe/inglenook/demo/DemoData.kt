package com.kf7mxe.inglenook.demo

import com.kf7mxe.inglenook.*

object DemoData {

    val coverUrls: Map<String, String> = mapOf(
        "demo-pride" to "https://covers.openlibrary.org/b/isbn/9780141439518-L.jpg",
        "demo-sherlock" to "https://covers.openlibrary.org/b/isbn/9780140439083-L.jpg",
        "demo-frankenstein" to "https://covers.openlibrary.org/b/isbn/9780141439471-L.jpg",
        "demo-alice" to "https://covers.openlibrary.org/b/isbn/9780141439761-L.jpg",
        "demo-dracula" to "https://covers.openlibrary.org/b/isbn/9780141439846-L.jpg",
        "demo-two-cities" to "https://covers.openlibrary.org/b/isbn/9780141439600-L.jpg",
        // Ebooks
        "demo-jekyll" to "https://covers.openlibrary.org/b/isbn/9780141389509-L.jpg",
        "demo-dorian" to "https://covers.openlibrary.org/b/isbn/9780141439570-L.jpg",
    )

    val ebookUrls: Map<String, String> = mapOf(
        "demo-jekyll" to "https://www.gutenberg.org/ebooks/43.epub3.images",
        "demo-dorian" to "https://www.gutenberg.org/ebooks/174.epub3.images",
    )

    // LibriVox chapter 1 recordings hosted on Archive.org (public domain).
    // URL format: https://archive.org/download/{item_id}/{filename}
    val audioUrls: Map<String, String> = mapOf(
        "demo-pride" to "https://archive.org/download/pride_prejudice_krs_librivox/pride_and_prejudice_01_austen.mp3",
        "demo-sherlock" to "https://archive.org/download/adventures_sherlockholmes_1007_librivox/adventuresherlockholmes_01_doyle.mp3",
        "demo-frankenstein" to "https://archive.org/download/frankenstein_1107_librivox/frankenstein_01_shelley.mp3",
        "demo-alice" to "https://archive.org/download/alice_in_wonderland_librivox/wonderland_ch_01.mp3",
        "demo-dracula" to "https://archive.org/download/dracula_librivox/dracula_01_stoker.mp3",
        "demo-two-cities" to "https://archive.org/download/tale_of_two_cities_1012_librivox/taleoftwocities_01_dickens.mp3",
    )

    // Durations are approximate for each chapter 1 MP3. The player will use the
    // real duration once audio metadata loads.  1 second = 10_000_000 ticks.
    val allBooks: List<Book> = listOf(
        Book(
            id = "demo-pride",
            title = "Pride and Prejudice",
            authors = listOf(Author("demo-austen", "Jane Austen")),
            authorInfos = listOf(AuthorInfo("Jane Austen", "demo-austen")),
            narrator = "Karen Savage",
            description = "Pride and Prejudice follows the turbulent relationship between Elizabeth Bennet, the daughter of a country gentleman, and Fitzwilliam Darcy, a rich aristocratic landowner. They must overcome the titular sins of pride and prejudice in order to fall in love and marry.",
            coverImageId = "demo-pride",
            duration = 11_400_000_000L, // ~19 min
            chapters = listOf(
                Chapter("Chapter 1", 0L),
            ),
            userData = UserData(
                playbackPositionTicks = 3_000_000_000L, // ~5 min in
                playCount = 1,
            ),
            year = 1813,
            itemType = ItemType.AudioBook,
        ),
        Book(
            id = "demo-sherlock",
            title = "The Adventures of Sherlock Holmes",
            authors = listOf(Author("demo-doyle", "Arthur Conan Doyle")),
            authorInfos = listOf(AuthorInfo("Arthur Conan Doyle", "demo-doyle")),
            narrator = "Mark Nelson",
            description = "The Adventures of Sherlock Holmes is a collection of twelve stories by Arthur Conan Doyle, featuring his fictional detective Sherlock Holmes. The stories are narrated by Holmes' friend and biographer Dr. John Watson.",
            coverImageId = "demo-sherlock",
            duration = 18_000_000_000L, // ~30 min
            chapters = listOf(
                Chapter("A Scandal in Bohemia", 0L),
            ),
            userData = UserData(
                playbackPositionTicks = 6_000_000_000L, // ~10 min in
                playCount = 1,
            ),
            year = 1892,
            itemType = ItemType.AudioBook,
        ),
        Book(
            id = "demo-frankenstein",
            title = "Frankenstein",
            authors = listOf(Author("demo-shelley", "Mary Shelley")),
            authorInfos = listOf(AuthorInfo("Mary Shelley", "demo-shelley")),
            narrator = "Ruth Golding",
            description = "Frankenstein tells the story of Victor Frankenstein, a young scientist who creates a sapient creature in an unorthodox scientific experiment. Shelley started writing the story when she was 18, and the first edition was published anonymously in 1818.",
            coverImageId = "demo-frankenstein",
            duration = 12_000_000_000L, // ~20 min
            chapters = listOf(
                Chapter("Chapter 1", 0L),
            ),
            year = 1818,
            itemType = ItemType.AudioBook,
        ),
        Book(
            id = "demo-alice",
            title = "Alice's Adventures in Wonderland",
            authors = listOf(Author("demo-carroll", "Lewis Carroll")),
            authorInfos = listOf(AuthorInfo("Lewis Carroll", "demo-carroll")),
            narrator = "Karen Savage",
            description = "Alice's Adventures in Wonderland follows a young girl named Alice who falls through a rabbit hole into a subterranean fantasy world populated by peculiar, anthropomorphic creatures.",
            coverImageId = "demo-alice",
            duration = 10_800_000_000L, // ~18 min
            chapters = listOf(
                Chapter("Down the Rabbit-Hole", 0L),
            ),
            year = 1865,
            itemType = ItemType.AudioBook,
        ),
        Book(
            id = "demo-dracula",
            title = "Dracula",
            authors = listOf(Author("demo-stoker", "Bram Stoker")),
            authorInfos = listOf(AuthorInfo("Bram Stoker", "demo-stoker")),
            narrator = "Tim Hawkins",
            description = "Dracula is a Gothic horror novel that tells the story of Count Dracula's attempt to move from Transylvania to England so that he may find new blood and spread the undead curse.",
            coverImageId = "demo-dracula",
            duration = 15_000_000_000L, // ~25 min
            chapters = listOf(
                Chapter("Chapter 1 - Jonathan Harker's Journal", 0L),
            ),
            year = 1897,
            itemType = ItemType.AudioBook,
        ),
        Book(
            id = "demo-two-cities",
            title = "A Tale of Two Cities",
            authors = listOf(Author("demo-dickens", "Charles Dickens")),
            authorInfos = listOf(AuthorInfo("Charles Dickens", "demo-dickens")),
            narrator = "Paul Adams",
            description = "A Tale of Two Cities is a historical novel set in London and Paris before and during the French Revolution. It depicts the plight of the French peasantry under the brutal oppression of the aristocracy.",
            coverImageId = "demo-two-cities",
            duration = 9_000_000_000L, // ~15 min
            chapters = listOf(
                Chapter("Chapter 1 - The Period", 0L),
            ),
            year = 1859,
            itemType = ItemType.AudioBook,
        ),
        // Ebooks
        Book(
            id = "demo-jekyll",
            title = "The Strange Case of Dr. Jekyll and Mr. Hyde",
            authors = listOf(Author("demo-stevenson", "Robert Louis Stevenson")),
            authorInfos = listOf(AuthorInfo("Robert Louis Stevenson", "demo-stevenson")),
            description = "The Strange Case of Dr Jekyll and Mr Hyde is a Gothic novella about a London lawyer who investigates strange occurrences between his old friend, Dr Henry Jekyll, and the evil Edward Hyde. The work is commonly associated with the rare mental condition often called 'split personality'.",
            coverImageId = "demo-jekyll",
            year = 1886,
            itemType = ItemType.Ebook,
            fileExtension = ".epub",
        ),
        Book(
            id = "demo-dorian",
            title = "The Picture of Dorian Gray",
            authors = listOf(Author("demo-wilde", "Oscar Wilde")),
            authorInfos = listOf(AuthorInfo("Oscar Wilde", "demo-wilde")),
            description = "The Picture of Dorian Gray is a philosophical novel in which a handsome young man, Dorian Gray, has his portrait painted by artist Basil Hallward. When Dorian sees the finished painting, he wishes that it would age instead of him. His wish is fulfilled, and Dorian pursues a life of hedonism.",
            coverImageId = "demo-dorian",
            year = 1890,
            itemType = ItemType.Ebook,
            fileExtension = ".epub",
        ),
    )

    val inProgressBooks: List<Book>
        get() = allBooks.filter { (it.userData?.playbackPositionTicks ?: 0) > 0 }

    val suggestedBooks: List<Book>
        get() = allBooks.filter { it.id == "demo-frankenstein" || it.id == "demo-alice" || it.id == "demo-jekyll" }

    val recentlyAddedBooks: List<Book>
        get() = allBooks

    val authors: List<Author>
        get() = allBooks.flatMap { it.authors }.distinctBy { it.id }

    val library = JellyfinLibrary(
        id = "demo-library",
        name = "Demo Library",
        collectionType = "books",
    )
}
