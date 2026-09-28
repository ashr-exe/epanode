package app.epanode.core

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.epanode.MainActivity
import app.epanode.EpanodeApp
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = EpanodeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private val serviceRule = object : org.junit.rules.ExternalResource() {
        private var service: org.robolectric.android.controller.ServiceController<app.epanode.playback.PlaybackService>? = null
        override fun before() {
            val application = ApplicationProvider.getApplicationContext<EpanodeApp>()
            service = org.robolectric.Robolectric.buildService(app.epanode.playback.PlaybackService::class.java).create()
            val intent = android.content.Intent(application, app.epanode.playback.PlaybackService::class.java).setAction("androidx.media3.session.MediaSessionService")
            org.robolectric.Shadows.shadowOf(application).setComponentNameAndServiceForBindService(android.content.ComponentName(application, app.epanode.playback.PlaybackService::class.java), service!!.get().onBind(intent))
        }
        override fun after() { service?.destroy() }
    }
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(serviceRule).around(compose)
    private val songs = listOf(
        Track("ui1", "content://media/external/audio/media/8001", "Night Drive", "Low Season", "After Hours", 210000, edited = true),
        Track("ui2", "content://media/external/audio/media/8002", "Slow Motion", "Low Season", "After Hours", 184000, edited = true),
        Track("ui3", "content://media/external/audio/media/8003", "Golden Hour", "The Paper Kites", "Bloom", 245000, edited = true))
    @Test(timeout = 90000) fun navigateSearchLikeCreatePlaylistAndSavePreciseBestPart() {
        val app = ApplicationProvider.getApplicationContext<EpanodeApp>()
        compose.mainClock.autoAdvance = false
        app.store.upsertTracks(songs)
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(100); compose.onAllNodesWithText("Play my mix").fetchSemanticsNodes().isNotEmpty() }
        capture("home")
        println("UI STEP library"); compose.onAllNodesWithText("Library").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        println("UI STEP search"); compose.onNodeWithText("Songs, artists, albums, lyrics…").performTextInput("nigt drive").also { settle() }
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(100); compose.onAllNodesWithText("Night Drive").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Slow Motion").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Slow Motion").assertDoesNotExist()
        println("UI STEP menu"); compose.onNodeWithContentDescription("More options for Night Drive").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        println("UI STEP like"); compose.onNodeWithText("Like song").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(100); app.store.library.value.tracks.first { it.id == "ui1" }.liked }
        println("UI STEP menu"); compose.onNodeWithContentDescription("More options for Night Drive").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        println("UI STEP playlist"); compose.onNodeWithText("Add to playlist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("New playlist name").performTextInput("After dark").also { settle() }
        compose.onNodeWithText("Create & add").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(100); app.store.library.value.playlists.any { it.name == "After dark" } }
        println("UI STEP menu"); compose.onNodeWithContentDescription("More options for Night Drive").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        println("UI STEP clip"); compose.onNodeWithText("Save a best part").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("Start · m:ss.sss").performTextReplacement("0:34.500").also { settle() }
        compose.onNodeWithText("End · m:ss.sss").performTextReplacement("0:51.750").also { settle() }
        compose.onNodeWithText("Name this moment").performTextReplacement("The chorus").also { settle() }
        compose.onNodeWithText("Save best part").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(100); app.store.library.value.parts.any { it.label == "The chorus" && it.startMs == 34500L && it.endMs == 51750L } }
        compose.onAllNodesWithText("Best parts").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("The chorus").assertIsDisplayed()
        capture("best-parts")
        compose.onAllNodesWithText("Discover").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("Paste link").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("Paste a link").performTextInput("https://example.org/song.mp3").also { settle() }
        compose.onNodeWithText("Cancel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("Find your next favorite").assertIsDisplayed()
        capture("discover")
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }.also { settle() }
        compose.onNodeWithText("Wi-Fi only").performScrollTo().assertIsDisplayed()
    }
    private fun settle() {
        repeat(3) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeBy(200) }
    }
    private fun capture(name: String) {
        val file = File("build/screenshots/$name.png"); file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
