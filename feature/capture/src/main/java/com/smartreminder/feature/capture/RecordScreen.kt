package com.smartreminder.feature.capture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun RecordScreen(
    partialText: String,
    amplitude: Float,
    elapsedMs: Long,
    isRecording: Boolean,
    error: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = onBack) { Text(if (isRecording) "Cancel" else "Back") }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    error != null -> error
                    isRecording -> "Listening…"
                    else -> "Tap to record"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (isRecording) {
                Text(
                    text = formatElapsed(elapsedMs),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = "Say something like\n\"I have a meeting tomorrow 3pm\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (partialText.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        RecordButton(
            isRecording = isRecording,
            amplitude = amplitude,
            onClick = { if (isRecording) onStop() else onStart() },
        )
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, amplitude: Float, onClick: () -> Unit) {
    val pulse by animateFloatAsState(
        targetValue = if (isRecording) 1f + amplitude * 0.4f else 1f,
        label = "record-pulse",
    )
    Box(
        modifier = Modifier.padding(bottom = 32.dp).size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop" else "Record",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun ProcessingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator()
            Text(
                "Understanding…",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
