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

## Tasks

| ID | Specs | Ruta | Estado |
|---|---|---|---|
| T1 | S10, S9, S7, S8, S6 | inline | pendiente |
| T2 | S1 | inline | pendiente |
| T3 | S2, S3, S4 | inline | pendiente |
| T4 | S5 | inline | pendiente |
| T5 | build + firma | inline | pendiente |

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
