package com.voicenotes.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.screens.HomeScreen
import com.voicenotes.app.ui.screens.LibraryScreen
import com.voicenotes.app.ui.screens.NoteDetailScreen
import com.voicenotes.app.ui.screens.RecorderScreen
import com.voicenotes.app.ui.theme.ThemeMode
import com.voicenotes.app.ui.theme.VoiceNotesTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: NotesViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      VoiceNotesTheme(mode = themeMode) {
        // The window background and the system bar icons are owned by the platform, so they
        // must be told about the in-app theme; otherwise light mode keeps white status icons
        // over a light surface and a black band behind the status bar.
        val backgroundColor = MaterialTheme.colorScheme.background
        val lightSystemBars = themeMode == ThemeMode.LIGHT
        SideEffect {
          window.setBackgroundDrawable(ColorDrawable(backgroundColor.toArgb()))
          WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
          }
        }
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          AppRoot(viewModel)
        }
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
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    if (grants[Manifest.permission.RECORD_AUDIO] == true) {
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
        if (viewModel.recording.value) {
          route = Route.Record
          return@HomeScreen
        }
        val granted = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
          route = Route.Record
        } else {
          permissionLauncher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
          )
        }
      },
      onNewTextNote = {
        val created = viewModel.createTextNote(title = "", body = "")
        route = Route.Detail(created.id)
      },
      onOpenLibrary = { route = Route.Library },
      onOpenRecorder = { route = Route.Record },
    )

    Route.Record -> RecorderScreen(
      onStop = {
        viewModel.stopRecording()
        route = Route.Home
      },
      onCancel = {
        viewModel.cancelRecording()
        route = Route.Home
      },
      onCompleted = { route = Route.Home },
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
