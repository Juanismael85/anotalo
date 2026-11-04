package com.voicenotes.app

import com.voicenotes.app.data.Library
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.Storage
import com.voicenotes.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory storage so the data layer is testable without Android. */
class FakeStorage(var data: String? = null) : Storage {
  /** Last payload the repository asked us to keep aside as unreadable. */
  var preserved: String? = null

  /** How many times the repository asked to persist; a batch must be one write. */
  var writes: Int = 0

  override fun read(): String? = data
  override fun write(text: String) {
    data = text
    writes++
  }

  override fun preserveCorrupt(text: String): String? {
    preserved = text
    return "notes.corrupt-test.json"
  }
}

class NotesRepositoryTest {

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage)
    repo.load()
  }

  // S10 — persistence
  @Test
  fun `note survives a save and reload from json`() {
    repo.createTextNote(title = "Comprar pan", body = "Integral")

    val reopened = NotesRepository(storage)
    reopened.load()

    assertEquals(1, reopened.state.value.notes.size)
    assertEquals("Comprar pan", reopened.state.value.notes.first().title)
    assertEquals("Integral", reopened.state.value.notes.first().body)
  }

  @Test
  fun `corrupt json does not crash and starts empty`() {
    storage.data = "{ esto no es json valido"

    val reopened = NotesRepository(storage)
    reopened.load()

    assertTrue(reopened.state.value.notes.isEmpty())
    assertTrue(reopened.state.value.folders.isEmpty())
  }

  @Test
  fun `missing data starts empty`() {
    storage.data = null
    val reopened = NotesRepository(storage)
    reopened.load()
    assertTrue(reopened.state.value.notes.isEmpty())
  }

  // S9 — search
  @Test
  fun `search matches title body and transcript case-insensitive`() {
    repo.createTextNote(title = "Reunion", body = "Presupuesto anual")
    repo.createTextNote(title = "Idea", body = "otra cosa")

    val byTitle = repo.visibleNotes(null, null, "REUNION")
    val byBody = repo.visibleNotes(null, null, "presupuesto")
    val none = repo.visibleNotes(null, null, "zzz")

    assertEquals(1, byTitle.size)
    assertEquals(1, byBody.size)
    assertTrue(none.isEmpty())
  }

  @Test
  fun `empty query returns every note`() {
    repo.createTextNote(title = "a", body = "b")
    repo.createTextNote(title = "c", body = "d")

    assertEquals(2, repo.visibleNotes(null, null, "").size)
  }

  @Test
  fun `search also covers the transcript`() {
    val note = repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000)
    repo.appendTranscript(note.id, "texto dictado sobre arquitectura")

    assertEquals(1, repo.visibleNotes(null, null, "arquitectura").size)
  }

  // S7 — folders
  @Test
  fun `filter by folder returns only its notes`() {
    val work = repo.createFolder("Trabajo")
    val a = repo.createTextNote(title = "a", body = "")
    repo.createTextNote(title = "b", body = "")
    repo.assignFolder(a.id, work.id)

    val filtered = repo.visibleNotes(work.id, null, "")

    assertEquals(1, filtered.size)
    assertEquals("a", filtered.first().title)
  }

  @Test
  fun `null folder filter returns notes without folder too`() {
    val work = repo.createFolder("Trabajo")
    val a = repo.createTextNote(title = "a", body = "")
    repo.createTextNote(title = "b", body = "")
    repo.assignFolder(a.id, work.id)

    assertEquals(2, repo.visibleNotes(null, null, "").size)
  }

  // S8 — tags
  @Test
  fun `filter by tag returns only tagged notes`() {
    val idea = repo.createTag("idea")
    val a = repo.createTextNote(title = "a", body = "")
    repo.createTextNote(title = "b", body = "")
    repo.toggleTag(a.id, idea.id)

    val filtered = repo.visibleNotes(null, idea.id, "")

    assertEquals(1, filtered.size)
    assertEquals("a", filtered.first().title)
  }

  @Test
  fun `toggling a tag twice removes it`() {
    val idea = repo.createTag("idea")
    val a = repo.createTextNote(title = "a", body = "")
    repo.toggleTag(a.id, idea.id)
    repo.toggleTag(a.id, idea.id)

    assertTrue(repo.visibleNotes(null, idea.id, "").isEmpty())
  }

  @Test
  fun `folder and tag filters combine`() {
    val work = repo.createFolder("Trabajo")
    val idea = repo.createTag("idea")
    val a = repo.createTextNote(title = "a", body = "")
    val b = repo.createTextNote(title = "b", body = "")
    repo.assignFolder(a.id, work.id)
    repo.assignFolder(b.id, work.id)
    repo.toggleTag(a.id, idea.id)

    val filtered = repo.visibleNotes(work.id, idea.id, "")

    assertEquals(1, filtered.size)
    assertEquals("a", filtered.first().title)
  }

  // S6 — text notes and untitled
  @Test
  fun `untitled note is displayed as Sin titulo`() {
    val note = repo.createTextNote(title = "   ", body = "contenido")
    assertEquals("Sin título", repo.displayTitle(note))
  }

  @Test
  fun `update note changes title and body`() {
    val note = repo.createTextNote(title = "viejo", body = "viejo")
    repo.updateNote(note.id, title = "nuevo", body = "nuevo cuerpo")

    val updated = repo.state.value.notes.first()
    assertEquals("nuevo", updated.title)
    assertEquals("nuevo cuerpo", updated.body)
  }

  // S5 — dictation appends
  @Test
  fun `appendTranscript adds text after existing transcript`() {
    val note = repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 2000)
    repo.appendTranscript(note.id, "primera parte")
    repo.appendTranscript(note.id, "segunda parte")

    val transcript = repo.state.value.notes.first().transcript
    assertEquals("primera parte segunda parte", transcript)
  }

  @Test
  fun `audio note stores path and duration`() {
    val note = repo.createAudioNote(audioPath = "/tmp/voz.m4a", durationMs = 4500)
    val stored = repo.state.value.notes.first()

    assertEquals(note.id, stored.id)
    assertEquals("/tmp/voz.m4a", stored.audioPath)
    assertEquals(4500L, stored.durationMs)
  }

  @Test
  fun `delete removes the note`() {
    val note = repo.createTextNote(title = "x", body = "y")
    repo.deleteNote(note.id)

    assertTrue(repo.state.value.notes.isEmpty())
    assertNull(repo.state.value.notes.firstOrNull { it.id == note.id })
  }

  @Test
  fun `newest note comes first`() {
    repo.createTextNote(title = "primera", body = "")
    repo.createTextNote(title = "segunda", body = "")

    val library: Library = repo.state.value
    assertEquals("segunda", library.notes.first().title)
  }

  // S12 — a note created from a filtered folder is filed into that folder
  @Test
  fun `audio note created with a folder id lands in that folder`() {
    val work = repo.createFolder("Trabajo")

    repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000, folderId = work.id)

    assertEquals(1, repo.visibleNotes(work.id, null, "").size)
  }

  @Test
  fun `text note created with a folder id lands in that folder`() {
    val work = repo.createFolder("Trabajo")

    repo.createTextNote(title = "x", body = "", folderId = work.id)

    assertEquals(1, repo.visibleNotes(work.id, null, "").size)
  }

  @Test
  fun `note created without a folder stays unfiled`() {
    repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000)
    assertNull(repo.state.value.notes.first().folderId)
  }

  // S11 — theme selection is a pure value
  @Test
  fun `theme mode parses known keys and falls back to dark`() {
    assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
    assertEquals(ThemeMode.AMOLED, ThemeMode.fromKey("amoled"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromKey("cualquier cosa"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromKey(null))
  }

  @Test
  fun `theme mode cycles light dark amoled`() {
    assertEquals(ThemeMode.DARK, ThemeMode.next(ThemeMode.LIGHT))
    assertEquals(ThemeMode.AMOLED, ThemeMode.next(ThemeMode.DARK))
    assertEquals(ThemeMode.LIGHT, ThemeMode.next(ThemeMode.AMOLED))
  }

  // S17 — library administration
  @Test
  fun `renaming a folder keeps its notes attached`() {
    val work = repo.createFolder("Trabajo")
    repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000, folderId = work.id)

    repo.renameFolder(work.id, "Oficina")

    assertEquals("Oficina", repo.state.value.folders.first().name)
    assertEquals(1, repo.visibleNotes(work.id, null, "").size)
  }

  @Test
  fun `deleting a folder unfiles its notes instead of deleting them`() {
    val work = repo.createFolder("Trabajo")
    repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000, folderId = work.id)

    repo.deleteFolder(work.id)

    assertTrue(repo.state.value.folders.isEmpty())
    assertEquals(1, repo.state.value.notes.size)
    assertNull(repo.state.value.notes.first().folderId)
  }

  @Test
  fun `deleting a tag detaches it from every note`() {
    val urgent = repo.createTag("urgente")
    val note = repo.createTextNote(title = "x", body = "")
    repo.toggleTag(note.id, urgent.id)

    repo.deleteTag(urgent.id)

    assertTrue(repo.state.value.tags.isEmpty())
    assertTrue(repo.state.value.notes.first().tagIds.isEmpty())
  }

  @Test
  fun `renaming a tag keeps the note assignment`() {
    val tag = repo.createTag("urgente")
    val note = repo.createTextNote(title = "x", body = "")
    repo.toggleTag(note.id, tag.id)

    repo.renameTag(tag.id, "prioridad")

    assertEquals("prioridad", repo.state.value.tags.first().name)
    assertTrue(repo.state.value.notes.first().tagIds.contains(tag.id))
  }

  // S19 — summary is part of the note and of the search
  @Test
  fun `summary survives a reload and is searchable`() {
    val note = repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000)
    repo.applyAiResult(
      noteId = note.id,
      title = "Llamada con Ana",
      transcript = "Coordinar la entrega del lunes.",
      summary = "Se acordó entregar el lunes.",
      tagNames = listOf("trabajo"),
    )

    val reopened = NotesRepository(storage)
    reopened.load()

    val saved = reopened.state.value.notes.first()
    assertEquals("Se acordó entregar el lunes.", saved.summary)
    assertEquals(1, reopened.visibleNotes(null, null, "lunes").size)
    assertEquals(1, reopened.visibleNotes(null, null, "entrega").size)
  }

  @Test
  fun `ai result reuses an existing tag instead of duplicating it`() {
    repo.createTag("trabajo")
    val note = repo.createAudioNote(audioPath = "/tmp/a.m4a", durationMs = 1000)

    repo.applyAiResult(
      noteId = note.id,
      title = "Reunión",
      transcript = "Texto",
      summary = "Resumen",
      tagNames = listOf("Trabajo", "ideas"),
    )

    assertEquals(2, repo.state.value.tags.size)
    assertEquals(2, repo.state.value.notes.first().tagIds.size)
  }

  @Test
  fun `ai result leaves an existing title untouched when the model returns nothing`() {
    val note = repo.createTextNote(title = "Mi título", body = "cuerpo")

    repo.applyAiResult(
      noteId = note.id,
      title = "",
      transcript = "Texto limpio",
      summary = "Resumen",
      tagNames = emptyList(),
    )

    assertEquals("Mi título", repo.state.value.notes.first().title)
  }

  // S16 — backup and restore of the whole library
  @Test
  fun `snapshot json round trips into a fresh repository`() {
    repo.createTextNote(title = "Comprar pan", body = "Integral")
    repo.createFolder("Trabajo")

    val raw = repo.snapshotJson()
    val restored = NotesRepository(FakeStorage())
    restored.load()

    assertTrue(restored.replaceWithJson(raw))
    assertEquals(1, restored.state.value.notes.size)
    assertEquals("Comprar pan", restored.state.value.notes.first().title)
    assertEquals(1, restored.state.value.folders.size)
  }

  @Test
  fun `replaceWithJson rejects garbage and keeps the current library`() {
    repo.createTextNote(title = "Queda", body = "")

    assertTrue(!repo.replaceWithJson("{esto no es un respaldo"))
    assertEquals(1, repo.state.value.notes.size)
    assertEquals("Queda", repo.state.value.notes.first().title)
  }

  @Test
  fun `rebase audio paths points notes at the current audio folder`() {
    val note = repo.createAudioNote(audioPath = "/viejo/files/audio/note-1.m4a", durationMs = 900L)

    repo.rebaseAudioPaths("/nuevo/files/audio")

    assertEquals("/nuevo/files/audio/note-1.m4a", repo.state.value.notes.first().audioPath)
    assertEquals(note.id, repo.state.value.notes.first().id)
  }
}
