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
- **S7** (varias actividades por nota): "una nota con varias actividades" debe proponer **varias** actividades, todas vinculadas a la nota. Pedido del usuario (verbatim): "si procede mejoraloby que quede perfecto y moderno" — además del comportamiento, la pantalla de agendar tiene que verse actual.
  - Criterios: una nota con N tareas produce N propuestas; cada una hereda día y hora de su propia oración y, si no tiene, del resto de la nota; nunca se solapan (la siguiente empieza cuando termina la anterior); lo que no entra antes del cierre pasa al siguiente día laboral; el usuario destilda, edita y confirma; todo el lote se guarda en **una** escritura y queda con `noteId`.
  - Supuesto: el título de la nota sigue siendo candidato cuando el cuerpo no tiene nada accionable (comportamiento previo).
- **S8** (cronograma con hoy): "en la aplicacion que creamos 'anotalo' puedes agregar en la funcion de crear cronograma que tambien se pueda crear eligiendo el dia de hoy, osea las actividades del dia de hoy".
  - Criterios: al crear el cronograma (con IA o versión simple) el día de hoy cuenta cuando es un día laboral habilitado y la jornada todavía no terminó; si no, se usa el siguiente día laboral. La pantalla de Actividades muestra el chip "Incluir hoy" (encendido por defecto) para apagarlo y volver al comportamiento anterior; si hoy no aplica el chip queda deshabilitado y avisa por qué.
- **S10** (calendario de la app con las actividades): pedido verbatim "quiero que las actividades se sincronicen con el mismo calendario de la app y claro si yo las edito se sincronicen tambien de acuerdo a la edicion verifica eso y que quede bien".
  - Criterios: toda actividad del cronograma aparece en la pestaña Calendario, en **todas** las fechas de sus días (día ancla + días de repetición), ordenada por hora; el mes marca con punto los días que tienen actividades o recordatorios; el día elegido lista las actividades antes que los recordatorios; tocar una actividad abre el editor compartido y al guardar el cambio se ve al instante en Calendario y en Actividades; el check de cumplida también se puede tocar desde ahí.
  - Supuesto: una actividad es semanal (día de la semana, no fecha), así que se repite en el calendario en cada semana del mes; no se crea ninguna copia por fecha.
  - Fuera de alcance: el calendario del teléfono (Google/Xiaomi). La app no pide `READ_CALENDAR`/`WRITE_CALENDAR` y no escribe eventos del sistema.

- **S9** (elegir hoy a mano): pregunta del usuario "puedo elegir qué actividades son para hoy?" y aprobación "ok si añadelo" del atajo propuesto.
  - Criterios: el editor de actividad muestra un chip **"Hoy"** antes de los siete días que pone el día de esa actividad en el día de hoy; las filas del diálogo de "Agendar" y el encabezado de cada día en el cronograma dicen **"Hoy"** cuando el día coincide, en vez de la abreviatura.
  - Supuesto: el atajo solo cambia el día ancla de **una** actividad; no toca el reparto de la IA, ni el chip "Incluir hoy", ni los días de repetición.
  - La misma regla alcanza la propuesta de "Agendar" de una nota: una nota sin pistas se propone para hoy cuando hay tiempo, y para el siguiente día laboral cuando la jornada ya terminó. Es un cambio deliberado de la regla previa ("sin pistas, siguiente día laboral"), pedido por el usuario.

Supuestos anotados: el aviso del día se arma a la hora de entrada configurada; "reprogramar" mueve las actividades no repetidas al siguiente día laboral; la repetición semanal es un conjunto de días extra sobre el día ancla; la duración por defecto es 60 minutos.

Compatibilidad: los campos nuevos van con default dentro de `Library`/`Activity` y el `Json` ya usa `ignoreUnknownKeys = true`, así que `notes.json` viejo sigue abriendo y uno nuevo lo ignora una versión vieja.

- **S11** (fecha concreta): pedido verbatim "quiero que tengs ina fecha concreta y si quiero que se repita tambieb tebga esa opcion si diariamente hasta que se cumpla o semanal o en una gecha determinada".
  - Criterios: toda actividad guarda una fecha ISO (`yyyy-MM-dd`) además de la hora de inicio. El editor pide la fecha con un selector de calendario y la muestra como "vie 6 nov". La agenda y el calendario ubican la actividad **solo en su fecha** (y en las ocurrencias de su repetición, si la tiene). Nada de "todos los lunes del mes".
  - Migración: las actividades guardadas antes (día de semana, sin fecha) pasan a la **próxima** fecha de ese día de semana desde hoy, sin repetición. No se pierde ninguna.
  - Supuesto: los `repeatDays` viejos ("repetir otros días") no se traducen, porque la repetición nueva es una serie con fecha de inicio y fin; se limpian al migrar.

- **S12** (repetición opcional): pedido verbatim (misma frase).
  - Criterios: tres modos en el editor — **No se repite** (por defecto), **Diaria hasta cumplirla**, **Semanal**. Una actividad **cumplida no vuelve a aparecer**: cumplirla termina la serie. La serie se **calcula, no se copia**: una sola fila por actividad, sin filas por fecha.
  - Supuesto: "semanal" = cada 7 días contados desde la fecha de la actividad.

- **S13** (la serie puede terminar en una fecha): pedido verbatim "o en una gecha determinada".
  - Criterios: al elegir una repetición se puede fijar "hasta una fecha"; desde el día siguiente esa actividad ya no aparece ni avisa. Por defecto la serie dura "hasta que se cumpla".
  - Supuesto: la frase "o en una fecha determinada" se interpreta como el fin de la serie, porque una actividad sin repetición ya es, en sí, una fecha concreta.

- **S14** (recordatorio de actividad con respuesta): pedido verbatim "tambien que tenga la opcion de que si tiene recordatorio programado al notificarme me pregunte si ya establista o dibquisiera reprogramarla".
  - Criterios: el editor tiene un interruptor **Avisarme**. A la hora de inicio de la ocurrencia llega una notificación con dos acciones: **"Ya está lista"** (marca cumplida y corta la serie) y **"Reprogramar"** (abre el editor de esa actividad para cambiar fecha u hora). Si se ignora, la serie sigue y vuelve a avisar en la próxima ocurrencia.
  - Supuesto: el aviso suena en la hora de inicio de la actividad (no hay anticipación configurable en esta entrega).

## Tasks

- [x] T9 (S11,S12,S13 · datos): `Activity.date/repeat/repeatUntil/remind`; `ActivityDates.occursOn/nextOn/nextDateFor`; migración de día de semana a fecha en `NotesRepository.load`; `dayOfWeek` pasa a derivarse de la fecha; tests `ActivityDatesTest` + migración en `ActivitiesRepositoryTest`.
- [x] T10 (S11,S12,S13 · UI): editor con selector de fecha, chips de repetición y "hasta"; el calendario usa `occursOn` y deja de repetir por día de semana.
- [x] T11 (S14 · recordatorio): alarma por ocurrencia con `AlarmManager`, notificación con "Ya está lista" y "Reprogramar", y reprogramación de la serie al cumplir o al posponer.
- T12 (S11 · Actividades): agrupar por fecha ("Hoy", "Mañana", "vie 13 nov") en vez de por día de semana.
- T1 (S2 · datos): `Activity.durationMinutes/repeatDays`, `ScheduleRules` (días de una actividad, solapamientos, sobrantes, siguiente día laboral), `NotesRepository.createActivity/updateActivity/moveActivities` + tests.
- T2 (S1,S3 · lógica pura): `TaskText` (oraciones accionables, día y hora en lenguaje natural), `LocalSchedulePlanner` (reparto determinista), `TaskExtractor` (sugerencia para una nota) + tests.
- T3 (S1,S2 · UI): `ActivityEditorDialog` compartido, alta/edición de actividad en Actividades, botón "Agendar" en la tarjeta del Home con sugerencia precargada, duración y repetición visibles + aviso de solapamiento.
- T4 (S4,S5 · dispositivo): aviso diario por `AlarmManager` (canal propio, reprogramado en boot y en cada arranque) + banda "quedaron N sin hacer · pasar a mañana".
- T8 (S10 · calendario): `CalendarAgenda.onDay/hasOn` (puro); `CalendarScreen` lee `viewModel.activities`, pinta el punto del mes y lista las actividades del día con hora, duración y repetición; edición con `ActivityEditorDialog` y check de cumplida; tests en `CalendarAgendaTest` (incluye día y hora editados a nivel repositorio).
- T7 (S9 · atajo): `DayLabels.kt` (`dayNames`, `shortDayName`, `dayLabel`), chip "Hoy" con `FlowRow` en `ActivityEditorDialog`, etiqueta "Hoy" en `ScheduleSuggestionsDialog` y en el encabezado de `WeeklySchedule`; tests en `DayLabelsTest`.
- T6 (S8 · hoy): `ScheduleRules.startDay(config, today, nowMinutes, includeToday)`; `LocalSchedulePlanner.plan` rota los días para arrancar en ese día; `TaskExtractor.suggestAll`/`suggest`/`fallback` lo usan; `SchedulePlanner.prompt`/`parse` reciben el día y el permiso de hoy; `AiClient.generateSchedule` los propaga; `NotesViewModel.includeToday` + chip "Incluir hoy" en `ActivitiesScreen`; tests en `TodayScheduleTest`.
- T5 (S7 · lote desde una nota): `TaskText.taskSentences` (oración original), sustantivos de tarea al inicio y corte por conjunción "y"; `TaskExtractor.suggestAll` (una propuesta por tarea, sin solapamiento, desborde al día siguiente); `NotesRepository.createActivities` (lote en una escritura); diálogo `ScheduleSuggestionsDialog` con checkboxes y edición por fila; cableado del Home + tests.

Estado: T1–T8 implementadas, 187 tests verdes (eran 102 al empezar el bloque y 164 antes de S8). Verificación en dispositivo pendiente: la app no está instalada (MIUI bloquea la instalación por `adb`) y hay que tocar el APK en Descargas.

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

- L7 (2026-11-03 · publicación): repo público `https://github.com/Juanismael85/anotalo` con el código en `master` (21 commits + README de instalación, privacidad y compilación) y Release **v1.0** con el asset `Anotalo-1.0.apk` (2.037.046 bytes, sha256 `5c9ac475bada22289a12a5e982e2cc55cddaa727d4b0ee600b15620e052b4f7c`). Descarga anónima verificada: `https://github.com/Juanismael85/anotalo/releases/download/v1.0/Anotalo-1.0.apk` responde 200 y el sha256 coincide con el APK local. Se revisó el árbol y **todo el historial** antes de publicar: sin claves ni secretos (solo 3,3 MB). Autenticación: el prompt TTY de `gh auth login` se bloqueaba en la consulta OSC 11 del terminal, así que se usó el flujo de dispositivo OAuth directo (scope `public_repo`) y el token por `GH_TOKEN`.

- L8 (2026-11-04 · varias actividades por nota): pedido "si procede mejoraloby que quede perfecto y moderno".
  - Rojo: `MultiActivitySchedulingTest` (10 tests) escrito antes del código; falló por compilación porque `TaskText.taskSentences`, `TaskExtractor.suggestAll` y `NotesRepository.createActivities` no existían.
  - Lectura (`TaskText`): `TASK_NOUNS` para que "Reunión con el proveedor" cuente como tarea sin verbo, exigido **al inicio** de la oración (así "estuvimos hablando del presupuesto" no se confunde); corte por conjunción "y" cuando lo que sigue es accionable por sí solo ("…el martes y hay que revisar el stock" → dos tareas), que es lo que evita quedarse con una sola actividad cuando el dictado no trae puntos.
  - Reparto (`TaskExtractor.suggestAll`): día/hora de cada oración mandan sobre el resto de la nota; la siguiente tarea arranca al terminar la anterior; si cruza la hora de cierre pasa al siguiente día laboral. `suggest` sigue existiendo para el caso de una sola.
  - Persistencia: `NewActivity` + `createActivities(specs)` escriben **una** vez por lote; `createActivity` delega ahí (mismo comportamiento que antes).
  - UI: `ScheduleSuggestionsDialog` (esquinas 28 dp, tarjetas de 18 dp, checkbox, día · hora · duración, edición por fila con el editor existente, botón "Agendar N" deshabilitado si no queda ninguna, lista con tope de 380 dp para que los botones nunca queden fuera de pantalla).
  - Verde: `gradle testDebugUnitTest` → **164 tests, 0 fallos**; `assembleRelease` → 2.053.426 bytes, `versionCode 2`, `versionName 1.1`.
  - Incidente honesto: el teléfono tenía instalado un build firmado con **otra** clave (cert `CN=Android Release`, SHA-256 `874F23B2…`), así que `install -r` rechazó tanto el debug como el release. Se hizo `pm uninstall -k` para conservar datos y el instalador MIUI pidió confirmación en pantalla ("Instalar vía USB"); en ese momento el teléfono estaba en uso y los taps de confirmación cayeron sobre WhatsApp abierto. **La app quedó desinstalada** hasta que el usuario confirme la instalación. Los datos de la app estaban vacíos (`notes: []`) al momento del respaldo, así que no había notas que perder; la clave de Gemini queda en las preferencias conservadas por `-k`.
  - La clave del APK publicado (v1.0) sí coincide con `keystore/anotalo-release.jks` (SHA-256 `524B4F41…`), verificado con `apksigner verify --print-certs`: las actualizaciones desde GitHub se instalan encima sin perder datos.

- L9 (2026-11-06 · cronograma con hoy): pedido verbatim "en la aplicacion que creamos 'anotalo' puedes agregar en la funcion de crear cronograma que tambien se pueda crear eligiendo el dia de hoy, osea las actividades del dia de hoy".
  - Diagnóstico: los dos caminos de generación arrancaban en el primer día configurado (lunes) y la propuesta de una nota usaba `nextWorkday`, así que un viernes a la mañana nada caía en viernes.
  - Rojo: `TodayScheduleTest` (12 tests) escrito antes del código; falló con `Unresolved reference 'startDay'` y "No parameter with name 'today'".
  - Regla nueva (`ScheduleRules.startDay`): hoy cuenta si es un día habilitado y `nowMinutes < endMinutes`; si no, `nextWorkday`. `includeToday = false` devuelve la regla vieja.
  - Plan local: `LocalSchedulePlanner.plan` ordena los días rotando desde ese día (viernes → viernes, lunes, martes…) para que hoy se llene primero.
  - Nota: `TaskExtractor.suggestAll` usa la misma regla; se actualizaron tres asserts de `LocalPlanningTest` que fijaban "siempre mañana".
  - IA: el prompt recibe el día de hoy y la instrucción de empezar hoy (o de no usar hoy si el chip está apagado); `parse` descarta hoy cuando `includeToday = false`, así el filtro no depende del modelo.
  - UI: chip "Incluir hoy" en Actividades, encendido por defecto; se deshabilita con el texto "Hoy no disponible" cuando hoy no es laboral o la jornada terminó.
  - Verde: `gradle testDebugUnitTest` → **176 tests, 0 fallos**.

- L10 (2026-11-06 · elegir hoy a mano): el usuario preguntó "puedo elegir qué actividades son para hoy?"; se explicó que "Agendar" ya deja elegir una por una pero el editor solo muestra `Lu Ma Mi…` sin decir cuál es hoy, y aprobó el atajo con "ok si añadelo".
  - Rojo: `DayLabelsTest` (4 tests) antes del código; falló con `Unresolved reference 'dayLabel'`.
  - Se movieron `dayNames` y `shortDayName` a `DayLabels.kt` (sin Compose) para poder testearlos en JVM, y se sumó `dayLabel(day, today)` que devuelve "Hoy" cuando coincide.
  - Editor: chip "Hoy" delante de los siete días, en un `FlowRow` para que no se corte en pantallas angostas; el atajo setea el día ancla y no toca `repeatDays`.
  - Listas: las filas de "Agendar" muestran "Hoy · 08:00 · 60 min" y el cronograma titula "Hoy" el grupo del día actual.
  - Verde: `gradle testDebugUnitTest` → **180 tests, 0 fallos**.

- L11 (2026-11-06 · release v1.2): pedido "si y instalalo aqui par aprobarlo".
  - Publicación: Release **v1.2** (`https://github.com/Juanismael85/anotalo/releases/tag/v1.2`) con el asset `Anotalo-1.2.apk` (2.053.430 bytes, sha256 `89983717575a015eb54fe47ec862dd97b234687b81796128f2c165484b98e5d5`); descarga anónima 200 y hash coincidente con el APK local.
  - Instalación: esta vez `adb install -r` respondió **Success** (la restricción de MIUI ya no está), así que la 1.2 quedó instalada encima de la 1.1 sin perder datos: `dumpsys package` → `versionCode=3`, `versionName=1.2`.
  - Aprobación visual: la app abrió en el teléfono; el usuario tiene que tocar Actividades para ver el chip "Incluir hoy" y el editor para ver el chip "Hoy". No se hicieron taps a ciegas.

- L12 (2026-11-06 · calendario con actividades): pedido "quiero que las actividades se sincronicen con el mismo calendario de la app y claro si yo las edito se sincronicen tambien de acuerdo a la edicion".
  - Diagnóstico previo: la pestaña Calendario solo leía `notes.filter { reminderAt != null }`; las actividades no aparecían y la app no tenía (ni tiene) acceso al calendario del teléfono.
  - Rojo: `CalendarAgendaTest` (7 tests) antes del código; falló con `Unresolved reference 'CalendarAgenda'`.
  - Lógica pura (`CalendarAgenda`): una actividad cae en cada fecha cuyo día de semana esté en `ScheduleRules.daysOf` (día ancla + repetición), ordenada por hora; `hasOn` alimenta el punto del mes.
  - Sincronización: no hay copia ni caché — el Calendario lee `viewModel.activities` (el mismo `StateFlow` del repositorio), así que editar en Actividades o desde el propio calendario mueve la actividad en los dos lados. Probado a nivel repositorio: cambiar el día la saca del lunes y la pone en el martes; cambiar la hora se refleja.
  - UI: subtítulo "N actividades · M con recordatorio", punto en el mes si hay actividad o recordatorio, lista del día con hora + título + duración/repetición, tap para editar, checkbox de cumplida.
  - Verde: `gradle testDebugUnitTest` → **187 tests, 0 fallos**. Build `versionCode 4`, `versionName 1.3`, 2.053.430 bytes.
  - Evidencia en dispositivo: instalado y verificado en pantalla (Calendario → "7 actividades · 1 con recordatorio"; 6 nov lista 09:00, 10:00, 11:30 y 14:00; puntos en todos los lunes y viernes del mes).

- L13 (2026-11-06 · fecha concreta y repetición): pedido verbatim "quiero que tengs ina fecha concreta y si quiero que se repita tambieb tebga esa opcion si diariamente hasta que se cumpla o semanal o en una gecha determinada, tambien que tenga la opcion de que si tiene recordatorio programado al notificarme me pregunte si ya establista o dibquisiera reprogramarla".
  - Corrección del usuario sobre la entrega anterior: "observa bien porque se estanyrepiendo en varias fechas y eso no lo he pedido". El calendario repetía cada actividad en todas las fechas de su día de semana. Se tomó como decisión propia (asumida en L12) y ahora se corrige con fecha concreta.
  - Alcance de esta entrega: T9 + T10 (fecha, repetición, fin de serie y calendario). T11 (recordatorio con acciones) y T12 (Actividades por fecha) quedan para la siguiente.
  - Fuera de alcance explícito: calendario del teléfono (Google/Xiaomi).

- L14 (2026-11-06 · entrega T9/T10/T11): implementado y verificado en el teléfono.
  - T9: `Activity.date/repeat/repeatUntil/remind`; `data/ActivityDates.kt` (`occursOn`, `nextOn`, `nextDateFor`, `label`, `shortLabel`); migración en `NotesRepository.load` (JSON viejo sin `date` → próxima fecha de su día de semana, `repeat = none`, `repeatDays` limpio); `dayOfWeek` ahora se deriva de la fecha en cada escritura.
  - T10: el editor muestra la fecha real ("Vie 6 nov") con atajos "Hoy"/"Mañana" y selector de calendario, chips "No se repite / Cada día / Cada 7 días", "Hasta cumplirla / Hasta una fecha" y el interruptor "Avisarme cuando toque". El calendario usa `occursOn`.
  - T11: `reminder/ActivityReminders` (`nextAt`), `ActivityReminderScheduler`, `ActivityReminderReceiver` (ACTION_FIRE/DONE/RESCHEDULE; al disparar reprograma la ocurrencia siguiente), `ActivityReminderNotifications` (canal `activities_v1`, acciones "Ya está lista"/"Reprogramar"); `MainActivity` enruta `EXTRA_ACTIVITY_ID` al editor de Actividades; `NotesViewModel` reprograma ante cada cambio de actividades; `BootReceiver` rearma tras reiniciar.
  - Verde: `gradle testDebugUnitTest` → **201 tests, 0 fallos**.
  - Aprobación visual (1.4, `versionCode 5`): el mes de noviembre solo marca el 6 y el 9; el 6 lista las 4 actividades del viernes (09:00, 10:00, 11:30, 14:00). El editor abre con "Vie 6 nov", repetición en "No se repite" y el interruptor de aviso.
  - Pendiente: T12 (agrupar Actividades por fecha) y publicar el Release v1.4 (requiere confirmación).

