package com.voicenotes.app.ui.theme

/** User-selectable appearance. Kept free of Compose imports so it stays JVM-testable. */
enum class ThemeMode(val key: String, val label: String) {
  LIGHT("light", "Modo claro"),
  DARK("dark", "Modo oscuro"),
  AMOLED("amoled", "Negro puro"),
  ;

  companion object {
    fun fromKey(value: String?): ThemeMode =
      entries.firstOrNull { it.key == value } ?: DARK

    /** Cycles light -> dark -> amoled -> light for the quick toggle button. */
    fun next(current: ThemeMode): ThemeMode = when (current) {
      LIGHT -> DARK
      DARK -> AMOLED
      AMOLED -> LIGHT
    }
  }
}
