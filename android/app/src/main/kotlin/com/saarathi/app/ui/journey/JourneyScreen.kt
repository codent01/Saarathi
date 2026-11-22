package com.saarathi.app.ui.journey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.data.scripture.GitaChapter
import com.saarathi.app.journey.GitaJourney
import com.saarathi.app.ui.components.KrishnaAvatar
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JourneyViewModel(container: AppContainer) : ViewModel() {
    val chapters = MutableStateFlow<List<GitaChapter>>(emptyList())
    private val recordCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val prompts = MutableStateFlow<Map<Int, String>>(emptyMap())
    val progress = combine(chapters, recordCounts, container.journey.observeCounts()) { ch, counts, reads -> GitaJourney.progress(ch, counts, reads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ch = withContext(Dispatchers.IO) { container.scripture.chapters() }
            chapters.value = ch
            prompts.value = GitaJourney.reflectionPrompts(ch)
            recordCounts.value = ch.associate { it.chapter to container.scripture.versesForChapter(it.chapter).size }
        }
    }
}

/** All 18 chapters with how far the user has read each, and the reflection prompt that goes with it. */
@Composable
fun JourneyScreen(container: AppContainer, onOpenChapter: (Int) -> Unit, onBack: () -> Unit) {
    val vm: JourneyViewModel = viewModel(factory = factoryOf { JourneyViewModel(container) })
    val c = SaarathiTheme.colors
    val chapters by vm.chapters.collectAsState()
    val progress by vm.progress.collectAsState()
    val prompts by vm.prompts.collectAsState()
    val byChapter = progress.associateBy { it.chapter }
    val overall = GitaJourney.overall(progress)
    val completed = progress.count { it.complete }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Gita journey", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KrishnaAvatar(size = 44.dp)
                        Spacer(Modifier.padding(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Eighteen chapters, one conversation on a battlefield.", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text("Read at your own pace. Progress is counted as you scroll through each chapter and stays on this phone.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { overall.fraction }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceElevated)
                    Spacer(Modifier.height(6.dp))
                    Text("${overall.read} of ${overall.total} verses read · $completed of 18 chapters complete", style = MaterialTheme.typography.labelMedium, color = c.gold)
                }
            }
            items(chapters, key = { it.chapter }) { ch ->
                val p = byChapter[ch.chapter]
                SectionCard(Modifier.clickable { onOpenChapter(ch.chapter) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ch.chapter}", style = MaterialTheme.typography.headlineSmall, color = if (p?.complete == true) c.accent else c.gold)
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                when {
                                    p == null -> "${ch.verseCount} verses"
                                    p.complete -> "Complete · ${p.total} verses"
                                    p.started -> "${p.read} of ${p.total} verses read"
                                    else -> "Not started · ${p.total} verses"
                                }, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { p?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceElevated)
                    prompts[ch.chapter]?.let { q ->
                        Spacer(Modifier.height(8.dp))
                        Text(q, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
                    }
                }
            }
        }
    }
}
