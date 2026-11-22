package com.saarathi.app.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.BookmarkKind
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.components.ShlokaCard
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(container: AppContainer, onOpenPdf: (String, Int) -> Unit, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    val bookmarks by container.conversations.observeBookmarks().collectAsState(initial = emptyList())
    val messages by container.conversations.observeBookmarkedMessages().collectAsState(initial = emptyList())
    var cards by remember { mutableStateOf<Map<String, ScriptureRepository.VerseCard>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bookmarks) {
        val m = HashMap<String, ScriptureRepository.VerseCard>()
        for (b in bookmarks.filter { it.kind == BookmarkKind.VERSE }) container.scripture.cardByRef(b.refKey)?.let { m[b.refKey] = it }
        cards = m
    }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Saved wisdom", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Saved shlokas", style = MaterialTheme.typography.titleMedium, color = c.gold) }
            val verses = bookmarks.filter { it.kind == BookmarkKind.VERSE }
            if (verses.isEmpty()) item { Text("Bookmark a verse card to keep it here.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall) }
            items(verses, key = { "v" + it.id }) { b ->
                cards[b.refKey]?.let { card ->
                    ShlokaCard(card, bookmarked = true, onBookmark = { scope.launch { container.conversations.removeBookmark(BookmarkKind.VERSE, b.refKey) } }, onOpenPdf = onOpenPdf)
                }
            }
            val passages = bookmarks.filter { it.kind == BookmarkKind.PASSAGE }
            if (passages.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); Text("Saved Mahabharata passages", style = MaterialTheme.typography.titleMedium, color = c.gold) }
                items(passages, key = { "p" + it.id }) { b ->
                    SectionCard {
                        Text(b.title, style = MaterialTheme.typography.titleSmall, color = c.gold)
                        Text(b.preview, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                        TextButton(onClick = { scope.launch { container.conversations.removeBookmark(BookmarkKind.PASSAGE, b.refKey) } }) { Text("Remove", color = c.textSecondary) }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); Text("Bookmarked messages", style = MaterialTheme.typography.titleMedium, color = c.gold) }
            if (messages.isEmpty()) item { Text("Long-press a message in chat to bookmark it.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall) }
            items(messages, key = { "m" + it.id }) { m ->
                SectionCard {
                    Text(m.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    TextButton(onClick = { scope.launch { container.conversations.setBookmarked(m.id, false) } }) { Text("Remove", color = c.textSecondary) }
                }
            }
        }
    }
}
