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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Folder
import com.voicenotes.app.data.Note
import com.voicenotes.app.data.Tag
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.EmptyState
import com.voicenotes.app.ui.components.ScreenGradient
import com.voicenotes.app.ui.components.formatDate
import com.voicenotes.app.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: NotesViewModel,
  onOpenNote: (String) -> Unit,
  onRecord: () -> Unit,
  onNewTextNote: () -> Unit,
  onOpenLibrary: () -> Unit,
) {
  val notes by viewModel.notes.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val tags by viewModel.tags.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()
  val folderFilter by viewModel.folderFilter.collectAsStateWithLifecycle()
  val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
  val total by viewModel.totalNotes.collectAsStateWithLifecycle()

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      LargeTopAppBar(
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
        SearchField(query = query, onQueryChange = viewModel::setQuery)
        FilterRow(
          folders = folders,
          tags = tags,
          folderFilter = folderFilter,
          tagFilter = tagFilter,
          onFolderFilter = viewModel::setFolderFilter,
          onTagFilter = viewModel::setTagFilter,
        )
        if (notes.isEmpty()) {
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
            items(notes, key = { it.id }) { note ->
              NoteCard(
                note = note,
                title = viewModel.displayTitle(note),
                folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                onClick = { onOpenNote(note.id) },
              )
            }
          }
        }
      }
    }
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
          )
        }
        items(folders, key = { it.id }) { folder ->
          FilterChip(
            selected = folderFilter == folder.id,
            onClick = { onFolderFilter(if (folderFilter == folder.id) null else folder.id) },
            label = { Text(folder.name) },
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
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
          style = MaterialTheme.typography.titleMedium,
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
    }
  }
}

private fun Modifier.noop(): Modifier = this
