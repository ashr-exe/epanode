package app.epanode.core

import app.epanode.imports.Catalog
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*

/** Opt in with EPANODE_LIVE_TESTS=1. Provider failures are not hidden as passing tests. */
class CatalogLiveTest {
    @Test fun searchAndResolveAnOpenMusicTrack() {
        assumeTrue(System.getenv("EPANODE_LIVE_TESTS") == "1")
        val tracks = Catalog.search("Kevin MacLeod Carefree")
        assertTrue(tracks.isNotEmpty())
        val track = tracks.first()
        val stream = Catalog.stream(track.url)
        assertTrue(stream.audioUrl.startsWith("https://"))
        assertTrue(stream.remote.durationMs > 0)
        Catalog.client.newCall(okhttp3.Request.Builder().url(stream.audioUrl).header("Range", "bytes=0-4095").build()).execute().use { response ->
            assertTrue("Audio server returned ${response.code}", response.isSuccessful)
            val bytes = response.body!!.byteStream().use { it.readBounded(4096) }
            assertTrue("No audio bytes", bytes.size > 100)
        }
        println("LIVE music search and audio resolution succeeded; format=${stream.extension}")
    }
    @Test fun spotifyPublicTrackMetadata() {
        assumeTrue(System.getenv("EPANODE_LIVE_TESTS") == "1")
        val (name, tracks) = Catalog.spotify("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(tracks.isNotEmpty()); assertTrue(tracks.first().title.isNotBlank())
        println("LIVE Spotify public track metadata succeeded")
    }
}
