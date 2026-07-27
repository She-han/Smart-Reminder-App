package com.smartreminder.core.stt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and unpacks the Vosk English model on first launch, then serves its directory.
 *
 * The model ships out-of-band (not in the APK) to keep the download small; once unpacked to
 * internal storage, transcription is fully offline and never needs the network again.
 */
@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val modelsRoot: File by lazy { File(context.filesDir, "models").apply { mkdirs() } }
    private val installedDir: File get() = File(modelsRoot, MODEL_NAME)

    init {
        if (isValidModel(installedDir)) _state.value = ModelState.Ready(installedDir)
    }

    /** True once the model is present and usable; lets the UI skip the setup screen. */
    fun isReady(): Boolean = isValidModel(installedDir)

    fun readyDirOrNull(): File? = installedDir.takeIf { isValidModel(it) }

    /**
     * Ensures the model is present, downloading and unpacking if needed. Idempotent and safe to
     * call again after a failure. Progress is reported through [state].
     */
    suspend fun ensureModel(): Result<File> = withContext(Dispatchers.IO) {
        if (isValidModel(installedDir)) {
            _state.value = ModelState.Ready(installedDir)
            return@withContext Result.success(installedDir)
        }

        val zipFile = File(modelsRoot, "$MODEL_NAME.zip")
        try {
            _state.value = ModelState.Downloading(null)
            download(MODEL_URL, zipFile)

            _state.value = ModelState.Unpacking
            // Remove any partial extraction from a previous failed run.
            installedDir.deleteRecursively()
            unzip(zipFile, modelsRoot)
            zipFile.delete()

            if (!isValidModel(installedDir)) {
                error("Unpacked model is missing expected files")
            }
            _state.value = ModelState.Ready(installedDir)
            Result.success(installedDir)
        } catch (t: Throwable) {
            Log.e(TAG, "Model install failed", t)
            zipFile.delete()
            _state.value = ModelState.Failed(t.message ?: "Model download failed")
            Result.failure(t)
        }
    }

    /** Frees storage; the setup screen offers this. Recoverable via [ensureModel]. */
    fun deleteModel() {
        installedDir.deleteRecursively()
        _state.value = ModelState.Missing
    }

    private fun download(url: String, dest: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode} downloading model")
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        _state.value = ModelState.Downloading(
                            if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null,
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zip: File, targetDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Guard against zip-slip: entries must stay under targetDir.
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    error("Zip entry escapes target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** A usable model has the acoustic model config and final model files. */
    private fun isValidModel(dir: File): Boolean =
        dir.isDirectory &&
            File(dir, "conf/model.conf").exists() &&
            File(dir, "am/final.mdl").exists()

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        private const val TAG = "VoskModelManager"
    }
}
