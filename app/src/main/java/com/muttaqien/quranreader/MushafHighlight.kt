package com.muttaqien.quranreader

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Tighten each selected ayah's line segment to its printed ink. The result is
 * still one rounded highlight area per segment, never a recoloring of glyphs.
 */
internal object MushafHighlight {
    fun tighten(bitmap: Bitmap, areas: List<RectF>): List<RectF> {
        if (areas.isEmpty()) return emptyList()
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return areas.mapNotNull { area ->
            val left = ceil(area.left + 1f).toInt().coerceIn(0, width)
            val right = floor(area.right - 1f).toInt().coerceIn(0, width)
            val top = floor(area.top).toInt().coerceIn(0, height)
            val bottom = ceil(area.bottom).toInt().coerceIn(0, height)
            if (left >= right || top >= bottom) return@mapNotNull null

            var minX = right
            var minY = bottom
            var maxX = left - 1
            var maxY = top - 1
            for (y in top until bottom) {
                for (x in left until right) {
                    val color = pixels[y * width + x]
                    if (color ushr 24 < 128) continue
                    val red = color ushr 16 and 0xff
                    val green = color ushr 8 and 0xff
                    val blue = color and 0xff
                    val luminance = (77 * red + 150 * green + 29 * blue) ushr 8
                    if (luminance >= 165) continue
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
            if (maxX < minX || maxY < minY) return@mapNotNull null
            RectF(
                max(area.left, minX - 1f),
                max(area.top, minY - 1f),
                min(area.right, maxX + 2f),
                min(area.bottom, maxY + 2f)
            )
        }
    }
}
