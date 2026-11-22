package com.saarathi.app.llm

/** Streaming callback invoked from the native generation loop (on the llama thread). */
interface TokenCallback {
    /** Return false to stop generation. */
    fun onToken(piece: String): Boolean
    fun onPromptProgress(done: Int, total: Int)
}

/** JNI surface of libsaarathi_llama.so (see src/main/cpp/llama_jni.cpp). */
object LlamaNative {
    init {
        System.loadLibrary("saarathi_llama")
    }

    external fun systemInfo(): String
    external fun loadModel(path: String, nCtx: Int, nThreads: Int, nBatch: Int, repack: Boolean): Long
    external fun freeModel(handle: Long)
    external fun modelInfo(handle: Long): String
    external fun applyChatTemplate(handle: Long, roles: Array<String>, contents: Array<String>, addAssistant: Boolean): String
    external fun tokenCount(handle: Long, text: String): Int
    external fun cancel(handle: Long)
    external fun clearCache(handle: Long)
    external fun generate(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int,
        repeatPenalty: Float, stops: Array<String>, callback: TokenCallback,
    ): Int
}
