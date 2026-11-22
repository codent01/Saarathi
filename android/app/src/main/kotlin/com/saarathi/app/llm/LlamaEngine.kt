package com.saarathi.app.llm

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.Executors

data class ChatMessage(val role: String, val content: String)

data class GenParams(
    val maxTokens: Int = 400,
    val temperature: Float = 0.7f,
    val topP: Float = 0.92f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.08f,
    val stops: List<String> = emptyList(),
)

sealed class GenEvent {
    data class PromptProgress(val done: Int, val total: Int) : GenEvent()
    data class Token(val piece: String) : GenEvent()
    data class Finished(val tokens: Int, val cancelled: Boolean) : GenEvent()
    data class Error(val message: String) : GenEvent()
}

data class ModelInfo(
    val fileName: String, val description: String, val sizeBytes: Long, val params: Long,
    val ctxTrain: Int, val ctx: Int, val threads: Int,
)

sealed class ModelState {
    data object Unloaded : ModelState()
    data class Loading(val fileName: String) : ModelState()
    data class Ready(val info: ModelInfo) : ModelState()
    data class Error(val message: String) : ModelState()
}

/**
 * Owns the single native llama session. All native calls run on one dedicated thread so the
 * context is never touched concurrently; the UI only ever observes [state] and token flows.
 */
class LlamaEngine {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "saarathi-llama").apply { priority = Thread.NORM_PRIORITY } }
    private val dispatcher = executor.asCoroutineDispatcher()

    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    val state: StateFlow<ModelState> = _state

    @Volatile private var handle: Long = 0
    @Volatile private var priming = false

    val isReady: Boolean get() = handle != 0L && _state.value is ModelState.Ready

    suspend fun load(file: File, contextSize: Int, threads: Int): Result<ModelInfo> {
        // Interrupt whatever the llama thread is doing (a warm-up or a reply) so the load is not queued behind it.
        if (handle != 0L) LlamaNative.cancel(handle)
        return withContext(dispatcher) { loadLocked(file, contextSize, threads) }
    }

    private fun loadLocked(file: File, contextSize: Int, threads: Int): Result<ModelInfo> {
        return runCatching {
            if (handle != 0L) { LlamaNative.freeModel(handle); handle = 0 }
            _state.value = ModelState.Loading(file.name)
            // Repacked (i8mm/dotprod) kernels are faster but copy the weights into anonymous memory,
            // which 8 GB phones then swap to zram. Only do it for models that comfortably fit.
            val repack = file.length() < 1_500_000_000L
            val h = LlamaNative.loadModel(file.absolutePath, contextSize, threads, 512, repack)
            if (h == 0L) throw IllegalStateException("The model could not be loaded. It may be corrupted, not a GGUF file, or too large for this phone's memory.")
            handle = h
            val info = parseInfo(file.name, LlamaNative.modelInfo(h), contextSize, threads)
            _state.value = ModelState.Ready(info)
            Log.w(TAG, "loaded ${file.name}: $info")
            info
        }.onFailure { e ->
            _state.value = ModelState.Error(e.message ?: "Unknown error while loading the model")
        }
    }

    suspend fun unload() = withContext(dispatcher) {
        if (handle != 0L) { LlamaNative.freeModel(handle); handle = 0 }
        _state.value = ModelState.Unloaded
    }

    suspend fun applyTemplate(messages: List<ChatMessage>, addAssistant: Boolean): String = withContext(dispatcher) {
        check(handle != 0L) { "model not loaded" }
        LlamaNative.applyChatTemplate(handle, messages.map { it.role }.toTypedArray(), messages.map { it.content }.toTypedArray(), addAssistant)
    }

    suspend fun tokenCount(text: String): Int = withContext(dispatcher) {
        if (handle == 0L) text.length / 4 else LlamaNative.tokenCount(handle, text)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun generate(messages: List<ChatMessage>, params: GenParams): Flow<GenEvent> = callbackFlow {
        val h = handle
        if (h == 0L) { trySend(GenEvent.Error("Model not loaded")); close(); return@callbackFlow }
        // A user's message outranks the background warm-up.
        if (priming) LlamaNative.cancel(h)
        Log.w(TAG, "generate: ${messages.size} turns, ${messages.sumOf { it.content.length }} chars, max ${params.maxTokens} tokens")
        val job = launch(dispatcher) {
            val scope = this
            try {
                val prompt = LlamaNative.applyChatTemplate(h, messages.map { it.role }.toTypedArray(), messages.map { it.content }.toTypedArray(), true)
                var stopped = false
                val n = LlamaNative.generate(h, prompt, params.maxTokens, params.temperature, params.topP, params.topK, params.repeatPenalty,
                    params.stops.toTypedArray(), object : TokenCallback {
                        override fun onToken(piece: String): Boolean {
                            if (!scope.isActive) { stopped = true; return false }
                            return trySend(GenEvent.Token(piece)).isSuccess
                        }
                        override fun onPromptProgress(done: Int, total: Int) { trySend(GenEvent.PromptProgress(done, total)) }
                    })
                Log.w(TAG, "generate finished: $n tokens")
                when {
                    n == -1 -> trySend(GenEvent.Error("The conversation is too long for the model's context window. Start a new conversation or reduce the context size."))
                    n == -2 -> trySend(GenEvent.Error("The model failed while generating. Try again, or reload the model."))
                    n == -3 -> trySend(GenEvent.Error("The model is busy with another reply."))
                    else -> trySend(GenEvent.Finished(n, stopped))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                trySend(GenEvent.Error(e.message ?: "generation failed"))
            } finally {
                close()
            }
        }
        awaitClose {
            if (job.isActive) { LlamaNative.cancel(h); job.cancel() }
        }
    }

    fun cancel() { if (handle != 0L) LlamaNative.cancel(handle) }

    /**
     * Evaluates the stable prefix (system prompt) into the KV cache so the first real reply only has to
     * process the user's own words. Runs on the llama thread; safe to call right after [load].
     */
    suspend fun prime(messages: List<ChatMessage>): Int = withContext(dispatcher) {
        val h = handle
        if (h == 0L) return@withContext 0
        val prompt = LlamaNative.applyChatTemplate(h, messages.map { it.role }.toTypedArray(), messages.map { it.content }.toTypedArray(), true)
        val n = LlamaNative.tokenCount(h, prompt)
        val started = System.currentTimeMillis()
        priming = true
        try {
            LlamaNative.generate(h, prompt, 0, 0.7f, 0.9f, 40, 1.0f, emptyArray(), object : TokenCallback {
                override fun onToken(piece: String): Boolean = false
                override fun onPromptProgress(done: Int, total: Int) {}
            })
        } finally { priming = false }
        Log.w(TAG, "primed $n prompt tokens in ${System.currentTimeMillis() - started} ms")
        n
    }

    suspend fun clearCache() = withContext(dispatcher) { if (handle != 0L) LlamaNative.clearCache(handle) }

    fun systemInfo(): String = runCatching { LlamaNative.systemInfo() }.getOrDefault("unavailable")

    private fun parseInfo(fileName: String, jsonText: String, ctx: Int, threads: Int): ModelInfo = runCatching {
        val o = Json.parseToJsonElement(jsonText).jsonObject
        ModelInfo(
            fileName, o["description"]?.jsonPrimitive?.content ?: "", o["sizeBytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            o["params"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L, o["ctxTrain"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            o["ctx"]?.jsonPrimitive?.content?.toIntOrNull() ?: ctx, o["threads"]?.jsonPrimitive?.content?.toIntOrNull() ?: threads,
        )
    }.getOrDefault(ModelInfo(fileName, "", 0, 0, 0, ctx, threads))

    companion object { private const val TAG = "LlamaEngine" }
}
