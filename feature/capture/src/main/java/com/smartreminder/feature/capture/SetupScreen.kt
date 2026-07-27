package com.smartreminder.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartreminder.core.stt.ModelState

@Composable
fun SetupScreen(
    modelState: ModelState,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Set up speech recognition",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.padding(top = 12.dp)) {
            Text(
                "A one-time ~40 MB download. After this, transcription runs entirely on your " +
                    "phone with no internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (modelState) {
                is ModelState.Missing -> Button(onClick = onDownload) { Text("Download") }

                is ModelState.Downloading -> {
                    val p = modelState.progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${(p * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        "Downloading…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                ModelState.Unpacking -> {
                    CircularProgressIndicator()
                    Text(
                        "Unpacking…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                is ModelState.Ready -> Text("Ready", style = MaterialTheme.typography.bodyLarge)

                is ModelState.Failed -> {
                    Text(
                        modelState.reason,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry")
                    }
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
    }
}
