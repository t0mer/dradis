package dev.tomerklein.dradis.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.commands.GalleryItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pictures received on PICTURE custom topics: thumbnails, full view, share, delete. */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val store = ServiceLocator.pictures
    val version by store.changes.collectAsState()
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var opened by remember { mutableStateOf<GalleryItem?>(null) }

    LaunchedEffect(version) { items = withContext(Dispatchers.IO) { store.list() } }

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pictures yet. Pictures arrive through custom topics of type Picture.", Modifier.padding(24.dp))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.name }) { item ->
                Thumbnail(item, 300, Modifier.aspectRatio(1f).clickable { opened = item })
            }
        }
    }

    opened?.let { item ->
        ViewerDialog(item, onClose = { opened = null }, onDelete = {
            store.delete(item.name)
            opened = null
        })
    }
}

@Composable
private fun Thumbnail(item: GalleryItem, maxPx: Int, modifier: Modifier) {
    var bmp by remember(item.name, maxPx) { mutableStateOf(Cache.get(item.name, maxPx)) }
    LaunchedEffect(item.name, maxPx) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) { decode(item.name, maxPx) }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier)
    }
}

@Composable
private fun ViewerDialog(item: GalleryItem, onClose: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        text = { Thumbnail(item, 1600, Modifier.fillMaxWidth().aspectRatio(1f)) },
        confirmButton = {
            Row {
                TextButton(onClick = { share(context, item) }) { Text("Share") }
                TextButton(onClick = { confirm = true }) { Text("Delete") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this picture?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun share(context: Context, item: GalleryItem) {
    val file = ServiceLocator.pictures.fileFor(item.name) ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.gallery", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.name.endsWith(".png")) "image/png" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share picture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Bounded in-memory thumbnail cache keyed by file name and target size. */
private object Cache {
    private val lru = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(name: String, px: Int): Bitmap? = lru.get("$name@$px")
    fun put(name: String, px: Int, b: Bitmap) { lru.put("$name@$px", b) }
}

private fun decode(name: String, maxPx: Int): Bitmap? {
    val file: File = ServiceLocator.pictures.fileFor(name) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    Cache.put(name, maxPx, bmp)
    return bmp
}
