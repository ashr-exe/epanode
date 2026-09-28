package app.epanode.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.epanode.EpanodeApp
import app.epanode.core.*
import app.epanode.imports.Catalog
import app.epanode.playback.PlayerConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import okhttp3.Request
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as EpanodeApp
    val store = app.store
    val library = store.library
    val player = PlayerConnection(application)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val results = MutableStateFlow<List<RemoteTrack>>(emptyList())
    val searchBusy = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val onlineQuery = MutableStateFlow("")
    val initialTab = MutableStateFlow("Home")
    val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val metadataLock = Mutex()
    private var lastMetadataRequest = 0L
    private var searchJob: Job? = null
    private var scanJob: Job? = null
    init { viewModelScope.launch(Dispatchers.IO) { store.refresh() } }
    fun work(block: suspend () -> Unit) { viewModelScope.launch { try { withContext(Dispatchers.IO) { block() } } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = e.message ?: "Something went wrong. Please try again." } } }
    fun scan(folder: Uri? = null) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            busy.value = true
            try {
                val count = withContext(Dispatchers.IO) { if (folder == null) app.scanner.scanDevice() else app.scanner.scanFolder(folder) }
                message.value = "$count songs found. Your library is up to date."
            } catch (e: SecurityException) { message.value = "Allow music access in Android settings, or choose a folder." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Scan failed: ${e.message}" }
            finally { busy.value = false }
        }
    }
    fun like(t: Track) = work { store.library.value.tracks.find { it.id == t.id }?.let { store.update(it.copy(liked = !it.liked)) } }
    fun saveTrack(t: Track) = work { store.update(t.copy(edited = true, confidence = "edited")); message.value = "Song details saved on this phone." }
    fun savePart(t: Track, label: String, start: Long, end: Long, id: String = UUID.randomUUID().toString()) = work {
        store.savePart(BestPart(id, t.id, label.trim(), start, end)); message.value = "Best part saved."
    }
    fun playlist(name: String, ids: List<String> = emptyList()) = work { store.createPlaylist(name, ids); message.value = "Playlist created." }
    fun enqueue(text: String) = work {
        val url = MusicLogic.extractUrl(text) ?: throw IllegalArgumentException("Paste a Spotify, YouTube, or direct HTTPS audio link.")
        app.downloads.enqueue(url); initialTab.value = "Discover"; message.value = "Added to downloads. Wi-Fi is required by default."
    }
    fun download(t: RemoteTrack) = work { app.downloads.enqueue(t.url, t.title); message.value = "${t.title} added to downloads." }
    fun searchOnline(query: String) {
        searchJob?.cancel(); onlineQuery.value = query
        if (query.isBlank()) { results.value = emptyList(); searchError.value = null; searchBusy.value = false; return }
        searchJob = viewModelScope.launch {
            searchBusy.value = true; searchError.value = null
            try { results.value = withContext(Dispatchers.IO) { Catalog.search(query) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { results.value = emptyList(); searchError.value = "Music search is unavailable: ${e.message?.take(180)}" }
            finally { searchBusy.value = false }
        }
    }
    fun backup(uri: Uri) = work { app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) } ?: error("Could not write backup."); message.value = "Playlists, best parts, and song details backed up." }
    fun restore(uri: Uri) = work {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBounded(16 * 1024 * 1024 + 1) } ?: error("Could not read backup.")
        require(bytes.size <= 16 * 1024 * 1024) { "Backup exceeds 16 MB." }; store.restoreJson(bytes.toString(Charsets.UTF_8)); message.value = "Backup restored."
    }
    fun importAudio(uri: Uri, name: String) = work {
        val t = app.scanner.readUri(uri, name, "Shared audio", "shared") ?: error("This file is not readable audio.")
        store.upsertTracks(listOf(t)); message.value = "Shared audio added. For permanent access, add its folder from Library."
    }
    suspend fun metadataCandidates(t: Track): List<Track> = metadataLock.withLock { withContext(Dispatchers.IO) {
        val remaining = 1100L - (android.os.SystemClock.elapsedRealtime() - lastMetadataRequest)
        if (remaining > 0) delay(remaining)
        lastMetadataRequest = android.os.SystemClock.elapsedRealtime()
        val query = java.net.URLEncoder.encode("recording:\"${t.title.replace("\"", "") }\"" + if (t.artist != "Unknown artist") " AND artist:\"${t.artist.replace("\"", "")}\"" else "", "UTF-8")
        val req = Request.Builder().url("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=8").header("User-Agent", "Epanode/0.1 (local Android music organizer)").build()
        val raw = Catalog.client.newCall(req).execute().use { require(it.isSuccessful) { "Metadata search is unavailable (HTTP ${it.code})." }; it.body!!.string() }
        val records = JSONObject(raw).getJSONArray("recordings")
        (0 until records.length()).map { i ->
            val record = records.getJSONObject(i); val credits = record.optJSONArray("artist-credit")
            val artist = if (credits == null) t.artist else (0 until credits.length()).joinToString("") { credits.getJSONObject(it).optString("name") + credits.getJSONObject(it).optString("joinphrase") }
            val release = record.optJSONArray("releases")?.optJSONObject(0)
            t.copy(title = record.getString("title"), artist = artist, album = release?.optString("title") ?: t.album,
                artwork = release?.optString("id")?.takeIf { it.isNotBlank() }?.let { "https://coverartarchive.org/release/$it/front-500" } ?: t.artwork, confidence = "reviewed online", edited = true)
        }
    }
    }
    override fun onCleared() { player.release(); super.onCleared() }
}
