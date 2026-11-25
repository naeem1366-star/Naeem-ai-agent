package com.naeem.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared SUPRA visual language. Keep effects translucent and inexpensive for mid-range phones. */
object SupraGlass {
    val Night = Color(0xFF050B16)
    val Deep = Color(0xFF09172B)
    val Navy = Color(0xFF102744)
    val Ice = Color(0xFFB9D8FF)
    val Silver = Color(0xFFE7F0FC)
    val Muted = Color(0xFFA8BBD2)
    val Glass = Color(0x16D7E9FF)
    val GlassStrong = Color(0x25D7E9FF)
    val Edge = Color(0x45D7E9FF)
    val Glow = Color(0x6655A9FF)
}

@Composable
fun SupraGlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "supra-glass-atmosphere")
    val drift by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "supra-glass-light-drift"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SupraGlass.Night, SupraGlass.Deep, Color(0xFF0B1C34), SupraGlass.Night))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.maxDimension * 0.72f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x6654A8FF), Color(0x223A78C4), Color.Transparent),
                    center = Offset(size.width * drift, size.height * 0.20f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width * drift, size.height * 0.20f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x333F8DDA), Color(0x113F8DDA), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.82f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.88f, size.height * 0.82f)
            )
            // Fine orbital arcs give the background a restrained, recognizable SUPRA signature.
            drawCircle(
                color = Color(0x1C9CCBFF),
                radius = size.minDimension * 0.31f,
                center = Offset(size.width * 0.83f, size.height * 0.18f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        content()
    }
}

@Composable
fun SupraGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = Color(0x553B8FE8), spotColor = Color(0x553B8FE8))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x70FFFFFF), SupraGlass.Edge, Color(0x18FFFFFF))), shape),
        shape = shape,
        color = SupraGlass.Glass,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box {
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color(0x18FFFFFF), Color.Transparent, Color(0x080A65B8))))
                }
            ) { content() }
        }
    }
}

@Composable
fun SupraGlassChip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.border(1.dp, SupraGlass.Edge, RoundedCornerShape(50)),
        color = SupraGlass.GlassStrong,
        contentColor = SupraGlass.Silver,
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}
