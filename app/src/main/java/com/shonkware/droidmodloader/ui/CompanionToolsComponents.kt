package com.shonkware.droidmodloader.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shonkware.droidmodloader.engine.companion.CompanionToolState
import com.shonkware.droidmodloader.engine.companion.CompanionToolStatus
import com.skyrimsuite.ui.SuiteSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * APK tools that no longer publish a launcher icon. Detection and launching are
 * in `engine.companion`; this file only presents the rows and renders each
 * tool's adaptive icon with the device mask.
 */
@Composable
fun CompanionToolsCard(
    tools: List<CompanionToolState>,
    onOpenTool: (String) -> Unit,
    onOpenToolPage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuiteSectionHeader(
            title = "Companion apps",
            subtitle = "These tools open from here. They no longer have their own launcher icon."
        )

        if (tools.isEmpty()) {
            Text(
                text = "Checking installed apps...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        tools.forEachIndexed { index, state ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            CompanionToolRow(
                state = state,
                onOpenTool = onOpenTool,
                onOpenToolPage = onOpenToolPage
            )
        }
    }
}

@Composable
private fun CompanionToolRow(
    state: CompanionToolState,
    onOpenTool: (String) -> Unit,
    onOpenToolPage: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompanionToolIcon(state)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = state.tool.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusLabel(state),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(state.status)
            )
        }

        TextButton(
            onClick = {
                when (state.status) {
                    CompanionToolStatus.NOT_INSTALLED,
                    CompanionToolStatus.UPDATE_REQUIRED,
                    CompanionToolStatus.NO_HUB_ENTRY -> onOpenToolPage(state.tool.id)

                    CompanionToolStatus.INSTALLED,
                    CompanionToolStatus.DISABLED -> onOpenTool(state.tool.id)
                }
            }
        ) {
            Text(actionLabel(state.status))
        }
    }
}

private fun statusLabel(state: CompanionToolState): String = when (state.status) {
    CompanionToolStatus.NOT_INSTALLED -> "Not installed"
    CompanionToolStatus.INSTALLED -> state.versionName
        ?.takeIf { it.isNotBlank() }
        ?.let { "Installed · $it" }
        ?: "Installed"
    CompanionToolStatus.UPDATE_REQUIRED -> "Update required"
    CompanionToolStatus.DISABLED -> "Disabled in Android settings"
    CompanionToolStatus.NO_HUB_ENTRY -> "This version cannot be opened from here"
}

private fun actionLabel(status: CompanionToolStatus): String = when (status) {
    CompanionToolStatus.NOT_INSTALLED -> "Get"
    CompanionToolStatus.INSTALLED -> "Open"
    CompanionToolStatus.UPDATE_REQUIRED,
    CompanionToolStatus.NO_HUB_ENTRY -> "Update"
    CompanionToolStatus.DISABLED -> "Enable"
}

@Composable
private fun statusColor(status: CompanionToolStatus) = when (status) {
    CompanionToolStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant
    CompanionToolStatus.INSTALLED -> MaterialTheme.colorScheme.primary
    CompanionToolStatus.UPDATE_REQUIRED,
    CompanionToolStatus.DISABLED,
    CompanionToolStatus.NO_HUB_ENTRY -> MaterialTheme.colorScheme.error
}

@Composable
private fun CompanionToolIcon(
    state: CompanionToolState,
    modifier: Modifier = Modifier
) {
    val packageManager = LocalContext.current.packageManager
    val key = "${state.tool.packageName}:${state.versionCode}"
    val icon by produceState<ImageBitmap?>(
        initialValue = AppIconCache.get(key),
        key1 = key
    ) {
        if (value == null && state.isInstalled) {
            val loaded = withContext(Dispatchers.IO) {
                AppIconLoader.load(packageManager, state.tool.packageName)
            }
            if (loaded != null) {
                AppIconCache.put(key, loaded)
            }
            value = loaded
        }
    }

    val shape = CircleShape
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = modifier
                .size(44.dp)
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

private object AppIconCache {
    private const val MAX_ENTRIES = 8
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, icon: ImageBitmap) {
        cache.put(key, icon)
    }
}

private object AppIconLoader {
    private const val ICON_SIZE = 192

    fun load(packageManager: PackageManager, packageName: String): ImageBitmap? =
        runCatching {
            render(packageManager.getApplicationIcon(packageName), ICON_SIZE)
        }.getOrNull()

    /**
     * Adaptive icons are three layers with no shape of their own. Draw them
     * into a bitmap, then fill the device's adaptive mask path with that bitmap
     * so the hub matches the launcher's icon shape. Legacy icons are drawn
     * inside a circle because they have no adaptive mask.
     */
    private fun render(drawable: Drawable, size: Int): ImageBitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (drawable is AdaptiveIconDrawable) {
            val layers = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layers)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(layerCanvas)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(layers, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(iconMask(drawable, size), paint)
            layers.recycle()
        } else {
            val inset = (size * 0.06f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            canvas.save()
            canvas.clipPath(circlePath(size))
            drawable.draw(canvas)
            canvas.restore()
        }

        return output.asImageBitmap()
    }

    private fun iconMask(drawable: AdaptiveIconDrawable, size: Int): Path {
        val mask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { drawable.iconMask }.getOrNull()
        } else {
            null
        }

        if (mask == null || mask.isEmpty) return circlePath(size)

        val bounds = RectF()
        mask.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return circlePath(size)

        val normalized = Path(mask)
        if (bounds.left != 0f || bounds.top != 0f ||
            bounds.width() != size.toFloat() || bounds.height() != size.toFloat()
        ) {
            val matrix = android.graphics.Matrix()
            matrix.setRectToRect(
                bounds,
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                android.graphics.Matrix.ScaleToFit.FILL
            )
            normalized.transform(matrix)
        }
        return normalized
    }

    private fun circlePath(size: Int): Path = Path().apply {
        addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
    }
}