package app.epanode.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object MusicLogic {
    fun id(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("(?<=\\p{IsLatin})\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ").trim()
    private fun usefulTag(s: String?) = !s.isNullOrBlank() && s != "<unknown>" && !s.equals("unknown", true)
    /** Never guesses artist from a hash or bare numeric name. User edits are stored separately. */
    fun metadata(filename: String, title: String?, artist: String?): Triple<String, String, String> {
        val stem = filename.substringBeforeLast('.', filename)
        val cleaned = stem.replace(Regex("[_]+"), " ")
            .replace(Regex("(?i)\\s*[\\[(](official (music )?(video|audio)|lyrics?|visualizer|\\d{3}kbps|HD|4K)[\\])]"), "")
            .replace(Regex("^\\s*\\d{1,3}[. -]+(?=\\p{L})"), "")
            .replace(Regex("\\s+"), " ").trim().ifBlank { "Untitled audio" }
        val pair = cleaned.split(Regex("\\s+[–—-]\\s+"), limit = 2)
        val taggedTitle = title?.takeIf { usefulTag(it) && normalize(it) != normalize(stem) }
        return Triple(taggedTitle ?: pair.getOrNull(1)?.trim().orEmpty().ifBlank { cleaned },
            artist?.takeIf { usefulTag(it) } ?: if (pair.size == 2) pair[0].trim() else "Unknown artist",
            if (taggedTitle != null && usefulTag(artist)) "tags" else if (pair.size == 2) "filename" else "needs review")
    }
    fun editDistance(a: String, b: String, bound: Int = 3): Int {
        if (abs(a.length - b.length) > bound) return bound + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1); current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            if (current.minOrNull()!! > bound) return bound + 1
            previous = current
        }
        return previous[b.length]
    }
    fun score(query: String, value: String): Int {
        val q = normalize(query).take(160); val v = normalize(value)
        if (q.isEmpty()) return 1
        if (q == v) return 1000
        if (v.contains(q)) return 800
        val words = v.take(10000).split(' ')
        val tokens = q.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 1
        var total = 0
        for (token in tokens) {
            val best = words.maxOfOrNull { word ->
                when {
                    word == token -> 100
                    word.startsWith(token) -> 80
                    token.length >= 4 && abs(word.length - token.length) <= 2 -> {
                        val distance = editDistance(token, word, if (token.length >= 8) 2 else 1)
                        if (distance <= (if (token.length >= 8) 2 else 1)) 60 - distance * 10 else 0
                    }
                    else -> 0
                }
            } ?: 0
            if (best == 0) return 0
            total += best
        }
        return total / tokens.size
    }
    fun search(tracks: List<Track>, query: String): List<Track> = if (query.isBlank()) tracks else tracks.map {
        it to max(score(query, "${it.title} ${it.artist} ${it.album}"), score(query, it.lyrics) / 2)
    }.filter { it.second > 0 }.sortedWith(compareByDescending<Pair<Track, Int>> { it.second }.thenBy { it.first.id }).map { it.first }
    fun recommendations(tracks: List<Track>, seed: Track? = null, day: Long = System.currentTimeMillis() / 86400000): List<Track> = tracks
        .filter { it.id != seed?.id }
        .sortedWith(compareByDescending<Track> {
            (if (it.liked) 8 else 0) + (if (seed?.artist == it.artist && it.artist != "Unknown artist") 12 else 0) +
                (if (seed?.album == it.album && it.album != "Unknown album") 6 else 0) +
                (if (it.plays == 0) 4 else 0) + (id("${it.id}:$day").take(4).toInt(16) % 8) - minOf(it.plays, 10) / 2
        }.thenBy { it.id }).take(50)
    fun lyrics(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        val offset = Regex("\\[offset:([+-]?\\d+)\\]").find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
        raw.lineSequence().forEach { line ->
            val text = line.replace(stamp, "").trim()
            stamp.findAll(line).forEach {
                val ms = it.groupValues[1].toLong() * 60000 + it.groupValues[2].toLong() * 1000 + it.groupValues[3].padEnd(3, '0').toLong()
                result += LyricsLine((ms + offset).coerceAtLeast(0), text)
            }
        }
        return result.sortedBy { it.timeMs }
    }
    fun safeFilename(title: String): String = title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), " ").trim().trim('.').take(100).ifBlank { "Audio" }
    fun extractUrl(text: String): String? = Regex("https://[^\\s<>\"]+").find(text)?.value?.trimEnd('.', ',', ')', ']')
    fun sharedKind(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return "invalid"
        if (uri.scheme != "https" || uri.userInfo != null) return "invalid"
        val host = uri.host?.lowercase(Locale.ROOT) ?: return "invalid"
        return when (host) {
            "open.spotify.com", "spotify.link" -> "spotify"
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be" -> "youtube"
            else -> if (uri.path.lowercase(Locale.ROOT).matches(Regex(".*\\.(mp3|m4a|flac|ogg|opus|wav|aac|webm)"))) "direct" else "unsupported"
        }
    }
    fun canonicalUrl(value: String): String {
        val uri = URI(value)
        if (sharedKind(value) != "youtube") return value
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { field ->
            val pair = field.split('=', limit = 2)
            if (pair.size == 2) pair[0] to URLDecoder.decode(pair[1], "UTF-8") else null
        }.toMap()
        if (query["list"]?.isNotBlank() == true) return "https://www.youtube.com/playlist?list=${query["list"]}"
        val video = if (uri.host == "youtu.be") uri.path.trim('/') else query["v"] ?: uri.path.substringAfterLast('/')
        require(video.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This YouTube link does not identify a song or playlist." }
        return "https://www.youtube.com/watch?v=$video"
    }
}
