package app.epanode.core

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val durationMs: Long = 0,
    val artwork: String = "",
    val folder: String = "",
    val filename: String = "",
    val modified: Long = 0,
    val added: Long = System.currentTimeMillis(),
    val liked: Boolean = false,
    val plays: Int = 0,
    val lastPlayed: Long = 0,
    val lyrics: String = "",
    val source: String = "device",
    val confidence: String = "tags",
    val edited: Boolean = false
)
data class BestPart(val id: String, val trackId: String, val label: String, val startMs: Long, val endMs: Long) {
    fun validFor(duration: Long) = startMs >= 0 && endMs - startMs >= 500 && endMs <= duration
}
data class Playlist(val id: String, val name: String, val trackIds: List<String>, val created: Long)
data class DownloadEntry(val id: String, val url: String, val title: String, val state: String, val progress: Int = 0, val error: String = "", val playlistId: String = "", val trackId: String = "", val artist: String = "", val durationMs: Long = 0)
data class Library(val tracks: List<Track> = emptyList(), val playlists: List<Playlist> = emptyList(), val parts: List<BestPart> = emptyList(), val downloads: List<DownloadEntry> = emptyList())
data class RemoteTrack(val url: String, val title: String, val artist: String, val durationMs: Long = 0, val artwork: String = "")
data class LyricsLine(val timeMs: Long, val text: String)
fun clock(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
