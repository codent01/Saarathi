package com.saarathi.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.saarathi.app.AppContainer
import com.saarathi.app.data.prefs.UserSettings
import com.saarathi.app.llm.ChatMessage
import com.saarathi.app.llm.ModelState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: settings and model status. Also auto-loads the chosen model on launch. */
class AppViewModel(val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val modelState: StateFlow<ModelState> = container.engine.state

    private var autoLoadAttempted = false

    init {
        viewModelScope.launch {
            container.conversations.markInterrupted()
            val s = container.settings.settings.first()
            if (s.dailyWisdomEnabled) com.saarathi.app.daily.DailyWisdom.schedule(container.appContext, s.dailyWisdomHour, 0)
            if (!autoLoadAttempted && s.modelFileName.isNotBlank()) {
                autoLoadAttempted = true
                loadSelectedModel(s)
            }
        }
    }

    fun loadSelectedModel(s: UserSettings = settings.value) {
        val model = container.models.find(s.modelFileName) ?: return
        if (container.engine.state.value is ModelState.Loading) return
        viewModelScope.launch {
            val threads = if (s.threads > 0) s.threads else container.models.recommendedThreads()
            container.engine.load(model.file, s.contextSize, threads).onSuccess {
                // Pre-evaluate the system prompt so the first reply only processes the user's message.
                val memories = if (s.memoryEnabled) container.conversations.enabledMemories() else emptyList()
                val system = container.promptBuilder.systemPrompt(s, memories, arjunaMode = false)
                runCatching { container.engine.prime(listOf(ChatMessage("system", system), ChatMessage("user", "Namaste"))) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { container.embedder.embed("warm up"); container.scripture.verseEmbeddings() } }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { AppViewModel(container) } }
    }
}

inline fun <reified T : ViewModel> factoryOf(crossinline create: () -> T): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
