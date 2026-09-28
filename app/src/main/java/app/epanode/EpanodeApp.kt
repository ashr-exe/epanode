package app.epanode

import android.app.Application
import app.epanode.data.LibraryScanner
import app.epanode.data.LibraryStore
import app.epanode.imports.Downloads
import kotlinx.coroutines.*

class EpanodeApp : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader() = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(12 * 1024 * 1024).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(java.io.File(cacheDir, "covers")).maxSizeBytes(24L * 1024 * 1024).build() }
        .build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var store: LibraryStore; private set
    lateinit var scanner: LibraryScanner; private set
    lateinit var downloads: Downloads; private set
    override fun onCreate() {
        super.onCreate()
        store = LibraryStore(this)
        scanner = LibraryScanner(this, store)
        downloads = Downloads(this, store)
        scope.launch { store.refresh() }
    }
}
