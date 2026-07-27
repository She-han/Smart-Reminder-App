package com.smartreminder.feature.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.smartreminder.core.model.ParsedIntent
import com.smartreminder.core.ui.components.ReminderScheduleEditor
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun ConfirmScreen(
    draft: ReminderDraft,
    onSave: (ReminderDraft) -> Unit,
    onPlay: (String) -> Unit,
    onStopPlay: () -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf(draft.title) }
    var date by remember { mutableStateOf(draft.date ?: LocalDate.now()) }
    var time by remember { mutableStateOf(draft.time ?: LocalTime.of(9, 0)) }
    var leadMinutes by remember { mutableStateOf(draft.leadMinutes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Confirm reminder", style = MaterialTheme.typography.headlineSmall)

        if (draft.confidence == ParsedIntent.Confidence.LOW) {
            Text(
                "Couldn't read a clear time — please set it below.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (draft.transcript.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = highlightedTranscript(draft.transcript, draft.matchedSpan),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (draft.audioPath != null) {
                        TextButton(onClick = { onPlay(draft.audioPath) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        ReminderScheduleEditor(
            date = date,
            time = time,
            leadMinutes = leadMinutes,
            onDateChange = { date = it },
            onTimeChange = { time = it },
            onLeadChange = { leadMinutes = it },
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                onStopPlay()
                onSave(draft.copy(title = title, date = date, time = time, leadMinutes = leadMinutes))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save reminder")
        }
        TextButton(
            onClick = { onStopPlay(); onCancel() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text("Discard")
        }
    }
}

private fun highlightedTranscript(transcript: String, span: IntRange?) = buildAnnotatedString {
    if (span == null || span.first < 0 || span.last >= transcript.length) {
        append(transcript)
        return@buildAnnotatedString
    }
    append(transcript.substring(0, span.first))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(transcript.substring(span.first, span.last + 1))
    }
    append(transcript.substring(span.last + 1))
}
