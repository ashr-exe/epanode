package app.epanode.imports

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.work.*
import app.epanode.R
import app.epanode.EpanodeApp
import app.epanode.core.*
import app.epanode.data.LibraryStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class Downloads(private val context: Context, private val store: LibraryStore) {
    @Synchronized fun enqueue(url: String, title: String = "Shared music", playlistId: String = "", artist: String = "", duration: Long = 0): String {
        val kind = MusicLogic.sharedKind(url)
        require(kind in setOf("youtube", "spotify", "direct") || url.startsWith("spotify:track:")) { "Use a YouTube, Spotify, or direct HTTPS audio link." }
        val canonical = if (kind == "youtube") MusicLogic.canonicalUrl(url) else url
        val id = MusicLogic.id(canonical)
        val old = store.library.value.downloads.find { it.id == id }
        if (old?.state == "Downloaded" && store.library.value.tracks.any { it.id == old.trackId }) {
            if (playlistId.isNotEmpty() && old.trackId.isNotEmpty()) store.addToPlaylist(playlistId, old.trackId)
            return id
        }
        if (old?.state in setOf("Queued", "Resolving", "Downloading", "Retrying")) {
            if (playlistId.isNotBlank()) store.saveDownload(old!!.copy(playlistId = (old.playlistId.split("|") + playlistId).filter { it.isNotBlank() }.distinct().joinToString("|")))
            return id
        }
        val entry = DownloadEntry(id, canonical, title, "Queued", playlistId = playlistId, artist = artist.ifBlank { old?.artist.orEmpty() }, durationMs = if (duration > 0) duration else old?.durationMs ?: 0)
        store.saveDownload(entry)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workDataOf("id" to id, "artist" to artist, "duration" to duration))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (prefs.getBoolean("wifiOnly", true)) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag("music-download").addTag(id).build()
        WorkManager.getInstance(context).enqueueUniqueWork("epanode-download-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return id
    }
    fun cancel(id: String) {
        store.library.value.downloads.find { it.id == id }?.let { store.saveDownload(it.copy(state = "Cancelled")) }
    }
}

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as EpanodeApp
    private val store get() = app.store
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        store.refresh()
        val id = inputData.getString("id") ?: return@withContext Result.success()
        var entry = store.library.value.downloads.find { it.id == id } ?: return@withContext Result.success()
        if (entry.state == "Cancelled" || entry.state == "Downloaded") return@withContext Result.success()
        var pending: Uri? = null
        try {
            setForeground(foreground(entry.title, id))
            entry = entry.copy(state = "Resolving", error = ""); store.saveDownload(entry)
            var resolvedUrl = entry.url
            val kind = MusicLogic.sharedKind(entry.url)
            if (kind == "spotify" || entry.url.startsWith("spotify:track:")) {
                val known = entry.artist
                if (entry.url.startsWith("spotify:track:") || known.isNotEmpty()) {
                    val match = Catalog.match(RemoteTrack(entry.url, entry.title, known, entry.durationMs))
                    // Continue within the same worker so the original entry retains its outcome.
                    resolvedUrl = match.url
                } else {
                    val (name, songs) = Catalog.spotify(entry.url)
                    if (songs.size == 1) {
                        val match = Catalog.match(songs.first()); resolvedUrl = match.url; entry = entry.copy(title = songs.first().title)
                    } else {
                        val playlistId = store.createPlaylist(name)
                        songs.forEach { currentCoroutineContext().ensureActive(); checkRequested(id); app.downloads.enqueue(it.url, it.title, playlistId, it.artist, it.durationMs) }
                        store.saveDownload(entry.copy(title = name, state = "Playlist queued", playlistId = playlistId)); return@withContext Result.success()
                    }
                }
            }
            if (MusicLogic.sharedKind(entry.url) == "youtube" && entry.url.contains("/playlist?")) {
                val (name, songs) = Catalog.youtubePlaylist(entry.url)
                require(songs.isNotEmpty()) { "This playlist contains no available songs." }
                val playlistId = store.createPlaylist(name)
                songs.forEach { currentCoroutineContext().ensureActive(); checkRequested(id); app.downloads.enqueue(it.url, it.title, playlistId) }
                store.saveDownload(entry.copy(title = name, state = "Playlist queued", playlistId = playlistId)); return@withContext Result.success()
            }
            currentCoroutineContext().ensureActive()
            checkRequested(id)
            val stream = if (MusicLogic.sharedKind(entry.url) == "direct") {
                val ext = Uri.parse(entry.url).lastPathSegment.orEmpty().substringAfterLast('.').lowercase()
                Catalog.Stream(RemoteTrack(entry.url, if (entry.title == "Shared music") Uri.parse(entry.url).lastPathSegment.orEmpty().substringBeforeLast('.') else entry.title, "Unknown artist"), entry.url, ext,
                    when (ext) { "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"; "webm" -> "audio/webm"; else -> "audio/mp4" })
            } else Catalog.stream(resolvedUrl)
            entry = entry.copy(title = stream.remote.title, state = "Downloading"); store.saveDownload(entry)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "${MusicLogic.safeFilename(stream.remote.artist + " - " + stream.remote.title)}-${id}.${stream.extension}")
                put(MediaStore.Audio.Media.MIME_TYPE, stream.mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Epanode")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.TITLE, stream.remote.title)
                put(MediaStore.Audio.Media.ARTIST, stream.remote.artist)
            }
            val resolver = applicationContext.contentResolver
            resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf("_id"), "is_pending=1 AND relative_path=? AND _display_name LIKE ?", arrayOf("Music/Epanode/", "%${id}.%"), null)?.use { cursor ->
                while (cursor.moveToNext()) resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)), null, null)
            }
            pending = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the music file.")
            Catalog.downloadClient.newCall(Request.Builder().url(stream.audioUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("The audio server returned HTTP ${response.code}.")
                val body = response.body ?: throw IOException("The audio server returned an empty file.")
                val mime = body.contentType()?.toString().orEmpty()
                require(!mime.contains("text/") && !mime.contains("json")) { "The link returned a webpage, not audio." }
                val size = body.contentLength()
                require(size <= 512L * 1024 * 1024) { "This file exceeds the 512 MB download limit." }
                resolver.openOutputStream(pending!!, "w")!!.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024); var count = 0L; var updateAt = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            checkRequested(id)
                            val read = input.read(buffer); if (read < 0) break
                            count += read; require(count <= 512L * 1024 * 1024) { "This file exceeds the 512 MB download limit." }
                            output.write(buffer, 0, read)
                            if (System.currentTimeMillis() - updateAt > 1000) {
                                updateAt = System.currentTimeMillis(); entry = entry.copy(progress = if (size > 0) ((count * 100) / size).toInt().coerceAtMost(99) else 0); store.saveDownload(entry)
                            }
                        }
                        require(count > 0 && (size <= 0 || size == count)) { "Download was incomplete. Please retry." }
                    }
                }
            }
            val found = app.scanner.readUri(pending!!, values.getAsString(MediaStore.Audio.Media.DISPLAY_NAME), "Music/Epanode/", "device") ?: error("Downloaded data could not be decoded as audio.")
            currentCoroutineContext().ensureActive()
            checkRequested(id)
            resolver.update(pending!!, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            val track = found.copy(title = stream.remote.title, artist = stream.remote.artist, artwork = stream.remote.artwork, edited = true, confidence = "download")
            store.upsertTracks(listOf(track))
            store.library.value.downloads.find { it.id == entry.id }?.playlistId.orEmpty().split("|").filter { it.isNotBlank() }.forEach { store.addToPlaylist(it, track.id) }
            store.saveDownload(entry.copy(state = "Downloaded", progress = 100, trackId = track.id))
            pending = null
            Result.success()
        } catch (e: DownloadCancelled) {
            store.saveDownload(entry.copy(state = "Cancelled", error = ""))
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { store.saveDownload(entry.copy(state = "Retrying", error = "Waiting to resume after an interruption.")) }
            throw e
        } catch (e: Exception) {
            if (store.library.value.downloads.find { it.id == id }?.state == "Cancelled") return@withContext Result.success()
            val retry = e is IOException && runAttemptCount < 2
            store.saveDownload(entry.copy(state = if (retry) "Retrying" else "Failed", error = e.message?.take(400) ?: "Download failed. Try again."))
            // A handled failure must not cancel the remaining playlist chain.
            if (retry) Result.retry() else Result.success()
        } finally {
            pending?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
        }
    }
    private class DownloadCancelled : Exception()
    private fun checkRequested(id: String) { if (store.library.value.downloads.find { it.id == id }?.state == "Cancelled") throw DownloadCancelled() }
    private fun foreground(title: String, id: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("downloads", "Music downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_notification).setContentTitle("Saving music to your phone").setContentText(title).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Cancel", android.app.PendingIntent.getBroadcast(applicationContext, id.hashCode(), android.content.Intent(applicationContext, DownloadCancelReceiver::class.java).putExtra("id", id), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)).build()).build()
        return ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

class DownloadCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        val app = context.applicationContext as EpanodeApp
        app.scope.launch { try { app.downloads.cancel(id) } finally { pending.finish() } }
    }
}
