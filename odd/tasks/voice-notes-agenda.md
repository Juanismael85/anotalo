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

## Log

- L1 (2026-10-29): "todas me gustaron, procede, pero en el ounto 3 de igual manera va a quedar que se pueda hacer con IA verdad?" — se implementan las cinco propuestas del mensaje anterior; la IA queda como camino principal y el planificador local solo aparece cuando la IA falla.
- Antes: bloque Actividades cerrado en `93d42aa` + `99b0d91` (filtro de notas listas y cronograma en PDF), 102 tests verdes.
