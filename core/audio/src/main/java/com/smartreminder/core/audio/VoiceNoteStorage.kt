package com.smartreminder.core.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the on-disk location of voice notes: `filesDir/notes/<uuid>.m4a`. */
@Singleton
class VoiceNoteStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy { File(context.filesDir, "notes").apply { mkdirs() } }

    fun newFile(): File = File(dir, "${UUID.randomUUID()}.m4a")

    fun fileFor(path: String): File = File(path)
}
