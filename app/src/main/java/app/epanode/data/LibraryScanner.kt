package app.epanode.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import app.epanode.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LibraryScanner(private val context: Context, private val store: LibraryStore) {
    private val mutex = Mutex()
    suspend fun scanDevice(): Int = mutex.withLock {
        val old = store.library.value.tracks.associateBy { it.id }
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf("_id", "title", "artist", "album", "duration", "album_id", "_display_name", "date_modified", "relative_path", "date_added")
        val cursor = context.contentResolver.query(collection, projection, "is_pending=0", null, "_id ASC") ?: error("Storage could not be read. Try scanning again.")
        cursor.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri = ContentUris.withAppendedId(collection, c.getLong(0)).toString(); val id = MusicLogic.id(uri)
                val duration = c.getLong(4); if (duration <= 0) continue
                val modified = c.getLong(7)
                if (old[id]?.modified == modified && old[id]?.source == "device") { tracks += old.getValue(id); continue }
                val name = c.getString(6).orEmpty(); val tags = MusicLogic.metadata(name, c.getString(1), c.getString(2))
                tracks += Track(id, uri, tags.first, tags.second, c.getString(3)?.takeUnless { it == "<unknown>" } ?: "Unknown album", duration,
                    folder = c.getString(8).orEmpty(), filename = name, modified = modified, added = c.getLong(9) * 1000, confidence = tags.third)
            }
        }
        store.upsertTracks(tracks, tracks.map { it.id }.toSet()); tracks.size
    }
    suspend fun scanFolder(treeUri: Uri): Int = mutex.withLock {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Folder is unavailable.")
        val stack = ArrayDeque<DocumentFile>(); stack.add(root)
        val tracks = mutableListOf<Track>()
        val extensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "webm", "aiff", "alac")
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = stack.removeLast()
            val files = folder.listFiles()
            if (files.any { it.name == ".nomedia" }) continue
            val lyrics = files.filter { it.name?.substringAfterLast('.')?.lowercase() in setOf("lrc", "txt") }.associateBy { it.name!!.substringBeforeLast('.') }
            for (file in files) {
                currentCoroutineContext().ensureActive()
                if (file.isDirectory) { stack.add(file); continue }
                if (file.type?.startsWith("audio/") != true && file.name?.substringAfterLast('.')?.lowercase() !in extensions) continue
                val t = readUri(file.uri, file.name.orEmpty(), folder.name.orEmpty(), "folder", file.lastModified()) ?: continue
                val lyricFile = lyrics[file.name?.substringBeforeLast('.')]
                val raw = lyricFile?.let { runCatching { context.contentResolver.openInputStream(it.uri)?.use { s -> String(s.readBounded(256000)) } }.getOrNull() }.orEmpty()
                tracks += t.copy(lyrics = raw)
            }
        }
        store.upsertTracks(tracks); tracks.size
    }
    fun readUri(uri: Uri, filename: String, folder: String, source: String, modified: Long = 0): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            if (duration <= 0) return null
            val tags = MusicLogic.metadata(filename, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            Track(MusicLogic.id(uri.toString()), uri.toString(), tags.first, tags.second,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown album", duration, folder = folder, filename = filename, source = source, modified = modified, confidence = tags.third)
        } catch (_: Exception) { null } finally { retriever.release() }
    }
    fun cacheArtwork(track: Track): File? {
        val directory = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(directory, "${track.id}-${track.modified}.jpg")
        if (file.exists()) return file.takeIf { it.length() > 0 }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(track.uri))
            val bytes = retriever.embeddedPicture
            if (bytes != null && bytes.size <= 12 * 1024 * 1024) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 600).coerceAtLeast(1) }
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bitmap != null) { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }; bitmap.recycle() }
            }
            if (!file.exists()) file.createNewFile()
            // Bound the disk cache without keeping decoded bitmaps alive.
            val cached = directory.listFiles().orEmpty().sortedBy { it.lastModified() }
            var total = cached.sumOf { it.length() }
            for (old in cached) { if (total < 48L * 1024 * 1024) break; total -= old.length(); old.delete() }
        } catch (_: Exception) { return null } finally { retriever.release() }
        return file.takeIf { it.length() > 0 }
    }
}
