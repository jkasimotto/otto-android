package com.otto.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otto.launcher.ui.theme.OttoGraphite
import com.otto.launcher.ui.theme.OttoSilver
import com.otto.launcher.ui.theme.OttoWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ProcessingOverlay(
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 420)),
        modifier = modifier.fillMaxSize()
    ) {
        ProcessingOverlayContent(
            label = label,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ProcessingOverlayContent(
    label: String,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        List(88) { index ->
            val random = Random(index * 73 + 11)
            ProcessingParticle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 1.1f + random.nextFloat() * 2.8f,
                alpha = 0.12f + random.nextFloat() * 0.28f,
                pull = 0.18f + random.nextFloat() * 0.82f,
                seed = random.nextFloat()
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "processing-overlay")
    val driftPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing)
        ),
        label = "drift-phase"
    )
    val pulsePhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing)
        ),
        label = "pulse-phase"
    )
    val scanPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "scan-phase"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.42f)
            val tau = (PI * 2).toFloat()
            val sweepY = size.height * (0.12f + 0.76f * scanPhase.value)

            drawRect(color = Color.Black.copy(alpha = 0.24f))

            val glowRadius = size.minDimension * (0.12f + 0.18f * pulsePhase.value)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OttoWhite.copy(alpha = 0.24f),
                        OttoGraphite.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius * 2.2f
                ),
                radius = glowRadius * 2.2f,
                center = center
            )

            particles.forEach { particle ->
                val angle = driftPhase.value * tau + particle.seed * tau
                val driftX = cos(angle) * (10f + 22f * particle.pull)
                val driftY = sin(angle * 1.37f) * (8f + 18f * particle.pull)
                val baseX = size.width * particle.x + driftX
                val baseY = size.height * particle.y + driftY
                val pull = 0.18f + 0.56f * particle.pull
                val x = lerp(baseX, center.x, pull)
                val y = lerp(baseY, center.y, pull * 0.92f)

                drawCircle(
                    color = OttoWhite.copy(
                        alpha = particle.alpha * 1.9f
                    ),
                    radius = particle.radius * (1f + 1.6f * particle.pull),
                    center = Offset(x, y)
                )
            }

            val lineAlpha = 0.11f
            drawLine(
                color = OttoWhite.copy(alpha = lineAlpha),
                start = Offset(0f, sweepY),
                end = Offset(size.width, sweepY),
                strokeWidth = 2f
            )

            repeat(12) { index ->
                val y = size.height * (index / 12f)
                drawLine(
                    color = OttoSilver.copy(alpha = 0.032f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            val ringRadius = size.minDimension * (0.08f + 0.22f * pulsePhase.value)
            drawCircle(
                color = OttoWhite.copy(alpha = 0.28f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = OttoSilver.copy(alpha = 0.18f),
                radius = ringRadius * 1.38f,
                center = center,
                style = Stroke(width = 1f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = OttoWhite.copy(alpha = 0.84f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 7.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Signal path engaged",
                color = OttoSilver.copy(alpha = 0.52f),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private data class ProcessingParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val pull: Float,
    val seed: Float
)

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
