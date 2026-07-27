package com.smartreminder.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bumping this recomputes granted-state; ON_RESUME does it after returning from Settings.
    var refresh by remember { mutableIntStateOf(0) }
    var lastRequested by remember { mutableStateOf<AppPermission?>(null) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statuses = remember(refresh) {
        AppPermission.entries.associateWith { it.isGranted(context) }
    }
    val allCriticalGranted = AppPermission.entries.filter { it.critical }.all { statuses[it] == true }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.size(16.dp))
        Text("Before we start", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reminders ring like a phone call. Grant these so they always get through.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.size(16.dp))
        AppPermission.entries.forEach { permission ->
            PermissionRow(
                permission = permission,
                granted = statuses[permission] == true,
                onGrant = {
                    lastRequested = permission
                    val runtime = permission.runtimePermission()
                    if (runtime != null) {
                        runtimeLauncher.launch(runtime)
                    } else {
                        permission.settingsIntent(context)?.let { intent ->
                            runCatching { ContextCompat.startActivity(context, intent, null) }
                        }
                    }
                },
            )
            Spacer(Modifier.size(8.dp))
        }

        if (OemAutoStart.isNeeded()) {
            OemAutoStartCard()
            Spacer(Modifier.size(8.dp))
        }

        Spacer(Modifier.size(16.dp))
        Button(
            onClick = onDone,
            enabled = allCriticalGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allCriticalGranted) "Continue" else "Grant the required permissions")
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun OemAutoStartCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Allow auto-start (${OemAutoStart.manufacturerLabel})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Your phone may kill reminders in the background. Enable auto-start so they " +
                        "always ring.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { OemAutoStart.open(context) }) { Text("Open") }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: AppPermission,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (permission.critical) permission.title else "${permission.title} (optional)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    permission.rationale,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}
