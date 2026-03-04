package com.dstranslator.ui.region

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.service.CaptureService
import kotlinx.coroutines.runBlocking

/**
 * Region setup screen with frozen screenshot and draggable corner handles.
 *
 * User captures a screenshot of the current game, then drags corner handles
 * to define the text dialog region for OCR. Region is stored in bitmap
 * coordinates and persisted via SettingsRepository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSetupScreen(
    viewModel: RegionSetupViewModel,
    onBack: () -> Unit
) {
    val screenshotBitmap by viewModel.screenshotBitmap.collectAsState()
    val currentRegion by viewModel.currentRegion.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Capture Region") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (screenshotBitmap != null) {
                        TextButton(onClick = { viewModel.resetRegion() }) {
                            Text("Reset")
                        }
                        TextButton(onClick = {
                            viewModel.saveRegion()
                            onBack()
                        }) {
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Position your game to the dialog screen, then drag the handles to select the text region.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val bitmap = screenshotBitmap
                if (bitmap != null) {
                    // Show screenshot with crop overlay
                    ScreenshotWithCropOverlay(
                        bitmap = bitmap,
                        region = currentRegion,
                        onRegionChanged = { viewModel.updateRegion(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    // Show capture button or message
                    Spacer(modifier = Modifier.weight(1f))
                    CaptureScreenshotSection(viewModel = viewModel)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Section displayed when no screenshot is available.
 * Provides a button to capture a screenshot from the active MediaProjection session
 * via the CaptureService's static ScreenCaptureManager reference.
 */
@Composable
private fun CaptureScreenshotSection(viewModel: RegionSetupViewModel) {
    val captureManager = CaptureService.screenCaptureManagerRef

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (captureManager != null) {
            // CaptureService is running with an active MediaProjection session
            Button(onClick = {
                val bitmap = runBlocking {
                    try {
                        captureManager.acquireScreenshot()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    viewModel.setScreenshot(bitmap)
                }
            }) {
                Text("Capture Screenshot")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Captures the current screen for region selection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            Text(
                text = "Start capture first to take a screenshot for region setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Displays the screenshot with a draggable crop overlay.
 *
 * The overlay shows:
 * - Semi-transparent dark overlay outside the crop region
 * - Primary-colored border around the crop region
 * - Four corner drag handles (filled circles) with enlarged touch targets
 *
 * All coordinate math converts between display coordinates and bitmap coordinates
 * using a scale factor derived from the displayed image size.
 *
 * IMPORTANT: The pointerInput key must be stable (Unit) -- NOT the region itself.
 * Using region as the key causes the gesture detector to restart on every drag
 * movement, making dragging nearly impossible. Instead, we read the current
 * region from a mutable state reference inside the gesture lambda.
 */
@Composable
private fun ScreenshotWithCropOverlay(
    bitmap: android.graphics.Bitmap,
    region: CaptureRegion?,
    onRegionChanged: (CaptureRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bitmapWidth = bitmap.width
    val bitmapHeight = bitmap.height
    val aspectRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()

    // Track the display size of the image container
    var displayWidth by remember { mutableIntStateOf(0) }
    var displayHeight by remember { mutableIntStateOf(0) }

    // Scale factor: display coordinates -> bitmap coordinates
    var scaleFactor by remember { mutableFloatStateOf(1f) }

    // Track which handle is being dragged: 0=TL, 1=TR, 2=BL, 3=BR, -1=none
    var activeHandle by remember { mutableIntStateOf(-1) }

    // Mutable state reference for region -- allows pointerInput to read current
    // region without restarting the gesture detector.
    var currentRegionState by remember { mutableStateOf(region) }
    // Keep in sync with external region changes (e.g., reset button)
    currentRegionState = region

    // Minimum region size in bitmap coordinates
    val minSize = 50

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .onSizeChanged { size ->
                displayWidth = size.width
                displayHeight = size.height
                scaleFactor = if (size.width > 0) bitmapWidth.toFloat() / size.width.toFloat() else 1f
            }
    ) {
        // Screenshot image
        Image(
            bitmap = imageBitmap,
            contentDescription = "Game screenshot",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Crop overlay with drag handling
        if (region != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val r = currentRegionState ?: return@detectDragGestures

                                // Determine which handle is closest
                                val handleRadius = 48f // enlarged touch target in display pixels
                                val sf = scaleFactor

                                // Convert region corners to display coordinates
                                val left = r.x / sf
                                val top = r.y / sf
                                val right = (r.x + r.width) / sf
                                val bottom = (r.y + r.height) / sf

                                val handles = listOf(
                                    Offset(left, top),     // TL
                                    Offset(right, top),    // TR
                                    Offset(left, bottom),  // BL
                                    Offset(right, bottom)  // BR
                                )

                                activeHandle = handles
                                    .mapIndexed { index, pos ->
                                        index to (offset - pos).getDistance()
                                    }
                                    .filter { it.second < handleRadius }
                                    .minByOrNull { it.second }
                                    ?.first ?: -1
                            },
                            onDrag = { change, _ ->
                                if (activeHandle < 0) return@detectDragGestures
                                change.consume()

                                val r = currentRegionState ?: return@detectDragGestures
                                val sf = scaleFactor
                                // Convert touch position to bitmap coordinates
                                val bx = (change.position.x * sf).toInt()
                                    .coerceIn(0, bitmapWidth)
                                val by = (change.position.y * sf).toInt()
                                    .coerceIn(0, bitmapHeight)

                                val newRegion = when (activeHandle) {
                                    0 -> { // Top-Left
                                        val newX = bx.coerceAtMost(r.x + r.width - minSize)
                                        val newY = by.coerceAtMost(r.y + r.height - minSize)
                                        CaptureRegion(
                                            x = newX,
                                            y = newY,
                                            width = (r.x + r.width) - newX,
                                            height = (r.y + r.height) - newY
                                        )
                                    }
                                    1 -> { // Top-Right
                                        val newRight = bx.coerceAtLeast(r.x + minSize)
                                        val newY = by.coerceAtMost(r.y + r.height - minSize)
                                        CaptureRegion(
                                            x = r.x,
                                            y = newY,
                                            width = newRight - r.x,
                                            height = (r.y + r.height) - newY
                                        )
                                    }
                                    2 -> { // Bottom-Left
                                        val newX = bx.coerceAtMost(r.x + r.width - minSize)
                                        val newBottom = by.coerceAtLeast(r.y + minSize)
                                        CaptureRegion(
                                            x = newX,
                                            y = r.y,
                                            width = (r.x + r.width) - newX,
                                            height = newBottom - r.y
                                        )
                                    }
                                    3 -> { // Bottom-Right
                                        val newRight = bx.coerceAtLeast(r.x + minSize)
                                        val newBottom = by.coerceAtLeast(r.y + minSize)
                                        CaptureRegion(
                                            x = r.x,
                                            y = r.y,
                                            width = newRight - r.x,
                                            height = newBottom - r.y
                                        )
                                    }
                                    else -> r
                                }
                                currentRegionState = newRegion
                                onRegionChanged(newRegion)
                            },
                            onDragEnd = {
                                activeHandle = -1
                            }
                        )
                    }
            ) {
                val r = currentRegionState ?: return@Canvas
                val sf = scaleFactor
                val left = r.x / sf
                val top = r.y / sf
                val right = (r.x + r.width) / sf
                val bottom = (r.y + r.height) / sf

                // Draw semi-transparent dark overlay outside the crop region
                val cropRect = Rect(left, top, right, bottom)
                val cropPath = Path().apply {
                    addRect(cropRect)
                }
                clipPath(cropPath, clipOp = ClipOp.Difference) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                }

                // Draw border around crop region
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw corner handles - all four corners with larger visual handles
                val handleRadius = 10.dp.toPx()
                val corners = listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom)
                )
                corners.forEach { corner ->
                    // Outer ring for better visibility
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius + 2.dp.toPx(),
                        center = corner
                    )
                    // Filled handle
                    drawCircle(
                        color = primaryColor,
                        radius = handleRadius,
                        center = corner
                    )
                }
            }
        }
    }
}
