package com.muttaqien.quranreader

import android.graphics.Bitmap

/** Keep the printed layout, render ink white on black, and retain Glossa red ornaments. */
internal object MushafNightPalette {
    private const val GLOSSA_RED = 0xEF4D4D
    // The original PNG palette has ink tones near 32, 78, 111 and 132;
    // lighter tones near 183 and above are the page/antialias fringe.
    // Keep the printed ink bright while retaining the fine edge pixels.
    private const val INK_LUMINANCE = 140
    private const val PAPER_LUMINANCE = 190

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
                // Stretch the printed ink to white and the pale page residue to black,
                // retaining intermediate levels for antialiased glyph edges.
                val luminance = (54 * red + 183 * green + 19 * blue) / 256
                val ink = ((PAPER_LUMINANCE - luminance) * 255 /
                    (PAPER_LUMINANCE - INK_LUMINANCE)).coerceIn(0, 255)
                (ink shl 16) or (ink shl 8) or ink
            }
            pixels[index] = (alpha shl 24) or mapped
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
