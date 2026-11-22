package com.saarathi.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.saarathi.app.daily.DailyWisdom
import com.saarathi.app.AppContainer
import com.saarathi.app.data.prefs.ConversationStyle
import com.saarathi.app.data.prefs.LanguageStyle
import com.saarathi.app.data.prefs.ResponseLength
import com.saarathi.app.data.prefs.ThemeKey
import com.saarathi.app.data.prefs.UserSettings
import com.saarathi.app.llm.ModelState
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.components.SectionTitle
import com.saarathi.app.ui.components.StatusRow
import com.saarathi.app.ui.navigation.AppViewModel
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch

@Composable
private fun <T> ChoiceGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = SaarathiTheme.colors
    Text(title, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    for (o in options) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = o == selected, onClick = { onSelect(o) })
            Text(label(o), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingsScreen(container: AppContainer, appVm: AppViewModel, onOpenDiagnostics: () -> Unit, onOpenSetup: () -> Unit, onBack: () -> Unit, onOpenDaily: () -> Unit = {}) {
    val c = SaarathiTheme.colors
    val s by appVm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun update(block: (UserSettings) -> UserSettings) = scope.launch { container.settings.update(block) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val memories by container.conversations.observeMemories().collectAsState(initial = emptyList())
    var name by remember(s.userName) { mutableStateOf(s.userName) }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Settings", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                SectionTitle("Appearance")
                ChoiceGroup("Theme", ThemeKey.entries, s.theme, { it.label }) { t -> update { it.copy(theme = t) } }
                Text("Text size", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Slider(value = s.textScale, onValueChange = { v -> update { it.copy(textScale = v) } }, valueRange = 0.85f..1.4f, steps = 10)
            }
            SectionCard {
                SectionTitle("How Krishna talks")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (name != s.userName) TextButton(onClick = { update { it.copy(userName = name.trim()) } }) { Text("Save name") }
                Spacer(Modifier.height(8.dp))
                ChoiceGroup("Language", LanguageStyle.entries, s.language, { it.label }) { v -> update { it.copy(language = v) } }
                ChoiceGroup("Response length", ResponseLength.entries, s.responseLength, { it.label }) { v -> update { it.copy(responseLength = v) } }
                ChoiceGroup("Conversation preference", ConversationStyle.entries, s.conversationStyle, { it.label }) { v -> update { it.copy(conversationStyle = v) } }
            }
            SectionCard {
                SectionTitle("Memory & privacy")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Long-term memory", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        Text("When on, notes you save here are shared with Krishna in new conversations. Nothing is saved automatically.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    Switch(checked = s.memoryEnabled, onCheckedChange = { v -> update { it.copy(memoryEnabled = v) } })
                }
                Spacer(Modifier.height(8.dp))
                var newMemory by remember { mutableStateOf("") }
                OutlinedTextField(value = newMemory, onValueChange = { newMemory = it }, label = { Text("Add something Krishna should remember") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { if (newMemory.isNotBlank()) scope.launch { container.conversations.addMemory(newMemory.trim(), "user"); newMemory = "" } }) { Text("Save memory") }
                for (m in memories) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                        TextButton(onClick = { scope.launch { container.conversations.deleteMemory(m.id) } }) { Text("Delete", color = c.textSecondary) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "history" }) { Text("Clear all conversations") }
                    OutlinedButton(onClick = { confirm = "memories" }) { Text("Clear memories") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "journal" }) { Text("Clear journal & steps") }
                    OutlinedButton(onClick = { confirm = "journey" }) { Text("Reset Gita journey") }
                }
                Spacer(Modifier.height(6.dp))
                Text("Saarathi has no internet permission. Nothing you type ever leaves this phone.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            SectionCard {
                SectionTitle("Model")
                val state by appVm.modelState.collectAsState()
                StatusRow("Loaded model", (state as? ModelState.Ready)?.info?.fileName ?: "none", ok = state is ModelState.Ready)
                Text("Context window: ${s.contextSize} tokens", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Slider(value = s.contextSize.toFloat(), onValueChange = { v -> update { it.copy(contextSize = (v / 512).toInt() * 512) } }, valueRange = 2048f..8192f, steps = 11)
                Text("Threads: ${if (s.threads == 0) "auto (${container.models.recommendedThreads()})" else s.threads}", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Slider(value = s.threads.toFloat(), onValueChange = { v -> update { it.copy(threads = v.toInt()) } }, valueRange = 0f..8f, steps = 7)
                Text("Changes apply the next time the model is loaded.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSetup) { Text("Model & scripture setup") }
                    OutlinedButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                }
            }
            SectionCard {
                SectionTitle("Daily wisdom")
                val context = LocalContext.current
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) { DailyWisdom.schedule(context, s.dailyWisdomHour, 0); update { it.copy(dailyWisdomEnabled = true) } }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("One verified verse each day", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        Text("A local notification with a short reflection. No server, no internet.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    Switch(checked = s.dailyWisdomEnabled, onCheckedChange = { v ->
                        if (v) {
                            if (Build.VERSION.SDK_INT >= 33 && !DailyWisdom.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { DailyWisdom.schedule(context, s.dailyWisdomHour, 0); update { it.copy(dailyWisdomEnabled = true) } }
                        } else { DailyWisdom.cancel(context); update { it.copy(dailyWisdomEnabled = false) } }
                    })
                }
                Text("Time: %02d:00".format(s.dailyWisdomHour), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Slider(value = s.dailyWisdomHour.toFloat(), onValueChange = { v -> update { it.copy(dailyWisdomHour = v.toInt()) } }, onValueChangeFinished = { if (s.dailyWisdomEnabled) DailyWisdom.schedule(context, s.dailyWisdomHour, 0) }, valueRange = 4f..22f, steps = 17)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenDaily) { Text("Today's verse") }
                    OutlinedButton(onClick = { if (DailyWisdom.canNotify(context)) DailyWisdom.showNow(context) else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Send test notification") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(when (what) { "history" -> "Delete all conversations?"; "memories" -> "Delete all memories?"; "journal" -> "Delete every journal entry and Karma Step?"; else -> "Reset Gita journey progress?" }) },
            text = { Text("This cannot be undone.") },
            confirmButton = { Button(onClick = { scope.launch { when (what) {
                "history" -> container.conversations.clearAllConversations()
                "memories" -> container.conversations.clearMemories()
                "journal" -> { container.journal.deleteAll(); container.karma.deleteAll() }
                else -> container.journey.deleteAll()
            } }; confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun DiagnosticsScreen(container: AppContainer, appVm: AppViewModel, onBack: () -> Unit) {
    val c = SaarathiTheme.colors
    val state by appVm.modelState.collectAsState()
    val report = remember { container.models.deviceReport() }
    val sysinfo = remember { container.engine.systemInfo() }
    var status by remember { mutableStateOf<com.saarathi.app.data.scripture.ScriptureRepository.Status?>(null) }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { status = runCatching { container.scripture.status() }.getOrNull() }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Diagnostics", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                SectionTitle("Model")
                when (val st = state) {
                    is ModelState.Ready -> {
                        StatusRow("File", st.info.fileName, ok = true)
                        StatusRow("Architecture", st.info.description)
                        StatusRow("Parameters", "%.2f B".format(st.info.params / 1e9))
                        StatusRow("Size", "%.2f GB".format(st.info.sizeBytes / 1e9))
                        StatusRow("Context (train / used)", "${st.info.ctxTrain} / ${st.info.ctx}")
                        StatusRow("Threads", st.info.threads.toString())
                    }
                    is ModelState.Loading -> StatusRow("State", "loading ${st.fileName}")
                    is ModelState.Error -> StatusRow("State", st.message, ok = false)
                    ModelState.Unloaded -> StatusRow("State", "not loaded", ok = false)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { appVm.loadSelectedModel() }) { Text("Reload") }
                    OutlinedButton(onClick = { scope.launch { container.engine.unload() } }) { Text("Unload") }
                }
            }
            SectionCard {
                SectionTitle("Device")
                StatusRow("Device", report.device)
                StatusRow("Android", report.androidVersion)
                StatusRow("ABI", report.abi)
                StatusRow("Cores", report.cores.toString())
                StatusRow("RAM total", "%.2f GB".format(report.totalRamBytes / 1e9))
                StatusRow("RAM available", "%.2f GB".format(report.availableRamBytes / 1e9))
                StatusRow("Free storage", "%.2f GB".format(report.freeStorageBytes / 1e9))
                StatusRow("ARM dot-product", if (report.supportsDotProd) "yes" else "no", ok = report.supportsDotProd)
            }
            SectionCard {
                SectionTitle("Scripture index")
                StatusRow("Gita verse records", status?.verses?.toString() ?: "…")
                StatusRow("Mahabharata passages", status?.passages?.toString() ?: "…")
                StatusRow("Imported edition verses", status?.editionVerses?.toString() ?: "…")
                StatusRow("Built", status?.builtAt ?: "…")
            }
            SectionCard {
                SectionTitle("Engine")
                Text(sysinfo, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}
