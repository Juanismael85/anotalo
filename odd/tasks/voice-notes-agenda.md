# Agenda inteligente — VoiceNotes

Objetivo: cerrar el ciclo nota → actividad → día → cierre, sin depender de la IA.
Branch: `feature/agenda-inteligente`. Tests: `gradle testDebugUnitTest` (JVM).

## Specs

- **S1** (agendar desde la nota): "Cuando dictás 'mañana hay que limpiar los quirófanos', la app ya lo entiende pero vos tenés que ir a Actividades y escribirlo. Propongo que al terminar la transcripción aparezca '¿Lo agendo? Mar, 08:00' y con un toque quede en el cronograma."
- **S2** (repetición y duración): "Hoy cada actividad es un punto suelto (día + hora). En la vida real 'limpieza de quirófanos' se repite todos los días y dura 45 minutos. Sin duración tampoco puedo avisarte cuando dos cosas se solapan."
- **S3** (respaldo sin IA): "Ya lo vimos hoy: Gemini devolvió 503 y el botón quedó muerto. Con un planificador local (reglas: repartir las tareas de cada nota en tus días y horas, sin IA) el cronograma siempre sale, aunque sea más simple. La IA pasa a ser la mejora, no el requisito."
  - Restricción del usuario (verbatim): "en el ounto 3 de igual manera va a quedar que se pueda hacer con IA verdad?" → la IA sigue siendo el camino principal; el planificador local se ofrece **solo** cuando la generación con IA falla.
- **S4** (aviso del día): "Un aviso a las 7:00 con las actividades de hoy y una acción para tildar la primera." → la hora del aviso es el inicio de `WorkConfig` (07:00 es el default de la config actual).
- **S5** (cierre del día): "Al final del día, 'quedaron 2 sin hacer: ¿reprogramo para mañana?'. Es lo que hace que un cronograma se use más de tres días."
- **S6** (autorización): "todas me gustaron, procede" — las cinco entran en este bloque.

Supuestos anotados: el aviso del día se arma a la hora de entrada configurada; "reprogramar" mueve las actividades no repetidas al siguiente día laboral; la repetición semanal es un conjunto de días extra sobre el día ancla; la duración por defecto es 60 minutos.

Compatibilidad: los campos nuevos van con default dentro de `Library`/`Activity` y el `Json` ya usa `ignoreUnknownKeys = true`, así que `notes.json` viejo sigue abriendo y uno nuevo lo ignora una versión vieja.

## Tasks

- T1 (S2 · datos): `Activity.durationMinutes/repeatDays`, `ScheduleRules` (días de una actividad, solapamientos, sobrantes, siguiente día laboral), `NotesRepository.createActivity/updateActivity/moveActivities` + tests.
- T2 (S1,S3 · lógica pura): `TaskText` (oraciones accionables, día y hora en lenguaje natural), `LocalSchedulePlanner` (reparto determinista), `TaskExtractor` (sugerencia para una nota) + tests.
- T3 (S1,S2 · UI): `ActivityEditorDialog` compartido, alta/edición de actividad en Actividades, botón "Agendar" en la tarjeta del Home con sugerencia precargada, duración y repetición visibles + aviso de solapamiento.
- T4 (S4,S5 · dispositivo): aviso diario por `AlarmManager` (canal propio, reprogramado en boot y en cada arranque) + banda "quedaron N sin hacer · pasar a mañana".

Estado: T1–T4 implementadas, 141 tests verdes (eran 102). Verificación en dispositivo pendiente: el teléfono está bloqueado con credencial y `screencap` devuelve negro.

## Log

- L1 (2026-10-29): "todas me gustaron, procede, pero en el ounto 3 de igual manera va a quedar que se pueda hacer con IA verdad?" — se implementan las cinco propuestas del mensaje anterior; la IA queda como camino principal y el planificador local solo aparece cuando la IA falla.
- Antes: bloque Actividades cerrado en `93d42aa` + `99b0d91` (filtro de notas listas y cronograma en PDF), 102 tests verdes.

- L2 (2026-10-29 · avance): T1 `581b0a9`?? (ver `git log`), T2, T3 y T4 implementadas; `gradle testDebugUnitTest` → 141 tests, 0 fallos. Build instalado sobre los datos reales: `notes.json` sigue con 3 notas y 5 actividades y el `workConfig` Lun-Vie 07:00-16:00; los campos nuevos (`durationMinutes`, `repeatDays`) se leen con default sobre las actividades viejas.
- L3 (2026-10-29 · evidencia sin pantalla): `dumpsys alarm` muestra la alarma `com.voicenotes.app.action.DAILY_AGENDA` programada para 2026-10-30 07:00 (hoy las 07:00 ya pasaron), o sea que el aviso diario quedó armado con el horario real.
- Pendiente: verificar en pantalla el botón "Agendar" del Home, el editor de actividad (duración + repetición), la banda de sobrantes y el botón de respaldo sin IA cuando la generación falla. El teléfono quedó bloqueado y `screencap` sale negro.

- L4 (2026-10-29 · lote de IA): pedido nuevo del usuario — botón en el Home que corre la misma IA que el botón de la nota, solo sobre las que no la tienen. Se agregó `Note.aiAppliedAt`, las reglas puras de `AiBatch`, el camino compartido `processNote` (el botón individual y el lote usan la misma función) y la corrección de mandar `transcript.ifBlank { body }` para que las notas escritas no viajen vacías. 151 tests verdes.
- Bloqueo: el teléfono se desconectó de `adb` (`adb devices` vacío), así que la verificación en pantalla del bloque de agenda y del botón nuevo sigue pendiente.

- L5 (2026-11-03 · nombre e ícono): el usuario propuso "AnotaloConTuVoz" y aprobó la forma separada: etiqueta del ícono **Anótalo** y frase completa **"Anótalo con tu voz"** como tagline en la app (drawer) y en la ficha de la tienda. `applicationId` sigue siendo `com.voicenotes.app` (no se toca el package: cambiaría la identidad y rompería datos/actualizaciones). Se sumó un ícono adaptativo nuevo: fondo `#0B0B0D`, onda de voz en barras naranjas (`#FF7A1A`, barra central `#FFA04D`) y capa `ic_launcher_monochrome` para los íconos temáticos de Android 13+. Commit `3564cfa`, 151 tests verdes.
  - Evidencia en dispositivo: `aapt dump badging` → `application-label:'Anótalo'`, `package: name='com.voicenotes.app'`; launcher muestra "Anótalo" con el ícono negro/naranja sin truncar; drawer muestra "Anótalo 1.0" + "Anótalo con tu voz".
  - Motivo de la etiqueta corta: el launcher dibuja el texto en una línea y corta con `…` cerca de los 11-12 caracteres; `AnotaloConTuVoz` (15) se veía como `AnotaloConTu…`.
- Hallazgo (2026-11-03): `files/notes.json` tenía `notes: []` y `files/audio` vacío desde 2026-11-02 08:06; sobreviven 9 tags, 1 carpeta, 5 actividades y el `workConfig`. El único camino que borra notas es `deleteNote` (llamado solo desde la UI), así que el borrado vino de la pantalla, no del build. El respaldo `/tmp/opencode/appdata.tar` es del 26/10 y trae una sola nota vieja: no sirve para recuperar las 3.

- L6 (2026-11-03 · pérdida de datos y release):
  - Investigación: la biblioteca quedó en `notes 0 / tags 0 / folders 0 / activities 0` y con `workConfig` igual a los valores **por defecto** del código (`[1..5]`, 420, 960). Eso solo se explica si el arranque no pudo decodificar `notes.json` y el primer guardado persistió una biblioteca vacía: `load()` devolvía `Library()` en silencio y el siguiente `commit()` reescribía el archivo. La escritura es atómica (temp + rename), así que no fue un archivo truncado: fue un valor que el decodificador estricto rechazó.
  - Prueba: `LibraryRecoveryTest` reproduce el mecanismo (un valor inválido en una nota borraba etiquetas, carpetas y actividades) y falla en rojo antes del arreglo.
  - Arreglo (`73b8c9f`): `coerceInputValues = true` para que un `null` en un campo con default no rechace el archivo entero; si igual no se puede leer, se guarda una copia `notes.corrupt-<epoch>.json` antes de arrancar vacío y el Home muestra el aviso con el nombre de la copia. 154 tests verdes.
  - Evidencia en dispositivo: con `notes.json` roto a propósito, la app mostró el aviso y dejó `notes.corrupt-1793741592878.json` de 29 bytes con el contenido original; luego se restauró el archivo sano y volvió a mostrar la nota con su resumen.
  - Release (`bd879cc`): keystore propio en `keystore/` (ignorado por git), `assembleRelease` con R8 + recursos reducidos → 2,03 MB firmado, instalado y probado en el teléfono; copia en `/sdcard/Download/Anotalo-1.0.apk` (md5 `f7e686a3737cca92e1cda2bdb87dae80`). El teléfono quedó con el build debug porque el release no permite `run-as` y hacía falta restaurar datos; los dos tienen el mismo código.
