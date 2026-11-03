# Anótalo

**Anótalo con tu voz.** Dictás y la app escribe. Notas, etiquetas y una agenda que se arma sola — todo guardado en el teléfono.

Android 8.0 o superior · 2 MB · sin cuentas ni servidores propios.

## Descargar

**[Descargar el APK](../../releases/latest)** desde la sección *Releases* de este repositorio.

1. Abrí el archivo `Anotalo-1.0.apk` en el teléfono.
2. La primera vez Android pide permitir **"instalar apps de origen desconocido"** para la app desde la que lo abrís (Archivos, Chrome, WhatsApp). Se activa una vez y listo.
3. Instalar y abrir.

Las versiones siguientes firmadas con la misma clave se actualizan encima **sin perder las notas**.

## Qué hace

- **Dictado**: hablás y el texto aparece en la nota; después lo editás como cualquier texto.
- **Notas**: etiquetas, carpetas, búsqueda, audio adjunto de cada dictado.
- **Agenda**: cada nota puede convertirse en una actividad con día, hora, duración y repetición. Aviso diario a la hora de entrada, aviso de solapamientos y cierre del día con lo que quedó sin hacer.
- **IA opcional (Gemini)**: resumen, tareas y planificación del día. Se configura con una clave de API propia.

## La IA es opcional

La app funciona completa sin internet: dictado, notas y agenda local. Los botones de IA necesitan **tu propia clave de Google Gemini** (gratuita en [aistudio.google.com](https://aistudio.google.com/apikey)), que se guarda en las preferencias del teléfono.

Sin clave, si la generación falla o no hay red, el cronograma se arma igual con un planificador local por reglas: reparte las tareas de la nota en tus días y horarios. La IA es la mejora, no un requisito.

## Privacidad

- Las notas, las etiquetas, la agenda y los audios viven en el almacenamiento privado de la app. No hay backend propio.
- La clave de Gemini queda en el teléfono y se usa solo para llamar a la API de Google.
- Cuando usás una función de IA, el texto de esa nota se envía a Google para procesarlo. Si no configurás clave, no sale nada del dispositivo.

## Compilar

Requisitos: JDK 17, Android SDK 36, Gradle.

```bash
gradle assembleDebug          # APK de desarrollo
gradle testDebugUnitTest      # 154 tests JVM
gradle assembleRelease        # APK firmado y reducido con R8
```

El build de release lee la firma de `keystore/keystore.properties` (no incluido en el repositorio, por seguridad). Sin ese archivo, `assembleRelease` genera un APK sin firmar.

## Estructura

```
app/src/main/java/com/voicenotes/app/
  data/       Library, Note, Activity, NotesRepository (JSON local atómico)
  ai/         cliente de Gemini, resumen, extracción de tareas
  agenda/     reglas puras: días, solapamientos, sobrantes, planificador local
  speech/     dictado por RecognizerIntent
  ui/         Home, detalle de nota, actividades, ajustes
```

Los datos se guardan en `files/notes.json` con escritura atómica y copia de cuarentena si el archivo no se puede leer (`notes.corrupt-<epoch>.json`), para no perder la biblioteca en silencio.

## Estado

Versión 1.0. La app se usa a diario y se prueba en dispositivo antes de cada publicación.

## Licencia

Sin licencia definida por ahora: el código es público para leerlo y compilarlo, pero no para redistribuirlo comercialmente. Si necesitás otro esquema, abrí un issue.
