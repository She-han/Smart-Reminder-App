package com.smartreminder.core.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates [VoskTranscriber] instances against a lazily-loaded, cached [Model].
 *
 * Loading the acoustic model is expensive (seconds) and allocates native memory, so the heavy
 * [Model] is loaded once on a background thread and reused; each recording gets a cheap fresh
 * [org.vosk.Recognizer] wrapped in a new transcriber.
 */
@Singleton
class VoskTranscriberFactory @Inject constructor(
    private val modelManager: VoskModelManager,
) {
    private val mutex = Mutex()
    @Volatile private var cachedModel: Model? = null

    /** Returns a transcriber, or null if the model isn't installed yet. */
    suspend fun create(): VoskTranscriber? {
        val model = loadModel() ?: return null
        return VoskTranscriber(model)
    }

    private suspend fun loadModel(): Model? = withContext(Dispatchers.IO) {
        cachedModel?.let { return@withContext it }
        mutex.withLock {
            cachedModel?.let { return@withContext it }
            val dir = modelManager.readyDirOrNull() ?: return@withContext null
            runCatching { Model(dir.absolutePath) }
                .onSuccess { cachedModel = it }
                .onFailure { Log.e(TAG, "Failed to load Vosk model", it) }
                .getOrNull()
        }
    }

    private companion object {
        const val TAG = "VoskTranscriberFactory"
    }
}
