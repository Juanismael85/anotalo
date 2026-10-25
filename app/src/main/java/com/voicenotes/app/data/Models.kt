package com.voicenotes.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Note(
  val id: String,
  val title: String = "",
  val body: String = "",
  val transcript: String = "",
  val audioPath: String? = null,
  val durationMs: Long = 0L,
  val folderId: String? = null,
  val tagIds: List<String> = emptyList(),
  val createdAt: Long = 0L,
  val updatedAt: Long = 0L,
)

@Serializable
data class Folder(val id: String, val name: String, val createdAt: Long = 0L)

@Serializable
data class Tag(val id: String, val name: String, val createdAt: Long = 0L)

@Serializable
data class Library(
  val notes: List<Note> = emptyList(),
  val folders: List<Folder> = emptyList(),
  val tags: List<Tag> = emptyList(),
)
