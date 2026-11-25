package com.muttaqien.quranreader

import android.graphics.Bitmap

/** Keep the printed layout, invert text as before, and retain muted red ornaments. */
internal object MushafNightPalette {
    private const val LIGHT_RED = 0xC46B6B
    private const val DARK_RED = 0xB87575

    fun convert(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val argb = pixels[index]
            val alpha = argb ushr 24
            if (alpha == 0) continue
            val rgb = argb and 0xFFFFFF
            val mapped = if (rgb == LIGHT_RED) DARK_RED else {
                val red = (240f - .85f * (rgb ushr 16 and 255)).toInt().coerceIn(0, 255)
                val green = (240f - .85f * (rgb ushr 8 and 255)).toInt().coerceIn(0, 255)
                val blue = (240f - .85f * (rgb and 255)).toInt().coerceIn(0, 255)
                (red shl 16) or (green shl 8) or blue
            }
            pixels[index] = (alpha shl 24) or mapped
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
