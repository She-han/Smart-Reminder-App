package com.smartreminder.core.stt

import java.io.File

/** Lifecycle of the on-device speech model. Surfaced to the setup screen (Phase 5). */
sealed interface ModelState {
    /** Not yet downloaded. */
    data object Missing : ModelState

    /** Download in progress. [progress] is 0f..1f, or null when total size is unknown. */
    data class Downloading(val progress: Float?) : ModelState

    /** Downloaded; unzipping and verifying. */
    data object Unpacking : ModelState

    /** Ready to transcribe; [modelDir] is the extracted Vosk model directory. */
    data class Ready(val modelDir: File) : ModelState

    data class Failed(val reason: String) : ModelState
}
