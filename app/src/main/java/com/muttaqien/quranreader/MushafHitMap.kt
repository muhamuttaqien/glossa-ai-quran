package com.muttaqien.quranreader

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject
import kotlin.math.abs

/**
 * Hit boxes for the bundled Madinah Mushaf images. The source coordinates are
 * adapted from GitCreativeCode/quran-ayah-coords (CC BY-SA 4.0); see the
 * attribution beside ayah_bounds.json. A long press near a verse boundary
 * returns null, leaving the page unchanged.
 */
class MushafHitMap(private val context: Context) {
    private val pages: JSONObject by lazy {
        context.assets.open("mushaf/ayah_bounds.json").bufferedReader().use {
            JSONObject(it.readText())
        }
    }
    private val versePages: Map<VerseReference, Int> by lazy {
        buildMap {
            for (page in 1..604) {
                val bounds = pages.optJSONArray(page.toString()) ?: continue
                for (index in 0 until bounds.length()) {
                    val box = bounds.getJSONArray(index)
                    val reference = VerseReference(box.getInt(1), box.getInt(2))
                    if (reference !in this) put(reference, page)
                }
            }
        }
    }

    fun pageFor(reference: VerseReference): Int? = versePages[reference]

    /** Ayahs in their printed order, including pages shared by multiple surahs. */
    fun versesOn(page: Int): List<VerseReference> {
        val bounds = pages.optJSONArray(page.toString()) ?: return emptyList()
        return buildList {
            for (index in 0 until bounds.length()) {
                val box = bounds.getJSONArray(index)
                val reference = VerseReference(box.getInt(1), box.getInt(2))
                if (reference != lastOrNull()) add(reference)
            }
        }
    }

    fun firstVerseOn(page: Int): VerseReference? {
        val bounds = pages.optJSONArray(page.toString()) ?: return null
        if (bounds.length() == 0) return null
        val first = bounds.getJSONArray(0)
        return VerseReference(first.getInt(1), first.getInt(2))
    }

    fun verseAt(page: Int, x: Float, y: Float, width: Int, height: Int): VerseReference? {
        if (page !in 1..604 || width <= 0 || height <= 0 ||
            x !in 0f..width.toFloat() || y !in 0f..height.toFloat()
        ) return null
        if (page == 3 && width == 1000 && height == 1902) {
            return PageThreeHighResMap.verseAt(x, y)
        }
        // The opening pages have ornamental frames and a different text canvas.
        if (page <= 2 && (width != 442 || height != 645)) return null

        val sourceX = 54f + x * 940f / width
        val sourceY = if (page <= 2) {
            125f + (y * 645f / height - 200f) * 589f / 245f
        } else {
            24f + y * 1590f / height
        }
        val verticalInset = if (page <= 2) 3f * 589f * 645f / (245f * height)
            else 3f * 1590f / height
        val bounds = pages.optJSONArray(page.toString()) ?: return null
        var match: VerseReference? = null
        var matchLine = -1

        for (index in 0 until bounds.length()) {
            val box = bounds.getJSONArray(index)
            if (sourceX < box.getInt(3) || sourceX > box.getInt(4) ||
                sourceY < box.getInt(5) + verticalInset ||
                sourceY > box.getInt(6) - verticalInset
            ) continue
            val reference = VerseReference(box.getInt(1), box.getInt(2))
            if (match != null && match != reference) return null
            match = reference
            matchLine = box.getInt(0)
        }
        val reference = match ?: return null

        // Source and bundled images differ slightly at mid-line verse endings.
        // Treat presses close to another verse's box as ambiguous instead of
        // opening the wrong ayah.
        val boundaryMargin = 40f * 940f / width
        for (index in 0 until bounds.length()) {
            val box = bounds.getJSONArray(index)
            if (box.getInt(0) != matchLine ||
                (box.getInt(1) == reference.surah && box.getInt(2) == reference.ayah)
            ) continue
            if (abs(sourceX - box.getInt(3)) < boundaryMargin ||
                abs(sourceX - box.getInt(4)) < boundaryMargin
            ) return null
        }
        return reference
    }

    /** Image-pixel rectangles for a selected ayah; no artwork is modified. */
    fun areasFor(page: Int, reference: VerseReference, width: Int, height: Int): List<RectF> {
        if (page !in 1..604 || width <= 0 || height <= 0 ||
            (page <= 2 && (width != 442 || height != 645))
        ) return emptyList()
        if (page == 3 && width == 1000 && height == 1902) {
            return PageThreeHighResMap.areasFor(reference)
        }
        val bounds = pages.optJSONArray(page.toString()) ?: return emptyList()
        val areas = mutableListOf<RectF>()
        for (index in 0 until bounds.length()) {
            val box = bounds.getJSONArray(index)
            if (box.getInt(1) != reference.surah || box.getInt(2) != reference.ayah) continue
            val left = ((box.getInt(3) - 54f) * width / 940f).coerceIn(0f, width.toFloat())
            val right = ((box.getInt(4) - 54f) * width / 940f).coerceIn(0f, width.toFloat())
            val top = imageY(page, box.getInt(5).toFloat(), height).coerceIn(0f, height.toFloat())
            val bottom = imageY(page, box.getInt(6).toFloat(), height).coerceIn(0f, height.toFloat())
            if (right > left && bottom > top) {
                areas.add(RectF(left, top, right, bottom))
            }
        }
        return areas
    }

    private fun imageY(page: Int, sourceY: Float, height: Int): Float =
        if (page <= 2) (200f + (sourceY - 125f) * 245f / 589f) * height / 645f
        else (sourceY - 24f) * height / 1590f
}

/** The high-resolution source shifts verse endings relative to the 442 px pages. */
internal object PageThreeHighResMap {
    // The original 1080x1440 source is cropped by 40 px on each side. Fourteen
    // transparent row gaps are inserted after these source y coordinates.
    private val lineCuts = intArrayOf(
        96, 189, 288, 384, 480, 576, 672, 768, 864, 960, 1056, 1152, 1249, 1344
    )
    private const val lineGap = 33

    private data class Segment(val line: Int, val ayah: Int, val left: Float, val right: Float) {
        val top: Float get() = if (line == 1) 0f
            else (PageThreeHighResMap.lineCuts[line - 2] + (line - 1) * lineGap).toFloat()
        val bottom: Float get() = if (line == 15) 1902f
            else (PageThreeHighResMap.lineCuts[line - 1] + (line - 1) * lineGap).toFloat()
        fun rect() = RectF((left - 40f).coerceAtLeast(0f), top,
            (right - 40f).coerceAtMost(1000f), bottom)
    }

    // Boundaries are the centers of the original printed verse markers. The
    // original Arabic numeral pixels remain in the high-resolution artwork.
    private val segments = listOf(
        Segment(1, 6, 24f, 1056f),
        Segment(2, 6, 802f, 1056f), Segment(2, 7, 24f, 802f),
        Segment(3, 7, 285f, 1056f), Segment(3, 8, 24f, 285f),
        Segment(4, 8, 24f, 1056f),
        Segment(5, 9, 24f, 1056f),
        Segment(6, 9, 761f, 1056f), Segment(6, 10, 24f, 761f),
        Segment(7, 10, 327f, 1056f), Segment(7, 11, 24f, 327f),
        Segment(8, 11, 24f, 1056f),
        Segment(9, 12, 243f, 1056f), Segment(9, 13, 24f, 243f),
        Segment(10, 13, 24f, 1056f),
        Segment(11, 13, 284f, 1056f), Segment(11, 14, 24f, 284f),
        Segment(12, 14, 24f, 1056f),
        Segment(13, 14, 508f, 1056f), Segment(13, 15, 24f, 508f),
        Segment(14, 15, 628f, 1056f), Segment(14, 16, 24f, 628f),
        Segment(15, 16, 24f, 1056f)
    )

    fun verseAt(x: Float, y: Float): VerseReference? {
        val sourceX = x + 40f
        val segment = segments.firstOrNull {
            sourceX >= it.left && sourceX <= it.right &&
                y >= it.top + 4f && y <= it.bottom - 4f
        } ?: return null
        // Avoid assigning a long press on a marker to the wrong adjacent ayah.
        if (segments.any {
                it.line == segment.line && it.ayah != segment.ayah &&
                    (abs(sourceX - it.left) < 30f || abs(sourceX - it.right) < 30f)
            }) return null
        return VerseReference(2, segment.ayah)
    }

    fun areasFor(reference: VerseReference): List<RectF> =
        if (reference.surah == 2) segments.filter { it.ayah == reference.ayah }.map { it.rect() }
        else emptyList()
}
