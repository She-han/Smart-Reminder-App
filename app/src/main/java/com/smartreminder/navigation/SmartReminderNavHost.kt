package com.smartreminder.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.smartreminder.feature.capture.CaptureRoute
import com.smartreminder.feature.list.ReminderDetailRoute
import com.smartreminder.feature.list.ReminderListRoute
import com.smartreminder.permissions.AppPermission
import com.smartreminder.permissions.PermissionsScreen
import com.smartreminder.settings.SettingsRoute

/** Route constants. Feature modules contribute their own screens onto this graph. */
object Routes {
    const val PERMISSIONS = "permissions"
    const val LIST = "list"
    const val CAPTURE = "capture"
    const val SETTINGS = "settings"
    const val DETAIL = "reminder/{id}"

    fun detail(id: Long): String = "reminder/$id"
}

@Composable
fun SmartReminderNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val start = if (AppPermission.allCriticalGranted(context)) Routes.LIST else Routes.PERMISSIONS

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = start) {
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(
                    onDone = {
                        navController.navigate(Routes.LIST) {
                            popUpTo(Routes.PERMISSIONS) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.LIST) {
                ReminderListRoute(
                    onRecordClick = { navController.navigate(Routes.CAPTURE) },
                    onManualAddClick = { navController.navigate(Routes.detail(0L)) },
                    onReminderClick = { id -> navController.navigate(Routes.detail(id)) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CAPTURE) {
                CaptureRoute(
                    onDone = { navController.popBackStack(Routes.LIST, inclusive = false) },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                ReminderDetailRoute(onDone = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onReRunPermissions = { navController.navigate(Routes.PERMISSIONS) },
                )
            }
        }
    }
}
