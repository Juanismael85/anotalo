# VoiceNotes — core sólido

App Android nativa (Kotlin + Jetpack Compose) de notas de voz, extraída del proyecto
`/root/voice-notes-ai` (Expo/React Native, no compilable localmente por falta de NDK aarch64).
Branch: `main` (repo local, sin remoto). Runner de tests: `gradle test` (JUnit4, JVM).

## Specs

**S1 — UI moderna y navegación.** La app abre en una pantalla principal con tema oscuro por
defecto, lista de notas y barra de búsqueda. Tres destinos: Notas, Carpetas, Ajustes.
Paleta oscura propia (acento violeta/cian), tipografía Material3, esquinas redondeadas,
FAB de grabación flotante.

**S2 — Grabación de voz real.** El FAB abre la pantalla de grabación. Al tocar "Grabar" se
inicia la captura con el micrófono en formato AAC/M4A dentro de `filesDir/audio/`.
Se muestra un cronómetro `mm:ss` en tiempo real y una animación de pulso. Al detener,
la nota se guarda automáticamente con fecha, duración y ruta del audio.

**S3 — Permiso de micrófono.** Antes de grabar se solicita `RECORD_AUDIO` en runtime.
Si se deniega, se muestra el texto exacto: "Necesito acceso al micrófono para grabar."
y no se inicia la captura. Si se concede, la grabación arranca sin pasos extra.

**S4 — Reproducción.** Cada nota con audio muestra un botón play/pausa y una barra de
progreso. La reproducción usa `MediaPlayer` y libera el recurso al salir de la nota.
Al terminar el audio, el botón vuelve al estado inicial.

**S5 — Dictado a texto.** Desde una nota se puede dictar para generar/agregar la
transcripción usando `SpeechRecognizer` del sistema. Mientras escucha se muestra
"Escuchando…". Si el reconocimiento falla se muestra: "No pude transcribir el audio."
El texto reconocido se appendea a la transcripción existente.

**S6 — Notas de texto.** Se puede crear una nota escrita sin audio y editar título y
cuerpo de cualquier nota. El título vacío se muestra como "Sin título".

**S7 — Carpetas.** Se pueden crear carpetas con nombre. Una nota pertenece a cero o una
carpeta. En Notas se filtra por carpeta mediante chips; "Todas" muestra todo.

**S8 — Etiquetas.** Se pueden crear etiquetas y asignar varias a una nota. En Notas se
filtra por etiqueta mediante chips seleccionables.

**S9 — Búsqueda.** La barra de búsqueda filtra en vivo por coincidencia (case-insensitive)
en título, cuerpo y transcripción. Sin coincidencias muestra: "No hay notas que coincidan."

**S10 — Persistencia.** Las notas, carpetas y etiquetas se guardan en JSON en
`filesDir/notes.json` y sobreviven al reinicio. Un archivo corrupto o ausente no
rompe la app: se arranca con datos vacíos.

**S11 — Tres modos de tema.** Un botón en la barra superior (ícono de paleta) abre un menú
con las tres opciones exactas: "Modo claro", "Modo oscuro", "Negro puro" (fondo `#000000`).
La opción activa se marca con un tilde. La elección se persiste en
`SharedPreferences("voice_notes_settings", "theme_mode")` y sobrevive al reinicio.

**S12 — La nota nueva hereda el filtro de carpeta.** Si hay una carpeta seleccionada en
Notas y se graba una nota (o se crea una nota escrita), la nota queda asignada a esa
carpeta y aparece al filtrar por ella.

**S13 — Contraste de texto en los tres modos.** Títulos, cuerpos, transcripciones y chips
usan colores derivados del esquema activo. Ningún texto queda negro sobre fondo oscuro:
los `Card` con `containerColor` modificado con alfa declaran `contentColor` explícito,
porque `contentColorFor()` no reconoce un color que no está en el esquema y cae a negro.
Los chips seleccionados usan `primaryContainer`/`onPrimaryContainer`, no el verde secundario.

## Tasks

| ID | Specs | Ruta | Estado |
|---|---|---|---|
| T1 | S10, S9, S7, S8, S6 | inline | ✅ 6192e21 |
| T2 | S1 | inline | ✅ e6cb9d4 |
| T3 | S2, S3, S4 | inline | ✅ e6cb9d4 |
| T4 | S5 | inline | ✅ e6cb9d4 |
| T5 | build + firma | inline | ✅ APK 1.6 MB firmado v2/v3 |
| T6 | tipografía empaquetada + slider | inline | ✅ instalado 16:59 |
| T7 | S12 | inline | ✅ RED `No parameter with name 'folderId'` → 22 tests verdes |
| T8 | S11, S13 | inline | ✅ verificado en los tres modos por screenshot |

## Log

**L1 (pedido original del usuario, verbatim):**
"lo que quiero es que crees una apk, ya tebenos todo en una carpeta se llama voice notes ai, buscala y quiero que crees la aplicsacion con esa informacion ,que sea una interfaz moderna"

**L2 (verbatim):** "instala todo lo que sea necesario para que puedas crear aplicaciones de alto nivel"

**L3 (verbatim):** "arranca con el sore solido"

**L4 — Decisión de stack.** El proyecto `/root/voice-notes-ai` es Expo/React Native con
`react-native-reanimated`, `worklets` y `mmkv`, que exigen compilar C++ con el NDK. En este
dispositivo el NDK no puede ejecutarse (binarios de host x86_64, sin box64/qemu). Por eso se
implementa en Kotlin + Compose nativo, con el toolchain verificado
(Gradle 9.5.0 / AGP 8.13.2 / Kotlin 2.4.21 / compileSdk 36).

**L5 — Alcance.** El usuario elige "core sólido" en vez de portar las 17 pantallas con
Firebase/RevenueCat. Quedan fuera de esta entrega: login, sync, IA en la nube, premium,
export PDF. La transcripción es por dictado del sistema (`SpeechRecognizer`), sin API keys.

**L6 — Persistencia elegida.** JSON con `kotlinx.serialization` en vez de Room/KSP: elimina
el riesgo de incompatibilidad de KSP con Kotlin 2.4.21 y permite testear la lógica en JVM puro.

**L10 — Runtime verificado en el dispositivo real.** `adb connect 127.0.0.1:5554`
(ADB por Wi-Fi) → Redmi `23129RA5FL`, Android 15 (SDK 35), arm64-v8a, HyperOS V816.
APK release instalado y ejecutado: `topResumedActivity = com.voicenotes.app/.MainActivity`,
`logcat -b crash` vacío para el paquete.

**L11 — Evidencia funcional observada.** Nota real creada en el teléfono a las 16:25:
grabación de **00:05** con transcripción por `SpeechRecognizer` de 13 palabras
("Hola cómo estás Esta es una nota importante para que se guarde como debe ser").
Esto prueba S2 (grabación), S4 (el reproductor cargó el archivo real: duración 00:05)
y S5 (dictado). Los datos sobrevivieron **tres reinstalaciones** (`install -r`, misma
clave de firma), lo que prueba S10 en el dispositivo.

**L12 — Defecto encontrado y corregido: tipografía serif.** La app renderizaba con una
tipografía serif. Causa raíz: el teléfono tiene un **tema de fuente del sistema** que
reasigna el alias `sans-serif`; `/system/fonts/MiSansVF_Overlay.ttf` es un symlink a
`/data/system/fonts/theme_webview/Roboto-Regular.ttf` → `/data/system/theme/fonts/Roboto-Regular.ttf`
(instalado 24 oct). Ni `android:fontFamily=sans-serif` ni `FontFamily.SansSerif` de Compose
alcanzan porque el alias del sistema ya está secuestrado. Solución: empaquetar
`RobotoStatic-Regular.ttf` (299 KB) en `res/font/roboto_regular.ttf` y construir
`Typography` completo sobre esa familia. Verificado por screenshot antes/después.

**L13 — Pulido.** Slider del reproductor con colores explícitos (thumb/track primary,
track inactivo gris) porque el default se veía verde. Release final: 1.6 MB firmado v2/v3,
`lastUpdateTime=2026-10-25 16:59:49`, datos y fuente correctos.

**L14 — Límite restante.** El audio no se puede *escuchar* desde acá: se verificó que
`MediaPlayer` carga y reporta duración, pero la salida por parlante no es observable vía
ADB. La búsqueda y los filtros están cubiertos por tests JVM, no por interacción en pantalla.

**L15 — Reporte del usuario (25 oct).** "en el nombre de la nota esta la letra de color negro y
no se logra ver bien, otro detalle creo la carpeta pero al grabar en esa carpeta no se guarda
alli, tengo que verla en el apartado de todas pero en la carpeta no se guarda, puedes agregarle
un boton tambien de modo claro modo oscuro y negro puro, y que el color de las letras sean
acordes a cada uno".

**L16 — Causa raíz del texto negro.** No era el `TextField`: era `CardDefaults.cardColors(
containerColor = surfaceVariant.copy(alpha = 0.35f))`. `contentColorFor()` resuelve el color de
contenido buscando el `containerColor` dentro del `ColorScheme`; un color con alfa modificado no
coincide con ninguno y la función devuelve `Color.Unspecified`, que termina resolviendo a negro.
Solución: declarar `contentColor = MaterialTheme.colorScheme.onSurface` en cada `Card` con
contenedor modificado (HomeScreen, NoteDetailScreen ×2, LibraryScreen ×2), más colores explícitos
de texto/cursor/placeholder en todos los `TextField` y `.copy(color = onSurface)` en el
`textStyle` del título. Verificado por screenshot: los títulos pasan de casi negros a blancos.

**L17 — Carpeta al grabar.** `createTextNote`/`createAudioNote` del repositorio aceptan
`folderId: String? = null` y el ViewModel pasa `_folderFilter.value`. RED observado primero
(`No parameter with name 'folderId' found`), luego 22 tests JVM verdes (S5–S12). Confirmado en
el dispositivo: dos grabaciones hechas con la carpeta "juanillo" activa aparecen como
"00:01 · juanillo · 25 oct · 17:42" y "17:51" dentro del filtro.

**L18 — Temas.** `ThemeMode` (light/dark/amoled) vive en un archivo sin imports de Compose para
ser testeable en JVM, con `fromKey` (fallback a DARK) y `next`. Tres `ColorScheme` completos en
`Theme.kt` y persistencia en SharedPreferences. El menú de la barra superior muestra las tres
opciones con tilde en la activa; verificado en dispositivo en los tres modos.

**L19 — Chips.** Los `FilterChip` seleccionados usaban `secondaryContainer` (verde/cian) y
rompían la coherencia con el acento violeta. Se centralizó en `appFilterChipColors()`:
`primaryContainer`/`onPrimaryContainer` al seleccionar, `surfaceVariant` al 35% sin seleccionar.

**L20 — Límite de plataforma (barra de estado).** En modo claro, HyperOS (V816) dibuja la barra
de estado como un panel negro con esquinas inferiores redondeadas y íconos blancos, porque el
tema **del sistema** está en oscuro. Evidencia: la ventana de la app es fullscreen
(`frame=[0,0][1080,2400]`), `vsysui=LIGHT_STATUS_BAR` está solicitado, `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`
limpiado y el artefacto persiste tras rotar y reiniciar; con el fondo de la app en rojo puro el
rojo llega hasta arriba, así que la app sí pinta debajo y es SystemUI quien lo tapa. No se puede
corregir desde la app sin permisos de sistema. La barra de navegación sí sigue al tema.
