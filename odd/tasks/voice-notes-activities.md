# Actividades, cronograma con IA, checklists y notas listas

Branch `feature/bloque-actividades` · runner `gradle testDebugUnitTest` · entrega: ask-on-risk.

## Specs

- **S1** (nuevo apartado): "quiero que crees un apartado donde se pueda gestionar las actividades" — pestaña propia en la barra inferior, además de Notas/Radar/Calendario.
- **S2**: "un cronograma de actividades semanales" — vista semanal de actividades con día y hora.
- **S3**: "su propia configuracion si se trabaja de lunes a viernes o que tambien se pueda elegir para fin de semana, cualquier dia de la semana" — selector de días laborables con presets.
- **S4**: "si el horario de trabajo es de 7 a 4 o 8 a 5 ... que uno pueda colocar las horas de trabajo laborales" — horas inicio/fin editables por el usuario.
- **S5**: "teniendo los dias laborales y las horas laborales al activar la inteligencia artificial con las notas que esten se cree un cronograma de actividades semanales automaticamente" — generación con IA usando las notas existentes; respeta días y horario configurados; no inventa fuera del horario.
- **S6**: "este se pueda exportar".
- **S7**: "que se puedan crear check list que tambien se puedan exportar para ir marcando las que ya esten listas" — checklists con ítems marcables y export.
- **S8**: "las notas ya cargadas las que aparecen en la pantalla principal tambien se puedan marcar como listas".

Convenciones (mismo criterio que recordatorios): la IA reemplaza solo actividades generadas por IA, nunca las manuales. Campos nuevos de `notes.json` son aditivos con defaults.

## Tasks

- T1 (S2,S7,S8 · datos): `Activity{id,title,dayOfWeek,slotMinutes,source,noteId,done,doneAt}`, `ChecklistItem`, `Checklist`, `Note.done/doneAt`, `WorkConfig{days,hourFrom,hourTo}` en settings, `Library.activities/checklists`, métodos de repositorio + tests.
- T2 (S1 · navegación): ruta `Activities` + 4.ª pestaña en `AppBottomBar`.
- T3 (S2,S3,S4 · UI): `ActivitiesScreen` — cronograma semanal por día + diálogo de configuración (presets Lun-Vie, Sab-Dom, todos; horas).
- T4 (S5 · IA): `AiClient.generateSchedule(config, notes)` + parser + tests; botón "Generar con IA"; regla IA-reemplaza-IA.
- T5 (S6,S7 · export): `export/ScheduleExporter`, `export/ChecklistExporter` (texto, puros, con tests) + compartir vía `NoteSharing`.
- T6 (S7 · UI checklists): crear/editar ítems, marcar, exportar.
- T7 (S8 · UI notas): check "marcar como lista" en tarjeta del Home + estado persistente + tests.

## Log

- L1 (2026-10-29): "quiero que crees un apartado donde se pueda gestionar las actividades, que tenga varias formas por ejemplo así como un cronograma de actividades semanales, que tenga su propia configuración si se trabaja de lunes a viernes o que también se pueda elegir para fin de semana, cualquier día de la semana, también si el horario de trabajo es de 7 a 4 o 8 a 5 y así pero que uno pueda colocar las horas de trabajo laborales, de esta manera que ya teniendo los días laborales y las horas laborales al activar la inteligencia artificial ya con las notas que estén se cree un cronograma de actividades semanales automáticamente y este se pueda exportar, también que se puedan crear check list que también se puedan exportar para ir marcando las que ya estén listas, también me gustaría que las notas ya cargadas las que aparecen en la pantalla principal también se puedan marcar como listas"
- Antes: commiteado el bloque de recordatorios en `e47d321` + `befab10` (77 tests verdes).
- Espejo Engram pendiente (proyecto no resuelve desde `/`).
