package com.saarathi.app.rag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * On-device sentence embeddings with the quantized all-MiniLM-L6-v2 model bundled in assets.
 * Output is mean-pooled over the attention mask and L2-normalised, exactly like tools/scripture-compiler/embed.py,
 * so cosine similarity against the precomputed verse vectors is meaningful.
 */
class Embedder(private val context: Context) {
    val dim = 384
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null
    private var inputNames: Set<String> = emptySet()
    @Volatile var available = true
        private set

    @Synchronized
    private fun ensureLoaded(): Boolean {
        if (session != null) return true
        if (!available) return false
        return runCatching {
            val started = System.currentTimeMillis()
            val vocab = context.assets.open("embeddings/vocab.txt").bufferedReader().readLines()
            tokenizer = WordPieceTokenizer(vocab)
            val bytes = context.assets.open("embeddings/model_quantized.onnx").readBytes()
            val e = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            val s = e.createSession(bytes, opts)
            env = e; session = s; inputNames = s.inputNames
            Log.i(TAG, "embedding model ready in ${System.currentTimeMillis() - started} ms (inputs=$inputNames)")
            true
        }.getOrElse { t ->
            Log.w(TAG, "embedding model unavailable: ${t.message}")
            available = false
            false
        }
    }

    /** Returns a unit vector, or null if the model could not be loaded. */
    @Synchronized
    fun embed(text: String): FloatArray? {
        if (!ensureLoaded()) return null
        val s = session ?: return null
        val tok = tokenizer ?: return null
        val ids = tok.encode(text)
        val n = ids.size
        val shape = longArrayOf(1, n.toLong())
        val env = this.env ?: return null
        val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { ids[it].toLong() }), shape)
        val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 1L }), shape)
        val types = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 0L }), shape)
        return try {
            val feed = HashMap<String, OnnxTensor>()
            feed["input_ids"] = inputIds
            feed["attention_mask"] = mask
            if ("token_type_ids" in inputNames) feed["token_type_ids"] = types
            s.run(feed).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = result[0].value as Array<Array<FloatArray>>   // [1][seq][384]
                val out = FloatArray(dim)
                for (t in 0 until n) { val row = hidden[0][t]; for (d in 0 until dim) out[d] += row[d] }
                var norm = 0f
                for (d in 0 until dim) { out[d] /= n; norm += out[d] * out[d] }
                val inv = 1f / (sqrt(norm).coerceAtLeast(1e-9f))
                for (d in 0 until dim) out[d] *= inv
                out
            }
        } catch (t: Throwable) {
            Log.w(TAG, "embedding failed: ${t.message}")
            null
        } finally {
            inputIds.close(); mask.close(); types.close()
        }
    }

    companion object {
        private const val TAG = "Embedder"
        fun cosine(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            val n = minOf(a.size, b.size)
            for (i in 0 until n) s += a[i] * b[i]
            return s
        }
    }
}
