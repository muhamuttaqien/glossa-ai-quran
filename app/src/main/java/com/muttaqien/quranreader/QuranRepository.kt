package com.muttaqien.quranreader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Surah(
    val number: Int,
    val name: String,
    val arabicName: String,
    val ayahCount: Int,
    val revelationType: String
)

data class Ayah(
    val number: Int,
    val globalNumber: Int,
    val page: Int,
    val juz: Int,
    val arabic: String,
    val translationId: String,
    val translationEn: String
)

data class Chapter(val verses: List<Ayah>, val translatorId: String, val translatorEn: String)
data class VerseReference(val surah: Int, val ayah: Int)
data class PageVerse(val reference: VerseReference, val surahName: String, val juz: Int)

data class Reciter(val label: String, val edition: String) {
    companion object {
        val choices = listOf(
            Reciter("Mishary Rashid Alafasy", "ar.alafasy"),
            Reciter("Mahmoud Khalil Al-Husary", "ar.husary"),
            Reciter("Muhammad Siddiq Al-Minshawi", "ar.minshawi"),
            Reciter("Abdul Rahman Al-Sudais", "ar.sudais"),
            Reciter("Saud Al-Shuraim", "ar.shuraim")
        )
    }
}

/** The API response is cached verbatim; the Uthmani text is never transformed. */
class QuranRepository(private val context: Context) {
    suspend fun pageVerses(page: Int): List<PageVerse> = withContext(Dispatchers.IO) {
        require(page in 1..604)
        val ayahs = fetch("page/$page/quran-uthmani", "mushaf-page-$page.json")
            .getJSONObject("data").getJSONArray("ayahs")
        (0 until ayahs.length()).map { index ->
            val ayah = ayahs.getJSONObject(index)
            val surah = ayah.getJSONObject("surah")
            PageVerse(
                VerseReference(surah.getInt("number"), ayah.getInt("numberInSurah")),
                surah.getString("englishName"), ayah.getInt("juz")
            )
        }
    }
    suspend fun juzStart(juz: Int): VerseReference = withContext(Dispatchers.IO) {
        require(juz in 1..30)
        val first = fetch("juz/$juz/quran-uthmani?limit=1", "juz-start-$juz.json")
            .getJSONObject("data").getJSONArray("ayahs").getJSONObject(0)
        VerseReference(first.getJSONObject("surah").getInt("number"), first.getInt("numberInSurah"))
    }

    suspend fun surahs(): List<Surah> = withContext(Dispatchers.IO) {
        val data = fetch("surah", "surahs.json").getJSONArray("data")
        (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            Surah(
                item.getInt("number"), item.getString("englishName"),
                item.getString("name"), item.getInt("numberOfAyahs"),
                item.getString("revelationType")
            )
        }
    }

    suspend fun chapter(surah: Int): Chapter = withContext(Dispatchers.IO) {
        require(surah in 1..114)
        val editions = fetch(
            "surah/$surah/editions/quran-uthmani,id.indonesian,en.sahih",
            "chapter-bilingual-$surah.json"
        ).getJSONArray("data")
        val arabic = editions.findEdition("quran-uthmani")
        val indonesian = editions.findEdition("id.indonesian")
        val english = editions.findEdition("en.sahih")
        val arabicAyahs = arabic.getJSONArray("ayahs")
        val indonesianAyahs = indonesian.getJSONArray("ayahs")
        val englishAyahs = english.getJSONArray("ayahs")
        require(arabicAyahs.length() == indonesianAyahs.length() && arabicAyahs.length() == englishAyahs.length())
        val verses = (0 until arabicAyahs.length()).map { i ->
            val a = arabicAyahs.getJSONObject(i)
            val id = indonesianAyahs.getJSONObject(i)
            val en = englishAyahs.getJSONObject(i)
            require(a.getInt("numberInSurah") == id.getInt("numberInSurah") &&
                a.getInt("numberInSurah") == en.getInt("numberInSurah"))
            Ayah(
                a.getInt("numberInSurah"), a.getInt("number"), a.getInt("page"), a.getInt("juz"),
                a.getString("text"), id.getString("text"), en.getString("text")
            )
        }
        Chapter(
            verses,
            indonesian.getJSONObject("edition").getString("name"),
            english.getJSONObject("edition").getString("name")
        )
    }

    suspend fun audio(surah: Int, edition: String): Map<Int, String> = withContext(Dispatchers.IO) {
        require(surah in 1..114 && Reciter.choices.any { it.edition == edition })
        val data = fetch("surah/$surah/$edition", "audio-$surah-$edition.json")
            .getJSONObject("data").getJSONArray("ayahs")
        (0 until data.length()).associate { i ->
            val item = data.getJSONObject(i)
            item.getInt("numberInSurah") to item.getString("audio")
        }
    }

    private fun JSONArray.findEdition(identifier: String): JSONObject {
        for (i in 0 until length()) {
            val item = getJSONObject(i)
            if (item.getJSONObject("edition").getString("identifier") == identifier) return item
        }
        error("Edition $identifier is unavailable")
    }

    private fun fetch(path: String, cacheName: String): JSONObject {
        val directory = context.filesDir.resolve("quran-cache").apply { mkdirs() }
        val cache = directory.resolve(cacheName)
        if (cache.isFile) {
            try { return verified(cache.readText()) } catch (_: Exception) { cache.delete() }
        }
        val connection = (URL("https://api.alquran.cloud/v1/$path").openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val result = verified(raw)
            cache.writeText(raw)
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun verified(raw: String): JSONObject = JSONObject(raw).also {
        require(it.getInt("code") == 200) { "AlQuran Cloud returned an unsuccessful response" }
    }
}
