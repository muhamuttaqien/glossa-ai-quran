package com.muttaqien.quranreader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * White, glyph-shaped overlay for the selected ayah. The bundled Mushaf bitmap
 * stays unchanged; the ayah bounds only limit which printed ink can be lit.
 */
internal object MushafHighlight {
    fun create(bitmap: Bitmap, areas: List<RectF>): ImageBitmap? {
        if (areas.isEmpty()) return null
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        val overlay = IntArray(width * height)
        var hasInk = false

        for (area in areas) {
            // Stay inside the ayah's horizontal boundary; include diacritics at
            // the top and bottom of a printed line.
            val left = ceil(area.left + 1f).toInt().coerceIn(0, width)
            val right = floor(area.right - 1f).toInt().coerceIn(0, width)
            val top = floor(area.top - 2f).toInt().coerceIn(0, height)
            val bottom = ceil(area.bottom + 2f).toInt().coerceIn(0, height)
            if (left >= right || top >= bottom) continue

            for (y in top until bottom) {
                for (x in left until right) {
                    val color = source[y * width + x]
                    if (color ushr 24 < 128) continue
                    val red = color ushr 16 and 0xff
                    val green = color ushr 8 and 0xff
                    val blue = color and 0xff
                    val luminance = (77 * red + 150 * green + 29 * blue) ushr 8
                    if (luminance >= 165) continue
                    hasInk = true

                    for (dy in -2..2) {
                        val targetY = y + dy
                        if (targetY !in top until bottom) continue
                        for (dx in -2..2) {
                            val targetX = x + dx
                            if (targetX !in left until right) continue
                            val alpha = when (dx * dx + dy * dy) {
                                0 -> 88
                                1 -> 52
                                2 -> 38
                                4 -> 24
                                5 -> 16
                                else -> 0
                            }
                            if (alpha == 0) continue
                            val index = targetY * width + targetX
                            if (alpha > overlay[index] ushr 24) {
                                overlay[index] = (alpha shl 24) or 0x00ffffff
                            }
                        }
                    }
                }
            }
        }
        if (!hasInk) return null
        return Bitmap.createBitmap(overlay, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
