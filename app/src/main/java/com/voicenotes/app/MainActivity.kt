package com.voicenotes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.screens.HomeScreen
import com.voicenotes.app.ui.screens.LibraryScreen
import com.voicenotes.app.ui.screens.NoteDetailScreen
import com.voicenotes.app.ui.screens.RecorderScreen
import com.voicenotes.app.ui.theme.VoiceNotesTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      VoiceNotesTheme {
        val viewModel: NotesViewModel = viewModel()
        AppRoot(viewModel)
      }
    }
  }
}

private sealed interface Route {
  data object Home : Route
  data object Record : Route
  data object Library : Route
  data class Detail(val noteId: String) : Route
}

@Composable
private fun AppRoot(viewModel: NotesViewModel) {
  var route by remember { mutableStateOf<Route>(Route.Home) }
  var permissionDenied by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      route = Route.Record
    } else {
      permissionDenied = true
    }
  }

  BackHandler(enabled = route != Route.Home) {
    route = Route.Home
  }

  when (val current = route) {
    Route.Home -> HomeScreen(
      viewModel = viewModel,
      onOpenNote = { route = Route.Detail(it) },
      onRecord = {
        val granted = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) route = Route.Record else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      },
      onNewTextNote = {
        val created = viewModel.createTextNote(title = "", body = "")
        route = Route.Detail(created.id)
      },
      onOpenLibrary = { route = Route.Library },
    )

    Route.Record -> RecorderScreen(
      onFinished = { path, duration ->
        viewModel.createAudioNote(path, duration)
        route = Route.Home
      },
      onCancel = { route = Route.Home },
    )

    Route.Library -> LibraryScreen(
      viewModel = viewModel,
      onBack = { route = Route.Home },
    )

    is Route.Detail -> NoteDetailScreen(
      noteId = current.noteId,
      viewModel = viewModel,
      onBack = { route = Route.Home },
    )
  }

  if (permissionDenied) {
    AlertDialog(
      onDismissRequest = { permissionDenied = false },
      title = { Text("Micrófono") },
      text = { Text("Necesito acceso al micrófono para grabar.") },
      confirmButton = {
        TextButton(onClick = { permissionDenied = false }) { Text("Entendido") }
      },
    )
  }
}
