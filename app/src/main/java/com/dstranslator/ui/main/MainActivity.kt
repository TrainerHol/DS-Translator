package com.dstranslator.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dstranslator.service.CaptureService
import com.dstranslator.service.FloatingButtonService
import com.dstranslator.ui.navigation.NavGraph
import com.dstranslator.ui.theme.DsTranslatorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point activity. Hosts the Compose navigation graph and manages
 * system permission flows: MediaProjection, SYSTEM_ALERT_WINDOW, and POST_NOTIFICATIONS.
 * Also handles intents from FloatingButtonService for profile navigation and capture.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent this window from being captured by MediaProjection.
        // This is critical: without it, the translator captures its own UI,
        // OCRs it, translates it, and creates an infinite feedback loop.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Register MediaProjection permission result handler
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startCaptureService(result.resultCode, result.data!!)
                startFloatingButtonService()
            }
        }

        // Register notification permission result handler (Android 13+)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            // No action needed -- service will work regardless, just may not show notification
        }

        // Request POST_NOTIFICATIONS permission for Android 13+
        requestNotificationPermissionIfNeeded()

        setContent {
            DsTranslatorTheme {
                val nc = rememberNavController()
                navController = nc
                NavGraph(
                    navController = nc,
                    onStartCapture = ::onStartCapture,
                    onStopCapture = ::onStopCapture
                )
            }
        }

        // Handle intent that launched the activity (e.g., from FloatingButtonService)
        handleActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleActionIntent(intent)
    }

    /**
     * Handle action intents from FloatingButtonService and other components.
     */
    private fun handleActionIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_PROFILES -> {
                navController?.navigate("settings?section=profiles")
            }
            ACTION_START_CAPTURE_THEN_EDIT_REGIONS -> {
                // Trigger normal capture permission flow.
                // FloatingButtonService handles the region edit overlay opening via
                // its pendingRegionEdit flag after CaptureService starts.
                onStartCapture()
            }
        }
    }

    /**
     * Start capture flow: check overlay permission, then request MediaProjection.
     */
    private fun onStartCapture() {
        // Check SYSTEM_ALERT_WINDOW permission for floating button overlay
        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
            return
        }

        // Request MediaProjection permission
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    /**
     * Stop capture: send stop intent to CaptureService and stop FloatingButtonService.
     */
    private fun onStopCapture() {
        val stopCaptureIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        startService(stopCaptureIntent)

        val stopButtonIntent = Intent(this, FloatingButtonService::class.java)
        stopService(stopButtonIntent)
    }

    /**
     * Start CaptureService with MediaProjection consent data.
     */
    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureService.EXTRA_DATA, data)
        }
        startForegroundService(serviceIntent)
    }

    /**
     * Start FloatingButtonService for the overlay capture button.
     */
    private fun startFloatingButtonService() {
        val buttonIntent = Intent(this, FloatingButtonService::class.java)
        startService(buttonIntent)
    }

    /**
     * Request POST_NOTIFICATIONS permission on Android 13+ if not already granted.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Full teardown: release MediaProjection and stop service when app closes
        val releaseIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_RELEASE_CAPTURE
        }
        try {
            startService(releaseIntent)
        } catch (_: Exception) {
            // Service may not be running
        }
    }

    companion object {
        const val ACTION_OPEN_PROFILES = "com.dstranslator.action.OPEN_PROFILES"
        const val ACTION_START_CAPTURE_THEN_EDIT_REGIONS = "com.dstranslator.action.START_CAPTURE_THEN_EDIT_REGIONS"
    }
}
