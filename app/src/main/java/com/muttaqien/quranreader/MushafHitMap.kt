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
