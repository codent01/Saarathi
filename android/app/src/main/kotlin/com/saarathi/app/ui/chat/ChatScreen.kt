package com.saarathi.app.ui.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.text.style.TextDecoration
import com.saarathi.app.data.db.KarmaStepEntity
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.MessageEntity
import com.saarathi.app.data.db.MessageRole
import com.saarathi.app.data.db.MessageStatus
import com.saarathi.app.data.scripture.Citation
import com.saarathi.app.ingest.Attachment
import com.saarathi.app.llm.ModelState
import com.saarathi.app.ui.components.KrishnaAvatar
import com.saarathi.app.ui.components.OfflineBadge
import com.saarathi.app.ui.components.ShlokaCard
import com.saarathi.app.ui.navigation.AppViewModel
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme

private val STARTERS = listOf(
    "Krishna, I feel lost.", "Krishna, I am afraid of failing.", "Krishna, why am I never satisfied?",
    "Krishna, I cannot stop overthinking.", "Krishna, I need courage.", "Krishna, I am angry with someone.",
    "Krishna, help me understand my purpose.", "Krishna, I need help making a decision.",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    container: AppContainer, appVm: AppViewModel, conversationId: Long, mode: String,
    onOpenHistory: () -> Unit, onOpenSaved: () -> Unit, onOpenLibrary: () -> Unit, onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit, onOpenPdf: (String, Int) -> Unit, onNewConversation: (String) -> Unit,
    onOpenDaily: () -> Unit = {}, onOpenJournal: () -> Unit = {}, onOpenKarma: () -> Unit = {}, onOpenCompass: () -> Unit = {}, onOpenJourney: () -> Unit = {},
) {
    val vm: ChatViewModel = viewModel(key = "chat-$conversationId-$mode", factory = factoryOf { ChatViewModel(container, conversationId, mode) })
    val c = SaarathiTheme.colors
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val attachment by vm.attachment.collectAsState()
    val attachmentBusy by vm.attachmentBusy.collectAsState()
    val error by vm.error.collectAsState()
    val modelState by appVm.modelState.collectAsState()
    val cards by vm.cards.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val conversationSteps by vm.conversationSteps.collectAsState()
    val todaySteps by vm.todaySteps.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        vm.consumePrefill()?.let { (text, now) ->
            // Sending straight away only makes sense when Krishna can answer; otherwise leave it in the composer.
            if (now && modelState is ModelState.Ready) vm.send(text) else input = text
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.attach(it) } }

    val starters = remember { STARTERS.shuffled().take(4) }
    val isStreaming = streaming != null

    LaunchedEffect(messages.size, streaming?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KrishnaAvatar(size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Krishna", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (mode == "arjuna") "Arjuna Mode · Your Saarathi" else "Your Saarathi", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                OfflineBadge()
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, contentDescription = "Conversations", tint = c.textPrimary) }
                    IconButton(onClick = onOpenSaved) { Icon(Icons.Default.Bookmark, contentDescription = "Saved", tint = c.textPrimary) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = c.textPrimary) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("New conversation") }, onClick = { menuOpen = false; onNewConversation("chat") })
                            DropdownMenuItem(text = { Text(if (mode == "arjuna") "Leave Arjuna Mode" else "Arjuna Mode (guided reflection)") }, onClick = { menuOpen = false; onNewConversation(if (mode == "arjuna") "chat" else "arjuna") })
                            DropdownMenuItem(text = { Text("Decision Compass") }, onClick = { menuOpen = false; onOpenCompass() })
                            DropdownMenuItem(text = { Text("My Kurukshetra journal") }, onClick = { menuOpen = false; onOpenJournal() })
                            DropdownMenuItem(text = { Text("Karma Steps") }, onClick = { menuOpen = false; onOpenKarma() })
                            DropdownMenuItem(text = { Text("Today's verse") }, onClick = { menuOpen = false; onOpenDaily() })
                            DropdownMenuItem(text = { Text("Gita journey") }, onClick = { menuOpen = false; onOpenJourney() })
                            DropdownMenuItem(text = { Text("Scripture library") }, onClick = { menuOpen = false; onOpenLibrary() })
                            DropdownMenuItem(text = { Text("Model & setup") }, onClick = { menuOpen = false; onOpenSetup() })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
        bottomBar = {
            Column(Modifier.background(c.surface).navigationBarsPadding().imePadding()) {
                if (modelState !is ModelState.Ready) {
                    val label = when (val s = modelState) {
                        is ModelState.Loading -> "Loading ${s.fileName}…"
                        is ModelState.Error -> "Model error: ${s.message}"
                        else -> "No model loaded. Krishna cannot reply until a model is set up."
                    }
                    Row(Modifier.fillMaxWidth().background(c.surfaceElevated).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (modelState is ModelState.Loading) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent); Spacer(Modifier.width(10.dp)) }
                        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        if (modelState !is ModelState.Loading) TextButton(onClick = onOpenSetup) { Text("Setup") }
                    }
                }
                if (error != null) {
                    Row(Modifier.fillMaxWidth().background(c.danger.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = c.textSecondary) }
                    }
                }
                attachment?.let { AttachmentChip(it, onRemove = { vm.clearAttachment() }) }
                if (attachmentBusy) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent); Spacer(Modifier.width(8.dp))
                    Text("Reading attachment on device…", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                Composer(
                    value = input, onValueChange = { input = it }, streaming = isStreaming,
                    onSend = { vm.send(input); input = "" }, onStop = { vm.cancel() },
                    onAttach = { picker.launch(arrayOf("image/*", "application/pdf", "text/plain")) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyState(starters = starters, arjuna = mode == "arjuna", onPick = { input = it }) }
                if (todaySteps.isNotEmpty() && mode != "arjuna") item { KarmaStepsCard(todaySteps, onToggle = { s, done -> vm.setStepDone(s, done) }, onOpenAll = onOpenKarma) }
            }
            items(messages, key = { it.id }) { m ->
                val live = streaming?.takeIf { it.messageId == m.id }
                MessageRow(
                    message = m, liveText = live?.text, promptProgress = live?.let { it.promptDone to it.promptTotal },
                    citations = vm.citations(m), suggestedRef = vm.suggestedRef(m), cards = cards,
                    ensureCard = { vm.ensureCard(it) }, ensureCardByRef = { ref, cb -> vm.ensureCardByRef(ref, cb) },
                    isVerseBookmarked = { ref -> bookmarks.any { it.kind == com.saarathi.app.data.db.BookmarkKind.VERSE && it.refKey == ref } },
                    onToggleVerseBookmark = { vm.toggleVerseBookmark(it) }, onOpenPdf = onOpenPdf,
                    onCopy = { clipboard.setText(AnnotatedString(m.text)) },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.text) }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    },
                    onBookmark = { vm.toggleMessageBookmark(m) }, onDelete = { vm.deleteMessage(m) },
                    onRegenerate = if (m.role == MessageRole.KRISHNA && !isStreaming) ({ vm.regenerate(m) }) else null,
                    karmaSteps = if (m.role == MessageRole.KRISHNA && m.status == MessageStatus.COMPLETE && live == null) vm.stepsIn(m) else emptyList(),
                    stepsSaved = conversationSteps.any { it.messageId == m.id },
                    onSaveSteps = { vm.saveSteps(m) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun EmptyState(starters: List<String>, arjuna: Boolean, onPick: (String) -> Unit) {
    val c = SaarathiTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KrishnaAvatar(size = 96.dp, glow = true)
        Spacer(Modifier.height(16.dp))
        Text(if (arjuna) "Arjuna Mode" else "Every mind has its Kurukshetra.", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(if (arjuna) "Bring a decision you are wrestling with. I will ask before I advise." else "Every Arjuna needs a Saarathi.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(24.dp))
        for (s in starters) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .border(1.dp, c.krishnaBubbleBorder, RoundedCornerShape(14.dp)).clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 12.dp)
            ) { Text(s, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
        }
        Spacer(Modifier.height(12.dp))
        Text("An AI interpretation inspired by Krishna's teachings, grounded in your Gita and Mahabharata. Not a literal divine presence.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary.copy(alpha = 0.8f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: MessageEntity, liveText: String?, promptProgress: Pair<Int, Int>?,
    citations: List<Citation>, suggestedRef: String?, cards: Map<Int, com.saarathi.app.data.scripture.ScriptureRepository.VerseCard>,
    ensureCard: (Int) -> Unit, ensureCardByRef: (String, (Int) -> Unit) -> Unit,
    isVerseBookmarked: (String) -> Boolean, onToggleVerseBookmark: (com.saarathi.app.data.scripture.ScriptureRepository.VerseCard) -> Unit,
    onOpenPdf: (String, Int) -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onBookmark: () -> Unit, onDelete: () -> Unit, onRegenerate: (() -> Unit)?,
    karmaSteps: List<String> = emptyList(), stepsSaved: Boolean = false, onSaveSteps: () -> Unit = {},
) {
    val c = SaarathiTheme.colors
    val isUser = message.role == MessageRole.USER
    var menu by remember { mutableStateOf(false) }
    val text = liveText ?: message.text
    val attachment = remember(message.attachmentJson) { Attachment.fromJson(message.attachmentJson) }

    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 6 }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
            if (!isUser) { KrishnaAvatar(size = 28.dp); Spacer(Modifier.width(8.dp)) }
            Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                Box {
                    Column(
                        Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isUser) 18.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 18.dp))
                            .background(if (isUser) c.userBubble else c.krishnaBubble)
                            .border(1.dp, if (isUser) Color.Transparent else c.krishnaBubbleBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isUser) 18.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 18.dp))
                            .combinedClickable(onClick = {}, onLongClick = { menu = true })
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (attachment != null) {
                            Text("📎 ${attachment.name}", style = MaterialTheme.typography.labelSmall, color = if (isUser) c.userBubbleText.copy(alpha = 0.85f) else c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                        }
                        when {
                            text.isBlank() && liveText != null -> {
                                if (promptProgress != null && promptProgress.second > 0 && promptProgress.first < promptProgress.second)
                                    Text("Reading… ${(100 * promptProgress.first / promptProgress.second)}%", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                                else TypingIndicator()
                            }
                            else -> Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isUser) c.userBubbleText else c.textPrimary)
                        }
                        if (message.status == MessageStatus.ERROR) Text("Could not generate a reply.", style = MaterialTheme.typography.labelSmall, color = c.danger)
                        if (message.status == MessageStatus.CANCELLED && liveText == null) Text("(stopped)", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; onCopy() })
                        DropdownMenuItem(text = { Text(if (message.bookmarked) "Remove bookmark" else "Bookmark") }, onClick = { menu = false; onBookmark() })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                        if (onRegenerate != null) DropdownMenuItem(text = { Text("Regenerate") }, onClick = { menu = false; onRegenerate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
                if (message.bookmarked) Text("Bookmarked", style = MaterialTheme.typography.labelSmall, color = c.gold)
                if (!isUser && karmaSteps.isNotEmpty()) {
                    if (stepsSaved) Text("Saved as Karma Steps", style = MaterialTheme.typography.labelSmall, color = c.gold, modifier = Modifier.padding(top = 2.dp))
                    else TextButton(onClick = onSaveSteps, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Checklist, contentDescription = null, tint = c.gold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text("Save as Karma Steps (${karmaSteps.size})", style = MaterialTheme.typography.labelMedium, color = c.gold)
                    }
                }
                if (!isUser && liveText == null) {
                    for (cit in citations) {
                        if (cit is Citation.Verse) {
                            LaunchedEffect(cit.verseId) { ensureCard(cit.verseId) }
                            cards[cit.verseId]?.let { card ->
                                Spacer(Modifier.height(8.dp))
                                ShlokaCard(card, bookmarked = isVerseBookmarked(card.verse.ref), onBookmark = { onToggleVerseBookmark(card) }, onOpenPdf = onOpenPdf)
                            }
                        }
                    }
                    if (citations.isEmpty() && suggestedRef != null) {
                        var id by remember(suggestedRef) { mutableStateOf<Int?>(null) }
                        LaunchedEffect(suggestedRef) { ensureCardByRef(suggestedRef) { id = it } }
                        id?.let { cards[it] }?.let { card ->
                            Spacer(Modifier.height(8.dp))
                            ShlokaCard(card, bookmarked = isVerseBookmarked(card.verse.ref), onBookmark = { onToggleVerseBookmark(card) }, onOpenPdf = onOpenPdf, header = "A related teaching you may want to read")
                        }
                    }
                }
            }
        }
    }
}

/** Today's Karma Steps on the empty chat: open steps plus the ones finished today. Plain progress, no nudging. */
@Composable
fun KarmaStepsCard(steps: List<KarmaStepEntity>, onToggle: (KarmaStepEntity, Boolean) -> Unit, onOpenAll: () -> Unit) {
    val c = SaarathiTheme.colors
    val shown = steps.take(8)
    val done = steps.count { it.done }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.krishnaBubbleBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Karma Steps", style = MaterialTheme.typography.titleSmall, color = c.gold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("All steps", style = MaterialTheme.typography.labelMedium, color = c.textSecondary) }
        }
        Text(if (done == steps.size) "All ${steps.size} done today." else "$done of ${steps.size} done today.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        for (s in shown) {
            Row(Modifier.fillMaxWidth().clickable { onToggle(s, !s.done) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.done, onCheckedChange = { onToggle(s, it) }, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary))
                Text(s.text, style = MaterialTheme.typography.bodyMedium, color = if (s.done) c.textSecondary else c.textPrimary,
                    textDecoration = if (s.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            }
        }
        if (steps.size > shown.size) Text("and ${steps.size - shown.size} more", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

@Composable
private fun TypingIndicator() {
    val c = SaarathiTheme.colors
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 180, easing = LinearEasing), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.padding(horizontal = 3.dp).size(7.dp).alpha(a).clip(CircleShape).background(c.accent))
        }
    }
}

@Composable
private fun AttachmentChip(att: Attachment, onRemove: () -> Unit) {
    val c = SaarathiTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceElevated).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${att.kind.uppercase()} · ${att.name}", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            Text(if (att.extractedText.isBlank()) att.note else "${att.extractedText.length} characters read" + (if (att.truncated) " (truncated)" else ""), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = c.textSecondary) }
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit, onAttach: () -> Unit) {
    val c = SaarathiTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = onAttach, enabled = !streaming) { Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = c.textSecondary) }
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
            placeholder = { Text("Speak your heart, Vats…", color = c.textSecondary) },
            maxLines = 6, shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surfaceElevated, unfocusedContainerColor = c.surfaceElevated,
                focusedBorderColor = c.accent.copy(alpha = 0.6f), unfocusedBorderColor = c.krishnaBubbleBorder,
                focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (streaming) c.surfaceElevated else c.accent)
                .clickable(enabled = streaming || value.isNotBlank()) { if (streaming) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (streaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, contentDescription = if (streaming) "Stop" else "Send", tint = if (streaming) c.textPrimary else Color(0xFF1A0E04))
        }
    }
}
