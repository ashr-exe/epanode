package app.epanode.core

import org.junit.Assert.*
import org.junit.Test
import app.epanode.ui.parseTime
import kotlin.system.measureTimeMillis

class MusicLogicTest {
    private fun song(id: String, title: String = id, artist: String = "Artist", lyrics: String = "") = Track(id, "content://media/$id", title, artist, durationMs = 180000, lyrics = lyrics)
    @Test fun embeddedTagsBeatWeirdFilenames() {
        assertEquals(Triple("Teardrop", "Massive Attack", "tags"), MusicLogic.metadata("9d0af129812.mp3", "Teardrop", "Massive Attack"))
    }
    @Test fun filenameCleanupIsConservative() {
        assertEquals(Triple("A Song", "An Artist", "filename"), MusicLogic.metadata("03 An Artist - A_Song (Official Audio).mp3", null, "<unknown>"))
        assertEquals("needs review", MusicLogic.metadata("918726381763.mp3", null, null).third)
        assertEquals("Unknown artist", MusicLogic.metadata("918726381763.mp3", null, null).second)
        assertEquals("Song (Live)", MusicLogic.metadata("Artist - Song (Live).mp3", null, null).first)
    }
    @Test fun unicodeAndMultilingualSearchWorks() {
        assertEquals("beyonce deja vu", MusicLogic.normalize("Beyoncé — Déjà Vu"))
        assertTrue(MusicLogic.score("गीत", "मेरा गीत") > 0)
        assertNotEquals(MusicLogic.normalize("गीत"), MusicLogic.normalize("गत"))
        assertTrue(MusicLogic.score("東京", "東京の音楽") > 0)
    }
    @Test fun fuzzySearchAcceptsTyposButNotUnrelatedWords() {
        val tracks = listOf(song("1", "Midnight City", "M83"), song("2", "Midnight Train", "Other"))
        assertEquals("1", MusicLogic.search(tracks, "midnigt city").first().id)
        assertEquals(0, MusicLogic.search(tracks, "unknown pineapple").size)
        assertEquals(listOf("1"), MusicLogic.search(tracks, "M83").map { it.id })
    }
    @Test fun lyricQueriesAreIndexedAndRankBelowTitles() {
        val tracks = listOf(song("1", "Hello world"), song("2", "A song", lyrics = "And then I said hello world"))
        assertEquals(listOf("1", "2"), MusicLogic.search(tracks, "hello world").map { it.id })
    }
    @Test fun searchDoesNotMatchOnlyOneQueryWord() {
        assertEquals(0, MusicLogic.score("midnight city", "Midnight Train"))
    }
    @Test fun recommendationIsDeterministicAndExcludesSeed() {
        val tracks = (0..80).map { song("$it").copy(liked = it % 3 == 0, plays = it % 7) }
        val a = MusicLogic.recommendations(tracks, tracks[0], 10)
        assertEquals(a, MusicLogic.recommendations(tracks.shuffled(), tracks[0], 10))
        assertEquals(50, a.size)
        assertFalse(a.any { it.id == "0" })
        assertNotEquals(a, MusicLogic.recommendations(tracks, tracks[0], 11))
    }
    @Test fun clipsRejectInvalidBoundaries() {
        assertTrue(BestPart("a", "t", "Chorus", 1000, 1500).validFor(5000))
        assertFalse(BestPart("a", "t", "Chorus", -1, 1000).validFor(5000))
        assertFalse(BestPart("a", "t", "Chorus", 5000, 5001).validFor(5000))
        assertFalse(BestPart("a", "t", "Chorus", 2000, 1000).validFor(5000))
    }
    @Test fun lrcSupportsRepeatedTimestampsOffsetsAndFractions() {
        val lines = MusicLogic.lyrics("[ar:Artist]\n[offset:-100]\n[00:02.50][01:03.123]A line\n[00:01]First")
        assertEquals(listOf(900L, 2400L, 63023L), lines.map { it.timeMs })
        assertEquals("A line", lines.last().text)
        assertTrue(MusicLogic.lyrics("plain lyrics").isEmpty())
    }
    @Test fun timeEntryHasMillisecondPrecision() {
        assertEquals(62501L, parseTime("1:02.501"))
        assertEquals(500L, parseTime("0.5"))
        assertNull(parseTime("1:90")); assertNull(parseTime("NaN")); assertNull(parseTime("-1")); assertNull(parseTime("Infinity"))
    }
    @Test fun maliciousHostsAreNotClassifiedAsProviders() {
        assertEquals("unsupported", MusicLogic.sharedKind("https://open.spotify.com.evil.example/track/x"))
        assertEquals("invalid", MusicLogic.sharedKind("https://name:password@open.spotify.com/track/x"))
        assertEquals("invalid", MusicLogic.sharedKind("file:///storage/song.mp3"))
        assertEquals("invalid", MusicLogic.sharedKind("http://example.org/song.mp3"))
        assertEquals("direct", MusicLogic.sharedKind("https://example.org/a.MP3?token=1"))
    }
    @Test fun shareTextAndPlaylistUrlsNormalizeCorrectly() {
        assertEquals("https://youtu.be/jfKfPfyJRdk", MusicLogic.extractUrl("Listen: https://youtu.be/jfKfPfyJRdk."))
        assertEquals("https://www.youtube.com/watch?v=jfKfPfyJRdk", MusicLogic.canonicalUrl("https://youtu.be/jfKfPfyJRdk?si=abc"))
        assertEquals("https://www.youtube.com/playlist?list=PL123", MusicLogic.canonicalUrl("https://music.youtube.com/watch?v=jfKfPfyJRdk&list=PL123"))
    }
    @Test fun filenamesCannotEscapeStorageFolder() {
        val name = MusicLogic.safeFilename("../../x\\x:foo\u0000/bar*")
        assertFalse(name.contains('/')); assertFalse(name.contains('\\')); assertFalse(name.contains('\u0000'))
        assertTrue(name.length <= 100)
    }
    @Test fun identityIsStableAndDoesNotDependOnTitle() {
        assertEquals(MusicLogic.id("content://media/1"), MusicLogic.id("content://media/1"))
        assertNotEquals(MusicLogic.id("content://media/1"), MusicLogic.id("content://media/2"))
    }
    @Test fun tenThousandTrackSearchHasBoundedRuntime() {
        val library = (0 until 10000).map { song("$it", "Test song number $it", "Artist ${it % 100}") }
        var found: List<Track> = emptyList()
        val elapsed = measureTimeMillis { found = MusicLogic.search(library, "test song 9999") }
        assertEquals("9999", found.first().id)
        println("PERFORMANCE search_10000_tracks_ms=$elapsed")
        assertTrue("Search took ${elapsed}ms", elapsed < 4000)
    }
}
