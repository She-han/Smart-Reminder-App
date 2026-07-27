package com.smartreminder.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Chinese-OEM skins (MIUI, ColorOS, Funtouch, EMUI/HarmonyOS) aggressively kill background
 * apps, silently preventing alarms from firing regardless of granted permissions. Standard
 * Android has no API for this, so we deep-link into each vendor's "autostart" settings.
 *
 * This is best-effort: the target screens are undocumented and vary by version, so every launch
 * is guarded and callers fall back gracefully when it can't resolve.
 */
object OemAutoStart {

    /** True only on manufacturers known to need a manual autostart/background allowance. */
    fun isNeeded(): Boolean = candidateComponents().isNotEmpty()

    val manufacturerLabel: String
        get() = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }

    /**
     * Opens the vendor autostart screen if one resolves, returning true on success. Tries the
     * known component for the current OEM, then a couple of fallbacks.
     */
    fun open(context: Context): Boolean {
        for (component in candidateComponents()) {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                return runCatching { context.startActivity(intent); true }.getOrDefault(false)
            }
        }
        return false
    }

    private fun candidateComponents(): List<ComponentName> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )

            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )

            manufacturer.contains("vivo") -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )

            else -> emptyList()
        }
    }
}
