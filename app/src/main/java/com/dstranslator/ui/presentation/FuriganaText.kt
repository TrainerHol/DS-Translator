package com.dstranslator.ui.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.dstranslator.domain.model.FuriganaSegment

/**
 * Measurement data for a single furigana segment, pre-computed to avoid
 * recomputation on every scroll frame.
 */
private data class MeasuredSegment(
    val segment: FuriganaSegment,
    val index: Int,
    val baseWidth: Int,
    val baseHeight: Int,
    val furiganaWidth: Int,
    val furiganaHeight: Int,
    val hasFurigana: Boolean
)

/**
 * Position data for a laid-out segment after line wrapping.
 */
private data class PositionedSegment(
    val measured: MeasuredSegment,
    val x: Float,
    val y: Float,
    val totalWidth: Int
)

/**
 * Custom composable that renders Japanese text with furigana (reading annotations)
 * positioned above kanji characters.
 *
 * Uses a single Layout composable with drawBehind for Canvas-based text rendering.
 * The Layout measures and positions segments with line wrapping, then drawBehind
 * renders both base text and furigana annotations using TextMeasurer.
 *
 * This unified approach ensures the composable has the correct measured height
 * (furigana + gap + base text per line), making furigana visible and enabling
 * proper scrolling in parent containers like LazyColumn.
 *
 * @param segments List of FuriganaSegments to render
 * @param modifier Modifier for the composable
 * @param baseTextStyle Style for the main text (default: titleMedium)
 * @param furiganaTextStyle Style for the furigana text above kanji (default: labelSmall)
 * @param baseTextColor Color for main text
 * @param furiganaTextColor Color for furigana text
 * @param onWordTap Optional callback when a word is tapped, receives the word index
 */
@Composable
fun FuriganaText(
    segments: List<FuriganaSegment>,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    furiganaTextStyle: TextStyle = MaterialTheme.typography.labelSmall,
    baseTextColor: Color = MaterialTheme.colorScheme.onBackground,
    furiganaTextColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    onWordTap: ((Int) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()

    // Pre-measure all segments
    val measured = remember(segments, baseTextStyle, furiganaTextStyle) {
        segments.mapIndexed { index, segment ->
            val baseResult = textMeasurer.measure(
                text = segment.text,
                style = baseTextStyle
            )
            val furiganaResult = if (segment.reading != null) {
                textMeasurer.measure(
                    text = segment.reading,
                    style = furiganaTextStyle
                )
            } else null

            MeasuredSegment(
                segment = segment,
                index = index,
                baseWidth = baseResult.size.width,
                baseHeight = baseResult.size.height,
                furiganaWidth = furiganaResult?.size?.width ?: 0,
                furiganaHeight = furiganaResult?.size?.height ?: 0,
                hasFurigana = segment.reading != null
            )
        }
    }

    // Determine furigana line height (max across all segments that have furigana)
    val furiganaLineHeight = remember(measured) {
        measured.filter { it.hasFurigana }.maxOfOrNull { it.furiganaHeight } ?: 0
    }

    val baseLineHeight = remember(measured) {
        measured.maxOfOrNull { it.baseHeight } ?: 0
    }

    // Single line height = furigana height + base text height + small gap
    val furiganaGap = 2 // px gap between furigana and base text
    val lineHeight = furiganaLineHeight + furiganaGap + baseLineHeight
    val lineSpacing = 4 // px between wrapped lines

    /**
     * Compute positioned segments for a given max width.
     * Used by both layout measurement and drawing/tap detection.
     */
    fun layoutSegments(maxWidth: Int): List<PositionedSegment> {
        val positions = mutableListOf<PositionedSegment>()
        var currentX = 0f
        var currentY = 0f

        for (m in measured) {
            val segWidth = maxOf(m.baseWidth, m.furiganaWidth)

            // Wrap to next line if needed
            if (currentX + segWidth > maxWidth && currentX > 0) {
                currentX = 0f
                currentY += lineHeight + lineSpacing
            }

            positions.add(
                PositionedSegment(
                    measured = m,
                    x = currentX,
                    y = currentY,
                    totalWidth = segWidth
                )
            )

            currentX += segWidth
        }

        return positions
    }

    Layout(
        content = {},
        modifier = modifier
            .drawBehind {
                val maxWidth = size.width.toInt()
                val positions = layoutSegments(maxWidth)

                // Draw each segment
                for (pos in positions) {
                    val m = pos.measured
                    val baseY = pos.y + furiganaLineHeight + furiganaGap

                    // Draw base text centered within segment width
                    val baseOffsetX = pos.x + (pos.totalWidth - m.baseWidth) / 2f
                    drawText(
                        textMeasurer = textMeasurer,
                        text = m.segment.text,
                        style = baseTextStyle.copy(color = baseTextColor),
                        topLeft = Offset(baseOffsetX, baseY)
                    )

                    // Draw furigana above base text, centered
                    if (m.hasFurigana && m.segment.reading != null) {
                        val furiganaOffsetX =
                            pos.x + (pos.totalWidth - m.furiganaWidth) / 2f
                        val furiganaY =
                            pos.y + (furiganaLineHeight - m.furiganaHeight)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = m.segment.reading,
                            style = furiganaTextStyle.copy(color = furiganaTextColor),
                            topLeft = Offset(furiganaOffsetX, furiganaY)
                        )
                    }
                }
            }
            .pointerInput(segments, onWordTap) {
                if (onWordTap != null) {
                    detectTapGestures { offset ->
                        val maxWidth = size.width
                        val positions = layoutSegments(maxWidth)

                        for (pos in positions) {
                            val segTop = pos.y
                            val segBottom = pos.y + lineHeight
                            val segLeft = pos.x
                            val segRight = pos.x + pos.measured.let {
                                maxOf(it.baseWidth, it.furiganaWidth).toFloat()
                            }

                            if (offset.x in segLeft..segRight &&
                                offset.y in segTop..segBottom
                            ) {
                                onWordTap(pos.measured.index)
                                return@detectTapGestures
                            }
                        }
                    }
                }
            }
    ) { _, constraints ->
        // Perform line wrapping layout to compute total height
        val maxWidth = constraints.maxWidth
        val positions = layoutSegments(maxWidth)

        // Calculate total height
        val totalHeight = if (positions.isEmpty()) 0
        else (positions.last().y + lineHeight).toInt()

        layout(
            width = constraints.maxWidth,
            height = totalHeight.coerceAtLeast(0)
        ) {
            // No child placeables -- all rendering happens in drawBehind
        }
    }
}
