package com.saarathi.app.ui.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.ConversationEntity
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(container: AppContainer, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    val all by container.conversations.observeConversations().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ConversationEntity>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, all) { results = if (query.isBlank()) null else container.conversations.searchConversations(query) }
    val shown = results ?: all
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Conversations", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search your conversations") }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
            if (shown.isEmpty()) Text(if (query.isBlank()) "No conversations yet." else "Nothing matched.", modifier = Modifier.padding(16.dp), color = c.textSecondary)
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { conv ->
                    SectionCard(Modifier.clickable { onOpen(conv.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(conv.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text((if (conv.mode == "arjuna") "Arjuna Mode · " else "") + fmt.format(Date(conv.updatedAt)), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            }
                            IconButton(onClick = { scope.launch { container.conversations.deleteConversation(conv.id) } }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
}
