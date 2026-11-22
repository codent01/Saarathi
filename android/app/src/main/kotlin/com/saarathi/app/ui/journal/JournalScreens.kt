package com.saarathi.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.JournalEntryEntity
import com.saarathi.app.ingest.Attachment
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Builds the attachment that carries a journal entry into a chat turn. Kind "journal" is labelled accordingly in the prompt. */
fun journalAttachment(entry: JournalEntryEntity): Attachment =
    Attachment(kind = "journal", name = entry.title, extractedText = entry.body.take(6000), truncated = entry.body.length > 6000, note = "Journal entry")

const val JOURNAL_REFLECT_PROMPT = "Krishna, I wrote this in my journal. Help me reflect on it honestly."

@OptIn(ExperimentalCoroutinesApi::class)
class JournalListViewModel(container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val entries: StateFlow<List<JournalEntryEntity>> = query
        .flatMapLatest { container.journal.observeSearch(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun JournalListScreen(container: AppContainer, onOpen: (Long) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val vm: JournalListViewModel = viewModel(factory = factoryOf { JournalListViewModel(container) })
    val c = SaarathiTheme.colors
    val entries by vm.entries.collectAsState()
    val query by vm.query.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<JournalEntryEntity?>(null) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Scaffold(
        containerColor = c.background,
        topBar = { SaarathiTopBar("My Kurukshetra", onBack) },
        floatingActionButton = { FloatingActionButton(onClick = onNew, containerColor = c.accent) { Icon(Icons.Default.Add, contentDescription = "New entry") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = query, onValueChange = { vm.query.value = it }, placeholder = { Text("Search your journal") }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
            if (entries.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(if (query.isBlank()) "Your private battlefield notes." else "Nothing matched.", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(if (query.isBlank()) "Write what you are wrestling with. Entries stay on this phone, and you can ask Krishna to reflect on any of them." else "Try a word from the title, the text or a tag.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { e ->
                    SectionCard(Modifier.clickable { onOpen(e.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(e.body.replace('\n', ' ').take(140), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2)
                                Spacer(Modifier.height(4.dp))
                                val meta = buildList {
                                    add(fmt.format(Date(e.updatedAt)))
                                    if (e.tagList.isNotEmpty()) add(e.tagList.joinToString(" ") { "#$it" })
                                    if (e.linkedConversationId != null) add("reflected with Krishna")
                                }
                                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.gold)
                            }
                            IconButton(onClick = { pendingDelete = e }) { Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("\"${e.title}\" will be removed from this phone. This cannot be undone.") },
            confirmButton = { Button(onClick = { scope.launch { container.journal.delete(e.id) }; pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

class JournalEditViewModel(private val container: AppContainer, private val entryId: Long) : ViewModel() {
    val entry: StateFlow<JournalEntryEntity?> = (if (entryId > 0) container.journal.observe(entryId) else MutableStateFlow(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedId = MutableStateFlow(entryId)
    val draft: Pair<String, String>? = if (entryId <= 0) container.pendingJournalDraft.also { container.pendingJournalDraft = null } else null

    /** Saves and returns the entry id (new entries get one on first save). */
    suspend fun save(title: String, body: String, tags: String): Long {
        val current = if (savedId.value > 0) container.journal.get(savedId.value) else null
        return if (current == null) container.journal.create(title, body, tags).also { savedId.value = it }
        else { container.journal.save(current, title, body, tags); current.id }
    }

    fun delete() = viewModelScope.launch { if (savedId.value > 0) container.journal.delete(savedId.value) }

    /** Saves, then hands the entry to the chat as a "journal" attachment with a prompt the user can edit before sending. */
    suspend fun reflect(title: String, body: String, tags: String): Long {
        val id = save(title, body, tags)
        val e = container.journal.get(id) ?: return id
        container.pendingAttachment = journalAttachment(e)
        container.pendingPrompt = JOURNAL_REFLECT_PROMPT
        container.pendingJournalLink = id
        return id
    }
}

@Composable
fun JournalEditScreen(container: AppContainer, entryId: Long, onReflect: () -> Unit, onOpenConversation: (Long) -> Unit, onBack: () -> Unit) {
    val vm: JournalEditViewModel = viewModel(key = "journal-$entryId", factory = factoryOf { JournalEditViewModel(container, entryId) })
    val c = SaarathiTheme.colors
    val entry by vm.entry.collectAsState()
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(vm.draft?.first ?: "") }
    var body by rememberSaveable { mutableStateOf(vm.draft?.second ?: "") }
    var tags by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(entryId <= 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(entry) {
        val e = entry
        if (!loaded && e != null) { title = e.title; body = e.body; tags = e.tagList.joinToString(", "); loaded = true }
    }
    val dirty = entry?.let { it.title != title.trim() || it.body != body.trim() || it.tagList.joinToString(", ") != tags.trim() } ?: (title.isNotBlank() || body.isNotBlank())

    fun saveAndBack() {
        scope.launch {
            if (body.isNotBlank() || title.isNotBlank()) vm.save(title, body, tags)
            onBack()
        }
    }

    Scaffold(containerColor = c.background, topBar = {
        SaarathiTopBar(if (entryId > 0) "Journal entry" else "New entry", onBack = { saveAndBack() }) {
            if (entryId > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = c.textPrimary) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("What is on your mind?") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Private. Stored only on this phone. Nothing is sent anywhere.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveAndBack() }, enabled = dirty || entryId <= 0, modifier = Modifier.weight(1f)) { Text("Save") }
                Button(onClick = { scope.launch { vm.reflect(title, body, tags); onReflect() } }, enabled = body.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Reflect with Krishna") }
            }
            entry?.linkedConversationId?.let { id ->
                TextButton(onClick = { onOpenConversation(id) }) { Text("Open the conversation about this entry", color = c.gold) }
            }
            entry?.let { e ->
                val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                Text("Written ${fmt.format(Date(e.createdAt))}" + (if (e.updatedAt != e.createdAt) " · edited ${fmt.format(Date(e.updatedAt))}" else ""), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.delete(); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
