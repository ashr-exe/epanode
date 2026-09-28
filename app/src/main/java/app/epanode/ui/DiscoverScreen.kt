package app.epanode.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.epanode.core.*
import coil.compose.AsyncImage

@Composable fun DiscoverScreen(vm: AppViewModel, library: Library) {
    var query by rememberSaveable { mutableStateOf("") }
    var downloadTab by rememberSaveable { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val results by vm.results.collectAsStateWithLifecycle()
    val busy by vm.searchBusy.collectAsStateWithLifecycle()
    val error by vm.searchError.collectAsStateWithLifecycle()
    val searched by vm.onlineQuery.collectAsStateWithLifecycle()
    Column {
        SectionTitle("Find your next favorite", "Save it once. Listen anywhere.")
        SearchField(query, { query = it }, "A song, artist, or a line of lyrics…")
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { if (MusicLogic.extractUrl(query) != null) { vm.enqueue(query); downloadTab = true } else { vm.searchOnline(query); downloadTab = false } }, enabled = query.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Glyph(Icons.Rounded.Search, null, color = Ink); Text("Search", Modifier.padding(start = 6.dp)) }
            OutlinedButton(onClick = { linkDialog = true }, modifier = Modifier.weight(1f)) { Glyph(Icons.Rounded.Link, null, color = Lime); Text("Paste link", Modifier.padding(start = 6.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !downloadTab, onClick = { downloadTab = false }, label = { Text("Explore") })
            FilterChip(selected = downloadTab, onClick = { downloadTab = true }, label = { Text("Downloads · ${library.downloads.size}") })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 25.dp, top = 12.dp)) {
            if (downloadTab) {
                item { Text(if (vm.prefs.getBoolean("wifiOnly", true)) "Downloads wait for Wi-Fi. Change this in Settings." else "Downloads can use mobile data.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)) }
                if (library.downloads.isEmpty()) item { EmptyState("A little more for your library", "Search above, paste a link, or share a song or playlist to Epanode from another app.", Icons.Rounded.Download) }
                items(library.downloads, key = { it.id }) { d ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(if (d.state == "Downloaded") Icons.Rounded.CheckCircle else if (d.state == "Failed") Icons.Rounded.ErrorOutline else Icons.Rounded.Download, null, color = if (d.state == "Failed") Lilac else Lime)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(d.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(d.state + if (d.state == "Downloading") " · ${d.progress}%" else "", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) }
                            when (d.state) {
                                "Failed", "Cancelled" -> IconAction(Icons.Rounded.Refresh, "Retry ${d.title}") { vm.work { vm.app.downloads.enqueue(d.url, d.title, d.playlistId) } }
                                "Queued", "Downloading", "Resolving", "Retrying" -> IconAction(Icons.Rounded.Close, "Cancel ${d.title}") { vm.work { vm.app.downloads.cancel(d.id) } }
                            }
                        }
                        if (d.error.isNotBlank()) Text(d.error, fontSize = 12.sp, color = Lilac, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
                        if (d.state == "Downloading") LinearProgressIndicator(progress = { d.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = Lime)
                    }
                }
            } else {
                error?.let { item { EmptyState("Couldn’t reach music search", it, Icons.Rounded.CloudOff, "Try again", { vm.searchOnline(query) }) } }
                if (results.isEmpty() && error == null && !busy) item {
                    if (searched.isNotBlank()) EmptyState("No matches this time", "Try the artist and song title, or another line of lyrics.", Icons.Rounded.SearchOff)
                    else {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(Raised, RoundedCornerShape(22.dp)).padding(22.dp)) {
                            Pill("BRING IT HOME", Lilac)
                            Text("From a shared link\nto your library.", fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp, modifier = Modifier.padding(vertical = 18.dp))
                            Text("Share a Spotify or YouTube song or playlist directly to Epanode. Downloads appear in Music/Epanode on your phone.", color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                            Text("Music search is powered by YouTube. Spotify links are matched to available audio; restricted or uncertain matches are reported in Downloads.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 18.dp))
                        }
                        Text("Download music you own or have permission to save. Search and imports need an internet connection.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 19.dp, start = 4.dp, end = 4.dp))
                    }
                }
                items(results, key = { it.url }) { t ->
                    val d = library.downloads.find { it.id == MusicLogic.id(runCatching { MusicLogic.canonicalUrl(t.url) }.getOrDefault(t.url)) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(t.artwork, contentDescription = null, modifier = Modifier.size(57.dp).background(Raised, RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(start = 13.dp)) { Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${t.artist} · ${clock(t.durationMs)}", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)) }
                        IconAction(if (d?.state == "Downloaded") Icons.Rounded.CheckCircle else Icons.Rounded.Download, if (d?.state == "Downloaded") "Downloaded ${t.title}" else "Download ${t.title}", Lime) { vm.download(t) }
                    }
                }
            }
        }
    }
    if (linkDialog) FormDialog("Bring music home", { linkDialog = false }, "Download", link.isNotBlank(), { vm.enqueue(link); linkDialog = false; downloadTab = true; link = "" }) {
        Text("Spotify, YouTube, YouTube Music, or a direct audio link.", fontSize = 14.sp, color = Muted)
        OutlinedTextField(link, { link = it }, label = { Text("Paste a link") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    }
}
