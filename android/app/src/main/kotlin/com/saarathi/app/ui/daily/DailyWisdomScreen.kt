package com.saarathi.app.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.saarathi.app.AppContainer
import com.saarathi.app.daily.DailyWisdom
import com.saarathi.app.data.db.BookmarkKind
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ui.components.KrishnaAvatar
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.components.ShlokaCard
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DailyWisdomScreen(container: AppContainer, onOpenPdf: (String, Int) -> Unit, onDiscuss: (String) -> Unit, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    var card by remember { mutableStateOf<ScriptureRepository.VerseCard?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val bookmarks by container.conversations.observeBookmarks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { card = DailyWisdom.verseForDate(container.scripture); loaded = true }
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Daily wisdom", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KrishnaAvatar(size = 72.dp, glow = true)
            Text(today, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            val current = card
            when {
                !loaded -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                current == null -> Text("The scripture library is not available yet.", color = c.textSecondary)
                else -> {
                    ShlokaCard(
                        current, initiallyExpanded = true,
                        bookmarked = bookmarks.any { it.kind == BookmarkKind.VERSE && it.refKey == current.verse.ref },
                        onBookmark = {
                            scope.launch {
                                val ref = current.verse.ref
                                if (bookmarks.any { it.kind == BookmarkKind.VERSE && it.refKey == ref }) container.conversations.removeBookmark(BookmarkKind.VERSE, ref)
                                else container.conversations.addBookmark(BookmarkKind.VERSE, ref, "Bhagavad Gita $ref", current.translation?.text?.take(160) ?: current.verse.iast.take(160))
                            }
                        },
                        onOpenPdf = onOpenPdf,
                    )
                    SectionCard {
                        Text("A reflection for today", style = MaterialTheme.typography.titleSmall, color = c.gold)
                        Spacer(Modifier.height(8.dp))
                        Text(DailyWisdom.reflectionFor(current), style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = c.textPrimary)
                    }
                    Button(onClick = { onDiscuss("Krishna, today's verse is Gita ${current.verse.ref}. Can you help me understand it and how it applies to my life right now?") }, modifier = Modifier.fillMaxWidth()) { Text("Discuss this verse with Krishna") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to chat") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
