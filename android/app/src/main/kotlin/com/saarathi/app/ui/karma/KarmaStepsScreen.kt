package com.saarathi.app.ui.karma

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.KarmaStepEntity
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Every Karma Step, open ones first. Steps are the user's own list: tick, untick, delete, open the conversation. */
@Composable
fun KarmaStepsScreen(container: AppContainer, onOpenConversation: (Long) -> Unit, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    val steps by container.karma.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val open = steps.filter { !it.done }
    val done = steps.filter { it.done }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Karma Steps", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Small steps you chose to keep from Krishna's replies. No reminders; they wait here until you are ready.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))
            }
            if (steps.isEmpty()) item {
                Text("Nothing saved yet. When a reply ends with a short list of steps, tap \"Save as Karma Steps\" under it.", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
            if (open.isNotEmpty()) item { Text("Open · ${open.size}", style = MaterialTheme.typography.titleSmall, color = c.gold) }
            items(open, key = { "o" + it.id }) { s -> StepRow(s, fmt, onToggle = { scope.launch { container.karma.setDone(s.id, it) } }, onDelete = { scope.launch { container.karma.delete(s.id) } }, onOpenConversation = onOpenConversation) }
            if (done.isNotEmpty()) item { Spacer(Modifier.height(8.dp)); Text("Done · ${done.size}", style = MaterialTheme.typography.titleSmall, color = c.gold) }
            items(done, key = { "d" + it.id }) { s -> StepRow(s, fmt, onToggle = { scope.launch { container.karma.setDone(s.id, it) } }, onDelete = { scope.launch { container.karma.delete(s.id) } }, onOpenConversation = onOpenConversation) }
        }
    }
}

@Composable
private fun StepRow(s: KarmaStepEntity, fmt: DateFormat, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onOpenConversation: (Long) -> Unit) {
    val c = SaarathiTheme.colors
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.done, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary))
            Column(Modifier.weight(1f)) {
                Text(s.text, style = MaterialTheme.typography.bodyMedium, color = if (s.done) c.textSecondary else c.textPrimary, textDecoration = if (s.done) TextDecoration.LineThrough else null)
                Text((if (s.done && s.doneAt != null) "done ${fmt.format(Date(s.doneAt))} · " else "") + "saved ${fmt.format(Date(s.createdAt))}", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete step", tint = c.textSecondary) }
        }
        s.conversationId?.let { id -> TextButton(onClick = { onOpenConversation(id) }) { Text("Open the conversation", style = MaterialTheme.typography.labelMedium, color = c.gold) } }
    }
}
