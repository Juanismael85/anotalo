package com.voicenotes.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The three top-level sections: the library, the reminder radar and the calendar (S22, S24). */
enum class AppTab(val label: String) {
  NOTES("Notas"),
  RADAR("Radar"),
  CALENDAR("Calendario"),
  ACTIVITIES("Actividades"),
}

@Composable
fun AppBottomBar(
  selected: AppTab,
  onSelect: (AppTab) -> Unit,
  reminderCount: Int = 0,
) {
  NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
    AppTab.entries.forEach { tab ->
      NavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = {
          if (tab == AppTab.RADAR && reminderCount > 0) {
            BadgedBox(badge = { Badge { Text(reminderCount.toString()) } }) {
              Icon(iconFor(tab), contentDescription = tab.label)
            }
          } else {
            Icon(iconFor(tab), contentDescription = tab.label)
          }
        },
        label = { Text(tab.label) },
      )
    }
  }
}

private fun iconFor(tab: AppTab) = when (tab) {
  AppTab.NOTES -> Icons.Filled.Mic
  AppTab.RADAR -> Icons.Filled.NotificationsActive
  AppTab.CALENDAR -> Icons.Filled.CalendarMonth
  AppTab.ACTIVITIES -> Icons.Filled.FactCheck
}
