package com.smartreminder.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartreminder.core.model.ReminderSettings
import com.smartreminder.core.ui.format.formatLeadTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onReRunPermissions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ChoiceSetting(
                title = "Default reminder lead time",
                subtitle = "How far before the event a new reminder rings",
                options = ReminderSettings.LEAD_TIME_PRESETS,
                selected = settings.defaultLeadMinutes,
                label = { formatLeadTime(it) },
                onSelect = viewModel::setDefaultLead,
            )

            ChoiceSetting(
                title = "Redial interval",
                subtitle = "Wait between call attempts if you don't answer",
                options = listOf(2, 5, 10, 15),
                selected = settings.redialIntervalMinutes,
                label = { "$it min" },
                onSelect = viewModel::setRedialInterval,
            )

            ChoiceSetting(
                title = "Max call attempts",
                subtitle = "How many times to redial before giving up",
                options = listOf(1, 2, 3, 5),
                selected = settings.maxCallAttempts,
                label = { "$it" },
                onSelect = viewModel::setMaxAttempts,
            )

            ChoiceSetting(
                title = "Snooze length",
                subtitle = "Delay when you snooze a ringing reminder",
                options = listOf(5, 10, 15, 30),
                selected = settings.snoozeMinutes,
                label = { "$it min" },
                onSelect = viewModel::setSnooze,
            )

            ChoiceSetting(
                title = "Ring duration",
                subtitle = "How long a reminder rings before it counts as missed",
                options = listOf(30, 45, 60, 90),
                selected = settings.ringSeconds,
                label = { "$it s" },
                onSelect = viewModel::setRingSeconds,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vibrate", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "Vibrate while a reminder rings",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.vibrate, onCheckedChange = viewModel::setVibrate)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReRunPermissions) { Text("Review permissions") }
            TextButton(onClick = viewModel::deleteModel) {
                Text("Delete speech model", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSetting(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
