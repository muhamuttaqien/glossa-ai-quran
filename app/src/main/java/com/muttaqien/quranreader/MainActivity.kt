package com.muttaqien.quranreader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import android.graphics.BitmapFactory
import kotlin.math.cos
import kotlin.math.sin

private val Brand = Color(0xFFEF4D4D)
private val Background = Color(0xFF080808)
private val SurfaceBlack = Color(0xFF101010)
private val CardsBlack = Color(0xFF171717)
private val TextWhite = Color(0xFFF8F8F8)
private val Paper = Color(0xFFFFFEFA)
private val Ink = Color(0xFF222423)
private val Gold = Color(0xFFB89A65)
private val ArabicBlack = Color(0xFF080808)
private val LightLine = Color(0xFFE6E4DC)

class MainActivity : ComponentActivity() {
    private val player = VersePlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = QuranRepository(applicationContext)
        setContent {
            val preferences = remember { getSharedPreferences("reader", MODE_PRIVATE) }
            var night by remember { mutableStateOf(preferences.getBoolean("night", false)) }
            val palette = if (night) darkColorScheme(
                primary = Brand, onPrimary = TextWhite,
                background = Background, onBackground = TextWhite,
                surface = SurfaceBlack, onSurface = TextWhite,
                surfaceVariant = CardsBlack, secondary = Brand,
            ) else lightColorScheme(
                primary = Brand, onPrimary = TextWhite,
                background = Color(0xFFF7F6F2), surface = Paper,
                onSurface = Ink, secondary = Gold,
            )
            MaterialTheme(colorScheme = palette) {
                QuranApp(repository, player, night) {
                    night = !night
                    preferences.edit { putBoolean("night", night) }
                }
            }
        }
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranApp(
    repository: QuranRepository, player: VersePlayer,
    night: Boolean, onToggleNight: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("reader", Context.MODE_PRIVATE) }
    val hitMap = remember { MushafHitMap(context.applicationContext) }
    var reciter by remember {
        val savedEdition = preferences.getString("reciter", "ar.alafasy")
        val savedReciter = Reciter.choices.firstOrNull { (_, edition) -> edition == savedEdition }
        mutableStateOf(savedReciter ?: Reciter.choices.first())
    }
    var bookmarks by remember { mutableStateOf(preferences.getStringSet("bookmarks", emptySet())!!.toSet()) }
    var lastRead by remember {
        mutableStateOf(VerseReference(
            preferences.getInt("last_surah", 0), preferences.getInt("last_ayah", 1)
        ))
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(preferences.getInt("mode", 0)) } // 0: Arabic page, 1: translation
    var language by remember { mutableIntStateOf(preferences.getInt("translation_language", 1).coerceIn(0, 2)) }
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var listError by remember { mutableStateOf<String?>(null) }
    var listRetry by remember { mutableIntStateOf(0) }
    var pendingJuz by remember { mutableStateOf<Int?>(null) }
    var juzError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Surah?>(null) }
    var openRevision by remember { mutableIntStateOf(0) }
    var startAyah by remember { mutableIntStateOf(1) }
    var chapter by remember { mutableStateOf<Chapter?>(null) }
    var chapterError by remember { mutableStateOf<String?>(null) }
    var chapterRetry by remember { mutableIntStateOf(0) }
    var audioUrls by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var audioRetry by remember { mutableIntStateOf(0) }
    var currentVerse by remember { mutableIntStateOf(-1) }
    var focusedAyah by remember { mutableStateOf<Ayah?>(null) }
    var pendingFocusedAyah by remember { mutableStateOf<VerseReference?>(null) }
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var pageVerses by remember { mutableStateOf<List<PageVerse>>(emptyList()) }
    val verseListState = rememberLazyListState()

    fun openSurah(surah: Surah, ayah: Int = 1) {
        player.stop()
        currentVerse = -1
        focusedAyah = null
        pendingFocusedAyah = null
        chapter = null
        audioUrls = emptyMap()
        startAyah = ayah
        selected = surah
        openRevision++
    }

    fun markRead(surah: Int, ayah: Int) {
        lastRead = VerseReference(surah, ayah)
        preferences.edit {
            putInt("last_surah", surah)
            putInt("last_ayah", ayah)
        }
    }

    fun openPageAyah(reference: VerseReference) {
        val surah = surahs.firstOrNull { it.number == reference.surah } ?: return
        if (selected?.number != reference.surah) {
            openSurah(surah, reference.ayah)
            pendingFocusedAyah = reference
        } else {
            val ayah = chapter?.verses?.firstOrNull { it.number == reference.ayah }
                ?: return
            focusedAyah = ayah
            markRead(reference.surah, reference.ayah)
        }
    }

    fun toggleBookmark(surah: Int, ayah: Int) {
        val key = "$surah:$ayah"
        bookmarks = if (key in bookmarks) bookmarks - key else bookmarks + key
        preferences.edit { putStringSet("bookmarks", bookmarks) }
    }

    fun shareAyah(ayah: Ayah) {
        val (surahNumber, surahName) = selected ?: return
        val translations = when (language) {
            1 -> "${ayah.translationEn}\n${chapter?.translatorEn.orEmpty()}"
            2 -> "${ayah.translationEn}\n${chapter?.translatorEn.orEmpty()}\n\n${ayah.translationId}\n${chapter?.translatorId.orEmpty()}"
            else -> "${ayah.translationId}\n${chapter?.translatorId.orEmpty()}"
        }
        val text = "$surahName $surahNumber:${ayah.number}\n${ayah.arabic}\n\n$translations"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share verse"))
    }

    LaunchedEffect(listRetry) {
        listError = null
        try { surahs = repository.surahs() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { listError = "Couldn't load surahs. Check your internet connection." }
    }
    LaunchedEffect(pendingJuz) {
        val number = pendingJuz ?: return@LaunchedEffect
        juzError = null
        try {
            val reference = repository.juzStart(number)
            val surah = surahs.firstOrNull { it.number == reference.surah }
                ?: error("Surah list is not ready")
            openSurah(surah, reference.ayah)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { juzError = "Couldn't open Juz $number. Try again." }
        finally { pendingJuz = null }
    }
    LaunchedEffect(selected?.number, openRevision, chapterRetry) {
        val surah = selected ?: return@LaunchedEffect
        chapter = null
        chapterError = null
        try { chapter = repository.chapter(surah.number) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { chapterError = "Couldn't load verses. Try again." }
    }
    LaunchedEffect(selected?.number, openRevision, reciter.edition, audioRetry) {
        val surah = selected ?: return@LaunchedEffect
        audioUrls = emptyMap()
        audioError = null
        try { audioUrls = repository.audio(surah.number, reciter.edition) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { audioError = "Couldn't load recitation. Try again." }
    }
    LaunchedEffect(selected?.number, chapter) {
        if (selected == null) return@LaunchedEffect
        val verses = chapter?.verses ?: return@LaunchedEffect
        val index = (startAyah - 1).coerceIn(0, verses.lastIndex)
        verseListState.requestScrollToItem(index)
        currentPage = verses[index].page
    }
    LaunchedEffect(chapter, selected?.number, pendingFocusedAyah) {
        val reference = pendingFocusedAyah ?: return@LaunchedEffect
        if (selected?.number != reference.surah) return@LaunchedEffect
        val ayah = chapter?.verses?.firstOrNull { it.number == reference.ayah }
            ?: return@LaunchedEffect
        focusedAyah = ayah
        markRead(reference.surah, reference.ayah)
        pendingFocusedAyah = null
    }
    LaunchedEffect(currentPage, selected?.number) {
        if (selected == null) return@LaunchedEffect
        pageVerses = emptyList()
        try { pageVerses = repository.pageVerses(currentPage) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The bundled image and tap map remain usable. */ }
    }

    fun startVerse(index: Int) {
        val verses = chapter?.verses ?: return
        val surah = selected ?: return
        if (index !in verses.indices) return
        val url = audioUrls[verses[index].number]
        if (url == null) {
            audioError = "Audio for verse ${verses[index].number} is unavailable."
            return
        }
        currentVerse = index
        if (mode == 0) currentPage = verses[index].page
        markRead(surah.number, verses[index].number)
        player.play(url) {
            if (index < verses.lastIndex) startVerse(index + 1)
            else player.stop()
        }
    }

    fun backToIndex() {
        player.stop()
        currentVerse = -1
        selected = null
        focusedAyah = null
        pendingFocusedAyah = null
    }
    BackHandler(selected != null) { backToIndex() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (selected == null) {
            IndexScreen(
                surahs, tab, onTab = { tab = it }, bookmarks, lastRead,
                error = listError, juzError = juzError,
                retry = { listRetry++ },
                open = ::openSurah,
                openJuz = { pendingJuz = it },
                night = night,
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ReaderHeader(
                    if (mode == 0) pageVerses.firstOrNull()?.surahName ?: "Mushaf Madinah" else selected!!.name,
                    night, onToggleNight, ::backToIndex
                )
                Row(Modifier.fillMaxWidth()) {
                    listOf("Read Arabic", "Translation").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = {
                                mode = index
                                preferences.edit { putInt("mode", index) }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(label, color = if (mode == index) MaterialTheme.colorScheme.onSurface else Color.Gray)
                            }
                            HorizontalDivider(
                                Modifier.padding(horizontal = 22.dp),
                                thickness = 2.dp,
                                color = if (mode == index) Brand else Color.Transparent
                            )
                        }
                    }
                }
                if (mode == 1) TranslationSelector(language) { next ->
                    language = next
                    preferences.edit { putInt("translation_language", next) }
                }
                if (chapterError != null) Failure(chapterError!!) { chapterRetry++ }
                else if (chapter == null) Loading()
                else {
                    val verses = chapter!!.verses
                    if (mode == 0) {
                        MushafPage(
                            currentPage, pageVerses, night, hitMap,
                            selectedAyah = focusedAyah?.let { VerseReference(selected!!.number, it.number) },
                            onPrevious = { if (currentPage > 1) currentPage-- },
                            onNext = { if (currentPage < 604) currentPage++ },
                            onTapAt = { x, y, width, height ->
                                hitMap.verseAt(currentPage, x, y, width, height)
                                    ?.let(::openPageAyah)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(Modifier.weight(1f), state = verseListState) {
                            items(verses, key = { it.number }) { ayah ->
                                AyahCard(
                                    ayah, night, language, chapter!!,
                                    isBookmarked = "${selected!!.number}:${ayah.number}" in bookmarks,
                                    audioReady = audioUrls.containsKey(ayah.number),
                                    play = { startVerse(ayah.number - 1) },
                                    bookmark = { toggleBookmark(selected!!.number, ayah.number) },
                                    share = { shareAyah(ayah) },
                                ) { markRead(selected!!.number, ayah.number) }
                            }
                            item {
                                Text(
                                    "Text: AlQuran Cloud • Translation: ${translationCredits(chapter!!, language)}",
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    ReciterSelector(reciter) { next ->
                        player.stop()
                        currentVerse = -1
                        reciter = next
                        preferences.edit { putString("reciter", next.edition) }
                    }
                    if (audioError != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(audioError!!, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            TextButton(onClick = { audioRetry++ }) { Text("Retry") }
                        }
                    }
                    PlayerBar(
                        player, currentVerse, verses.size,
                        audioUrls.isEmpty() && audioError == null, night,
                        onPrevious = { startVerse((currentVerse - 1).coerceAtLeast(0)) },
                        onNext = { startVerse((currentVerse + 1).coerceAtMost(verses.lastIndex)) },
                        onToggle = {
                            if (currentVerse < 0 || player.error != null) {
                                if (mode == 0) {
                                    verses.firstOrNull { it.page == currentPage }
                                        ?.let { startVerse(it.number - 1) }
                                } else startVerse(startAyah - 1)
                            }
                            else player.toggle()
                        }
                    )
                }
            }
        }
    }

    val ayah = focusedAyah
    if (ayah != null && selected != null) {
        ModalBottomSheet(onDismissRequest = { focusedAyah = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("${selected!!.name} • Verse ${ayah.number}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                TranslationContent(ayah, language)
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { toggleBookmark(selected!!.number, ayah.number) }) {
                        Text(if ("${selected!!.number}:${ayah.number}" in bookmarks) "Remove bookmark" else "Bookmark verse")
                    }
                    TextButton(onClick = { focusedAyah = null; startVerse(ayah.number - 1) }, enabled = audioUrls.containsKey(ayah.number)) {
                        Text("▷ Listen")
                    }
                    TextButton(onClick = { shareAyah(ayah) }) { Text("Share") }
                }
                Text("Translation: ${chapter?.let { translationCredits(it, language) }.orEmpty()}", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IndexScreen(
    surahs: List<Surah>, tab: Int, onTab: (Int) -> Unit,
    bookmarks: Set<String>, lastRead: VerseReference,
    error: String?, juzError: String?, retry: () -> Unit,
    open: (Surah, Int) -> Unit, openJuz: (Int) -> Unit,
    night: Boolean,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        Surface(color = SurfaceBlack) {
            Column {
                Column(Modifier.padding(start = 22.dp, top = 19.dp, bottom = 10.dp)) {
                    Text("Glossa AI", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("QUR'AN VERSION", color = TextWhite.copy(alpha = 0.62f), fontSize = 11.sp)
                }
                TabRow(selectedTabIndex = tab, containerColor = SurfaceBlack, contentColor = Brand) {
                    listOf("SURAH", "JUZ", "BOOKMARKS").forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { onTab(index) }, text = {
                            Text(title, fontSize = 12.sp, color = if (tab == index) TextWhite else TextWhite.copy(alpha = 0.6f))
                        })
                    }
                }
            }
        }
        if (lastRead.surah in 1..114 && surahs.isNotEmpty()) {
            val lastSurah = surahs.firstOrNull { it.number == lastRead.surah }
            if (lastSurah != null) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp).clickable {
                        open(lastSurah, lastRead.ayah)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (night) Color(0xFF303030) else LightLine),
                    colors = CardDefaults.cardColors(containerColor = if (night) CardsBlack else Paper)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue reading", fontSize = 12.sp, color = Color.Gray)
                            Text("${lastSurah.name}  •  Verse ${lastRead.ayah}", fontWeight = FontWeight.SemiBold)
                        }
                        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (error != null) { Failure(error, retry); return@Column }
        if (surahs.isEmpty()) { Loading(); return@Column }
        when (tab) {
            0 -> {
                OutlinedTextField(
                    query, onValueChange = { query = it },
                    placeholder = { Text("Search surahs") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                )
                val filtered = surahs.filter {
                    it.number.toString() == query.trim() ||
                        it.name.contains(query.trim(), ignoreCase = true) ||
                        it.arabicName.contains(query.trim())
                }
                if (filtered.isEmpty()) Text("No surahs found.", Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f)) { items(filtered, key = { it.number }) { SurahRow(it, night) { open(it, 1) } } }
            }
            1 -> {
                juzError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f)) {
                    items((1..30).toList()) { number ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openJuz(number) }.padding(22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Juz $number", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("Open ›", color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider()
                    }
                }
            }
            else -> {
                val entries = bookmarks.asSequence().mapNotNull { key ->
                    val parts = key.split(':')
                    val surah = surahs.firstOrNull { it.number == parts.getOrNull(0)?.toIntOrNull() }
                    val ayah = parts.getOrNull(1)?.toIntOrNull()
                    if (surah != null && ayah != null && ayah in 1..surah.ayahCount) surah to ayah else null
                }.sortedWith(compareBy({ it.first.number }, { it.second })).toList()
                if (entries.isEmpty()) Text("No bookmarked verses yet.", Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { "${it.first.number}:${it.second}" }) { (surah, ayah) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open(surah, ayah) }.padding(22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${surah.name} • Verse $ayah", Modifier.weight(1f))
                            Text("★", color = Gold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahRow(surah: Surah, night: Boolean, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(surah.number.toString(), Modifier.padding(end = 22.dp), color = Color.Gray, fontSize = 15.sp)
        Column(Modifier.weight(1f)) {
            Text(surah.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${surah.ayahCount} verses • ${if (surah.revelationType == "Meccan") "Meccan" else "Medinan"}", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(surah.arabicName, Modifier.widthIn(max = 110.dp), color = if (night) TextWhite else ArabicBlack, fontSize = 20.sp, textAlign = TextAlign.Right, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(Modifier.padding(horizontal = 22.dp), color = if (night) Color(0xFF303030) else LightLine)
}

@Composable
private fun ReaderHeader(title: String, night: Boolean, toggleNight: () -> Unit, back: () -> Unit) {
    Surface(color = SurfaceBlack) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.semantics { contentDescription = "Back to index" }) {
                Text("‹", color = TextWhite, fontSize = 26.sp)
            }
            Text(title, Modifier.weight(1f), color = TextWhite, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = toggleNight,
                modifier = Modifier.semantics {
                    contentDescription = if (night) "Switch to light mode" else "Switch to dark mode"
                }
            ) {
                if (night) {
                    Canvas(Modifier.size(22.dp)) {
                        val radius = size.minDimension
                        drawCircle(color = TextWhite, radius = radius * 0.19f)
                        for (index in 0 until 8) {
                            val angle = index * Math.PI / 4
                            val dx = cos(angle).toFloat()
                            val dy = sin(angle).toFloat()
                            val start = radius * 0.32f
                            val end = radius * 0.46f
                            drawLine(
                                color = TextWhite,
                                start = Offset(center.x + dx * start, center.y + dy * start),
                                end = Offset(center.x + dx * end, center.y + dy * end),
                                strokeWidth = 1.6f.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                } else {
                    Text("☾", color = TextWhite, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun MushafPage(
    page: Int, verses: List<PageVerse>, night: Boolean,
    hitMap: MushafHitMap, selectedAyah: VerseReference?,
    onPrevious: () -> Unit, onNext: () -> Unit,
    onTapAt: (Float, Float, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image = remember(page) {
        context.assets.open("mushaf/$page.png").use { stream ->
            BitmapFactory.decodeStream(stream).asImageBitmap()
        }
    }
    val pageColor = if (night) CardsBlack else Paper
    val pageText = if (night) TextWhite else Ink
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val latestTapAt by rememberUpdatedState(onTapAt)
    val highlightAreas = remember(page, selectedAyah, image.width, image.height) {
        selectedAyah?.let { hitMap.areasFor(page, it, image.width, image.height) }
            ?: emptyList()
    }
    // Keep the fixed page layout and glyphs; only remap its luminance for night reading.
    val nightFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -0.85f, 0f, 0f, 0f, 240f,
            0f, -0.85f, 0f, 0f, 240f,
            0f, 0f, -0.85f, 0f, 240f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    Surface(modifier.fillMaxWidth().padding(vertical = 4.dp), color = pageColor) {
        Column(
            Modifier.fillMaxSize().pointerInput(page, swipeThreshold) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag >= swipeThreshold -> onNext()
                            totalDrag <= -swipeThreshold -> onPrevious()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    }
                )
            }.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(verses.firstOrNull()?.surahName ?: "Page $page", color = pageText, fontSize = 13.sp)
                Text(verses.firstOrNull()?.let { "Juz ${it.juz}" } ?: "", color = pageText, fontSize = 13.sp)
            }
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .semantics {
                        contentDescription = "Mushaf Madinah Hafs page $page. Tap an ayah to select it. Swipe right for the next page or left for the previous page."
                        customActions = listOf(
                            CustomAccessibilityAction("Next page") {
                                if (page < 604) { onNext(); true } else false
                            },
                            CustomAccessibilityAction("Previous page") {
                                if (page > 1) { onPrevious(); true } else false
                            }
                        )
                    }
                    .pointerInput(page, image.width, image.height) {
                        detectTapGestures { tap ->
                            val scale = minOf(
                                size.width.toFloat() / image.width,
                                size.height.toFloat() / image.height
                            )
                            val left = (size.width - image.width * scale) / 2f
                            val top = (size.height - image.height * scale) / 2f
                            val x = (tap.x - left) / scale
                            val y = (tap.y - top) / scale
                            if (x in 0f..image.width.toFloat() &&
                                y in 0f..image.height.toFloat()
                            ) latestTapAt(x, y, image.width, image.height)
                        }
                    }
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (night) nightFilter else null
                )
                if (highlightAreas.isNotEmpty()) {
                    Canvas(Modifier.matchParentSize()) {
                        val scale = minOf(size.width / image.width, size.height / image.height)
                        val left = (size.width - image.width * scale) / 2f
                        val top = (size.height - image.height * scale) / 2f
                        highlightAreas.forEach { area ->
                            val x = left + (area.left + 2f) * scale
                            val y = top + (area.top + 1f) * scale
                            val width = (area.width() - 4f) * scale
                            val height = (area.height() - 2f) * scale
                            if (width > 0f && height > 0f) {
                                drawRoundRect(
                                    color = Gold.copy(alpha = if (night) 0.24f else 0.18f),
                                    topLeft = Offset(x, y),
                                    size = Size(width, height),
                                    cornerRadius = CornerRadius(3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "$page / 604",
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = pageText, textAlign = TextAlign.Center, fontSize = 13.sp
            )
        }
    }
}

private fun translationCredits(chapter: Chapter, language: Int): String = when (language) {
    1 -> chapter.translatorEn
    2 -> "${chapter.translatorEn} • ${chapter.translatorId}"
    else -> chapter.translatorId
}

@Composable
private fun TranslationSelector(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }
    val choices = listOf(1 to "English", 0 to "Indonesian", 2 to "English + Indonesian")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Language", color = Color.Gray, modifier = Modifier.padding(end = 10.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(choices.first { it.first == selected }.second, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(" ▾", color = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        expanded = false
                        onSelect(value)
                    })
                }
            }
        }
    }
}

@Composable
private fun TranslationContent(ayah: Ayah, language: Int) {
    Column {
        if (language != 0) {
            if (language == 2) Text("ENGLISH", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(ayah.translationEn, lineHeight = 25.sp)
        }
        if (language == 2) Spacer(Modifier.height(14.dp))
        if (language != 1) {
            if (language == 2) Text("INDONESIAN", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(ayah.translationId, lineHeight = 25.sp)
        }
    }
}

@Composable
private fun AyahCard(
    ayah: Ayah, night: Boolean, language: Int, chapter: Chapter,
    isBookmarked: Boolean, audioReady: Boolean,
    play: () -> Unit, bookmark: () -> Unit, share: () -> Unit, read: () -> Unit
) {
    var actionsExpanded by remember { mutableStateOf(value = false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (night) Color(0xFF303030) else LightLine),
        colors = CardDefaults.cardColors(containerColor = if (night) CardsBlack else Paper)
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = read).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verse ${ayah.number}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Box {
                    IconButton(onClick = { actionsExpanded = true }, modifier = Modifier.semantics { contentDescription = "Verse actions" }) {
                        Text("⋮", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp)
                    }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(text = { Text("Listen") }, enabled = audioReady, onClick = {
                            actionsExpanded = false
                            play()
                        })
                        DropdownMenuItem(text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark verse") }, onClick = {
                            actionsExpanded = false
                            bookmark()
                        })
                        DropdownMenuItem(text = { Text("Share verse") }, onClick = {
                            actionsExpanded = false
                            share()
                        })
                    }
                }
            }
            Text(
                ayah.arabic, Modifier.fillMaxWidth().padding(vertical = 10.dp),
                color = if (night) TextWhite else ArabicBlack,
                textAlign = TextAlign.Right, fontSize = 29.sp, lineHeight = 53.sp,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            TranslationContent(ayah, language)
            Text(translationCredits(chapter, language), Modifier.padding(top = 12.dp), color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReciterSelector(selected: Reciter, onSelect: (Reciter) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Reciter", color = Color.Gray, modifier = Modifier.padding(end = 10.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected.label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(" ▾", color = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Reciter.choices.forEach { item ->
                    DropdownMenuItem(text = { Text(item.label) }, onClick = { expanded = false; onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(
    player: VersePlayer, verse: Int, count: Int, audioLoading: Boolean,
    night: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onToggle: () -> Unit
) {
    Surface(color = if (night) SurfaceBlack else Paper, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (verse >= 0) {
                Text(
                    "Verse ${verse + 1} of $count${if (player.loading) " • Loading…" else ""}",
                    Modifier.fillMaxWidth(), color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
            if (player.error != null) Text(player.error!!, color = Color(0xFFFFA598), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous verse" }, enabled = verse > 0 && !audioLoading) {
                    Text("‹", color = if (verse > 0 && !audioLoading) { if (night) TextWhite else Ink } else Color.Gray, fontSize = 26.sp)
                }
                Button(onClick = onToggle, enabled = !audioLoading && !player.loading) {
                    Text(if (player.playing) "Pause" else "Play")
                }
                TextButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Next verse" }, enabled = verse in 0 until count - 1 && !audioLoading) {
                    Text("›", color = if (verse in 0 until count - 1 && !audioLoading) { if (night) TextWhite else Ink } else Color.Gray, fontSize = 26.sp)
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Failure(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        TextButton(onClick = retry) { Text("Retry") }
    }
}
