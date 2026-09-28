package app.epanode

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.epanode.core.*
import app.epanode.playback.PlaybackService
import app.epanode.playback.PlayerConnection
import androidx.media3.common.Player
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class EpanodeFunctionalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: EpanodeApp
    private val fixtureUris = mutableListOf<Uri>()
    private lateinit var songs: List<Track>
    private lateinit var connection: PlayerConnection
    @Before fun seedLocalAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant app.epanode android.permission.READ_MEDIA_AUDIO").close()
        instrumentation.uiAutomation.executeShellCommand("pm grant app.epanode android.permission.POST_NOTIFICATIONS").close()
        app = instrumentation.targetContext.applicationContext as EpanodeApp
        runBlocking(Dispatchers.IO) {
            val fixtures = listOf("Night Drive" to "Low Season", "Slow Motion" to "Low Season", "Golden Hour" to "The Paper Kites")
            songs = fixtures.mapIndexed { i, pair ->
                val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, "epanode_test_${System.nanoTime()}_$i.wav"); put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav"); put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/EpanodeTest"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
                val uri = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
                fixtureUris += uri
                app.contentResolver.openOutputStream(uri)!!.use { it.write(wave(12, 220.0 + i * 110)) }
                app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                app.scanner.readUri(uri, pair.first + ".wav", "Music/EpanodeTest", "test")!!.copy(title = pair.first, artist = pair.second, album = "After Hours", lyrics = "[00:00.00]In the quiet of the night\n[00:03.00]We find our way home", edited = true)
            }
            app.store.upsertTracks(songs)
        }
        compose.waitUntil(15000) { app.store.library.value.tracks.any { it.id == songs[0].id } }
        compose.runOnUiThread { connection = PlayerConnection(app) }
        compose.waitUntil(10000) { connection.controller != null }
    }
    @After fun cleanFiles() {
        if (::connection.isInitialized) compose.runOnUiThread {
            connection.controller?.apply { pause(); clearMediaItems() }; connection.release()
        }
        fixtureUris.forEach { app.contentResolver.delete(it, null, null); app.store.writableDatabase.delete("tracks", "id=?", arrayOf(MusicLogic.id(it.toString()))) }
        app.store.refresh()
        PlaybackService.playbackError.value = null
    }
    @Test fun localListeningPlaylistBestPartAndLyricsJourney() {
        compose.onAllNodesWithText("Library").onFirst().performClick()
        compose.onNodeWithText("Songs, artists, albums, lyrics…").performTextInput("Nigt Drive")
        compose.waitUntil(5000) { compose.onAllNodesWithText("Night Drive").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Night Drive").performClick()
        compose.waitUntil(15000) { PlaybackService.playbackError.value == null && app.store.library.value.tracks.any { it.id == songs[0].id && it.plays > 0 } }
        openSongMenu()
        compose.onNodeWithText("Like song").performScrollTo().performClick()
        compose.waitUntil(5000) { app.store.library.value.tracks.find { it.id == songs[0].id }?.liked == true }
        openSongMenu()
        compose.onNodeWithText("Add to playlist").performScrollTo().performClick()
        compose.onNodeWithText("New playlist name").performTextInput("Late nights")
        compose.onNodeWithText("Create & add").performScrollTo().performClick()
        compose.waitUntil(5000) { app.store.library.value.playlists.any { it.name == "Late nights" && songs[0].id in it.trackIds } }
        openSongMenu()
        compose.onNodeWithText("Save a best part").performScrollTo().performClick()
        compose.onNodeWithText("Start · m:ss.sss").performTextReplacement("0:01.000")
        compose.onNodeWithText("End · m:ss.sss").performTextReplacement("0:02.500")
        compose.onNodeWithText("Name this moment").performTextReplacement("That perfect chorus")
        compose.onNodeWithText("Save best part").performScrollTo().performClick()
        compose.waitUntil(5000) { app.store.library.value.parts.any { it.label == "That perfect chorus" } }
        compose.onAllNodesWithText("Best parts").onFirst().performClick()
        compose.onNodeWithContentDescription("Loop That perfect chorus").performScrollTo().performClick()
        compose.onAllNodesWithText("Night Drive").onLast().performClick()
        compose.onNodeWithText("BEST PART ON REPEAT").assertIsDisplayed()
        compose.onNodeWithText("Lyrics").performClick()
        compose.onNodeWithText("In the quiet of the night").assertExists()
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close player").performClick()
    }
    @Test fun persistentStoreRescanBackupAndRangeValidation() {
        runBlocking(Dispatchers.IO) {
            val t = songs[0]
            app.store.update(t.copy(title = "My title", liked = true, edited = true))
            app.store.upsertTracks(listOf(t.copy(title = "Wrong scanner name")))
            assertEquals("My title", app.store.library.value.tracks.first { it.id == t.id }.title)
            assertTrue(app.store.library.value.tracks.first { it.id == t.id }.liked)
            val p = app.store.createPlaylist("Fixture playlist", listOf(t.id, t.id))
            assertEquals(1, app.store.library.value.playlists.first { it.id == p }.trackIds.size)
            val part = BestPart("fixture-loop", t.id, "The hook", 1000, 2500)
            app.store.savePart(part)
            val backup = app.store.exportJson()
            app.store.deletePlaylist(p); app.store.deletePart(part.id)
            app.store.restoreJson(backup)
            assertEquals(part, app.store.library.value.parts.first { it.id == part.id })
            assertEquals(listOf(t.id), app.store.library.value.playlists.first { it.id == p }.trackIds)
            try { app.store.savePart(part.copy(endMs = t.durationMs + 1)); fail("Invalid loop was saved") } catch (_: IllegalArgumentException) { }
            app.store.refresh()
            assertEquals("My title", app.store.library.value.tracks.first { it.id == t.id }.title)
        }
    }
    @Test fun actualAudioEngineLoopsClipsAndAdvancesHighlightQueue() {
        val part = BestPart("engine-part-a", songs[0].id, "Loop A", 1000, 2000)
        val second = BestPart("engine-part-b", songs[1].id, "Loop B", 2000, 3500)
        runBlocking(Dispatchers.IO) { app.store.savePart(part); app.store.savePart(second) }
        compose.runOnUiThread {
            connection.play(listOf(songs[0]), highlights = true, singlePart = part)
        }
        // MediaController 1.8.1 suppresses onMediaItemTransition when the item is unchanged.
        // Observe real engine position wrapping, rather than a callback it never forwards.
        var previousPosition = 0L
        var wraps = 0
        var diagnostic = "Not sampled"
        try {
            compose.waitUntil(15000) {
                compose.runOnUiThread {
                    val c = connection.controller!!
                    val position = c.currentPosition
                    diagnostic = "state=${c.playbackState}, playing=${c.isPlaying}, duration=${c.duration}, position=$position, repeat=${c.repeatMode}, wraps=$wraps, error=${c.playerError}"
                    if (c.isPlaying && previousPosition - position > 400) wraps++
                    previousPosition = position
                }
                wraps >= 2
            }
        } catch (e: Throwable) { throw AssertionError("Clip did not wrap twice: $diagnostic", e) }
        compose.runOnUiThread { assertTrue(connection.controller!!.isPlaying); assertEquals(1000L, connection.controller!!.duration); connection.play(songs.take(2), parts = listOf(part, second), highlights = true) }
        compose.waitUntil(12000) { connection.state.value.trackId == songs[1].id }
        compose.runOnUiThread { assertEquals(Player.REPEAT_MODE_ALL, connection.controller!!.repeatMode); connection.controller!!.pause() }
    }
    private fun openSongMenu() {
        compose.onAllNodesWithContentDescription("More options for Night Drive").onFirst()
            .performScrollTo().assertIsDisplayed().performClick()
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Play next").fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("Song menu did not open:\n" + compose.onRoot().printToString(), e)
        }
    }
    private fun wave(seconds: Int, frequency: Double): ByteArray {
        val sampleRate = 16000; val count = seconds * sampleRate; val size = count * 2
        val out = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()); out.putInt(size + 36); out.put("WAVEfmt ".toByteArray()); out.putInt(16); out.putShort(1); out.putShort(1); out.putInt(sampleRate); out.putInt(sampleRate * 2); out.putShort(2); out.putShort(16); out.put("data".toByteArray()); out.putInt(size)
        repeat(count) { i -> out.putShort((sin(2 * Math.PI * frequency * i / sampleRate) * 2500).toInt().toShort()) }
        return out.array()
    }
}
