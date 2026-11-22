package com.saarathi.app.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saarathi.app.AppContainer
import com.saarathi.app.data.model.ImportProgress
import com.saarathi.app.data.model.ModelFile
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.llm.ModelState
import com.saarathi.app.ui.components.SaarathiTopBar
import com.saarathi.app.ui.components.SectionCard
import com.saarathi.app.ui.components.SectionTitle
import com.saarathi.app.ui.components.StatusRow
import com.saarathi.app.ui.navigation.AppViewModel
import com.saarathi.app.ui.navigation.factoryOf
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    val models = MutableStateFlow(container.models.list())
    val importProgress = MutableStateFlow<ImportProgress?>(null)
    val scriptureStatus = MutableStateFlow<ScriptureRepository.Status?>(null)
    val message = MutableStateFlow<String?>(null)
    val report = MutableStateFlow(container.models.deviceReport())

    init { refreshScripture() }

    fun refreshScripture() = viewModelScope.launch {
        scriptureStatus.value = runCatching { container.scripture.status() }.getOrNull()
        report.value = container.models.deviceReport()
    }

    fun importModel(uri: Uri) = viewModelScope.launch {
        container.models.import(uri).collect { p ->
            importProgress.value = p
            if (p is ImportProgress.Done) { models.value = container.models.list(); select(p.model) }
        }
    }

    fun select(model: ModelFile) = viewModelScope.launch { container.settings.update { it.copy(modelFileName = model.name) } }

    fun deleteModel(model: ModelFile) = viewModelScope.launch {
        val s = container.engine.state.value
        if (s is ModelState.Ready && s.info.fileName == model.name) container.engine.unload()
        container.models.delete(model.name)
        models.value = container.models.list()
        container.settings.update { if (it.modelFileName == model.name) it.copy(modelFileName = "") else it }
    }

    fun fitWarning(model: ModelFile, ctx: Int): String? = container.models.fitWarning(model, ctx)

    fun linkPdf(kind: String, uri: Uri) = viewModelScope.launch {
        runCatching {
            container.appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        container.settings.update { if (kind == "gita") it.copy(gitaPdfUri = uri.toString()) else it.copy(mahabharataPdfUri = uri.toString()) }
    }

    fun importPack(uri: Uri) = viewModelScope.launch {
        runCatching {
            val json = withContext(Dispatchers.IO) {
                container.appContext.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            container.scripture.importEditionPack(json)
        }.onSuccess { r ->
            message.value = "Imported ${r.imported} verse translations from your edition (${r.skipped} skipped)."
            container.settings.update { it.copy(editionPackImported = true) }
            refreshScripture()
        }.onFailure { message.value = "Could not import that pack: ${it.message}" }
    }
}

@Composable
fun SetupScreen(container: AppContainer, appVm: AppViewModel, onContinue: () -> Unit, onBack: () -> Unit) {
    val vm: SetupViewModel = viewModel(factory = factoryOf { SetupViewModel(container) })
    val c = SaarathiTheme.colors
    val settings by appVm.settings.collectAsState()
    val modelState by appVm.modelState.collectAsState()
    val models by vm.models.collectAsState()
    val progress by vm.importProgress.collectAsState()
    val status by vm.scriptureStatus.collectAsState()
    val message by vm.message.collectAsState()
    val report by vm.report.collectAsState()
    val context = LocalContext.current

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importModel(it) } }
    val gitaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.linkPdf("gita", it) } }
    val mbPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.linkPdf("mahabharata", it) } }
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importPack(it) } }

    Scaffold(containerColor = c.background, topBar = { SaarathiTopBar("Offline setup", onBack = if (settings.modelFileName.isBlank()) null else onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            SectionCard {
                SectionTitle("1. Language model")
                Text("Saarathi needs a GGUF model file on this phone. Copy one over USB or download it on another device, then import it here. Recommended: gemma-3-1b-it-Q4_K_M.gguf (0.8 GB) for phones with 6–8 GB RAM or mid-range chips; gemma-3-4b-it-Q4_K_M.gguf (2.5 GB) for 12 GB flagships. The 4B model is noticeably wiser but several times slower.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                if (models.isEmpty()) Text("No model imported yet.", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                for (m in models) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.modelFileName == m.name, onClick = { vm.select(m) })
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                            Text("%.2f GB".format(m.sizeGb), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            vm.fitWarning(m, settings.contextSize)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.danger) }
                        }
                        TextButton(onClick = { vm.deleteModel(m) }) { Text("Delete", color = c.textSecondary) }
                    }
                }
                when (val p = progress) {
                    is ImportProgress.Copying -> {
                        Spacer(Modifier.height(6.dp))
                        if (p.total > 0) LinearProgressIndicator(progress = { (p.copied.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = c.accent)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent)
                        Text("Copying… %.0f MB".format(p.copied / 1e6), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    is ImportProgress.Failed -> Text(p.reason, style = MaterialTheme.typography.bodySmall, color = c.danger)
                    else -> {}
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) { Text("Import .gguf") }
                    Button(onClick = { appVm.loadSelectedModel() }, enabled = settings.modelFileName.isNotBlank() && modelState !is ModelState.Loading) { Text("Load model") }
                }
                Spacer(Modifier.height(8.dp))
                val stateText = when (val s = modelState) {
                    is ModelState.Unloaded -> "Not loaded"
                    is ModelState.Loading -> "Loading ${s.fileName}… this can take a minute"
                    is ModelState.Ready -> "Ready: ${s.info.description.ifBlank { s.info.fileName }} · ctx ${s.info.ctx} · ${s.info.threads} threads"
                    is ModelState.Error -> "Error: ${s.message}"
                }
                StatusRow("Model", stateText, ok = modelState is ModelState.Ready)
            }

            SectionCard {
                SectionTitle("2. Scripture library")
                Text("The Bhagavad Gita (700 verses with Sanskrit) and the complete Ganguli Mahabharata are built into the app and indexed for offline search.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                StatusRow("Gita verse records", status?.verses?.toString() ?: "…", ok = (status?.verses ?: 0) > 600)
                StatusRow("Mahabharata passages", status?.passages?.toString() ?: "…", ok = (status?.passages ?: 0) > 1000)
                StatusRow("Your edition's translations", status?.editionVerses?.toString() ?: "…", ok = (status?.editionVerses ?: 0) > 0)
                Spacer(Modifier.height(8.dp))
                Text("Optional: link your own PDFs so verse cards can open the original page, and import the edition pack generated from your Bhagavad-gītā As It Is PDF (see docs/SETUP.md).", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                StatusRow("Gita PDF linked", if (settings.gitaPdfUri.isBlank()) "No" else "Yes", ok = settings.gitaPdfUri.isNotBlank())
                StatusRow("Mahabharata PDF linked", if (settings.mahabharataPdfUri.isBlank()) "No" else "Yes", ok = settings.mahabharataPdfUri.isNotBlank())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { gitaPicker.launch(arrayOf("application/pdf")) }) { Text("Link Gita PDF") }
                    OutlinedButton(onClick = { mbPicker.launch(arrayOf("application/pdf")) }) { Text("Link Mahabharata PDF") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { packPicker.launch(arrayOf("application/json", "*/*")) }) { Text("Import edition pack (.json)") }
                message?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = c.textPrimary) }
            }

            SectionCard {
                SectionTitle("3. This phone")
                StatusRow("Device", report.device)
                StatusRow("Android", report.androidVersion)
                StatusRow("RAM total / free", "%.1f / %.1f GB".format(report.totalRamBytes / 1e9, report.availableRamBytes / 1e9), ok = report.totalRamBytes > 5.5e9)
                StatusRow("Free storage", "%.1f GB".format(report.freeStorageBytes / 1e9), ok = report.freeStorageBytes > 3e9)
                StatusRow("CPU", "${report.cores} cores · ${report.abi}", ok = report.supportsDotProd)
                if (!report.supportsDotProd) Text("This CPU lacks the ARM dot-product instructions the model engine is built with. The app will not be able to run a model here.", style = MaterialTheme.typography.bodySmall, color = c.danger)
            }

            Button(onClick = onContinue, enabled = modelState is ModelState.Ready, modifier = Modifier.fillMaxWidth()) { Text("Start talking to Krishna") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
