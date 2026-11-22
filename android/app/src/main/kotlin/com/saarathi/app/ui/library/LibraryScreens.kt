package com.saarathi.app.ui.library

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.BookmarkKind
import com.saarathi.app.data.scripture.GitaChapter
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.data.scripture.ScriptureSource
import com.saarathi.app.journey.GitaJourney
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.components.SectionTitle
import com.saarathi.app.ui.components.ShlokaCard
import com.saarathi.app.ui.components.StatusRow
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow<ScriptureRepository.SearchResults?>(null)
    val searching = MutableStateFlow(false)
    val bookmarks = container.conversations.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { results.value = null; searching.value = false; return }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            searching.value = true
            results.value = runCatching { container.scripture.search(q) }.getOrNull()
            searching.value = false
        }
    }

    fun toggleVerseBookmark(card: ScriptureRepository.VerseCard) = viewModelScope.launch {
        val ref = card.verse.ref
        if (bookmarks.value.any { it.kind == BookmarkKind.VERSE && it.refKey == ref }) container.conversations.removeBookmark(BookmarkKind.VERSE, ref)
        else container.conversations.addBookmark(BookmarkKind.VERSE, ref, "Bhagavad Gita $ref", card.translation?.text?.take(160) ?: card.verse.iast.take(160))
    }

    fun togglePassageBookmark(p: com.saarathi.app.data.scripture.MbPassage) = viewModelScope.launch {
        val key = p.id.toString()
        if (bookmarks.value.any { it.kind == BookmarkKind.PASSAGE && it.refKey == key }) container.conversations.removeBookmark(BookmarkKind.PASSAGE, key)
        else container.conversations.addBookmark(BookmarkKind.PASSAGE, key, p.label, p.text.take(200))
    }

    val chapters = MutableStateFlow<List<GitaChapter>>(emptyList())
    val sources = MutableStateFlow<List<ScriptureSource>>(emptyList())
    val parvas = MutableStateFlow<List<Triple<Int, String, Int>>>(emptyList())
    val status = MutableStateFlow<ScriptureRepository.Status?>(null)
    init {
        viewModelScope.launch {
            chapters.value = withContext(Dispatchers.IO) { container.scripture.chapters() }
            sources.value = container.scripture.sources()
            parvas.value = container.scripture.parvaSummary()
            status.value = container.scripture.status()
        }
    }
}

@Composable
fun LibraryScreen(container: AppContainer, onOpenChapter: (Int) -> Unit, onBack: () -> Unit, onOpenPdf: (String, Int) -> Unit = { _, _ -> }, onOpenJourney: () -> Unit = {}) {
    val vm: LibraryViewModel = viewModel(factory = factoryOf { LibraryViewModel(container) })
    val c = SaarathiTheme.colors
    val chapters by vm.chapters.collectAsState()
    val sources by vm.sources.collectAsState()
    val parvas by vm.parvas.collectAsState()
    val status by vm.status.collectAsState()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Scripture library", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                androidx.compose.material3.OutlinedTextField(
                    value = query, onValueChange = { vm.search(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search verses and the Mahabharata (e.g. anger, Karna, 2.47)") },
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.padding(8.dp).height(20.dp), strokeWidth = 2.dp, color = c.accent) },
                )
            }
            if (results != null) {
                val r = results!!
                item { Text("${r.verses.size} verses · ${r.passages.size} Mahabharata passages", style = MaterialTheme.typography.labelLarge, color = c.textSecondary) }
                items(r.verses, key = { "sv" + it.verse.id }) { card ->
                    ShlokaCard(card, bookmarked = bookmarks.any { it.kind == BookmarkKind.VERSE && it.refKey == card.verse.ref }, onBookmark = { vm.toggleVerseBookmark(card) }, onOpenPdf = onOpenPdf)
                }
                items(r.passages, key = { "sp" + it.id }) { p ->
                    SectionCard {
                        Text(p.label + (if (p.speaker.isNotBlank()) " · ${p.speaker} speaking" else ""), style = MaterialTheme.typography.titleSmall, color = c.gold)
                        Spacer(Modifier.height(6.dp))
                        Text(p.text.take(700) + if (p.text.length > 700) "…" else "", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Mahabharata PDF page ${p.page}", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        val saved = bookmarks.any { it.kind == BookmarkKind.PASSAGE && it.refKey == p.id.toString() }
                        androidx.compose.material3.TextButton(onClick = { vm.togglePassageBookmark(p) }) { Text(if (saved) "Remove bookmark" else "Bookmark", color = if (saved) c.accent else c.textSecondary) }
                    }
                }
                if (r.verses.isEmpty() && r.passages.isEmpty() && !searching) item { Text("Nothing matched. Try a feeling, a name, or a verse number like 2.47.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall) }
                return@LazyColumn
            }
            item {
                SectionCard {
                    SectionTitle("Sources on this phone")
                    for (s in sources) {
                        val installed = s.bundled || (s.key == "as_it_is" && (status?.editionVerses ?: 0) > 0)
                        StatusRow(s.title, if (installed) "installed" else "not imported", ok = installed)
                        Text(listOf(s.edition, s.author).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                    }
                    status?.let { Text("Index built ${it.builtAt.take(10)} · ${it.verses} verse records · ${it.passages} Mahabharata passages", style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bhagavad Gita · 18 chapters", style = MaterialTheme.typography.titleMedium, color = c.gold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onOpenJourney) { Text("Your journey", color = c.accent) }
                }
            }
            items(chapters, key = { it.chapter }) { ch ->
                SectionCard(Modifier.clickable { onOpenChapter(ch.chapter) }) {
                    Text("Chapter ${ch.chapter} · ${ch.title}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    if (ch.titleEdition.isNotBlank()) Text(ch.titleEdition, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(ch.summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text("${ch.verseCount} verses · themes: ${ch.themes.joinToString(", ") { it.replace('_', ' ') }}", style = MaterialTheme.typography.labelSmall, color = c.gold)
                }
            }
            item { Text("Mahabharata · Ganguli translation", style = MaterialTheme.typography.titleMedium, color = c.gold) }
            item {
                SectionCard {
                    for ((book, parva, n) in parvas) StatusRow("Book $book · $parva", "$n passages")
                    Spacer(Modifier.height(6.dp))
                    Text("Ask Krishna about any character or episode; relevant passages are retrieved with their speaker and section so nothing is misattributed.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}

class ReaderViewModel(private val container: AppContainer, private val chapter: Int) : ViewModel() {
    val cards = MutableStateFlow<List<ScriptureRepository.VerseCard>>(emptyList())
    val chapterInfo = MutableStateFlow<GitaChapter?>(null)
    val reflection = MutableStateFlow("")
    val bookmarks = container.conversations.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Verse record ids already read in this chapter (journey progress). */
    val readIds = container.journey.observeReadIds(chapter).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init {
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) { container.scripture.chapters() }
            chapterInfo.value = chapters.firstOrNull { it.chapter == chapter }
            reflection.value = GitaJourney.reflectionPrompts(chapters)[chapter] ?: ""
            cards.value = container.scripture.versesForChapter(chapter).mapNotNull { container.scripture.card(it.id) }
        }
    }
    /** Called as verse cards come into view; a verse counts as read once it has been on screen. */
    fun markRead(verseIds: Collection<Int>) {
        val fresh = verseIds.filter { it !in readIds.value }
        if (fresh.isNotEmpty()) viewModelScope.launch { container.journey.markRead(chapter, fresh) }
    }
    fun markAllRead() = viewModelScope.launch { container.journey.markRead(chapter, cards.value.map { it.verse.id }) }
    fun resetProgress() = viewModelScope.launch { container.journey.clearChapter(chapter) }
    fun toggleBookmark(card: ScriptureRepository.VerseCard) = viewModelScope.launch {
        val ref = card.verse.ref
        if (bookmarks.value.any { it.kind == BookmarkKind.VERSE && it.refKey == ref }) container.conversations.removeBookmark(BookmarkKind.VERSE, ref)
        else container.conversations.addBookmark(BookmarkKind.VERSE, ref, "Bhagavad Gita $ref", card.translation?.text?.take(160) ?: card.verse.iast.take(160))
    }
}

@Composable
fun ReaderScreen(container: AppContainer, chapter: Int, onOpenPdf: (String, Int) -> Unit, onDiscuss: (String) -> Unit, onBack: () -> Unit, onJournal: (String, String) -> Unit = { _, _ -> }, onOpenJourney: () -> Unit = {}) {
    val vm: ReaderViewModel = viewModel(key = "reader-$chapter", factory = factoryOf { ReaderViewModel(container, chapter) })
    val c = SaarathiTheme.colors
    val cards by vm.cards.collectAsState()
    val info by vm.chapterInfo.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val readIds by vm.readIds.collectAsState()
    val reflection by vm.reflection.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val readCount = cards.count { it.verse.id in readIds }
    val total = cards.size

    // Journey progress: a verse card counts as read once it has been fully on screen.
    LaunchedEffect(cards.size) {
        if (cards.isEmpty()) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo }
            .collect { layout ->
                val viewportEnd = layout.viewportEndOffset
                val ids = layout.visibleItemsInfo.filter { it.key is Int && it.offset >= 0 && it.offset + it.size <= viewportEnd }.map { it.key as Int }
                if (ids.isNotEmpty()) vm.markRead(ids)
            }
    }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Chapter $chapter", onBack) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "header") {
                info?.let { ch ->
                    SectionCard {
                        Text(ch.title, style = MaterialTheme.typography.titleLarge, color = c.gold)
                        if (ch.titleEdition.isNotBlank()) Text(ch.titleEdition, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(ch.summary, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        if (total > 0) {
                            androidx.compose.material3.LinearProgressIndicator(progress = { readCount.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceElevated)
                            Spacer(Modifier.height(4.dp))
                            Text(if (readCount >= total) "Chapter complete · $total verses" else "$readCount of $total verses read", style = MaterialTheme.typography.labelSmall, color = c.gold)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = { onDiscuss("Krishna, help me understand chapter $chapter of the Gita, ${ch.title}. What does it mean for someone living an ordinary life today?") }) { Text("Discuss this chapter with Krishna") }
                    }
                }
            }
            if (cards.isEmpty()) item(key = "loading") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
            items(cards, key = { it.verse.id }) { card ->
                Column {
                    ShlokaCard(card, bookmarked = bookmarks.any { it.kind == BookmarkKind.VERSE && it.refKey == card.verse.ref }, onBookmark = { vm.toggleBookmark(card) }, onOpenPdf = onOpenPdf)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { onDiscuss("Krishna, can you explain Gita ${card.verse.ref} to me and how it applies to my life?") }) { Text("Discuss", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            if (cards.isNotEmpty()) item(key = "reflection") {
                info?.let { ch ->
                    SectionCard {
                        SectionTitle("A reflection for chapter $chapter")
                        Text(reflection, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onJournal("Chapter $chapter · ${ch.title}", reflection + "\n\n") }) { Text("Write in journal") }
                            OutlinedButton(onClick = { onDiscuss("Krishna, I just finished chapter $chapter, ${ch.title}. Here is the question I am sitting with: $reflection") }) { Text("Discuss") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (readCount < total) androidx.compose.material3.TextButton(onClick = { vm.markAllRead() }) { Text("Mark chapter as read", color = c.gold) }
                            else androidx.compose.material3.TextButton(onClick = { vm.resetProgress() }) { Text("Read again (reset progress)", color = c.textSecondary) }
                            androidx.compose.material3.TextButton(onClick = onOpenJourney) { Text("Journey overview", color = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
}

/** Renders one page of a linked PDF with Android's PdfRenderer. Works for any of the three linked sources. */
@Composable
fun PdfPageScreen(container: AppContainer, source: String, page: Int, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    val settings by container.settings.settings.collectAsState(initial = null)
    var current by remember(page) { mutableStateOf(page) }
    val uriString = when (source) {
        "gita", "shlokas" -> settings?.gitaPdfUri
        "mahabharata" -> settings?.mahabharataPdfUri
        else -> null
    }
    val label = when (source) { "gita" -> "Bhagavad-gītā As It Is"; "shlokas" -> "Slokas PDF"; else -> "Mahabharata" }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("$label · page $current", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (settings == null) return@Column
            if (uriString.isNullOrBlank()) {
                Column(Modifier.padding(24.dp)) {
                    Text("This PDF is not linked yet.", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (source == "shlokas") "Link the Slokas PDF as your Gita PDF in Setup to open pages, or link the full As It Is edition for translation pages." else "Open Setup and link the $label PDF from your phone storage to view original pages.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                return@Column
            }
            val bitmap by produceState<Bitmap?>(initialValue = null, uriString, current) {
                value = withContext(Dispatchers.IO) { renderPage(container, Uri.parse(uriString), current) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(c.surface).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                val b = bitmap
                if (b == null) CircularProgressIndicator(Modifier.padding(32.dp), color = c.accent)
                else Image(b.asImageBitmap(), contentDescription = "Page $current", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (current > 1) current-- }) { Text("Previous") }
                OutlinedButton(onClick = { current++ }) { Text("Next") }
            }
        }
    }
}

private fun renderPage(container: AppContainer, uri: Uri, page: Int): Bitmap? = runCatching {
    container.appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
        PdfRenderer(pfd).use { renderer ->
            val index = (page - 1).coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { p ->
                val scale = 1400f / p.width
                val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
}.getOrNull()
