package com.dstranslator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dstranslator.ui.main.MainScreen
import com.dstranslator.ui.main.MainViewModel
import com.dstranslator.ui.settings.SettingsScreen
import com.dstranslator.ui.settings.SettingsViewModel
import com.dstranslator.ui.savedvocab.SavedVocabularyScreen
import com.dstranslator.ui.vocabulary.VocabularyScreen

/**
 * Navigation graph connecting Main, Settings, and Vocabulary screens.
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

            // Refresh settings whenever the main screen resumes (e.g., returning
            // from Settings or RegionSetup) so changes take effect immediately.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshSettings()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToVocabulary = { navController.navigate("vocabulary") },
                onNavigateToSavedVocabulary = { navController.navigate("savedVocabulary") },
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture
            )
        }
        composable(
            route = "settings?section={section}",
            arguments = listOf(
                navArgument("section") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString("section") ?: ""
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                scrollToProfiles = (section == "profiles")
            )
        }
        composable("vocabulary") {
            VocabularyScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("savedVocabulary") {
            SavedVocabularyScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
