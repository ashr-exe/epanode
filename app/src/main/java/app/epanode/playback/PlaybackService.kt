package app.epanode.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.audiofx.Equalizer
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import app.epanode.MainActivity
import app.epanode.EpanodeApp
import app.epanode.core.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val sleep = Runnable { player.pause(); sleepUntil.value = 0 }
    private val checkpoint = object : Runnable {
        override fun run() {
            if (!player.isPlaying) return
            getSharedPreferences("playback", MODE_PRIVATE).edit().putInt("index", player.currentMediaItemIndex.coerceAtLeast(0)).putLong("position", player.currentPosition).apply()
            handler.postDelayed(this, 30000)
        }
    }
    private var equalizer: Equalizer? = null
    private var countedId: String? = null
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(pending).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                if (controller.packageName == packageName) { commands.add(SessionCommand("sleep", Bundle.EMPTY)); commands.add(SessionCommand("eq", Bundle.EMPTY)) }
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands.build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                when (customCommand.customAction) {
                    "sleep" -> {
                        handler.removeCallbacks(sleep)
                        val minutes = args.getInt("minutes", 0).coerceIn(0, 180)
                        sleepUntil.value = if (minutes > 0) System.currentTimeMillis() + minutes * 60000L else 0
                        if (minutes > 0) handler.postDelayed(sleep, minutes * 60000L)
                    }
                    "eq" -> applyEqualizer(args.getString("preset", "Off"))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                val library = (application as EpanodeApp).store.library.value
                return Futures.immediateFuture(mediaItems.mapNotNull { item ->
                    val id = item.mediaId.substringBefore('@')
                    val t = library.tracks.find { it.id == id } ?: return@mapNotNull null
                    val partId = item.mediaId.substringAfter('@', "")
                    val part = library.parts.find { it.id == partId && it.trackId == id && it.validFor(t.durationMs) }
                        ?: if (controller.packageName == packageName && partId == "preview") BestPart("preview", id, item.mediaMetadata.extras?.getString("partLabel") ?: "Preview", item.clippingConfiguration.startPositionMs, item.clippingConfiguration.endPositionMs).takeIf { it.validFor(t.durationMs) } else null
                    mediaItem(t, part)
                }.toMutableList())
            }
        }).build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_POSITION_DISCONTINUITY) || events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) persist()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(checkpoint)
                if (isPlaying) handler.postDelayed(checkpoint, 30000)
                if (isPlaying) {
                    val id = player.currentMediaItem?.mediaId?.substringBefore('@')
                    if (id != null && id != countedId) { countedId = id; (application as EpanodeApp).scope.launch { (application as EpanodeApp).store.played(id) } }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) countedId = null }
            override fun onAudioSessionIdChanged(audioSessionId: Int) { applyEqualizer(getSharedPreferences("settings", MODE_PRIVATE).getString("eq", "Off") ?: "Off") }
            override fun onPlayerError(error: PlaybackException) { playbackError.value = "This file could not be played. Check that it still exists and is a supported format." }
        })
        scope.launch {
            val app = application as EpanodeApp
            withContext(Dispatchers.IO) { app.store.refresh() }
            if (player.mediaItemCount == 0) {
                val saved = getSharedPreferences("playback", MODE_PRIVATE)
                val data = runCatching { JSONArray(saved.getString("queue", "[]")) }.getOrElse { JSONArray() }
                val library = app.store.library.value
                val items = (0 until data.length()).mapNotNull { i ->
                    val id = data.optString(i); val t = library.tracks.find { it.id == id.substringBefore('@') } ?: return@mapNotNull null
                    mediaItem(t, library.parts.find { it.id == id.substringAfter('@', "") && it.validFor(t.durationMs) })
                }
                if (items.isNotEmpty()) {
                    player.setMediaItems(items, saved.getInt("index", 0).coerceIn(0, items.lastIndex), saved.getLong("position", 0).coerceAtLeast(0))
                    player.repeatMode = saved.getInt("repeat", Player.REPEAT_MODE_OFF)
                    player.shuffleModeEnabled = saved.getBoolean("shuffle", false)
                    player.setPlaybackSpeed(saved.getFloat("speed", 1f).coerceIn(.5f, 2f))
                    player.prepare()
                }
            }
        }
    }
    private fun applyEqualizer(preset: String) {
        runCatching {
            if (player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
            equalizer?.release(); equalizer = null
            if (preset != "Off") {
                equalizer = Equalizer(0, player.audioSessionId).apply {
                    val index = (0 until numberOfPresets.toInt()).firstOrNull { getPresetName(it.toShort()).equals(preset, true) }
                    if (index != null) usePreset(index.toShort())
                    else {
                        val range = bandLevelRange
                        for (i in 0 until numberOfBands) {
                            val level = when (preset) { "Bass" -> if (i < 2) 400 else 0; "Vocal" -> if (i in 1..3) 250 else -100; else -> 0 }
                            setBandLevel(i.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                        }
                    }
                    enabled = true
                }
            }
        }.onFailure { playbackError.value = "Equalizer is unavailable on this audio output." }
    }
    private fun persist() {
        val q = JSONArray(); for (i in 0 until player.mediaItemCount) q.put(player.getMediaItemAt(i).mediaId)
        getSharedPreferences("playback", MODE_PRIVATE).edit().putString("queue", q.toString()).putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition).putInt("repeat", player.repeatMode).putBoolean("shuffle", player.shuffleModeEnabled).putFloat("speed", player.playbackParameters.speed).apply()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) { persist(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { persist(); handler.removeCallbacksAndMessages(null); sleepUntil.value = 0; equalizer?.release(); session?.release(); player.release(); scope.cancel(); super.onDestroy() }
    companion object {
        val sleepUntil = MutableStateFlow(0L)
        val playbackError = MutableStateFlow<String?>(null)
        fun mediaItem(track: Track, part: BestPart? = null): MediaItem {
            val clip = part?.takeIf { it.trackId == track.id && it.validFor(track.durationMs) }
            val extra = Bundle().apply { putString("trackId", track.id); putString("partLabel", clip?.label); putLong("clipStart", clip?.startMs ?: 0) }
            val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(extra)
            if (track.artwork.isNotBlank()) metadata.setArtworkUri(android.net.Uri.parse(track.artwork))
            val builder = MediaItem.Builder().setMediaId(track.id + (clip?.let { "@${it.id}" } ?: "")).setUri(track.uri).setMediaMetadata(metadata.build())
            if (clip != null) builder.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build())
            return builder.build()
        }
    }
}
