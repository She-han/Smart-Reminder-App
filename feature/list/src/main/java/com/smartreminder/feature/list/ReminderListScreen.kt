package com.smartreminder.feature.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartreminder.core.model.Reminder
import com.smartreminder.core.model.ReminderStatus
import com.smartreminder.core.ui.format.formatEventTime
import com.smartreminder.core.ui.format.formatLeadTime
import com.smartreminder.core.ui.format.formatRelativeTo
import com.smartreminder.core.ui.format.formatTimeOnly
import java.time.Instant

@Composable
fun ReminderListRoute(
    onRecordClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onReminderClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ReminderListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderListScreen(
        uiState = uiState,
        onRecordClick = onRecordClick,
        onManualAddClick = onManualAddClick,
        onReminderClick = onReminderClick,
        onSettingsClick = onSettingsClick,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    uiState: ReminderListUiState,
    onRecordClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onReminderClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onDelete: (Reminder) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = onManualAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Add manually")
                }
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = onRecordClick,
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    text = { Text("Record") },
                )
            }
        },
    ) { padding ->
        if (uiState.isEmpty) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.upcoming.isNotEmpty()) {
                    item { SectionHeader("Upcoming") }
                    items(uiState.upcoming, key = { it.id }) { reminder ->
                        ReminderCard(reminder, onClick = { onReminderClick(reminder.id) })
                    }
                }
                if (uiState.past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("Past")
                    }
                    items(uiState.past, key = { it.id }) { reminder ->
                        ReminderCard(reminder, onClick = { onReminderClick(reminder.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(reminder: Reminder, onClick: () -> Unit) {
    val now = Instant.now()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(reminder.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = reminder.eventAt.formatEventTime(now),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append("Rings ${reminder.alertAt.formatTimeOnly()}")
                    append(" · ")
                    append(formatLeadTime(reminder.leadMinutes))
                    if (reminder.status.isActive) {
                        append(" · ")
                        append(reminder.alertAt.formatRelativeTo(now))
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: ReminderStatus) {
    val (label, color) = when (status) {
        ReminderStatus.SCHEDULED -> "Scheduled" to MaterialTheme.colorScheme.primary
        ReminderStatus.RINGING -> "Ringing" to MaterialTheme.colorScheme.primary
        ReminderStatus.SNOOZED -> "Snoozed" to MaterialTheme.colorScheme.tertiary
        ReminderStatus.ANSWERED -> "Answered" to MaterialTheme.colorScheme.tertiary
        ReminderStatus.MISSED -> "Missed" to MaterialTheme.colorScheme.error
        ReminderStatus.DONE -> "Done" to MaterialTheme.colorScheme.onSurfaceVariant
        ReminderStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("No reminders yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap Record and say something like\n\"I have a meeting tomorrow 3pm\"",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
