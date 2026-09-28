package app.epanode.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun LibraryScreen(vm: AppViewModel, library: Library, scan: () -> Unit, folder: () -> Unit, open: (String, String) -> Unit, more: (Track) -> Unit, createPlaylist: () -> Unit) {
    var category by rememberSaveable { mutableStateOf("Songs") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("A–Z") }
    var sortMenu by remember { mutableStateOf(false) }
    val player by vm.player.state.collectAsStateWithLifecycle()
    val filtered by produceState(library.tracks, query, library.tracks, sort) {
        if (query.isNotEmpty()) delay(180)
        value = withContext(Dispatchers.Default) {
            val ts = MusicLogic.search(library.tracks, query)
            when (sort) { "Recent" -> ts.sortedByDescending { it.added }; "Most played" -> ts.sortedByDescending { it.plays }; "Duration" -> ts.sortedByDescending { it.durationMs }; else -> ts }
        }
    }
    Column {
        SectionTitle("Your library", "${library.tracks.size} songs, always within reach", "Scan", scan)
        SearchField(query, { query = it }, "Songs, artists, albums, lyrics…")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Songs", "Albums", "Artists", "Folders", "Playlists").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isBlank()) "YOUR COLLECTION" else "${filtered.size} MATCHES", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, modifier = Modifier.weight(1f))
                    Box { TextButton(onClick = { sortMenu = true }) { Text(sort, fontSize = 12.sp); Glyph(Icons.Rounded.ExpandMore, null, color = Lime) }; DropdownMenu(sortMenu, { sortMenu = false }) { listOf("A–Z", "Recent", "Most played", "Duration").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortMenu = false }) } } }
                    if (category == "Playlists") IconAction(Icons.Rounded.Add, "Create playlist", onClick = createPlaylist)
                }
            }
            if (library.tracks.isEmpty()) item { EmptyState("Make yourself at home", "Find the audio on your phone, or choose a music folder.", action = "Find my music", onAction = scan); OutlinedButton(onClick = folder, modifier = Modifier.fillMaxWidth()) { Text("Choose a folder") } }
            else if (filtered.isEmpty() && category != "Playlists") item { EmptyState("No songs found", "Try a different spelling, artist, or a lyric you saved.", Icons.Rounded.SearchOff) }
            else when (category) {
                "Songs" -> items(filtered, key = { it.id }) { t -> TrackRow(vm, t, player.trackId == t.id, onClick = { vm.player.play(filtered, filtered.indexOf(t)) }, onMore = { more(t) }) }
                "Playlists" -> {
                    item { GroupRow("Liked songs", "${library.tracks.count { it.liked }} songs", Icons.Rounded.Favorite) { open("liked", "Liked songs") } }
                    items(library.playlists.filter { MusicLogic.score(query, it.name) > 0 }, key = { it.id }) { p -> GroupRow(p.name, "${p.trackIds.count { id -> library.tracks.any { it.id == id } }} songs", Icons.Rounded.QueueMusic) { open("playlist:${p.id}", p.name) } }
                    if (library.playlists.isEmpty()) item { EmptyState("A mix for every mood", "Put your songs in the order you like.", Icons.Rounded.PlaylistAdd, "Create playlist", createPlaylist) }
                }
                else -> {
                    val groups = when (category) { "Albums" -> filtered.groupBy { "${it.artist} • ${it.album}" }; "Artists" -> filtered.groupBy { it.artist }; else -> filtered.groupBy { it.folder.ifBlank { "Other music" } } }
                    items(groups.toList(), key = { it.first }) { (name, tracks) ->
                        Row(Modifier.fillMaxWidth().clickable { open("${when (category) { "Albums" -> "album"; "Artists" -> "artist"; else -> "folder" }}:$name", name.substringAfter(" • ")) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(vm, tracks.first(), Modifier.size(68.dp))
                            Column(Modifier.weight(1f).padding(start = 15.dp)) { Text(name.substringAfter(" • "), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${tracks.size} songs${if (category == "Albums") " · " + name.substringBefore(" • ") else ""}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                            Glyph(Icons.Rounded.ChevronRight, null, color = Muted)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun GroupRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).background(Raised, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Glyph(icon, null, color = Lilac) }
        Column(Modifier.weight(1f).padding(start = 15.dp)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Glyph(Icons.Rounded.ChevronRight, null, color = Muted)
    }
}
@Composable fun CollectionScreen(vm: AppViewModel, tracks: List<Track>, library: Library, playlist: Playlist?, more: (Track) -> Unit, part: (Track) -> Unit, onDeleted: () -> Unit, onRenamed: (String) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var arrange by remember { mutableStateOf(false) }
    val player by vm.player.state.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri -> if (uri != null) vm.work {
        vm.app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.appendLine("#EXTM3U"); tracks.forEach { writer.appendLine("#EXTINF:${it.durationMs / 1000},${it.artist.replace('\n', ' ')} - ${it.title.replace('\n', ' ')}"); writer.appendLine(it.uri) }
        }; vm.message.value = "Playlist exported. Content links work on this phone."
    } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(vm, tracks.firstOrNull(), Modifier.size(105.dp), 22)
                Column(Modifier.padding(start = 20.dp)) { Pill(if (playlist != null) "YOUR PLAYLIST" else "YOUR COLLECTION"); Text("${tracks.size} songs", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp)); Text("${tracks.sumOf { it.durationMs } / 60000} minutes · On this phone", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.player.play(tracks) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) { Glyph(Icons.Rounded.PlayArrow, null, color = Ink); Text("Play", Modifier.padding(start = 7.dp)) }
                OutlinedButton(onClick = { vm.player.play(tracks, shuffle = true) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) { Glyph(Icons.Rounded.Shuffle, null, color = Lime); Text("Shuffle", Modifier.padding(start = 7.dp)) }
            }
            OutlinedButton(onClick = { vm.player.play(tracks, parts = library.parts, highlights = true) }, enabled = tracks.any { t -> library.parts.any { it.trackId == t.id } }, modifier = Modifier.fillMaxWidth()) { Glyph(Icons.Rounded.AllInclusive, null, color = Lime); Text("Loop this playlist’s best parts", Modifier.padding(start = 9.dp), fontSize = 12.sp) }
            if (playlist != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { arrange = !arrange }) { Text(if (arrange) "Done" else "Reorder", fontSize = 12.sp) }
                TextButton(onClick = { rename = true }) { Text("Rename", fontSize = 12.sp) }
                TextButton(onClick = { export.launch("${MusicLogic.safeFilename(playlist.name)}.m3u") }) { Text("Export", fontSize = 12.sp) }
                IconAction(Icons.Rounded.DeleteOutline, "Delete playlist", color = Muted) { delete = true }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (tracks.isEmpty()) item { EmptyState("The next great mix starts here", "Open a song’s menu in Library to add it to this playlist.", Icons.Rounded.QueueMusic) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            if (arrange && playlist != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Column { IconAction(Icons.Rounded.KeyboardArrowUp, "Move ${t.title} up") { if (index > 0) vm.work { val ids = playlist.trackIds.toMutableList(); val from = ids.indexOf(t.id); java.util.Collections.swap(ids, from, from - 1); vm.store.savePlaylist(playlist.copy(trackIds = ids)) } }; IconAction(Icons.Rounded.KeyboardArrowDown, "Move ${t.title} down") { if (index < tracks.lastIndex) vm.work { val ids = playlist.trackIds.toMutableList(); val from = ids.indexOf(t.id); java.util.Collections.swap(ids, from, from + 1); vm.store.savePlaylist(playlist.copy(trackIds = ids)) } } }
                Box(Modifier.weight(1f)) { TrackRow(vm, t, player.trackId == t.id, onClick = { vm.player.play(tracks, index) }) }
                IconAction(Icons.Rounded.RemoveCircleOutline, "Remove ${t.title} from playlist", color = Muted) { vm.work { vm.store.savePlaylist(playlist.copy(trackIds = playlist.trackIds - t.id)) } }
            } else TrackRow(vm, t, player.trackId == t.id, onClick = { vm.player.play(tracks, index) }, onMore = { more(t) })
        }
    }
    if (rename && playlist != null) NameDialog("Rename playlist", "Name", playlist.name, { rename = false }) { name -> vm.work { vm.store.savePlaylist(playlist.copy(name = name)) }; onRenamed(name); rename = false }
    if (delete && playlist != null) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this playlist?") }, text = { Text("The songs stay in your library.") }, confirmButton = { TextButton(onClick = { vm.work { vm.store.deletePlaylist(playlist.id) }; delete = false; onDeleted() }) { Text("Delete playlist") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}
@Composable fun BestPartsScreen(vm: AppViewModel, library: Library, edit: (Track) -> Unit, browse: () -> Unit) {
    val validParts = library.parts.mapNotNull { p -> library.tracks.find { it.id == p.trackId && p.validFor(it.durationMs) }?.let { it to p } }
    LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Pill("SKIP TO THE FEELING", Lilac)
            Text("The good parts.\nOn repeat.", fontSize = 37.sp, lineHeight = 41.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp, modifier = Modifier.padding(vertical = 18.dp))
            Text("The drop. That chorus. One perfect line.\nKeep the moments you come back for.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            if (validParts.isNotEmpty()) Button(onClick = { vm.player.play(validParts.map { it.first }.distinctBy { it.id }, parts = validParts.map { it.second }, highlights = true) }, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) { Glyph(Icons.Rounded.AllInclusive, null, color = Ink); Text("Loop all best parts", Modifier.padding(start = 9.dp)) }
        }
        if (validParts.isEmpty()) item { EmptyState("Keep your favorite moment", "Open a song’s menu and choose “Save a best part”. You can set exact start and end points.", Icons.Rounded.AllInclusive, "Browse my music", browse) }
        items(validParts, key = { it.second.id }) { (t, p) ->
            Column(Modifier.padding(bottom = 13.dp).background(Panel, RoundedCornerShape(20.dp)).padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(vm, t, Modifier.size(58.dp), 12)
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text(p.label, color = Lilac, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(t.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)); Text("${clock(p.startMs)} — ${clock(p.endMs)} · ${clock(p.endMs - p.startMs)}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
                    FilledIconButton(onClick = { vm.player.play(listOf(t), parts = listOf(p), highlights = true, singlePart = p) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lilac, contentColor = Ink)) { Glyph(Icons.Rounded.PlayArrow, "Loop ${p.label}", color = Ink) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { edit(t) }) { Text("Edit", color = Muted, fontSize = 12.sp) }; TextButton(onClick = { vm.work { vm.store.deletePart(p.id) } }) { Text("Remove", color = Muted, fontSize = 12.sp) } }
            }
        }
    }
}
