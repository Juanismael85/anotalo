# VoiceNotes — bloque A + IA

App Android nativa (Kotlin + Jetpack Compose) en `/root/VoiceNotes`. Branch `main`, repo local
sin remoto. Runner: `gradle testDebugUnitTest` (JUnit4 JVM). Sigue a `voice-notes-core.md`
(S1–S13 cerradas).

## Specs

**S14 — Grabación en segundo plano.** La grabación corre en un `ForegroundService` con
`foregroundServiceType="microphone"` y notificación persistente. Texto de la notificación:
"Grabando…" y acción "Detener y guardar". Salir de la app no corta la grabación. Si el proceso
muere, el audio ya escrito en `filesDir/audio/` no se pierde: al abrir la app, todo archivo de
audio sin nota asociada se importa como nota nueva.

**S15 — Exportar y compartir una nota.** Desde el detalle, menú "Compartir": texto plano,
Markdown, PDF y el audio original. Texto y Markdown van por `ACTION_SEND` con `EXTRA_TEXT`;
PDF y audio por `FileProvider` (`application/pdf`, `audio/mp4`). El PDF incluye título, fecha,
carpeta, etiquetas, transcripción y resumen, con paginación.

**S16 — Respaldo y restauración.** "Exportar respaldo" genera un `.zip` con `notes.json` y
todos los audios, vía `ACTION_CREATE_DOCUMENT`. "Restaurar respaldo" lo lee con
`ACTION_OPEN_DOCUMENT`, valida el JSON antes de tocar nada y deja una copia de seguridad del
estado anterior en `notes.json.bak`. Un ZIP inválido no destruye los datos actuales.

**S17 — Administrar biblioteca.** Carpetas y etiquetas se pueden renombrar y borrar. Borrar una
carpeta no borra sus notas: las deja sin carpeta. Borrar una etiqueta la quita de todas las notas.

**S18 — Menú lateral de configuración.** Un botón de ajustes arriba abre un `ModalNavigationDrawer`
con: Tema (Modo claro / Modo oscuro / Negro puro), IA (clave de API, modelo, "Probar conexión"),
Respaldo (Exportar respaldo / Restaurar respaldo) y la versión de la app. El modelo no se escribe a
mano: se elige de una lista que se carga desde la API con la clave configurada (solo modelos de
chat; los de imagen, audio TTS, investigación y embeddings quedan afuera porque no pueden devolver
el JSON que la app pide), con un botón para refrescar la lista.

**S19 — IA sobre la nota.** Con clave de API configurada, el detalle de una nota muestra el botón
"Procesar con IA". La acción: (a) si la nota tiene audio y no tiene transcripción, transcribe el
audio con el modelo; (b) corrige puntuación, muletillas y mayúsculas de la transcripción;
(c) genera un resumen de 2–3 frases; (d) propone un título si la nota no tiene y etiquetas nuevas
o existentes. El resultado se guarda y la nota queda buscable por el resumen. Sin clave, el botón
explica: "Configurá tu clave de API en Ajustes para usar la IA."

## Tasks

| ID | Specs | Ruta | Estado |
|---|---|---|---|
| T9 | S18 | inline | hecho — drawer, temas, IA, respaldo (verificado en dispositivo) |
| T10 | S17 | inline | hecho — renombrar/borrar carpeta y etiqueta (verificado en dispositivo) |
| T11 | S15 | inline | hecho — texto, Markdown, PDF y audio (verificado en dispositivo) |
| T12 | S16 | inline | hecho — exportar ZIP verificado en dispositivo; restaurar cubierto por test JVM |
| T13 | S14 | inline | hecho — servicio en primer plano verificado en dispositivo |
| T14 | S19 (cliente + ajustes) | inline | hecho — clave real, lista de modelos y "Probar conexión" OK |
| T15 | S19 (acción en la nota) | inline | hecho — transcripción, resumen y etiquetas verificados con audio real |

## Log

**L21 — Pedido del usuario (25 oct).** "empieza con todo el bloque a, del bloque b solo vas a
agregar IA para titulos, etiquetas y resumenes automaticos, debes crear un boton arriba para que
se despliegue un menu lateral para las configuraciones, me gustaria que la nota que grabe se
transcriba para que al entrar en la nota que fue grabada en ese menu tenga un boton para accionar
la IA y arregle lo que se transcribio de la nota y tambien se cree un resumen".

**L22 — Decisión de proveedor de IA.** Gemini (`generativelanguage.googleapis.com`) porque el plan
original ya preveía Firebase AI / Vertex AI Gemini, acepta audio inline (resuelve transcribir un
archivo grabado, que `SpeechRecognizer` no puede hacer porque solo escucha micrófono en vivo) y su
clave se obtiene sin backend propio. Modelo por defecto `gemini-2.5-flash`, editable en Ajustes.

**L23 — Límite conocido.** `SpeechRecognizer` no puede transcribir un archivo: solo micrófono en
vivo. Por eso la transcripción de una grabación va por el mismo cliente de IA (audio inline).
El dictado manual existente (S5) se mantiene sin cambios.

**L24 — Decisión de seguridad.** `android:allowBackup` pasa a `false`: con respaldo propio (S16)
y una clave de API en `SharedPreferences`, la copia automática de Android filtraría la clave a la
nube. El respaldo manual cubre el caso de migración.

**L25 — Arreglo del botón Grabar (26 oct).** Síntoma: tocar "Grabar" volvía a Inicio al instante y
dejaba una nota de 00:00. Causa: `LaunchedEffect(active)` de `RecorderScreen` se ejecutaba con el
primer valor `false` (el servicio todavía no había arrancado) y llamaba `onStop()`, que mandaba
`ACTION_STOP` unos 10 ms después de `ACTION_START`; el archivo quedaba con 3.288 bytes y duración
11–13 ms. Arreglo: la pantalla solo navega cuando vio `active = true` antes
(`wasActive`), y `saveAndStop` descarta grabaciones de menos de 800 ms borrando el archivo. La
importación de huérfanos también descarta los archivos sin duración utilizable en vez de crear
notas que no suenan. Verificado en dispositivo: grabación de 32 s y de 115 s con nota y audio
válidos.

**L26 — IA no conectaba (26 oct).** "Probar conexión" devolvía "No pude conectar: null". Causa:
`HttpURLConnection` se ejecutaba en el hilo principal (`NetworkOnMainThreadException`), que la
app mostraba como `null` porque la excepción no trae mensaje. Arreglo: `post()` y `get()` son
`suspend` y corren en `Dispatchers.IO`; cuando una excepción no trae mensaje se muestra el nombre
del tipo. Verificado en dispositivo: "Conexión verificada con gemini-2.5-flash".

**L27 — Modelo elegible desde lista (26 oct).** Pedido del usuario: "colocale todos los modelos de
gemini". El campo de texto libre pasó a un desplegable con los modelos de chat que devuelve
`GET /v1beta/models` para la clave cargada, con botón de refresco. Si el modelo guardado no está en
la lista, se vuelve al predeterminado para no fallar en cada pedido. Test JVM `AiModelsTest` cubre
el filtro (imagen, TTS, transcripción, research, embeddings y Gemma quedan afuera).

**L28 — Verificación en dispositivo del respaldo (26 oct).** "Exportar respaldo" abrió el selector
del sistema, guardó `voicenotes-<epoch>.zip` en Descargas y el ZIP contiene `notes.json` y
`audio/note-….m4a`. "Restaurar respaldo" abre el selector de archivos; la restauración completa
queda cubierta por `BackupArchiveTest` (validación previa del JSON y copia `.bak`).

**L29 — Verificación de la IA con audio real (26 oct).** Sobre la nota "grabacion": transcripción
corregida ("Hola, ¿cómo estás? Esta es una nota importante…"), resumen de dos frases y tres
etiquetas (`nota`, `recordatorio`, `mensaje`). La lista de la biblioteca muestra la transcripción
como preview y "3 etiqueta(s)".
