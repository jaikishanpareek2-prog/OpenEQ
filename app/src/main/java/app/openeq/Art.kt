package app.openeq

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object Art {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = Collections.synchronizedSet(HashSet<String>())

    private fun key(id: Long, px: Int) = "$id:$px"

    fun cached(id: Long, px: Int): Bitmap? = cache.get(key(id, px))

    fun load(ctx: Context, id: Long, px: Int): Bitmap? {
        val k = key(id, px)
        cache.get(k)?.let { return it }
        if (missing.contains(k)) return null
        var bmp: Bitmap? = null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                bmp = ctx.contentResolver.loadThumbnail(uri, android.util.Size(px, px), null)
            } catch (_: Throwable) {}
        }
        if (bmp == null) {
            try {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, uri)
                    val data = r.embeddedPicture
                    if (data != null) {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(data, 0, data.size, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
                        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                        bmp = BitmapFactory.decodeByteArray(data, 0, data.size, o2)
                    }
                } finally {
                    r.release()
                }
            } catch (_: Throwable) {}
        }
        if (bmp != null) cache.put(k, bmp) else missing.add(k)
        return bmp
    }
}

@Composable
fun ArtThumb(id: Long, sizeDp: Int) {
    val ctx = LocalContext.current
    val px = (sizeDp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    var bmp by remember(id) { mutableStateOf(Art.cached(id, px)) }
    LaunchedEffect(id) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) { Art.load(ctx, id, px) }
    }
    Box(
        Modifier.padding(end = 10.dp).size(sizeDp.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF00222A)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.size(sizeDp.dp), contentScale = ContentScale.Crop)
        else Text("♪", color = Color(0xFF0F6C7C), fontSize = (sizeDp / 2).sp)
    }
}
