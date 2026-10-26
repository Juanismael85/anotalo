package com.voicenotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.theme.ThemeMode

/** S18 — lateral settings drawer: theme, AI credentials, backup and version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
  viewModel: NotesViewModel,
  onExportBackup: () -> Unit,
  onRestoreBackup: () -> Unit,
) {
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
  val model by viewModel.aiModel.collectAsStateWithLifecycle()
  val running by viewModel.aiRunning.collectAsStateWithLifecycle()
  val status by viewModel.aiStatus.collectAsStateWithLifecycle()
  val error by viewModel.aiError.collectAsStateWithLifecycle()
  var showKey by remember { mutableStateOf(false) }
  var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }
  val models by viewModel.aiModels.collectAsStateWithLifecycle()
  val loadingModels by viewModel.modelsLoading.collectAsStateWithLifecycle()
  val modelsError by viewModel.modelsError.collectAsStateWithLifecycle()
  var modelMenuOpen by remember { mutableStateOf(false) }

  // The list depends on the key, so it is fetched when the drawer opens instead of on every keystroke.
  LaunchedEffect(apiKey) { viewModel.loadAiModels() }

  val context = LocalContext.current
  val version = remember(context) {
    runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 28.dp),
  ) {
    Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
      "VoiceNotes $version",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SectionTitle("Tema")
    ThemeMode.entries.forEach { mode ->
      NavigationDrawerItem(
        label = { Text(mode.label) },
        selected = mode == themeMode,
        icon = {
          if (mode == themeMode) Icon(Icons.Filled.Check, contentDescription = null)
        },
        onClick = { viewModel.setThemeMode(mode) },
        modifier = Modifier.padding(vertical = 2.dp),
      )
    }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    SectionTitle("Inteligencia artificial")
    Text(
      "Pegá tu clave de API de Gemini. Se guarda solo en este teléfono y se usa para transcribir, " +
        "corregir, resumir y etiquetar tus notas.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = keyDraft,
      onValueChange = {
        keyDraft = it
        viewModel.setApiKey(it)
        viewModel.clearAiFeedback()
      },
      label = { Text("Clave de API") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { showKey = !showKey }) {
          Icon(
            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (showKey) "Ocultar clave" else "Mostrar clave",
          )
        }
      },
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      ExposedDropdownMenuBox(
        expanded = modelMenuOpen,
        onExpandedChange = { modelMenuOpen = it },
        modifier = Modifier.weight(1f),
      ) {
        OutlinedTextField(
          value = model,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          label = { Text("Modelo") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
          modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = modelMenuOpen,
          onDismissRequest = { modelMenuOpen = false },
        ) {
          if (models.isEmpty()) {
            DropdownMenuItem(
              text = {
                Text(if (loadingModels) "Buscando modelos…" else "Tocá ↻ para traer la lista")
              },
              onClick = { viewModel.loadAiModels() },
            )
          }
          models.forEach { name ->
            DropdownMenuItem(
              text = { Text(name) },
              onClick = {
                viewModel.setAiModel(name)
                viewModel.clearAiFeedback()
                modelMenuOpen = false
              },
            )
          }
        }
      }
      IconButton(onClick = { viewModel.loadAiModels() }, enabled = !loadingModels) {
        if (loadingModels) {
          CircularProgressIndicator(
            modifier = Modifier.height(18.dp).width(18.dp),
            strokeWidth = 2.dp,
          )
        } else {
          Icon(Icons.Filled.Refresh, contentDescription = "Actualizar modelos")
        }
      }
    }
    Text(
      modelsError ?: "Modelos disponibles para tu clave. Por defecto: ${NotesViewModel.DEFAULT_MODEL}",
      style = MaterialTheme.typography.bodySmall,
      color = if (modelsError != null) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(
        onClick = { viewModel.checkAiConnection() },
        enabled = !running,
      ) {
        if (running) {
          CircularProgressIndicator(
            modifier = Modifier.height(18.dp).width(18.dp),
            strokeWidth = 2.dp,
          )
          Spacer(Modifier.width(10.dp))
        }
        Text("Probar conexión")
      }
    }
    status?.let {
      Spacer(Modifier.height(8.dp))
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    error?.let {
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
    }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    SectionTitle("Respaldo")
    Text(
      "El respaldo es un archivo .zip con tus notas y sus audios. Restaurar reemplaza la " +
        "biblioteca actual y guarda una copia del estado anterior.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
      Text("Exportar respaldo")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) {
      Text("Restaurar respaldo")
    }

    Spacer(Modifier.height(28.dp))
    Text(
      "Hecho para tus notas de voz.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
  )
}
