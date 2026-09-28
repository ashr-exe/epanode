package app.epanode.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.epanode.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LibraryStore(context: Context) : SQLiteOpenHelper(context, "epanode.db", null, 1) {
    private val state = MutableStateFlow(Library())
    val library = state.asStateFlow()
    override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, source TEXT NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE parts (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE downloads (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    private fun rows(table: String): List<JSONObject> = readableDatabase.rawQuery("SELECT data FROM $table", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(JSONObject(cursor.getString(0))) }
    }
    @Synchronized fun refresh() {
        state.value = Library(rows("tracks").map(::track).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
            rows("playlists").map(::playlist).sortedBy { it.created }, rows("parts").map(::part), rows("downloads").map(::download).reversed())
    }
    private fun put(table: String, id: String, data: JSONObject, extras: Map<String, String> = emptyMap()) {
        val values = ContentValues().apply { put("id", id); put("data", data.toString()); extras.forEach { (k, v) -> put(k, v) } }
        writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun upsertTracks(incoming: List<Track>, scannedDeviceIds: Set<String>? = null) {
        val existing = rows("tracks").map(::track).associateBy { it.id }
        writableDatabase.beginTransaction()
        try {
            incoming.forEach { fresh ->
                val old = existing[fresh.id]
                val t = if (old != null) fresh.copy(
                    title = if (old.edited) old.title else fresh.title, artist = if (old.edited) old.artist else fresh.artist,
                    album = if (old.edited) old.album else fresh.album, artwork = old.artwork.ifBlank { fresh.artwork },
                    liked = old.liked, plays = old.plays, lastPlayed = old.lastPlayed, added = old.added,
                    lyrics = old.lyrics.ifBlank { fresh.lyrics }, edited = old.edited, confidence = if (old.edited) old.confidence else fresh.confidence) else fresh
                put("tracks", t.id, json(t), mapOf("uri" to t.uri, "source" to t.source))
            }
            if (scannedDeviceIds != null) existing.values.filter { it.source == "device" && it.id !in scannedDeviceIds }.forEach {
                writableDatabase.delete("tracks", "id=?", arrayOf(it.id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        refresh()
    }
    @Synchronized fun update(t: Track) { put("tracks", t.id, json(t), mapOf("uri" to t.uri, "source" to t.source)); refresh() }
    @Synchronized fun played(id: String) { state.value.tracks.find { it.id == id }?.let { update(it.copy(plays = it.plays + 1, lastPlayed = System.currentTimeMillis())) } }
    @Synchronized fun createPlaylist(name: String, ids: List<String> = emptyList()): String {
        require(name.trim().isNotBlank()) { "Give your playlist a name." }
        val p = Playlist(UUID.randomUUID().toString(), name.trim().take(100), ids.distinct(), System.currentTimeMillis())
        savePlaylist(p); return p.id
    }
    @Synchronized fun savePlaylist(p: Playlist) { put("playlists", p.id, JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.trackIds)).put("created", p.created)); refresh() }
    @Synchronized fun addToPlaylist(playlistId: String, trackId: String) {
        state.value.playlists.find { it.id == playlistId }?.let { savePlaylist(it.copy(trackIds = (it.trackIds + trackId).distinct())) }
    }
    @Synchronized fun deletePlaylist(id: String) { writableDatabase.delete("playlists", "id=?", arrayOf(id)); refresh() }
    @Synchronized fun savePart(p: BestPart) {
        val t = state.value.tracks.find { it.id == p.trackId } ?: error("Song is no longer in your library.")
        require(p.validFor(t.durationMs)) { "Choose at least half a second within the song." }
        require(p.label.isNotBlank()) { "Name this best part." }
        put("parts", p.id, JSONObject().put("id", p.id).put("trackId", p.trackId).put("label", p.label.take(100)).put("start", p.startMs).put("end", p.endMs)); refresh()
    }
    @Synchronized fun deletePart(id: String) { writableDatabase.delete("parts", "id=?", arrayOf(id)); refresh() }
    @Synchronized fun saveDownload(d: DownloadEntry) {
        val previous = state.value.downloads.find { it.id == d.id }
        val kept = if (previous?.state == "Cancelled" && d.state != "Queued") d.copy(state = "Cancelled") else d
        val playlistIds = (previous?.playlistId.orEmpty().split("|") + d.playlistId.split("|")).filter { it.isNotBlank() }.distinct().joinToString("|")
        put("downloads", d.id, JSONObject().put("id", d.id).put("url", d.url).put("title", d.title).put("state", kept.state).put("progress", d.progress).put("error", d.error).put("playlistId", playlistIds).put("trackId", d.trackId).put("artist", d.artist).put("duration", d.durationMs))
        val merged = kept.copy(playlistId = playlistIds)
        val downloads = state.value.downloads
        state.value = state.value.copy(downloads = if (downloads.any { it.id == d.id }) downloads.map { if (it.id == d.id) merged else it } else listOf(merged) + downloads)
    }
    @Synchronized fun exportJson(): String = JSONObject().put("version", 1).put("tracks", JSONArray(rows("tracks")))
        .put("playlists", JSONArray(rows("playlists"))).put("parts", JSONArray(rows("parts"))).toString(2)
    @Synchronized fun restoreJson(raw: String) {
        val j = JSONObject(raw); require(j.getInt("version") == 1) { "Unsupported backup version." }
        // Backups restore metadata against existing URIs; never introduce paths or network requests.
        val current = state.value.tracks.associateBy { it.id }
        val ts = j.getJSONArray("tracks"); val ps = j.getJSONArray("playlists"); val bs = j.getJSONArray("parts")
        require(ts.length() <= 100000 && ps.length() <= 10000 && bs.length() <= 100000) { "Backup is too large." }
        val tracks = (0 until ts.length()).map { track(ts.getJSONObject(it)) }
        val playlists = (0 until ps.length()).map { playlist(ps.getJSONObject(it)) }
        val parts = (0 until bs.length()).map { part(bs.getJSONObject(it)) }
        writableDatabase.beginTransaction()
        try {
            tracks.forEach { old -> current[old.id]?.let { t ->
                val restored = t.copy(title = old.title, artist = old.artist, album = old.album, liked = old.liked, lyrics = old.lyrics, edited = old.edited, confidence = old.confidence, plays = old.plays, lastPlayed = old.lastPlayed)
                put("tracks", t.id, json(restored), mapOf("uri" to t.uri, "source" to t.source))
            } }
            playlists.forEach { p -> put("playlists", p.id, JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.trackIds)).put("created", p.created)) }
            parts.filter { p -> current[p.trackId]?.let { p.validFor(it.durationMs) } == true }.forEach { p ->
                put("parts", p.id, JSONObject().put("id", p.id).put("trackId", p.trackId).put("label", p.label).put("start", p.startMs).put("end", p.endMs))
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        refresh()
    }
    companion object {
        fun json(t: Track) = JSONObject().put("id", t.id).put("uri", t.uri).put("title", t.title).put("artist", t.artist).put("album", t.album).put("duration", t.durationMs)
            .put("artwork", t.artwork).put("folder", t.folder).put("filename", t.filename).put("modified", t.modified).put("added", t.added).put("liked", t.liked)
            .put("plays", t.plays).put("lastPlayed", t.lastPlayed).put("lyrics", t.lyrics).put("source", t.source).put("confidence", t.confidence).put("edited", t.edited)
        fun track(j: JSONObject) = Track(j.getString("id"), j.getString("uri"), j.getString("title"), j.optString("artist", "Unknown artist"), j.optString("album", "Unknown album"),
            j.optLong("duration"), j.optString("artwork"), j.optString("folder"), j.optString("filename"), j.optLong("modified"), j.optLong("added"), j.optBoolean("liked"),
            j.optInt("plays"), j.optLong("lastPlayed"), j.optString("lyrics"), j.optString("source", "device"), j.optString("confidence", "tags"), j.optBoolean("edited"))
        fun playlist(j: JSONObject): Playlist { val a = j.getJSONArray("ids"); return Playlist(j.getString("id"), j.getString("name"), (0 until a.length()).map { a.getString(it) }, j.optLong("created")) }
        fun part(j: JSONObject) = BestPart(j.getString("id"), j.getString("trackId"), j.getString("label"), j.getLong("start"), j.getLong("end"))
        fun download(j: JSONObject) = DownloadEntry(j.getString("id"), j.getString("url"), j.getString("title"), j.getString("state"), j.optInt("progress"), j.optString("error"), j.optString("playlistId"), j.optString("trackId"), j.optString("artist"), j.optLong("duration"))
    }
}
