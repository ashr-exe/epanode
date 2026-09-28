package app.epanode.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import app.epanode.core.*
import app.epanode.playback.PlaybackService
import kotlinx.coroutines.delay

@Composable fun playbackPosition(vm: AppViewModel): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableLongStateOf(0) }
    LaunchedEffect(lifecycle, vm) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { state.longValue = vm.player.controller?.currentPosition ?: 0; delay(500) }
        }
    }
    return state
}
@Composable fun MiniPlayer(vm: AppViewModel, track: Track, expand: () -> Unit) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Raised).semantics { contentDescription = "Open player for ${track.title}" }.clickable(onClick = expand)) {
        Row(Modifier.padding(start = 8.dp, top = 7.dp, bottom = 7.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(vm, track, Modifier.size(44.dp), 10)
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.part?.let { "∞  $it" } ?: track.artist, fontSize = 11.sp, color = if (player.part != null) Lilac else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            IconAction(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "Pause" else "Play", Lime) { vm.player.toggle() }
            IconAction(Icons.Rounded.SkipNext, "Next song") { vm.player.next() }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FullPlayer(vm: AppViewModel, track: Track, library: Library, onDismiss: () -> Unit, onPart: () -> Unit, onEdit: () -> Unit) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val position by playbackPosition(vm)
    val sleepUntil by PlaybackService.sleepUntil.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("Playing") }
    var sleepDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = (vm.player.controller?.duration ?: track.durationMs).takeIf { it > 0 } ?: track.durationMs
    val currentClip = vm.player.controller?.currentMediaItem?.mediaMetadata?.extras?.getLong("clipStart", 0) ?: 0
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EpanodeTheme { Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.Rounded.KeyboardArrowDown, "Close player", onClick = onDismiss)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (player.part == null) "FROM YOUR LIBRARY" else "BEST PART ON REPEAT", fontSize = 10.sp, letterSpacing = 1.7.sp, color = if (player.part == null) Muted else Lilac)
                        Text("epanode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                    }
                    IconAction(Icons.Rounded.MoreHoriz, "Edit song details", onClick = onEdit)
                }
                if (page == "Queue") {
                    SectionTitle("Up next", "${player.queue.size} songs · ${if (player.shuffle) "Shuffled" else "In order"}")
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(player.queue, key = { index, _ -> index }) { index, item ->
                            val t = library.tracks.find { it.id == item.mediaId.substringBefore('@') }
                            if (t != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { TrackRow(vm, t, index == player.index, subtitle = item.mediaMetadata.extras?.getString("partLabel") ?: t.artist, onClick = { vm.player.controller?.seekTo(index, 0); vm.player.controller?.play() }) }
                                Column {
                                    IconButton(onClick = { if (index > 0) vm.player.controller?.moveMediaItem(index, index - 1) }, enabled = index > 0) { Glyph(Icons.Rounded.KeyboardArrowUp, "Move ${t.title} earlier", color = Muted) }
                                    IconButton(onClick = { vm.player.controller?.removeMediaItem(index) }) { Glyph(Icons.Rounded.Close, "Remove ${t.title} from queue", color = Muted) }
                                }
                            }
                        }
                    }
                } else if (page == "Lyrics") {
                    val lines = remember(track.lyrics) { MusicLogic.lyrics(track.lyrics) }
                    val active = lines.indexOfLast { it.timeMs <= position + currentClip }
                    val scroll = rememberLazyListState()
                    LaunchedEffect(active) { if (active >= 0) scroll.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                    if (track.lyrics.isBlank()) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { EmptyState("Every word, right here", "Add lyrics or import an .lrc file from song details. Timestamped lyrics follow the music.", Icons.Rounded.Lyrics, "Add lyrics", onEdit) }
                    else LazyColumn(Modifier.weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 30.dp)) {
                        if (lines.isEmpty()) item { Text(track.lyrics, fontSize = 24.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium) }
                        else itemsIndexed(lines) { index, line -> Text(line.text, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = if (index == active) Lime else Muted.copy(alpha = .65f), modifier = Modifier.fillMaxWidth().clickable { vm.player.seek(line.timeMs - currentClip) }) }
                    }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        Artwork(vm, track, Modifier.size(maxWidth.coerceAtMost(370.dp)).align(Alignment.Center), 26)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 27.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-.5).sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                        IconAction(if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (track.liked) "Unlike song" else "Like song", if (track.liked) Lime else Cream) { vm.like(track) }
                    }
                    if (player.part != null) Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Pill("∞  ${player.part}", Lilac); Spacer(Modifier.weight(1f)); TextButton(onClick = { vm.player.play(listOf(track)) }) { Text("Full song", fontSize = 12.sp) } }
                    Slider(value = scrubbing ?: position.toFloat().coerceIn(0f, duration.toFloat()), onValueChange = { scrubbing = it }, onValueChangeFinished = { scrubbing?.let { vm.player.seek(it.toLong()) }; scrubbing = null }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), modifier = Modifier.padding(top = 20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(scrubbing?.toLong() ?: position), color = Muted, fontSize = 11.sp); Text(clock(duration), color = Muted, fontSize = 11.sp) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconAction(Icons.Rounded.Shuffle, if (player.shuffle) "Disable shuffle" else "Enable shuffle", if (player.shuffle) Lime else Muted) { vm.player.controller?.shuffleModeEnabled = !player.shuffle }
                        IconAction(Icons.Rounded.SkipPrevious, "Previous song") { vm.player.previous() }
                        FilledIconButton(onClick = { vm.player.toggle() }, modifier = Modifier.size(76.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Ink)) { Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "Pause" else "Play", Modifier.size(39.dp)) }
                        IconAction(Icons.Rounded.SkipNext, "Next song") { vm.player.next() }
                        IconAction(if (player.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat: ${when (player.repeat) { 1 -> "one"; 2 -> "all"; else -> "off" }}", if (player.repeat != 0) Lime else Muted) { vm.player.controller?.repeatMode = when (player.repeat) { 0 -> 2; 2 -> 1; else -> 0 } }
                    }
                    OutlinedButton(onClick = onPart, modifier = Modifier.fillMaxWidth()) { Glyph(Icons.Rounded.AllInclusive, null, color = Lilac); Text("Save a best part", color = Lilac, modifier = Modifier.padding(start = 10.dp)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { speedDialog = true }) { Text("${player.speed}× speed", color = Muted, fontSize = 12.sp) }
                        TextButton(onClick = { sleepDialog = true }) { Glyph(Icons.Rounded.Bedtime, null, Modifier.size(16.dp), if (sleepUntil > 0) Lime else Muted); Text(if (sleepUntil > 0) "  Sleep timer on" else "  Sleep timer", color = if (sleepUntil > 0) Lime else Muted, fontSize = 12.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Playing" to Icons.Rounded.GraphicEq, "Lyrics" to Icons.Rounded.Lyrics, "Queue" to Icons.Rounded.QueueMusic).forEach { (label, icon) ->
                        TextButton(onClick = { page = label }) { Glyph(icon, null, Modifier.size(18.dp), if (page == label) Lime else Muted); Text(label, Modifier.padding(start = 6.dp), color = if (page == label) Lime else Muted, fontSize = 12.sp) }
                    }
                }
            }
        } }
    }
    if (sleepDialog) AlertDialog(onDismissRequest = { sleepDialog = false }, title = { Text("Sleep timer") }, text = { Column { listOf(0, 5, 15, 30, 45, 60, 90).forEach { minutes -> TextButton(onClick = { vm.player.sleep(minutes); sleepDialog = false }) { Text(if (minutes == 0) "Turn off" else "$minutes minutes") } } } }, confirmButton = {})
    if (speedDialog) AlertDialog(onDismissRequest = { speedDialog = false }, title = { Text("Playback speed") }, text = { Column { listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> TextButton(onClick = { vm.player.controller?.setPlaybackSpeed(speed); speedDialog = false }) { Text("${speed}×") } } } }, confirmButton = {})
}
