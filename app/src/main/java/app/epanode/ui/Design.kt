package app.epanode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.epanode.core.*
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Ink = Color(0xFF10130F)
val Panel = Color(0xFF1B2019)
val Raised = Color(0xFF252C22)
val Lime = Color(0xFFD4F28B)
val Cream = Color(0xFFF1F2E9)
val Muted = Color(0xFFA1AA99)
val Lilac = Color(0xFFD0C7EC)
private val Scheme = darkColorScheme(primary = Lime, onPrimary = Ink, secondary = Lilac, background = Ink, surface = Panel, onSurface = Cream, onBackground = Cream, surfaceVariant = Raised, onSurfaceVariant = Muted, outline = Color(0xFF414B39))
@Composable fun EpanodeTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content) }
@Composable fun Glyph(icon: ImageVector, label: String?, modifier: Modifier = Modifier, color: Color = Cream) { Icon(icon, label, modifier.size(24.dp), tint = color) }
@Composable fun IconAction(icon: ImageVector, label: String, color: Color = Cream, onClick: () -> Unit) { IconButton(onClick = onClick) { Glyph(icon, label, color = color) } }
@Composable fun SectionTitle(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp); subtitle?.let { Text(it, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) } }
        action?.let { TextButton(onClick = onAction) { Text(it, fontSize = 12.sp) } }
    }
}
@Composable fun Pill(text: String, color: Color = Lime) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .11f)).padding(horizontal = 10.dp, vertical = 6.dp)) }
@Composable fun EmptyState(title: String, text: String, icon: ImageVector = Icons.Rounded.LibraryMusic, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Raised), contentAlignment = Alignment.Center) { Glyph(icon, null, Modifier.size(34.dp), Lime) }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(text, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
        action?.let { Button(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) { Text(it) } }
    }
}
@Composable fun Artwork(vm: AppViewModel, track: Track?, modifier: Modifier = Modifier, radius: Int = 16) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val art by produceState<Any?>(null, track?.id, track?.modified, track?.artwork) {
        value = if (track == null) null else withContext(Dispatchers.IO) { vm.app.scanner.cacheArtwork(track) ?: track.artwork.takeIf { it.isNotBlank() } }
    }
    val hash = (track?.album?.takeUnless { it == "Unknown album" } ?: track?.title ?: "Epanode").hashCode()
    val colors = listOf(Color(0xFF74885D) to Color(0xFF263A2C), Color(0xFF937C9E) to Color(0xFF373249), Color(0xFF9C7955) to Color(0xFF4B3329), Color(0xFF5D8790) to Color(0xFF203A40))
    val pair = colors[Math.floorMod(hash, colors.size)]
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(Brush.linearGradient(listOf(pair.first, pair.second)))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .52f, size.height * .49f)
            val radiusPx = size.minDimension * .39f
            drawCircle(Color.Black.copy(alpha = .22f), radiusPx, center)
            for (i in 1..8) drawCircle(Color.White.copy(alpha = .06f), radiusPx * (.30f + i * .08f), center, style = Stroke(size.minDimension * .005f))
            drawCircle(pair.first.copy(alpha = .75f), radiusPx * .26f, center)
            drawCircle(Cream.copy(alpha = .8f), radiusPx * .07f, center)
            drawLine(Cream.copy(alpha = .65f), Offset(size.width * .78f, size.height * .13f), Offset(size.width * .71f, size.height * .61f), size.minDimension * .025f, StrokeCap.Round)
        }
        if (art != null) AsyncImage(art, contentDescription = "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
@Composable fun TrackRow(vm: AppViewModel, track: Track, playing: Boolean = false, subtitle: String? = null, onClick: () -> Unit, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(vm, track, Modifier.size(53.dp), 10)
        Column(Modifier.weight(1f).padding(start = 13.dp, end = 6.dp)) {
            Text(track.title, color = if (playing) Lime else Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle ?: track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (track.liked) Glyph(Icons.Rounded.Favorite, null, Modifier.size(14.dp), Lime)
        if (onMore != null) IconAction(Icons.Rounded.MoreHoriz, "More options for ${track.title}", onClick = onMore)
        else Text(clock(track.durationMs), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 10.dp))
    }
}
@Composable fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), placeholder = { Text(placeholder, fontSize = 14.sp) }, singleLine = true,
        leadingIcon = { Glyph(Icons.Rounded.Search, null, color = Muted) }, trailingIcon = { if (value.isNotEmpty()) IconAction(Icons.Rounded.Close, "Clear search") { onValueChange("") } }, shape = RoundedCornerShape(18.dp))
}
