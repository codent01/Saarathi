package com.saarathi.app.ui.compass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.compass.DecisionCompass
import com.saarathi.app.ui.components.KrishnaAvatar
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.launch

class DecisionCompassViewModel(private val container: AppContainer) : ViewModel() {
    val answers = HashMap<String, String>()

    /** Creates the Arjuna-Mode conversation and queues the composed message to be sent as soon as the chat opens. */
    suspend fun begin(): Long {
        val id = container.conversations.newConversation(title = DecisionCompass.title(answers), mode = "arjuna")
        container.pendingPrompt = DecisionCompass.compose(answers)
        container.pendingSendNow = true
        return id
    }
}

@Composable
fun DecisionCompassScreen(container: AppContainer, onBegin: (Long) -> Unit, onBack: () -> Unit) {
    val vm: DecisionCompassViewModel = viewModel(factory = factoryOf { DecisionCompassViewModel(container) })
    val c = SaarathiTheme.colors
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = DecisionCompass.steps[index]
    var text by rememberSaveable(index) { mutableStateOf(vm.answers[step.key] ?: "") }
    val scope = rememberCoroutineScope()
    val last = index == DecisionCompass.steps.lastIndex

    fun keep() { vm.answers[step.key] = text }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Decision Compass", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (index == 0) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    KrishnaAvatar(size = 44.dp)
                    Spacer(Modifier.padding(6.dp))
                    Text("Seven questions to see a decision clearly. Your answers open one Arjuna-Mode conversation where Krishna asks before advising. The decision stays with you.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                }
            }
            Text("Step ${index + 1} of ${DecisionCompass.steps.size} · ${step.title}", style = MaterialTheme.typography.labelLarge, color = c.gold)
            LinearProgressIndicator(progress = { (index + 1f) / DecisionCompass.steps.size }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceElevated)
            SectionCard {
                Text(step.question, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(step.hint, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("Write freely. You can leave a step blank.", color = c.textSecondary) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { keep(); if (index > 0) index-- else onBack() }, modifier = Modifier.weight(1f)) { Text(if (index > 0) "Back" else "Cancel") }
                if (!last) Button(onClick = { keep(); index++ }, modifier = Modifier.weight(1f)) { Text("Next") }
                else Button(onClick = { keep(); scope.launch { onBegin(vm.begin()) } }, enabled = DecisionCompass.answered(vm.answers + (step.key to text)) >= 1, modifier = Modifier.weight(1f)) { Text("Begin with Krishna") }
            }
            if (last) Text("Krishna will read all seven answers and ask what he needs to understand first. No verdict is given; the choice remains yours.", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Spacer(Modifier.height(24.dp))
        }
    }
}
