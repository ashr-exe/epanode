package app.epanode.imports

import app.epanode.core.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URI
import java.util.concurrent.TimeUnit

object Catalog {
    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    val downloadClient = client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    private val extractionLock = Any()
    private var initialized = false
    private fun init() {
        if (initialized) return
        NewPipe.init(object : Downloader() {
            override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
                val builder = Request.Builder().url(request.url()).header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0")
                request.headers().forEach { (key, values) -> values.forEach { builder.addHeader(key, it) } }
                val method = request.httpMethod()
                builder.method(method, request.dataToSend()?.toRequestBody() ?: if (method in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null)
                return client.newCall(builder.build()).execute().use { r ->
                    Response(r.code, r.message, r.headers.toMultimap(), r.body?.string().orEmpty(), r.request.url.toString())
                }
            }
        })
        initialized = true
    }
    fun search(query: String): List<RemoteTrack> = synchronized(extractionLock) {
        require(query.isNotBlank())
        init()
        val extractor = ServiceList.YouTube.getSearchExtractor(query.take(250), listOf("music_songs"), "")
        extractor.fetchPage()
        val musicInfo = SearchInfo.getInfo(extractor)
        val music = musicInfo.relatedItems.filterIsInstance<StreamInfoItem>()
        val fallback = if (music.isEmpty()) SearchInfo.getInfo(ServiceList.YouTube.getSearchExtractor(query.take(250), listOf("videos"), "").apply { fetchPage() }) else null
        val found = if (music.isNotEmpty()) music else fallback!!.relatedItems.filterIsInstance<StreamInfoItem>()
        if (found.isEmpty() && (fallback?.errors?.isNotEmpty() == true || musicInfo.errors.isNotEmpty())) throw java.io.IOException("Music provider parsing failed: " + (fallback?.errors?.firstOrNull() ?: musicInfo.errors.firstOrNull())?.message)
        found.map {
            RemoteTrack(it.url, it.name, it.uploaderName.orEmpty(), it.duration.coerceAtLeast(0) * 1000, it.thumbnails.firstOrNull()?.url.orEmpty())
        }.take(50)
    }
    data class Stream(val remote: RemoteTrack, val audioUrl: String, val extension: String, val mime: String)
    fun stream(url: String): Stream = synchronized(extractionLock) {
        init()
        val info = StreamInfo.getInfo(ServiceList.YouTube, MusicLogic.canonicalUrl(url))
        val audio = info.audioStreams.filter { it.isUrl && it.content.startsWith("https://") }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.suffix == "m4a" }.thenByDescending { it.averageBitrate })
            .firstOrNull() ?: error("This song has no downloadable audio stream. It may be restricted or unavailable.")
        Stream(RemoteTrack(url, info.name, info.uploaderName, info.duration * 1000, info.thumbnails.firstOrNull()?.url.orEmpty()), audio.content, audio.format?.suffix ?: "m4a", audio.format?.mimeType ?: "audio/mp4")
    }
    fun youtubePlaylist(url: String): Pair<String, List<RemoteTrack>> = synchronized(extractionLock) {
        init()
        val canonical = MusicLogic.canonicalUrl(url)
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, canonical)
        val tracks = mutableListOf<StreamInfoItem>(); tracks += info.relatedItems
        var page = info.nextPage
        val seen = mutableSetOf<String>()
        while (org.schabi.newpipe.extractor.Page.isValid(page) && tracks.size < 5000) {
            val key = page.url.orEmpty() + page.id.orEmpty() + page.ids.orEmpty().joinToString()
            if (!seen.add(key)) error("The provider repeated a playlist page. Retry the import later.")
            val next = PlaylistInfo.getMoreItems(ServiceList.YouTube, canonical, page)
            tracks += next.items; page = next.nextPage
        }
        if (org.schabi.newpipe.extractor.Page.isValid(page) && tracks.size >= 5000) error("This playlist exceeds the 5,000-song import limit. Split it into smaller playlists.")
        info.name to tracks.distinctBy { it.url }.map { RemoteTrack(it.url, it.name, it.uploaderName.orEmpty(), it.duration * 1000, it.thumbnails.firstOrNull()?.url.orEmpty()) }
    }
    /** Spotify links provide metadata only; audio is independently matched from the music search provider. */
    fun spotify(url: String): Pair<String, List<RemoteTrack>> {
        val resolved = if (URI(url).host == "spotify.link") client.newCall(Request.Builder().url(url).build()).execute().use { it.request.url.toString() } else url
        val uri = URI(resolved); require(uri.host == "open.spotify.com") { "The Spotify share link could not be resolved." }
        val segments = uri.path.split('/').filter { it.isNotBlank() && !it.startsWith("intl-") }
        require(segments.size >= 2 && segments[0] in setOf("track", "album", "playlist") && segments[1].matches(Regex("[A-Za-z0-9]+"))) { "Share a Spotify song, album, or playlist." }
        val embed = "https://open.spotify.com/embed/${segments[0]}/${segments[1]}"
        val html = client.newCall(Request.Builder().url(embed).header("User-Agent", "Mozilla/5.0").build()).execute().use {
            require(it.isSuccessful) { "Spotify did not provide this public playlist. Private playlists need a local export." }; it.body?.string().orEmpty()
        }
        val raw = Jsoup.parse(html).getElementById("__NEXT_DATA__")?.data() ?: error("Spotify changed its public share format. This import needs a provider update.")
        val entity = JSONObject(raw).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        val songs = mutableListOf<RemoteTrack>()
        val list = entity.optJSONArray("trackList")
        if (list != null) for (i in 0 until list.length()) {
            val t = list.getJSONObject(i)
            val title = t.optString("title"); val artist = t.optString("subtitle")
            if (title.isNotBlank()) songs += RemoteTrack(t.optString("uri"), title, artist, t.optLong("duration"))
        } else if (segments[0] == "track") {
            val artists = entity.optJSONArray("artists")
            songs += RemoteTrack(resolved, entity.optString("title", entity.optString("name")),
                if (artists != null) (0 until artists.length()).joinToString(", ") { artists.getJSONObject(it).optString("name") } else entity.optString("subtitle"), entity.optLong("duration"))
        }
        require(songs.isNotEmpty()) { "Spotify did not expose any songs in this link. Private or restricted playlists cannot be imported." }
        val total = entity.optInt("totalTrackCount", songs.size)
        require(total <= songs.size) { "Spotify exposed only ${songs.size} of $total songs. Import a playlist export to avoid losing tracks." }
        return entity.optString("title", entity.optString("name", "Imported playlist")) to songs
    }
    fun match(song: RemoteTrack): RemoteTrack {
        val choices = search("${song.title} ${song.artist}")
        val targetTitle = MusicLogic.normalize(song.title)
        val targetArtist = MusicLogic.normalize(song.artist).substringBefore(" feat ").substringBefore(',')
        val ranked = choices.map { candidate ->
            val title = MusicLogic.normalize(candidate.title)
            val artist = MusicLogic.normalize(candidate.artist + " " + candidate.title)
            val titleMatch = title == targetTitle || title.contains(targetTitle)
            val artistMatch = targetArtist.isNotBlank() && artist.contains(targetArtist)
            val durationMatch = song.durationMs <= 0 || candidate.durationMs <= 0 || kotlin.math.abs(song.durationMs - candidate.durationMs) <= 10000
            candidate to (if (titleMatch && artistMatch && durationMatch) 1 else 0)
        }.filter { it.second > 0 }
        require(ranked.isNotEmpty()) { "No confident match for ${song.artist} — ${song.title}. Search this song in Discover to choose a version." }
        return ranked.first().first
    }
}
