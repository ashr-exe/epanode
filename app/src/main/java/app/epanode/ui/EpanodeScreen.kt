package app.epanode.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.epanode.core.*
import app.epanode.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EpanodeScreen(vm: AppViewModel) = EpanodeTheme {
    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val requestedTab by vm.initialTab.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Home") }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var editing by remember { mutableStateOf<Track?>(null) }
    var clipping by remember { mutableStateOf<Track?>(null) }
    var playlistFor by remember { mutableStateOf<Track?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionTitle by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val playbackError by PlaybackService.playbackError.collectAsStateWithLifecycle()
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.scan() else vm.message.value = "Music permission was not granted. You can still choose a folder." }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(library.downloads.size) {
        if (Build.VERSION.SDK_INT >= 33 && library.downloads.isNotEmpty() && !vm.prefs.getBoolean("notificationAsked", false)) {
            vm.prefs.edit().putBoolean("notificationAsked", true).apply()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.scan(it) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    val lyricLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = editing ?: player.trackId?.let { id -> library.tracks.find { it.id == id } }
        if (uri != null && track != null) vm.work {
            val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBounded(256000)) }.orEmpty()
            vm.store.update(track.copy(lyrics = text)); vm.message.value = "Lyrics imported."
        }
    }
    val scan: () -> Unit = { if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan() else permissionLauncher.launch(permission) }
    LaunchedEffect(requestedTab) { tab = requestedTab }
    LaunchedEffect(Unit) { if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(playbackError) { playbackError?.let { snack.showSnackbar(it); PlaybackService.playbackError.value = null } }
    BackHandler(collection != null || settings) { collection = null; settings = false }
    val now = library.tracks.find { it.id == player.trackId }
    Scaffold(containerColor = Ink, snackbarHost = {
        SnackbarHost(snack) { data ->
            Snackbar(dismissAction = { IconButton(onClick = { data.dismiss() }) { Glyph(Icons.Rounded.Close, "Dismiss message", color = MaterialTheme.colorScheme.inverseOnSurface) } }) {
                Text(data.visuals.message)
            }
        }
    }, bottomBar = {
        Column(Modifier.background(Ink).navigationBarsPadding()) {
            if (now != null) MiniPlayer(vm, now) { fullPlayer = true }
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp, windowInsets = WindowInsets(0), modifier = Modifier.height(76.dp)) {
                listOf("Home" to Icons.Rounded.Home, "Library" to Icons.Rounded.LibraryMusic, "Best parts" to Icons.Rounded.AllInclusive, "Discover" to Icons.Rounded.Explore).forEach { (label, icon) ->
                    NavigationBarItem(selected = tab == label && !settings, onClick = { tab = label; settings = false; collection = null }, icon = { Glyph(icon, label, color = if (tab == label && !settings) Lime else Muted) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Raised, selectedTextColor = Lime, unselectedTextColor = Muted))
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (collection != null || settings) IconAction(Icons.Rounded.ArrowBack, "Back") { collection = null; settings = false }
                else Glyph(Icons.Rounded.GraphicEq, "Epanode", color = Lime)
                Text(if (settings) "Settings" else if (collection != null) collectionTitle else "epanode", fontSize = if (collection == null && !settings) 28.sp else 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.weight(1f).padding(start = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!settings) IconAction(Icons.Rounded.Tune, "Settings", onClick = { settings = true; collection = null })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = Lime)
            when {
                settings -> SettingsScreen(vm, { scan() }, { folderLauncher.launch(null) }, { backupLauncher.launch("epanode-backup.json") }, { restoreLauncher.launch(arrayOf("application/json")) })
                collection != null -> {
                    val key = collection!!
                    val p = library.playlists.find { "playlist:${it.id}" == key }
                    val tracks = when {
                        p != null -> p.trackIds.mapNotNull { id -> library.tracks.find { it.id == id } }
                        key == "liked" -> library.tracks.filter { it.liked }
                        key == "recent" -> library.tracks.sortedByDescending { it.added }
                        key == "repeat" -> library.tracks.filter { it.plays > 0 }.sortedByDescending { it.plays }
                        key == "recommendations" -> MusicLogic.recommendations(library.tracks, now)
                        key.startsWith("album:") -> library.tracks.filter { "${it.artist} • ${it.album}" == key.removePrefix("album:") }
                        key.startsWith("artist:") -> library.tracks.filter { it.artist == key.removePrefix("artist:") }
                        key.startsWith("folder:") -> library.tracks.filter { it.folder == key.removePrefix("folder:") }
                        else -> emptyList()
                    }
                    CollectionScreen(vm, tracks, library, p, { focus.clearFocus(); keyboard?.hide(); selected = it }, { clipping = it }, onDeleted = { collection = null }, onRenamed = { collectionTitle = it })
                }
                tab == "Home" -> HomeScreen(vm, library, scan, { key, title -> collection = key; collectionTitle = title }, { focus.clearFocus(); keyboard?.hide(); selected = it })
                tab == "Library" -> LibraryScreen(vm, library, scan, { folderLauncher.launch(null) }, { key, title -> collection = key; collectionTitle = title }, { focus.clearFocus(); keyboard?.hide(); selected = it }, { creatingPlaylist = true })
                tab == "Best parts" -> BestPartsScreen(vm, library, { clipping = it }, { tab = "Library" })
                else -> DiscoverScreen(vm, library)
            }
        }
    }
    if (fullPlayer && now != null) FullPlayer(vm, now, library, onDismiss = { fullPlayer = false }, onPart = { clipping = now }, onEdit = { editing = now })
    selected?.let { t ->
        var playlistName by remember(t.id) { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { selected = null; playlistFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
                TrackRow(vm, t, onClick = {})
                if (playlistFor != null) {
                    Text("Add to playlist", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 16.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        library.playlists.forEach { p -> TextButton(onClick = { vm.work { vm.store.addToPlaylist(p.id, t.id) }; playlistFor = null; selected = null }) { Text(p.name) } }
                    }
                    OutlinedTextField(playlistName, { playlistName = it.take(100) }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(enabled = playlistName.isNotBlank(), onClick = { vm.playlist(playlistName, listOf(t.id)); playlistFor = null; selected = null }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Create & add") }
                    TextButton(onClick = { playlistFor = null }) { Text("Back to song options") }
                } else {
                    ActionRow(Icons.Rounded.PlayArrow, "Play next") { vm.player.add(t, true); selected = null }
                    ActionRow(Icons.Rounded.QueueMusic, "Add to queue") { vm.player.add(t, false); selected = null }
                    ActionRow(if (t.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (t.liked) "Remove from liked songs" else "Like song") { vm.like(t); selected = null }
                    ActionRow(Icons.Rounded.PlaylistAdd, "Add to playlist") { playlistFor = t }
                    ActionRow(Icons.Rounded.AllInclusive, "Save a best part") { clipping = t; selected = null }
                    ActionRow(Icons.Rounded.Radio, "Start a local song radio") { vm.player.play(listOf(t) + MusicLogic.recommendations(library.tracks, t)); selected = null }
                    ActionRow(Icons.Rounded.Edit, "Edit details & lyrics") { editing = t; selected = null }
                }
            }
        }
    }
    clipping?.let { t -> BestPartEditor(vm, t, library.parts.filter { it.trackId == t.id }, onDismiss = { clipping = null }) }
    editing?.let { t -> MetadataEditor(vm, library.tracks.find { it.id == t.id } ?: t, { lyricLauncher.launch(arrayOf("text/*", "application/octet-stream")) }) { editing = null } }
    if (creatingPlaylist) NameDialog("New playlist", "Playlist name", onDismiss = { creatingPlaylist = false }) { vm.playlist(it); creatingPlaylist = false }

}
@Composable fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Glyph(icon, null, color = Lime); Text(label, modifier = Modifier.padding(start = 18.dp), fontSize = 15.sp) }
}
@Composable fun FormDialog(title: String, onDismiss: () -> Unit, confirmLabel: String, enabled: Boolean = true, onConfirm: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(22.dp).heightIn(max = 650.dp), shape = RoundedCornerShape(26.dp), color = Panel) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.padding(start = 10.dp)) { Text(confirmLabel) }
                }
            }
        }
    }
}
@Composable fun NameDialog(title: String, hint: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    FormDialog(title, onDismiss, "Save", name.isNotBlank(), { onSave(name.trim()) }) {
        OutlinedTextField(name, { name = it.take(100) }, label = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
@Composable fun HomeScreen(vm: AppViewModel, library: Library, scan: () -> Unit, open: (String, String) -> Unit, more: (Track) -> Unit) {
    val now by vm.player.state.collectAsStateWithLifecycle()
    val recent = remember(library.tracks) { library.tracks.sortedByDescending { it.lastPlayed }.filter { it.lastPlayed > 0 }.take(8) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(ColorOlive, Panel)), RoundedCornerShape(28.dp)).padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Pill("ALL YOURS. ALL LOCAL."); Glyph(Icons.Rounded.OfflineBolt, null, color = Lime) }
                Text("Your music.\nYour rhythm.", fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.8).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 27.dp, bottom = 12.dp))
                Text(if (library.tracks.isEmpty()) "A little space for everything you love.\nBring your music home." else "${library.tracks.size} songs. No subscriptions.\nJust you and the good parts.", color = Cream.copy(alpha = .72f), fontSize = 14.sp, lineHeight = 21.sp)
                Button(onClick = { if (library.tracks.isEmpty()) scan() else vm.player.play(MusicLogic.recommendations(library.tracks), shuffle = true) }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp), modifier = Modifier.padding(top = 23.dp)) {
                    Glyph(if (library.tracks.isEmpty()) Icons.Rounded.LibraryMusic else Icons.Rounded.PlayArrow, null, color = Ink); Text(if (library.tracks.isEmpty()) "Find my music" else "Play my mix", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { SectionTitle("Made from your music", "A fresh way back into your collection") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MixCard("Liked songs", "${library.tracks.count { it.liked }} favorites", Icons.Rounded.Favorite, Lilac, Modifier.weight(1f)) { open("liked", "Liked songs") }
                MixCard("On repeat", "Your familiar favorites", Icons.Rounded.Repeat, Lime, Modifier.weight(1f)) { open("repeat", "On repeat") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MixCard("Rediscover", "A new mix, every day", Icons.Rounded.AutoAwesome, androidx.compose.ui.graphics.Color(0xFFE4BB91), Modifier.weight(1f)) { open("recommendations", "Rediscover") }
                MixCard("Fresh arrivals", "Recently added", Icons.Rounded.SouthWest, androidx.compose.ui.graphics.Color(0xFF9BCED0), Modifier.weight(1f)) { open("recent", "Fresh arrivals") }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Back to your favorites", "Pick up where you left off") }
            items(recent, key = { it.id }) { t -> TrackRow(vm, t, now.trackId == t.id, onClick = { vm.player.play(recent, recent.indexOf(t)) }, onMore = { more(t) }) }
        } else if (library.tracks.isNotEmpty()) {
            item { SectionTitle("A place to start", "Straight from your library") }
            items(library.tracks.take(6), key = { it.id }) { t -> TrackRow(vm, t, now.trackId == t.id, onClick = { vm.player.play(library.tracks, library.tracks.indexOf(t)) }, onMore = { more(t) }) }
        }
        item { Text("ON YOUR PHONE. ON YOUR TERMS.", color = Muted.copy(alpha = .65f), fontSize = 9.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 16.dp)) }
    }
}
private val ColorOlive = androidx.compose.ui.graphics.Color(0xFF35442A)
@Composable fun MixCard(title: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, click: () -> Unit) {
    Column(modifier.background(Panel, RoundedCornerShape(20.dp)).clickable(onClick = click).padding(17.dp)) {
        Glyph(icon, null, color = color)
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 5.dp))
        Text(caption, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
