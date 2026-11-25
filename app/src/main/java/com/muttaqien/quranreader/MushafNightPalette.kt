package com.muttaqien.quranreader

import android.graphics.Bitmap

/** Keep the printed layout, invert text, and retain Glossa red ornaments. */
internal object MushafNightPalette {
    private const val GLOSSA_RED = 0xEF4D4D

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
            val red = rgb ushr 16 and 255
            val green = rgb ushr 8 and 255
            val blue = rgb and 255
            // Decoding can round the RGB values of translucent floral edges.
            val isGlossaRed = red in 223..255 && green in 61..93 && blue in 61..93
            val mapped = if (isGlossaRed) GLOSSA_RED else {
                val invertedRed = (240f - .85f * red).toInt().coerceIn(0, 255)
                val invertedGreen = (240f - .85f * green).toInt().coerceIn(0, 255)
                val invertedBlue = (240f - .85f * blue).toInt().coerceIn(0, 255)
                (invertedRed shl 16) or (invertedGreen shl 8) or invertedBlue
            }
            pixels[index] = (alpha shl 24) or mapped
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
