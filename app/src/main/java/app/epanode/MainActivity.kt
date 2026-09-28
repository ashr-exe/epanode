package app.epanode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.epanode.ui.AppViewModel
import app.epanode.ui.EpanodeScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { EpanodeScreen(vm) }; if (savedInstanceState == null) receive(intent) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::enqueue)
        else if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: intent.data
            uri?.takeIf { it.scheme == "content" }?.let {
                runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                vm.importAudio(it, it.lastPathSegment.orEmpty())
            }
        }
    }
}
