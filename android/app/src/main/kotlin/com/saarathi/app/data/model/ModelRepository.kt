package com.saarathi.app.data.model

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class ModelFile(val file: File) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val sizeGb: Double get() = sizeBytes / 1e9
}

data class DeviceReport(
    val totalRamBytes: Long, val availableRamBytes: Long, val freeStorageBytes: Long, val cores: Int,
    val abi: String, val supportsDotProd: Boolean, val androidVersion: String, val device: String,
)

sealed class ImportProgress {
    data class Copying(val copied: Long, val total: Long) : ImportProgress()
    data class Done(val model: ModelFile) : ImportProgress()
    data class Failed(val reason: String) : ImportProgress()
}

/** Manages GGUF model files in app-private storage. Models are only ever imported from local files. */
class ModelRepository(private val context: Context) {
    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<ModelFile> = modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
        ?.map { ModelFile(it) }?.sortedBy { it.name } ?: emptyList()

    fun find(name: String): ModelFile? = list().firstOrNull { it.name == name }

    fun delete(name: String): Boolean = find(name)?.file?.delete() ?: false

    fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
    }

    fun sizeOf(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return -1
    }

    /** Copies a GGUF picked through the Storage Access Framework into the models folder. */
    fun import(uri: Uri): Flow<ImportProgress> = flow {
        val name = displayName(uri).let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
        val total = sizeOf(uri)
        val report = deviceReport()
        if (total > 0 && report.freeStorageBytes < total + 200L * 1024 * 1024) {
            emit(ImportProgress.Failed("Not enough free storage: the model needs ${"%.1f".format(total / 1e9)} GB and only ${"%.1f".format(report.freeStorageBytes / 1e9)} GB is free."))
            return@flow
        }
        val target = File(modelsDir, name)
        val tmp = File(modelsDir, "$name.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); copied += n
                        if (copied - lastEmit > 8L * 1024 * 1024) { emit(ImportProgress.Copying(copied, total)); lastEmit = copied }
                    }
                }
            } ?: run { emit(ImportProgress.Failed("Could not open the selected file.")); return@flow }
            if (!isGguf(tmp)) { tmp.delete(); emit(ImportProgress.Failed("That file is not a GGUF model (missing GGUF header).")); return@flow }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) { emit(ImportProgress.Failed("Could not move the model into app storage.")); return@flow }
            emit(ImportProgress.Done(ModelFile(target)))
        } catch (e: Exception) {
            tmp.delete()
            emit(ImportProgress.Failed(e.message ?: "import failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun isGguf(f: File): Boolean = runCatching {
        f.inputStream().use { val b = ByteArray(4); it.read(b); String(b, Charsets.US_ASCII) == "GGUF" }
    }.getOrDefault(false)

    fun deviceReport(): DeviceReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(context.filesDir.absolutePath)
        return DeviceReport(
            totalRamBytes = mi.totalMem, availableRamBytes = mi.availMem, freeStorageBytes = stat.availableBytes,
            cores = Runtime.getRuntime().availableProcessors(), abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportsDotProd = CpuFeatures.supportsRequiredInstructions(), androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    /**
     * Thread default = number of performance cores. ggml splits work evenly and waits for the slowest
     * thread, so one thread on an efficiency core drags the whole generation down to that core's speed.
     */
    fun recommendedThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until cores).mapNotNull { i ->
            runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        val max = freqs.maxOrNull() ?: return minOf(4, maxOf(2, cores / 2))
        val big = freqs.count { it >= max * 0.85 }
        return big.coerceIn(2, 6)
    }

    /** Human-readable warning when a model is unlikely to fit, or null if it looks fine. */
    fun fitWarning(model: ModelFile, contextSize: Int): String? {
        val r = deviceReport()
        val needed = (model.sizeBytes * 1.15).toLong() + contextSize.toLong() * 256L * 1024 // rough KV estimate
        val totalGb = r.totalRamBytes / 1e9
        return when {
            !r.supportsDotProd && r.abi == "arm64-v8a" -> "This phone's CPU lacks the ARM dot-product instructions this build needs. The model cannot run here."
            needed > r.totalRamBytes * 0.75 -> "This model needs about ${"%.1f".format(needed / 1e9)} GB of RAM but the phone has ${"%.1f".format(totalGb)} GB in total. It will likely crash; use a smaller model."
            needed > r.availableRamBytes -> "Only ${"%.1f".format(r.availableRamBytes / 1e9)} GB of RAM is free right now. Close other apps before loading."
            else -> null
        }
    }
}

object CpuFeatures {
    /** The arm64 native library is compiled with dotprod+fp16; refuse to run where the CPU lacks them. */
    fun supportsRequiredInstructions(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        if (abi.startsWith("x86")) return true
        if (abi != "arm64-v8a") return false
        return runCatching {
            File("/proc/cpuinfo").readText().lineSequence()
                .filter { it.startsWith("Features") }
                .any { it.contains("asimddp") }
        }.getOrDefault(true) // if we cannot read cpuinfo, assume a modern SoC
    }
}
