package com.voicenotes.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Note
import com.voicenotes.app.speech.DictationController
import com.voicenotes.app.speech.rememberDictation
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.ScreenGradient
import com.voicenotes.app.ui.components.formatDate
import com.voicenotes.app.ui.components.appFilterChipColors
import com.voicenotes.app.ui.components.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
  noteId: String,
  viewModel: NotesViewModel,
  onBack: () -> Unit,
) {
  val notes by viewModel.allNotes.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val tags by viewModel.tags.collectAsStateWithLifecycle()
  val note: Note? = notes.firstOrNull { it.id == noteId }

  if (note == null) {
    LaunchedEffect(noteId) { onBack() }
    return
  }

  var title by remember(noteId) { mutableStateOf(note.title) }
  var body by remember(noteId) { mutableStateOf(note.body) }
  var showDelete by remember { mutableStateOf(false) }
  var showFolderMenu by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }

  val dictation = rememberDictation(
    onText = { spoken -> viewModel.appendTranscript(noteId, spoken) },
    onError = { message = DictationController.NO_TRANSCRIPTION },
  )

  // Autosave with a small debounce keeps the list in sync (S6).
  LaunchedEffect(title, body) {
    delay(500)
    if (title != note.title || body != note.body) {
      viewModel.updateNote(noteId, title, body)
    }
  }

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        title = { Text("Nota") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = {
            viewModel.updateNote(noteId, title, body)
            onBack()
          }) {
            Icon(Icons.Filled.Check, contentDescription = "Guardar")
          }
          IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
  ) { padding ->
    ScreenGradient()
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      Text(formatDate(note.createdAt), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

      TextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Título") },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
          color = MaterialTheme.colorScheme.onSurface,
        ),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedTextColor = MaterialTheme.colorScheme.onSurface,
          unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
          cursorColor = MaterialTheme.colorScheme.primary,
          focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      )

      if (note.audioPath != null) {
        Spacer(Modifier.height(8.dp))
        AudioPlayerCard(path = note.audioPath, fallbackDuration = note.durationMs)
      }

      Spacer(Modifier.height(8.dp))
      TextField(
        value = body,
        onValueChange = { body = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Escribí algo…") },
        minLines = 3,
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedTextColor = MaterialTheme.colorScheme.onSurface,
          unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
          cursorColor = MaterialTheme.colorScheme.primary,
          focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      )

      Spacer(Modifier.height(16.dp))
      TranscriptCard(
        transcript = note.transcript,
        listening = dictation.listening,
        onDictate = {
          if (dictation.listening) dictation.stop() else dictation.start()
        },
      )

      Spacer(Modifier.height(16.dp))
      Text("Carpeta", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Box {
        AssistChip(
          onClick = { showFolderMenu = true },
          label = {
            Text(folders.firstOrNull { it.id == note.folderId }?.name ?: "Sin carpeta")
          },
          leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
        )
        DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
          DropdownMenuItem(
            text = { Text("Sin carpeta") },
            onClick = {
              viewModel.assignFolder(noteId, null)
              showFolderMenu = false
            },
          )
          folders.forEach { folder ->
            DropdownMenuItem(
              text = { Text(folder.name) },
              onClick = {
                viewModel.assignFolder(noteId, folder.id)
                showFolderMenu = false
              },
            )
          }
        }
      }

      if (tags.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Etiquetas", style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(tags, key = { it.id }) { tag ->
            FilterChip(
              selected = note.tagIds.contains(tag.id),
              onClick = { viewModel.toggleTag(noteId, tag.id) },
              label = { Text("#${tag.name}") },
              colors = appFilterChipColors(),
            )
          }
        }
      }

      Spacer(Modifier.height(48.dp))
    }
  }

  if (showDelete) {
    AlertDialog(
      onDismissRequest = { showDelete = false },
      title = { Text("¿Eliminar la nota?") },
      text = { Text("Esta acción no se puede deshacer.") },
      confirmButton = {
        TextButton(onClick = {
          showDelete = false
          viewModel.deleteNote(noteId)
          onBack()
        }) { Text("Eliminar") }
      },
      dismissButton = {
        TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
      },
    )
  }

  message?.let { text ->
    AlertDialog(
      onDismissRequest = { message = null },
      confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } },
      text = { Text(text) },
    )
  }
}

@Composable
private fun TranscriptCard(
  transcript: String,
  listening: Boolean,
  onDictate: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Transcripción",
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = listening,
          onClick = onDictate,
          label = { Text(if (listening) "Escuchando…" else "Dictar") },
          leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
          colors = appFilterChipColors(),
        )
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = transcript.ifBlank { "Todavía no hay transcripción." },
        style = MaterialTheme.typography.bodyMedium,
        color = if (transcript.isBlank()) {
          MaterialTheme.colorScheme.onSurfaceVariant
        } else {
          MaterialTheme.colorScheme.onSurface
        },
      )
    }
  }
}

@Composable
private fun AudioPlayerCard(path: String, fallbackDuration: Long) {
  var playing by remember { mutableStateOf(false) }
  var position by remember { mutableFloatStateOf(0f) }
  var duration by remember { mutableFloatStateOf(0f) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(path) {
    val created = MediaPlayer().apply {
      runCatching {
        setDataSource(path)
        prepare()
        duration = this.duration.toFloat()
      }
      setOnCompletionListener {
        playing = false
        position = 0f
        runCatching { seekTo(0) }
      }
    }
    player = created
    onDispose {
      runCatching { created.release() }
      player = null
    }
  }

  LaunchedEffect(playing) {
    while (playing) {
      val current = player
      if (current != null) {
        runCatching { position = current.currentPosition.toFloat() }
      }
      delay(200)
    }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = {
        val current = player ?: return@IconButton
        if (playing) {
          runCatching { current.pause() }
          playing = false
        } else {
          runCatching { current.start() }
          playing = true
        }
      }) {
        Icon(
          imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playing) "Pausar" else "Reproducir",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      }
      Column(Modifier.weight(1f)) {
        Slider(
          value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
          onValueChange = { value ->
            position = value
            runCatching { player?.seekTo(value.toInt()) }
          },
          valueRange = 0f..duration.coerceAtLeast(1f),
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
          ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelSmall)
          Text(
            formatDuration(if (duration > 0f) duration.toLong() else fallbackDuration),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
      Spacer(Modifier.width(4.dp))
    }
  }
}
