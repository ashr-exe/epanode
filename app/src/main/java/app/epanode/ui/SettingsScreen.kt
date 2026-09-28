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

@Composable fun SettingsScreen(vm: AppViewModel, scan: () -> Unit, folder: () -> Unit, backup: () -> Unit, restore: () -> Unit) {
    var wifi by remember { mutableStateOf(vm.prefs.getBoolean("wifiOnly", true)) }
    var eq by remember { mutableStateOf(vm.prefs.getString("eq", "Off") ?: "Off") }
    var licenses by remember { mutableStateOf(false) }
    var fullLicenses by remember { mutableStateOf(false) }
    val licenseText by produceState("") {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.app.assets.open("licenses.txt").bufferedReader().use { it.readText() } }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        Text("Make it yours.", fontSize = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 24.dp))
        SectionTitle("Your music")
        ActionRow(Icons.Rounded.Sync, "Scan phone storage", scan)
        ActionRow(Icons.Rounded.FolderOpen, "Add a music folder", folder)
        Text("Scanning uses Android’s media index. Add a folder for music or .lrc files Android has not indexed. Hidden .nomedia folders are skipped.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        HorizontalDivider(color = Raised)
        SectionTitle("Sound")
        Text("Equalizer", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Off", "Bass", "Vocal", "Rock", "Jazz", "Classical").forEach { preset -> FilterChip(selected = eq == preset, onClick = { eq = preset; vm.prefs.edit().putString("eq", preset).apply(); vm.player.eq(preset) }, label = { Text(preset) }) }
        }
        Text("Uses your device’s audio effects. Available presets depend on the output device.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 22.dp))
        HorizontalDivider(color = Raised)
        SectionTitle("Downloads")
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Wi-Fi only", fontSize = 15.sp); Text("Applies to newly queued downloads", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) }; Switch(checked = wifi, onCheckedChange = { wifi = it; vm.prefs.edit().putBoolean("wifiOnly", it).apply() }) }
        Text("Audio is saved in Music/Epanode. Each job checks for low storage and exposes errors and retries in Discover.", fontSize = 12.sp, lineHeight = 18.sp, color = Muted, modifier = Modifier.padding(vertical = 18.dp))
        HorizontalDivider(color = Raised)
        SectionTitle("Keep your collection")
        ActionRow(Icons.Rounded.SaveAlt, "Export a backup", backup)
        ActionRow(Icons.Rounded.Restore, "Restore a backup", restore)
        Text("Backups include playlists, best parts, likes, history, and edited details. Music files are separate. Scan your music before restoring metadata.", fontSize = 12.sp, lineHeight = 18.sp, color = Muted, modifier = Modifier.padding(bottom = 20.dp))
        HorizontalDivider(color = Raised)
        SectionTitle("About Epanode")
        Text("0.1.0 · Your music, close to home", color = Muted, fontSize = 13.sp)
        Text("No account, ads, analytics, or subscription. Listening history and recommendations stay on this phone. Search, downloads, metadata lookup, and remote artwork contact their respective providers.", fontSize = 12.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { licenses = true }) { Text("Open-source licenses") }
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Built in the open") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        if (fullLicenses) Text(licenseText, fontSize = 11.sp, lineHeight = 17.sp) else Text("Epanode is licensed under GPL-3.0-or-later.\n\nNewPipe Extractor — GPL-3.0-or-later\nAndroidX, Compose, Media3, WorkManager — Apache-2.0\nKotlin, Coroutines, OkHttp, Coil, nanojson — Apache-2.0\njsoup — MIT\nRhino — MPL-2.0\nProtocol Buffers — BSD-3-Clause\n\nFull license texts and dependency sources are included with the source distribution.", fontSize = 13.sp, lineHeight = 22.sp)
        TextButton(onClick = { fullLicenses = !fullLicenses }) { Text(if (fullLicenses) "Show summary" else "Read full license texts") }
    } }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Done") } })
}
