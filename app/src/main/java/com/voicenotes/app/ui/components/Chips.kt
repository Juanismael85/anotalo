package com.voicenotes.app.ui.components

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * One chip look for every filter row. Uses primary tones when selected so the accent stays
 * coherent with the active theme instead of the default secondary (green) container.
 */
@Composable
fun appFilterChipColors() = FilterChipDefaults.filterChipColors(
  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
  labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
  iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
  selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
