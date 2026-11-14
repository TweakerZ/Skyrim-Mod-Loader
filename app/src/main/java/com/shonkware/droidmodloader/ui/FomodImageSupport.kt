package com.shonkware.droidmodloader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.install.PreparedArchiveInstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_IMAGE_DIMENSION = 512

/**
 * Resolves a FOMOD `<image path>` against the prepared archive. The path is
 * normally relative to the archive root, but many mods also keep images beside
 * `ModuleConfig.xml` in the `fomod/` folder, so both roots are tried with a
 * case-insensitive fallback for Windows-authored paths on a case-sensitive
 * filesystem.
 */
internal fun resolveFomodImage(prepared: PreparedArchiveInstall, imagePath: String): File? {
    val normalized = imagePath
        .replace('\\', '/')
        .trim()
        .replace(Regex("/+"), "/")
        .trimStart('/')
    if (normalized.isEmpty()) return null

    val parts = normalized.split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty() || parts.any { it == ".." }) return null

    val roots = listOf(
        File(prepared.installRootPath),
        File(prepared.installRootPath, "fomod"),
        File(prepared.extractedRootPath),
        File(prepared.extractedRootPath, "fomod")
    )

    for (root in roots) {
        File(root, normalized).takeIf { it.isFile }?.let { return it }
        findCaseInsensitiveFile(root, parts)?.let { return it }
    }
    return null
}

private fun findCaseInsensitiveFile(dir: File, parts: List<String>): File? {
    if (parts.isEmpty()) return null
    val match = dir.listFiles()?.firstOrNull {
        it.name.equals(parts.first(), ignoreCase = true)
    } ?: return null
    return if (parts.size == 1) match.takeIf { it.isFile }
    else findCaseInsensitiveFile(match, parts.drop(1))
}

/**
 * Shows a FOMOD option image. Resolution and decoding run off the main thread
 * and decoded bitmaps are cached, since the options list is a LazyColumn that
 * recomposes rows while scrolling.
 */
@Composable
internal fun FomodOptionImage(
    prepared: PreparedArchiveInstall,
    imagePath: String,
    modifier: Modifier = Modifier
) {
    if (imagePath.isBlank()) return

    val key = prepared.installRootPath + "\u0000" + imagePath
    val image by produceState<ImageBitmap?>(
        initialValue = FomodImageCache.get(key),
        key1 = key
    ) {
        if (value == null) {
            val decoded = withContext(Dispatchers.IO) {
                resolveFomodImage(prepared, imagePath)
                    ?.let { decodeScaledBitmap(it, MAX_IMAGE_DIMENSION) }
                    ?.asImageBitmap()
            }
            if (decoded != null) FomodImageCache.put(key, decoded)
            value = decoded
        }
    }

    image?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .heightIn(max = 180.dp)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Fit
        )
    }
}

private fun decodeScaledBitmap(file: File, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

private object FomodImageCache {
    private const val MAX_ENTRIES = 24
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, image: ImageBitmap) {
        cache.put(key, image)
    }
}
