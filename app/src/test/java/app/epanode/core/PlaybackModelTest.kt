package app.epanode.core

import android.app.Application
import androidx.media3.common.C
import app.epanode.playback.PlaybackService
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class)
class PlaybackModelTest {
    private val track = Track("song", "content://media/external/audio/media/1", "Title", "Artist", durationMs = 120000)
    @Test fun clippedMediaItemCarriesExactBoundariesAndLocalUri() {
        val clip = BestPart("chorus", "song", "The chorus", 34500, 51750)
        val item = PlaybackService.mediaItem(track, clip)
        assertEquals("song@chorus", item.mediaId)
        assertEquals(34500L, item.clippingConfiguration.startPositionMs)
        assertEquals(51750L, item.clippingConfiguration.endPositionMs)
        assertEquals(track.uri, item.localConfiguration!!.uri.toString())
        assertEquals("The chorus", item.mediaMetadata.extras!!.getString("partLabel"))
    }
    @Test fun invalidOrUnrelatedClipFallsBackToTheCompleteSong() {
        listOf(BestPart("bad", "song", "Bad", 10000, 9999), BestPart("other", "another-song", "Other", 1000, 2000)).forEach { clip ->
            val item = PlaybackService.mediaItem(track, clip)
            assertEquals("song", item.mediaId)
            assertEquals(0L, item.clippingConfiguration.startPositionMs)
            assertEquals(C.TIME_END_OF_SOURCE, item.clippingConfiguration.endPositionMs)
            assertNull(item.mediaMetadata.extras!!.getString("partLabel"))
        }
    }
}
