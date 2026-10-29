# VoiceNotes — recordatorios, radar y calendario

App Android nativa (Kotlin + Jetpack Compose) en `/root/VoiceNotes`. Branch
`feature/bloque-a-ia`. Runner: `gradle testDebugUnitTest` (JUnit4 JVM). Sigue a
`voice-notes-block-a-ai.md` (S1–S19 cerradas).

## Specs

**S20 — Recordatorio manual.** Toda nota puede llevar una fecha y hora de recordatorio
(`reminderAt`, epoch ms local). En el detalle se agrega con un selector de fecha y otro de hora,
se puede cambiar y se puede quitar. Una nota sin recordatorio no muestra la fila de acciones.

**S21 — Recordatorio desde la IA.** Cuando el audio o el texto menciona una fecha y hora para algo
("mañana a las 5", "el lunes", "el 3 de noviembre a las 10"), la acción de IA agrega el recordatorio
además del título, resumen y etiquetas. El prompt recibe la fecha actual, el día de la semana y la
zona horaria, y responde `reminder` en ISO-8601 (`2026-10-27T17:00`) o `null`. Reglas:
- Fecha sin hora explícita → 09:00.
- Hora sin fecha explícita → hoy si todavía no pasó, si no mañana.
- Sin mención de fecha u hora → `null`.
- Si la nota ya tiene un recordatorio manual, la IA no lo pisa. Solo reemplaza uno que ella misma
  puso antes.
- Un valor que resuelve al pasado se descarta en vez de crear un recordatorio vencido.

**S22 — Radar.** Sección propia (tab inferior "Radar") con todos los recordatorios pendientes,
agrupados en Vencidos, Hoy, Mañana y Próximos, ordenados por hora. Cada fila muestra la hora, el
título de la nota y un fragmento. Acciones: abrir la nota, marcar como hecho y posponer 10 minutos,
1 hora o hasta mañana 9:00. Estado vacío explicativo.

**S23 — Notificación del recordatorio.** A la hora indicada, `AlarmManager` despierta un
`BroadcastReceiver` que publica una notificación de alta prioridad en el canal "Recordatorios" con
el título y el resumen de la nota. Acciones: "Listo" y "Posponer 10 min" sin abrir la app. Tocar la
notificación abre la nota. Si el permiso de notificaciones está bloqueado, el Radar lo avisa.
Al reiniciar el teléfono (`BOOT_COMPLETED`) y al abrir la app se reprograman todas las alarmas
pendientes.

**S24 — Calendario.** Tab "Calendario": grilla mensual (lunes primero) con un punto en los días que
tienen recordatorio, navegación de mes y botón "Hoy". Al tocar un día se listan sus recordatorios y
se puede abrir la nota.

**S25 — Estado del recordatorio.** `reminderDone` marca el recordatorio como cumplido: sale del
Radar, cancela la alarma y deja de notificar. La nota sigue existiendo. `reminderFromAi` distingue
los que puso la IA (se muestran con una marca) de los manuales.

## Tasks

| # | Spec | Ruta | Estado |
|---|------|------|--------|
| T16 | S20/S25 | inline | pendiente — modelo, repositorio y almacén compartido |
| T17 | S23 | inline | pendiente — scheduler, receiver, notificaciones y boot |
| T18 | S21 | inline | pendiente — prompt y parseo de fecha |
| T19 | S20 | inline | pendiente — detalle de nota con pickers |
| T20 | S22 | inline | pendiente — pantalla Radar |
| T21 | S24 | inline | pendiente — pantalla Calendario |
| T22 | S22/S24 | inline | pendiente — navegación inferior y verificación en dispositivo |

## Log

**L1 (pedido del usuario, verbatim).** "me gustaria que agregues lo siguiente,un calendario, en las
notas se pueda agregar manualmente el recordatorio con fecha y hora, tambien al usar la ia si en la
nota de voz hay una fecha pautada y hora o que diga para mañana o lines la misma ia agruegue esa
fecha y hora al recordatorio y tambien me gustaria que agregues algo como un radar,de manera que si
uno de esos recordaroris esta pendiente el radar envie una notificacion, radar tambien debe tener un
apartado aparte, si me entiendes?"
