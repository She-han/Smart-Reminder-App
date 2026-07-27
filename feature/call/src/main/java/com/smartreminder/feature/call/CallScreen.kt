package com.smartreminder.feature.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartreminder.core.model.Reminder
import java.util.Locale

@Composable
fun CallScreen(
    state: CallState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSnooze: () -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
    onFinished: () -> Unit,
) {
    LaunchedEffect(state) {
        if (state is CallState.Ended || state is CallState.None) onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        when (state) {
            is CallState.Ringing -> RingingContent(state.reminder, onAnswer, onDecline, onSnooze)
            is CallState.InCall -> InCallContent(state, onSpeaker, onEnd)
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RingingContent(
    reminder: Reminder,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            "Reminder",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Avatar(pulsing = true)
        Spacer(Modifier.height(24.dp))
        Text(
            reminder.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "is calling…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CallActionButton(
                icon = Icons.Filled.Snooze,
                label = "Snooze",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onSnooze,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = "Decline",
                color = MaterialTheme.colorScheme.error,
                onClick = onDecline,
            )
            CallActionButton(
                icon = Icons.Filled.Call,
                label = "Answer",
                color = Color(0xFF2E7D32),
                onClick = onAnswer,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InCallContent(
    state: CallState.InCall,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Avatar(pulsing = false)
        Spacer(Modifier.height(24.dp))
        Text(
            state.reminder.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            formatElapsed(state.elapsedMs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.reminder.transcript.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    state.reminder.transcript,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallActionButton(
                icon = Icons.Filled.VolumeUp,
                label = if (state.onSpeaker) "Speaker on" else "Speaker",
                color = if (state.onSpeaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                onClick = onSpeaker,
            )
            CallActionButton(
                icon = Icons.Filled.Check,
                label = "Done",
                color = Color(0xFF2E7D32),
                onClick = onEnd,
            )
            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = "End",
                color = MaterialTheme.colorScheme.error,
                onClick = onEnd,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Avatar(pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "avatar")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "avatar-scale",
    )
    Box(
        modifier = Modifier.size(140.dp).scale(scale).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
