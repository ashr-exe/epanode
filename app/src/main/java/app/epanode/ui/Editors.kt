package app.epanode.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.epanode.core.*
import kotlinx.coroutines.launch
import java.util.UUID

private fun exactTime(ms: Long) = "%d:%02d.%03d".format(java.util.Locale.ROOT, ms / 60000, ms / 1000 % 60, ms % 1000)
fun parseTime(text: String): Long? {
    val clean = text.trim()
    if (!clean.matches(Regex("(?:\\d+:)?\\d+(?:\\.\\d{1,3})?"))) return null
    val fields = clean.split(':'); val seconds = fields.last().toDoubleOrNull() ?: return null
    if (fields.size > 1 && seconds >= 60) return null
    val value = (fields.firstOrNull()?.takeIf { fields.size > 1 }?.toLongOrNull() ?: 0) * 60000 + (seconds * 1000).toLong()
    return value.takeIf { it >= 0 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BestPartEditor(vm: AppViewModel, track: Track, saved: List<BestPart>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<BestPart?>(null) }
    var label by remember { mutableStateOf("The best part") }
    val initialPosition = remember(track.id) { if (vm.player.state.value.trackId == track.id) vm.player.controller?.currentPosition?.coerceAtMost((track.durationMs - 500).coerceAtLeast(0)) ?: 0 else 0 }
    var startText by remember { mutableStateOf(exactTime(initialPosition)) }
    var endText by remember { mutableStateOf(exactTime((initialPosition + 15000).coerceAtMost(track.durationMs))) }
    val start = parseTime(startText); val end = parseTime(endText)
    val valid = start != null && end != null && end - start >= 500 && end <= track.durationMs && label.isNotBlank()
    fun currentPosition(): Long = if (vm.player.state.value.trackId == track.id) ((vm.player.controller?.currentPosition ?: 0) + (vm.player.controller?.currentMediaItem?.mediaMetadata?.extras?.getLong("clipStart") ?: 0)).coerceIn(0, track.durationMs) else 0
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Panel) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) { Glyph(Icons.Rounded.AllInclusive, null, color = Lilac); Text("Keep the good part", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.7).sp, modifier = Modifier.weight(1f).padding(start = 10.dp)); IconAction(Icons.Rounded.Close, "Close best part editor", onClick = onDismiss) }
                Text(track.title, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                if (saved.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected == null, onClick = { selected = null; label = "The best part" }, label = { Text("New") })
                    saved.forEach { p -> FilterChip(selected?.id == p.id, onClick = { selected = p; label = p.label; startText = exactTime(p.startMs); endText = exactTime(p.endMs) }, label = { Text(p.label) }) }
                }
                OutlinedTextField(label, { label = it.take(100) }, label = { Text("Name this moment") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Set the range", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 25.dp))
                RangeSlider(value = (start ?: 0).coerceIn(0, track.durationMs).toFloat()..(end ?: track.durationMs).coerceIn((start ?: 0).coerceAtMost(track.durationMs), track.durationMs).toFloat(), onValueChange = { range -> startText = exactTime(range.start.toLong()); endText = exactTime(range.endInclusive.toLong()) }, valueRange = 0f..track.durationMs.coerceAtLeast(1).toFloat())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(startText, { startText = it }, label = { Text("Start · m:ss.sss") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(endText, { endText = it }, label = { Text("End · m:ss.sss") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { startText = exactTime(currentPosition()) }) { Text("Mark current as start", fontSize = 11.sp) }
                    TextButton(onClick = { endText = exactTime(currentPosition()) }) { Text("Mark current as end", fontSize = 11.sp) }
                }
                Text(if (valid) "${clock(end!! - start!!)} on repeat · ${clock(track.durationMs)} full song" else "Choose at least 0.5 seconds within this song.", color = if (valid) Muted else Lilac, fontSize = 12.sp, modifier = Modifier.padding(vertical = 13.dp))
                OutlinedButton(onClick = { vm.player.play(listOf(track), highlights = true, singlePart = BestPart("preview", track.id, label, start!!, end!!)) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Glyph(Icons.Rounded.PlayArrow, null, color = Lilac); Text("Preview loop", color = Lilac, modifier = Modifier.padding(start = 8.dp)) }
                Button(onClick = { vm.savePart(track, label, start!!, end!!, selected?.id ?: UUID.randomUUID().toString()); onDismiss() }, enabled = valid, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Save best part") }
            }
        }
    }
}
@Composable fun MetadataEditor(vm: AppViewModel, track: Track, importLyrics: () -> Unit, onDismiss: () -> Unit) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var lyrics by remember(track.id, track.lyrics) { mutableStateOf(track.lyrics) }
    var artwork by remember(track.id) { mutableStateOf(track.artwork) }
    var candidates by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 760.dp), shape = RoundedCornerShape(24.dp), color = Panel) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Song details", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconAction(Icons.Rounded.Close, "Close details editor", onClick = onDismiss) }
                Text("${track.confidence} · ${track.filename}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(title, { title = it.take(300) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(artist, { artist = it.take(300) }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
                OutlinedTextField(album, { album = it.take(300) }, label = { Text("Album") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
                TextButton(enabled = !searching && title.isNotBlank(), onClick = {
                    scope.launch { searching = true; error = null; try { candidates = vm.metadataCandidates(track.copy(title = title, artist = artist)) } catch (e: Exception) { error = e.message } finally { searching = false } }
                }) { Text(if (searching) "Finding metadata…" else "Find metadata & cover online") }
                Text("Online lookup sends the title and artist to MusicBrainz. Choose a match to apply it.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                error?.let { Text(it, color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                candidates.forEach { candidate -> TextButton(onClick = { title = candidate.title; artist = candidate.artist; album = candidate.album; artwork = candidate.artwork; candidates = emptyList() }) { Text("${candidate.title} · ${candidate.artist}\n${candidate.album}", fontSize = 12.sp) } }
                OutlinedTextField(lyrics, { lyrics = it.take(256000) }, label = { Text("Lyrics · plain text or LRC") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), minLines = 4, maxLines = 7)
                TextButton(onClick = importLyrics) { Text("Import a lyrics file") }
                Button(enabled = title.isNotBlank(), onClick = { vm.saveTrack(track.copy(title = title.trim(), artist = artist.trim().ifBlank { "Unknown artist" }, album = album.trim().ifBlank { "Unknown album" }, lyrics = lyrics, artwork = artwork)); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Save details") }
                Text("Changes stay in Epanode and survive rescans. Your original files are preserved.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
