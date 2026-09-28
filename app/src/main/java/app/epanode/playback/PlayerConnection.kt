package app.epanode.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.*
import app.epanode.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(val trackId: String? = null, val playing: Boolean = false, val index: Int = 0, val queue: List<MediaItem> = emptyList(), val shuffle: Boolean = false, val repeat: Int = 0, val part: String? = null, val speed: Float = 1f)
class PlayerConnection(context: Context) {
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    var controller: MediaController? = null; private set
    private var pending: (() -> Unit)? = null
    private val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) = update() }
    init { future.addListener({
        runCatching { future.get() }.onSuccess { c -> controller = c; c.addListener(listener); update(); pending?.invoke(); pending = null }
            .onFailure { PlaybackService.playbackError.value = "Playback service could not connect. Reopen Epanode to try again." }
    }, ContextCompat.getMainExecutor(context)) }
    fun update() { controller?.let { c -> mutable.value = PlayerState(c.currentMediaItem?.mediaId?.substringBefore('@'), c.isPlaying, c.currentMediaItemIndex,
        (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }, c.shuffleModeEnabled, c.repeatMode, c.currentMediaItem?.mediaMetadata?.extras?.getString("partLabel"), c.playbackParameters.speed) } }
    fun play(tracks: List<Track>, index: Int = 0, parts: List<BestPart> = emptyList(), highlights: Boolean = false, singlePart: BestPart? = null, shuffle: Boolean = false) {
        val items = tracks.flatMap { t ->
            if (!highlights) listOf(PlaybackService.mediaItem(t))
            else {
                val selectedParts = if (singlePart != null) listOf(singlePart) else parts
                selectedParts.filter { it.trackId == t.id && it.validFor(t.durationMs) }.map { PlaybackService.mediaItem(t, it) }
            }
        }
        if (items.isEmpty()) { PlaybackService.playbackError.value = if (highlights) "Save a best part for a song in this playlist first." else "Add music to your library first."; return }
        val action: () -> Unit = {
            controller?.apply {
                setMediaItems(items, index.coerceIn(0, items.lastIndex), 0); shuffleModeEnabled = shuffle
                repeatMode = if (highlights && items.size == 1) Player.REPEAT_MODE_ONE else if (highlights) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                prepare(); play()
            }; Unit
        }
        if (controller == null) pending = action else action()
    }
    fun toggle() { controller?.apply { if (isPlaying) pause() else { if (playbackState == Player.STATE_ENDED) seekTo(0); play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.apply { if (currentPosition > 3000) seekTo(0) else seekToPreviousMediaItem() } }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0)) }
    fun add(track: Track, next: Boolean) { controller?.apply { addMediaItem(if (next) (currentMediaItemIndex + 1).coerceAtLeast(0) else mediaItemCount, PlaybackService.mediaItem(track)); if (playbackState == Player.STATE_IDLE) prepare() } }
    fun sleep(minutes: Int) { controller?.sendCustomCommand(SessionCommand("sleep", Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) }) }
    fun eq(preset: String) { controller?.sendCustomCommand(SessionCommand("eq", Bundle.EMPTY), Bundle().apply { putString("preset", preset) }) }
    fun release() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
