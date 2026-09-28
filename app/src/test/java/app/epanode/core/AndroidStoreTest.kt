package app.epanode.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.epanode.data.LibraryStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class)
class AndroidStoreTest {
    private lateinit var store: LibraryStore
    private val track = Track("a", "content://media/external/audio/media/1", "Original", "Artist", durationMs = 180000)
    @Before fun create() { val context = ApplicationProvider.getApplicationContext<Application>(); context.deleteDatabase("epanode.db"); store = LibraryStore(context); store.upsertTracks(listOf(track)) }
    @After fun close() { store.close() }
    @Test fun editsLikesAndHistorySurviveRescansAndDatabaseReopen() {
        store.update(track.copy(title = "Correct title", liked = true, edited = true)); store.played(track.id)
        store.upsertTracks(listOf(track.copy(title = "Incorrect rescan")))
        val t = store.library.value.tracks.single()
        assertEquals("Correct title", t.title); assertTrue(t.liked); assertEquals(1, t.plays)
        store.close(); store = LibraryStore(ApplicationProvider.getApplicationContext()); store.refresh()
        assertEquals(t, store.library.value.tracks.single())
    }
    @Test fun scannerOnlyPrunesDeviceRowsAfterCompleteScan() {
        store.upsertTracks(listOf(track.copy(id = "saf", uri = "content://documents/2", source = "folder")))
        store.upsertTracks(emptyList(), emptySet())
        assertEquals(listOf("saf"), store.library.value.tracks.map { it.id })
    }
    @Test fun playlistOrderDedupAndRemovalPersist() {
        val p = store.createPlaylist("Mix", listOf("a", "b", "a"))
        store.addToPlaylist(p, "b"); store.addToPlaylist(p, "c")
        assertEquals(listOf("a", "b", "c"), store.library.value.playlists.single().trackIds)
        store.savePlaylist(store.library.value.playlists.single().copy(trackIds = listOf("c", "a")))
        store.refresh(); assertEquals(listOf("c", "a"), store.library.value.playlists.single().trackIds)
        store.deletePlaylist(p); assertTrue(store.library.value.tracks.isNotEmpty())
    }
    @Test fun backupRestoresMetadataPlaylistsAndClipsWithoutAddingUntrustedPaths() {
        val p = store.createPlaylist("Mix", listOf(track.id))
        store.savePart(BestPart("c", track.id, "Chorus", 10000, 12000))
        store.update(track.copy(lyrics = "[00:01]Words", liked = true, edited = true))
        val backup = store.exportJson()
        store.deletePlaylist(p); store.deletePart("c"); store.update(track)
        store.restoreJson(backup)
        assertEquals("Chorus", store.library.value.parts.single().label)
        assertTrue(store.library.value.tracks.single().liked)
        assertEquals("[00:01]Words", store.library.value.tracks.single().lyrics)
        assertEquals(listOf(track.id), store.library.value.playlists.single().trackIds)
        val json = org.json.JSONObject(backup)
        json.getJSONArray("tracks").put(LibraryStore.json(track.copy(id = "evil", uri = "https://evil.invalid/file.mp3")))
        store.restoreJson(json.toString()); assertEquals(1, store.library.value.tracks.size)
    }
    @Test fun invalidBackupDoesNotPartiallyMutateDatabase() {
        val before = store.exportJson()
        val j = org.json.JSONObject(before); j.getJSONArray("parts").put(org.json.JSONObject().put("id", "bad"))
        try { store.restoreJson(j.toString()); fail("Corrupt backup accepted") } catch (_: org.json.JSONException) { }
        assertEquals(before, store.exportJson())
    }
    @Test fun loopCannotExtendPastTrackOrReferToMissingAudio() {
        try { store.savePart(BestPart("c", track.id, "Hook", 179900, 181000)); fail("Bad bounds") } catch (_: IllegalArgumentException) { }
        try { store.savePart(BestPart("c", "missing", "Hook", 1000, 2000)); fail("Missing audio") } catch (_: IllegalStateException) { }
        assertTrue(store.library.value.parts.isEmpty())
    }
    @Test fun downloadProgressKeepsTheLibrarySnapshotAndCancelledJobsStayCancelled() {
        val tracks = store.library.value.tracks
        val first = DownloadEntry("job", "https://example.org/a.mp3", "Song", "Downloading", 42, playlistId = "p1", artist = "Artist", durationMs = 1000)
        store.saveDownload(first)
        store.saveDownload(first.copy(playlistId = "p2"))
        assertEquals("p1|p2", store.library.value.downloads.single().playlistId)
        assertSame(tracks, store.library.value.tracks)
        store.saveDownload(first.copy(state = "Cancelled"))
        store.saveDownload(first.copy(state = "Downloading", progress = 99))
        assertEquals("Cancelled", store.library.value.downloads.single().state)
        store.saveDownload(first.copy(state = "Queued", progress = 0))
        assertEquals("Queued", store.library.value.downloads.single().state)
        store.refresh()
        assertEquals("Artist", store.library.value.downloads.single().artist)
        assertEquals(1000L, store.library.value.downloads.single().durationMs)
    }
    @Test fun downloadsRoundTripFailureProgressAndRetryState() {
        val d = DownloadEntry("job", "https://example.org/a.mp3", "Song", "Downloading", 42)
        store.saveDownload(d); store.saveDownload(d.copy(state = "Failed", error = "Network error")); store.refresh()
        assertEquals("Network error", store.library.value.downloads.single().error)
        store.saveDownload(d.copy(state = "Queued", progress = 0)); assertEquals(1, store.library.value.downloads.size)
    }
}
