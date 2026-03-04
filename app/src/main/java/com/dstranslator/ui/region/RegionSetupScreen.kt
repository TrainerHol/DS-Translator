package com.dstranslator.ui.region

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.service.CaptureService
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp

/**
 * Region setup screen with frozen screenshot and draggable corner handles.
 *
 * The screenshot is displayed at full width inside a vertically scrollable
 * container so both screens of a dual-display device (e.g., AYN Thor) are
 * accessible. Users scroll to find the game area, then drag corner handles
 * to define the OCR region.
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
            val bitmap = screenshotBitmap
            if (bitmap != null) {
                // Scrollable column so the full screenshot (both screens) is accessible
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scroll to find the game screen, then drag handles to select the text region.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ScreenshotWithCropOverlay(
                        bitmap = bitmap,
                        region = currentRegion,
                        onRegionChanged = { viewModel.updateRegion(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // No screenshot yet — show capture button centered
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
 */
@Composable
private fun CaptureScreenshotSection(viewModel: RegionSetupViewModel) {
    val captureManager = CaptureService.screenCaptureManagerRef

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (captureManager != null) {
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
 * Uses awaitEachGesture to conditionally consume touch events:
 * - Touch near a handle → enters drag mode, consumes events (scroll won't fire)
 * - Touch elsewhere → does NOT consume, letting the parent scroll container handle it
 *
 * This allows scrolling through the full screenshot while still supporting
 * handle dragging.
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

    // Mutable state reference for region
    var currentRegionState by remember { mutableStateOf(region) }
    currentRegionState = region

    val minSize = 50
    val primaryColor = MaterialTheme.colorScheme.primary
    val handleTouchRadius = 48f // Touch target in display pixels

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
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            val r = currentRegionState ?: return@awaitEachGesture
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

                            val closest = handles
                                .mapIndexed { index, pos ->
                                    index to (down.position - pos).getDistance()
                                }
                                .filter { it.second < handleTouchRadius }
                                .minByOrNull { it.second }

                            if (closest == null) {
                                // Not near any handle — don't consume, let scroll handle it
                                return@awaitEachGesture
                            }

                            // Near a handle — consume and start dragging
                            activeHandle = closest.first
                            down.consume()

                            // Drag loop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUp()) {
                                    activeHandle = -1
                                    break
                                }
                                change.consume()

                                val cr = currentRegionState ?: break
                                val s = scaleFactor
                                val bx = (change.position.x * s).toInt()
                                    .coerceIn(0, bitmapWidth)
                                val by = (change.position.y * s).toInt()
                                    .coerceIn(0, bitmapHeight)

                                val newRegion = when (activeHandle) {
                                    0 -> { // Top-Left
                                        val newX = bx.coerceAtMost(cr.x + cr.width - minSize)
                                        val newY = by.coerceAtMost(cr.y + cr.height - minSize)
                                        CaptureRegion(
                                            x = newX, y = newY,
                                            width = (cr.x + cr.width) - newX,
                                            height = (cr.y + cr.height) - newY
                                        )
                                    }
                                    1 -> { // Top-Right
                                        val newRight = bx.coerceAtLeast(cr.x + minSize)
                                        val newY = by.coerceAtMost(cr.y + cr.height - minSize)
                                        CaptureRegion(
                                            x = cr.x, y = newY,
                                            width = newRight - cr.x,
                                            height = (cr.y + cr.height) - newY
                                        )
                                    }
                                    2 -> { // Bottom-Left
                                        val newX = bx.coerceAtMost(cr.x + cr.width - minSize)
                                        val newBottom = by.coerceAtLeast(cr.y + minSize)
                                        CaptureRegion(
                                            x = newX, y = cr.y,
                                            width = (cr.x + cr.width) - newX,
                                            height = newBottom - cr.y
                                        )
                                    }
                                    3 -> { // Bottom-Right
                                        val newRight = bx.coerceAtLeast(cr.x + minSize)
                                        val newBottom = by.coerceAtLeast(cr.y + minSize)
                                        CaptureRegion(
                                            x = cr.x, y = cr.y,
                                            width = newRight - cr.x,
                                            height = newBottom - cr.y
                                        )
                                    }
                                    else -> cr
                                }
                                currentRegionState = newRegion
                                onRegionChanged(newRegion)
                            }
                        }
                    }
            ) {
                val r = currentRegionState ?: return@Canvas
                val sf = scaleFactor
                val left = r.x / sf
                val top = r.y / sf
                val right = (r.x + r.width) / sf
                val bottom = (r.y + r.height) / sf

                // Semi-transparent dark overlay outside the crop region
                val cropRect = Rect(left, top, right, bottom)
                val cropPath = Path().apply { addRect(cropRect) }
                clipPath(cropPath, clipOp = ClipOp.Difference) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                }

                // Border around crop region
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Corner handles - all four with white ring
                val handleRadius = 10.dp.toPx()
                val corners = listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom)
                )
                corners.forEach { corner ->
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius + 2.dp.toPx(),
                        center = corner
                    )
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
