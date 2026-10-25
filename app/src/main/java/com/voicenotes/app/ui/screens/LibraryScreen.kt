package com.voicenotes.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.EmptyState
import com.voicenotes.app.ui.components.ScreenGradient

/** Folders and tags management (S7, S8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: NotesViewModel, onBack: () -> Unit) {
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val tags by viewModel.tags.collectAsStateWithLifecycle()
  var tab by remember { mutableIntStateOf(0) }
  var newName by remember { mutableStateOf("") }

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        title = { Text("Carpetas y etiquetas") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
  ) { padding ->
    ScreenGradient()
    Column(Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Carpetas") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Etiquetas") })
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextField(
          value = newName,
          onValueChange = { newName = it },
          modifier = Modifier.weight(1f),
          placeholder = {
            Text(if (tab == 0) "Nombre de la carpeta" else "Nombre de la etiqueta")
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
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
        TextButton(
          onClick = {
            if (newName.isNotBlank()) {
              if (tab == 0) viewModel.createFolder(newName) else viewModel.createTag(newName)
              newName = ""
            }
          },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(Modifier.width(4.dp))
          Text("Agregar")
        }
      }

      if (tab == 0) {
        if (folders.isEmpty()) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "Sin carpetas",
            subtitle = "Creá una carpeta para ordenar tus notas.",
          )
        } else {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            folders.forEach { folder ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                  contentColor = MaterialTheme.colorScheme.onSurface,
                ),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Filled.Folder, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.width(12.dp))
                  Text(folder.name, style = MaterialTheme.typography.titleMedium)
                }
              }
            }
          }
        }
      } else {
        if (tags.isEmpty()) {
          EmptyState(
            icon = Icons.Filled.Label,
            title = "Sin etiquetas",
            subtitle = "Las etiquetas te permiten filtrar por tema.",
          )
        } else {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            tags.forEach { tag ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                  contentColor = MaterialTheme.colorScheme.onSurface,
                ),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Filled.Label, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary)
                  Spacer(Modifier.width(12.dp))
                  Text("#${tag.name}", style = MaterialTheme.typography.titleMedium)
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
