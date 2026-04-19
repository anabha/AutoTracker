package com.tyson.autotracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * Universal modifier to turn any composable into a frosted glass card.
 * Updated to match the React app: uses Surface color for deep, rich cards.
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.85f, // High opacity (85%) to look solid but still glassy
    borderAlpha: Float = 0.5f // Crisp border line
): Modifier {
    // Uses the "surface" color (Deep Slate in dark mode, White in light mode)
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)

    // Uses the "outline" color for a subtle, mode-aware border
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)

    return this
        .clip(shape)
        .background(containerColor)
        .border(1.dp, borderColor, shape)
}

/**
 * A beautiful glowing orb background with a heavy blur effect.
 * Wrap your Scaffolds in this for a premium glassmorphism look!
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary

    // Uses the true background color
    val bg = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        // Orb Layer with heavy blur
        Box(modifier = Modifier.fillMaxSize().blur(80.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top Right Orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.1f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.9f, size.height * 0.1f)
                )

                // Bottom Left Orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.8f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.1f, size.height * 0.8f)
                )
            }
        }
        content()
    }
}