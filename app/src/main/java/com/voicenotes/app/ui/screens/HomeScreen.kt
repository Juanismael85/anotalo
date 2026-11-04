package com.voicenotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Folder
import com.voicenotes.app.data.Note
import com.voicenotes.app.ui.components.ActivityDraft
import com.voicenotes.app.ui.components.ScheduleSuggestionsDialog
import com.voicenotes.app.data.Tag
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.EmptyState
import com.voicenotes.app.ui.components.ScreenGradient
import com.voicenotes.app.ui.components.appFilterChipColors
import com.voicenotes.app.ui.components.formatDate
import com.voicenotes.app.ui.components.formatDuration
import com.voicenotes.app.ui.theme.ThemeMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: NotesViewModel,
  onOpenNote: (String) -> Unit,
  onRecord: () -> Unit,
  onNewTextNote: () -> Unit,
  onOpenLibrary: () -> Unit,
  onOpenRecorder: () -> Unit,
  bottomBar: @Composable () -> Unit = {},
) {
  val notes by viewModel.notes.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val tags by viewModel.tags.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()
  val folderFilter by viewModel.folderFilter.collectAsStateWithLifecycle()
  val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
  val total by viewModel.totalNotes.collectAsStateWithLifecycle()
  val recording by viewModel.recording.collectAsStateWithLifecycle()
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  var themeMenuOpen by remember { mutableStateOf(false) }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
  val aiPending by viewModel.aiPendingCount.collectAsStateWithLifecycle()
  val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()
  val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
  val aiError by viewModel.aiError.collectAsStateWithLifecycle()
  val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
  val backupError by viewModel.backupError.collectAsStateWithLifecycle()
  var backupMessage by remember { mutableStateOf<String?>(null) }
  var doneFilter by remember { mutableStateOf(DoneFilter.ALL) }
  var scheduling by remember { mutableStateOf<List<ActivityDraft>?>(null) }
  var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
  var batchConfirm by remember { mutableStateOf(false) }

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
  val restoreLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> if (uri != null) pendingRestore = uri }

  BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
  }

  LaunchedEffect(aiStatus, aiError) {
    if (aiStatus != null || aiError != null) {
      delay(8_000)
      viewModel.clearAiFeedback()
    }
  }
  LaunchedEffect(backupStatus) {
    backupStatus?.let {
      backupMessage = it
      viewModel.clearBackupFeedback()
    }
  }
  LaunchedEffect(backupError) {
    backupError?.let {
      backupMessage = it
      viewModel.clearBackupFeedback()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        SettingsDrawerContent(
          viewModel = viewModel,
          onExportBackup = {
            scope.launch { drawerState.close() }
            exportLauncher.launch("voicenotes-${System.currentTimeMillis()}.zip")
          },
          onRestoreBackup = {
            scope.launch { drawerState.close() }
            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
          },
        )
      }
    },
  ) {
  Scaffold(
    containerColor = Color.Transparent,
    bottomBar = bottomBar,
    topBar = {
      LargeTopAppBar(
        navigationIcon = {
          IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
          }
        },
        title = {
          Column {
            Text("Notas", style = MaterialTheme.typography.headlineMedium)
            Text(
              if (total == 1) "1 nota" else "$total notas",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          BadgedBox(
            badge = {
              if (aiPending > 0) Badge { Text("$aiPending") }
            },
          ) {
            IconButton(
              onClick = { batchConfirm = true },
              enabled = aiConfigured && aiPending > 0 && batchProgress == null,
            ) {
              Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = "Aplicar IA a las notas que faltan",
              )
            }
          }
          Box {
            IconButton(onClick = { themeMenuOpen = true }) {
              Icon(Icons.Filled.Palette, contentDescription = "Cambiar tema")
            }
            DropdownMenu(
              expanded = themeMenuOpen,
              onDismissRequest = { themeMenuOpen = false },
            ) {
              ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                  text = { Text(mode.label) },
                  leadingIcon = {
                    if (mode == themeMode) {
                      Icon(Icons.Filled.Check, contentDescription = null)
                    }
                  },
                  onClick = {
                    viewModel.setThemeMode(mode)
                    themeMenuOpen = false
                  },
                )
              }
            }
          }
          IconButton(onClick = onOpenLibrary) {
            Icon(Icons.Filled.Folder, contentDescription = "Carpetas y etiquetas")
          }
          IconButton(onClick = onNewTextNote) {
            Icon(Icons.Filled.Add, contentDescription = "Nueva nota escrita")
          }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onRecord,
        icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
        text = { Text("Grabar") },
      )
    },
  ) { padding ->
    Box(Modifier.fillMaxSize()) {
      ScreenGradient()
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (recording) {
          Surface(
            onClick = onOpenRecorder,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Filled.Mic, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(10.dp))
              Text(
                "Grabando en segundo plano · tocar para volver",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
        SearchField(query = query, onQueryChange = viewModel::setQuery)
        viewModel.loadWarning?.let { path ->
          Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          ) {
            Text(
              "No se pudo leer la biblioteca guardada. Dejé una copia intacta en " +
                path.substringAfterLast('/') + " — no la borres si querés recuperarla.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
        }
        batchProgress?.let { progress ->
          BatchRow(
            text = "Aplicando IA ${progress.done + 1} de ${progress.total} · ${progress.currentTitle}",
            onCancel = viewModel::cancelAiBatch,
          )
        } ?: aiStatus?.let { message ->
          BatchRow(text = message, onCancel = null)
        } ?: aiError?.let { message ->
          BatchRow(text = message, onCancel = null, isError = true)
        }
        FilterRow(
          folders = folders,
          tags = tags,
          folderFilter = folderFilter,
          tagFilter = tagFilter,
          onFolderFilter = viewModel::setFolderFilter,
          onTagFilter = viewModel::setTagFilter,
        )
        val doneCount = notes.count { it.done }
        if (doneCount > 0 || doneFilter != DoneFilter.ALL) {
          DoneFilterRow(
            selected = doneFilter,
            doneCount = doneCount,
            onSelect = { doneFilter = it },
          )
        }
        val visibleNotes = when (doneFilter) {
          DoneFilter.ALL -> notes
          DoneFilter.PENDING -> notes.filterNot { it.done }
          DoneFilter.DONE -> notes.filter { it.done }
        }
        if (visibleNotes.isEmpty()) {
          EmptyState(
            icon = Icons.Filled.GraphicEq,
            title = if (query.isBlank() && folderFilter == null && tagFilter == null) {
              "Todavía no hay notas"
            } else {
              "No hay notas que coincidan."
            },
            subtitle = "Tocá Grabar para crear tu primera nota de voz.",
          )
        } else {
          LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(visibleNotes, key = { it.id }) { note ->
              NoteCard(
                note = note,
                title = viewModel.displayTitle(note),
                folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                onClick = { onOpenNote(note.id) },
                onToggleDone = { done -> viewModel.setNoteDone(note.id, done) },
                onSchedule = {
                  val drafts = viewModel.suggestActivities(note.id)
                  if (drafts.isNotEmpty()) scheduling = drafts
                },
              )
            }
          }
        }
      }
    }
  }
  }

  if (batchConfirm) {
    AlertDialog(
      onDismissRequest = { batchConfirm = false },
      title = { Text("Aplicar IA a $aiPending nota${if (aiPending == 1) "" else "s"}") },
      text = {
        Text(
          "Se procesan solo las notas que todavía no tienen IA. " +
            "Las que ya la tienen no se tocan.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            batchConfirm = false
            viewModel.runAiBatch()
          },
        ) { Text("Aplicar") }
      },
      dismissButton = {
        TextButton(onClick = { batchConfirm = false }) { Text("Cancelar") }
      },
    )
  }

  scheduling?.let { drafts ->
    ScheduleSuggestionsDialog(
      drafts = drafts,
      onDismiss = { scheduling = null },
      onSave = { chosen ->
        viewModel.saveActivities(chosen)
        scheduling = null
      },
    )
  }

  pendingRestore?.let { uri ->
    AlertDialog(
      onDismissRequest = { pendingRestore = null },
      title = { Text("¿Restaurar el respaldo?") },
      text = {
        Text(
          "Se reemplazan todas las notas actuales por las del archivo. " +
            "Antes se guarda una copia del estado actual en notes.json.bak.",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          viewModel.restoreBackup(uri)
          pendingRestore = null
        }) { Text("Restaurar") }
      },
      dismissButton = {
        TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
      },
    )
  }

  backupMessage?.let { text ->
    AlertDialog(
      onDismissRequest = { backupMessage = null },
      confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("Entendido") } },
      text = { Text(text) },
    )
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  val keyboard = LocalSoftwareKeyboardController.current
  TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    placeholder = { Text("Buscar en títulos, notas y transcripciones") },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
        }
      }
    },
    singleLine = true,
    shape = RoundedCornerShape(18.dp),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
      focusedTextColor = MaterialTheme.colorScheme.onSurface,
      unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
      cursorColor = MaterialTheme.colorScheme.primary,
      focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
      unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
  )
}

@Composable
private fun FilterRow(
  folders: List<Folder>,
  tags: List<Tag>,
  folderFilter: String?,
  tagFilter: String?,
  onFolderFilter: (String?) -> Unit,
  onTagFilter: (String?) -> Unit,
) {
  if (folders.isEmpty() && tags.isEmpty()) return
  Column {
    if (folders.isNotEmpty()) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          FilterChip(
            selected = folderFilter == null,
            onClick = { onFolderFilter(null) },
            label = { Text("Todas") },
            colors = appFilterChipColors(),
          )
        }
        items(folders, key = { it.id }) { folder ->
          FilterChip(
            selected = folderFilter == folder.id,
            onClick = { onFolderFilter(if (folderFilter == folder.id) null else folder.id) },
            label = { Text(folder.name) },
            colors = appFilterChipColors(),
          )
        }
      }
    }
    if (tags.isNotEmpty()) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(tags, key = { it.id }) { tag ->
          FilterChip(
            selected = tagFilter == tag.id,
            onClick = { onTagFilter(if (tagFilter == tag.id) null else tag.id) },
            label = { Text("#${tag.name}") },
            colors = appFilterChipColors(),
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(
  note: Note,
  title: String,
  folderName: String?,
  onClick: () -> Unit,
  onToggleDone: (Boolean) -> Unit,
  onSchedule: () -> Unit,
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
      // An alpha-modified container is not in the scheme, so contentColorFor() would
      // fall back to black; set it explicitly so titles stay readable.
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .background(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            RoundedCornerShape(14.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (note.audioPath != null) Icons.Filled.GraphicEq else Icons.Filled.Add,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(
            textDecoration = if (note.done) {
              androidx.compose.ui.text.style.TextDecoration.LineThrough
            } else {
              androidx.compose.ui.text.style.TextDecoration.None
            },
          ),
          color = if (note.done) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.onSurface
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val preview = note.transcript.ifBlank { note.body }
        if (preview.isNotBlank()) {
          Text(
            text = preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        val meta = buildList {
          if (note.durationMs > 0) add(formatDuration(note.durationMs))
          folderName?.let { add(it) }
          if (note.tagIds.isNotEmpty()) add("${note.tagIds.size} etiqueta(s)")
          formatDate(note.createdAt).takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("  ·  ")
        if (meta.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(onClick = onSchedule) {
        Icon(
          imageVector = Icons.Filled.Event,
          contentDescription = "Agendar esta nota",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = { onToggleDone(!note.done) }) {
        Icon(
          imageVector = if (note.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          contentDescription = if (note.done) "Marcar como pendiente" else "Marcar como lista",
          tint = if (note.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** S8/S9 — which notes the library shows: all of them, only the pending ones or only the finished. */
private enum class DoneFilter { ALL, PENDING, DONE }

@Composable
private fun DoneFilterRow(
  selected: DoneFilter,
  doneCount: Int,
  onSelect: (DoneFilter) -> Unit,
) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(
      listOf(
        DoneFilter.ALL to "Todas",
        DoneFilter.PENDING to "Pendientes",
        DoneFilter.DONE to "Listas ($doneCount)",
      ),
    ) { (value, label) ->
      FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = appFilterChipColors(),
      )
    }
  }
}

private fun Modifier.noop(): Modifier = this

/** The line that shows the batch while it runs, or what the last run left behind. */
@Composable
private fun BatchRow(text: String, onCancel: (() -> Unit)?, isError: Boolean = false) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
  ) {
    if (onCancel != null) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      Spacer(Modifier.width(10.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    onCancel?.let { cancel ->
      TextButton(onClick = cancel) { Text("Cancelar") }
    }
  }
}
