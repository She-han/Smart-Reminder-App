package com.smartreminder.feature.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager

@Composable
fun CaptureRoute(
    onDone: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingStart by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingStart) viewModel.startRecording()
        pendingStart = false
    }

    fun requestRecord() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startRecording()
        } else {
            pendingStart = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is CaptureUiState.Saved) onDone()
    }

    when (val state = uiState) {
        is CaptureUiState.Setup -> SetupScreen(
            modelState = state.modelState,
            onDownload = viewModel::downloadModel,
            onBack = onDone,
        )

        is CaptureUiState.Ready -> RecordScreen(
            partialText = "",
            amplitude = 0f,
            elapsedMs = 0,
            isRecording = false,
            error = state.error,
            onStart = ::requestRecord,
            onStop = viewModel::stopRecording,
            onBack = onDone,
        )

        is CaptureUiState.Recording -> RecordScreen(
            partialText = state.partialText,
            amplitude = state.amplitude,
            elapsedMs = state.elapsedMs,
            isRecording = true,
            error = null,
            onStart = ::requestRecord,
            onStop = viewModel::stopRecording,
            onBack = viewModel::cancel,
        )

        CaptureUiState.Processing -> ProcessingScreen()

        is CaptureUiState.Confirm -> ConfirmScreen(
            draft = state.draft,
            onSave = viewModel::save,
            onPlay = viewModel::playPreview,
            onStopPlay = viewModel::stopPreview,
            onCancel = onDone,
        )

        CaptureUiState.Saved -> Unit
    }
}
