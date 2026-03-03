package com.dstranslator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dstranslator.ui.main.MainScreen
import com.dstranslator.ui.main.MainViewModel
import com.dstranslator.ui.region.RegionSetupScreen
import com.dstranslator.ui.region.RegionSetupViewModel
import com.dstranslator.ui.settings.SettingsScreen
import com.dstranslator.ui.settings.SettingsViewModel

/**
 * Navigation graph connecting Main, Settings, and RegionSetup screens.
 *
 * @param navController The navigation controller managing the back stack
 * @param onStartCapture Callback to initiate the MediaProjection permission flow
 * @param onStopCapture Callback to stop CaptureService and FloatingButtonService
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            val viewModel: MainViewModel = hiltViewModel()
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToRegionSetup = { navController.navigate("region_setup") },
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture
            )
        }
        composable("settings") {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("region_setup") {
            val viewModel: RegionSetupViewModel = hiltViewModel()
            RegionSetupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
