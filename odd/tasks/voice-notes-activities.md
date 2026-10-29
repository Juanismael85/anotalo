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

- **S9**: "que al marcar una nota como lista puedas filtrarlas en el Home" — el Home ofrece tres estados: Todas / Pendientes / Listas (n); la fila de chips aparece solo si hay alguna nota lista o el filtro no es "Todas".
- **S10**: "que el cronograma se pueda mandar como PDF y no solo texto" — el botón de exportar abre un selector: "Como PDF" o "Como texto".

Convenciones (mismo criterio que recordatorios): la IA reemplaza solo actividades generadas por IA, nunca las manuales. Campos nuevos de `notes.json` son aditivos con defaults.

## Tasks

- T1 (S2,S7,S8 · datos): `Activity{id,title,dayOfWeek,slotMinutes,source,noteId,done,doneAt}`, `ChecklistItem`, `Checklist`, `Note.done/doneAt`, `WorkConfig{days,hourFrom,hourTo}` en settings, `Library.activities/checklists`, métodos de repositorio + tests. ✅ `8e083f6` + 12 tests `ActivitiesRepositoryTest`
- T2 (S1 · navegación): ruta `Activities` + 4.ª pestaña en `AppBottomBar`. ✅ `9dd1463`
- T3 (S2,S3,S4 · UI): `ActivitiesScreen` — cronograma semanal por día + diálogo de configuración (presets Lun-Vie, Sab-Dom, todos; horas). ✅ `9dd1463` + `93d42aa` (Scaffold, mensaje de estado 6 s)
- T4 (S5 · IA): `AiClient.generateSchedule(config, notes)` + parser + tests; botón "Generar con IA"; regla IA-reemplaza-IA. ✅ `a9ff964` + 7 tests `SchedulePlannerTest`
- T5 (S6,S7 · export): `export/ScheduleExporter`, `export/ChecklistExporter` (texto, puros, con tests) + compartir vía `NoteSharing`. ✅ `93d42aa` + 6 tests `ActivitiesExportTest` (102 tests verdes)
- T6 (S7 · UI checklists): crear/editar ítems, marcar, exportar. ✅ `93d42aa`
- T7 (S8 · UI notas): check "marcar como lista" en tarjeta del Home + estado persistente + tests. ✅ `93d42aa`
- T8 (S9 · UI): filtro de tres estados en el Home (`DoneFilter` ALL/PENDING/DONE, `visibleNotes`), sin persistencia (estado de pantalla). 102 tests verdes.
- T9 (S10 · export): `PdfExport.writeSchedule` + `NoteSharing.schedulePdfFor` + diálogo de export en `ActivitiesScreen`. 102 tests verdes.

## Log

- L1 (2026-10-29): "quiero que crees un apartado donde se pueda gestionar las actividades, que tenga varias formas por ejemplo así como un cronograma de actividades semanales, que tenga su propia configuración si se trabaja de lunes a viernes o que también se pueda elegir para fin de semana, cualquier día de la semana, también si el horario de trabajo es de 7 a 4 o 8 a 5 y así pero que uno pueda colocar las horas de trabajo laborales, de esta manera que ya teniendo los días laborales y las horas laborales al activar la inteligencia artificial ya con las notas que estén se cree un cronograma de actividades semanales automáticamente y este se pueda exportar, también que se puedan crear check list que también se puedan exportar para ir marcando las que ya estén listas, también me gustaría que las notas ya cargadas las que aparecen en la pantalla principal también se puedan marcar como listas"
- Antes: commiteado el bloque de recordatorios en `e47d321` + `befab10` (77 tests verdes).
- Espejo Engram pendiente (proyecto no resuelve desde `/`).
- L2 (2026-10-29 · verificación en dispositivo, `127.0.0.1:5554`): `ActivitiesScreen` sin `Scaffold` renderizaba vacía; reescrita con `Scaffold(containerColor=Transparent)` + `ScreenGradient()` copiando Radar/Calendar. Verificado end-to-end: pestaña Actividades, diálogo de horario (Lun-Vie, 07:00-16:00) guardado en `notes.json` (`workConfig`), "Generar con la IA" con las 3 notas reales produjo 6 actividades `source=ai` con `noteId` apuntando a la nota de origen, persistidas tras reiniciar. Checklist creado, ítem agregado y marcado ("1 de 1 listos", tachado) y exportado por el share sheet ("Compras para sala 1 de 1 listos [x] Globos..."). Toggle de nota lista en el Home: título tachado + ícono lleno, revertido tras la prueba. Datos de prueba eliminados.
- L3 (2026-10-29 · límite externo): regeneraciones posteriores devolvieron error de Gemini "This model is currently experiencing high demand" (503); la app lo muestra en pantalla como "Error de la IA: ...". No es un defecto de la app: el primer cronograma se generó correctamente y queda guardado.

- L4 (2026-10-29): "exacto si aplicalo, y dome que mas sujieres con tu experiencia?" — se implementan S9 (filtro de notas listas en el Home) y S10 (cronograma en PDF además de texto).
- L5 (2026-10-29 · verificación en dispositivo): PDF del cronograma generado (44 KB, `%PDF-1.4`, 2 páginas) en `cache/exports/cronograma-semanal.pdf` y aceptado por el share sheet ("Se compartirá 1 archivo"). Filtro del Home probado en los tres estados con una nota lista: "Listas (1)" muestra solo la lista, "Pendientes" las otras dos, y al volver a "Todas" aparece la fila completa; al revertir las marcas la fila de chips se oculta. Sin renderizador de PDF en el entorno: validación estructural, no visual.
- Nota de diagnóstico: una nota lista pareció desaparecer de "Todas"; era el offset retenido del `LazyColumn` tras cambiar de filtro, no un bug del filtro (confirmado al volver a la parte superior de la lista).
