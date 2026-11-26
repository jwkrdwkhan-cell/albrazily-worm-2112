package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LiquidGold
import com.example.ui.theme.NeuralCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.VioletPulse
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NexusNeuralOrb(
    isActive: Boolean,
    size: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neural_orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 2600 else 9000,
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 750 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isActive) NeuralCyan else LiquidGold).copy(alpha = 0.28f * pulse),
                        Color.Transparent
                    )
                ),
                radius = radius * pulse
            )

            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(LiquidGold, NeuralCyan, VioletPulse, LiquidGold)
                    ),
                    startAngle = 0f,
                    sweepAngle = 290f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            rotate(-rotation * 1.4f) {
                drawArc(
                    color = NeuralCyan.copy(alpha = 0.6f),
                    startAngle = 45f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        this.size.width - 16.dp.toPx(),
                        this.size.height - 16.dp.toPx()
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.img_nexus_logo),
            contentDescription = "Nexus AI Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size * 0.68f)
                .clip(CircleShape)
                .border(1.5.dp, LiquidGold.copy(alpha = 0.7f), CircleShape)
        )
    }
}

@Composable
fun AnimatedAiThinkingBar(
    phaseLabel: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking_bar")
    val shimmerX by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing)
        ),
        label = "shimmer"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianElevated)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        LiquidGold.copy(alpha = 0.25f),
                        NeuralCyan.copy(alpha = 0.85f),
                        VioletPulse.copy(alpha = 0.35f)
                    ),
                    start = Offset(shimmerX * 400f, 0f),
                    end = Offset((shimmerX + 0.5f) * 400f, 120f)
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusNeuralOrb(isActive = true, size = 34.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = phaseLabel.ifBlank { "محرك Nexus يحلل ويعالج الآن..." },
            style = MaterialTheme.typography.labelLarge,
            color = NeuralCyan,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LiveVoiceWaveform(
    isListening: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 900 else 2200, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        val bars = 26
        val spacing = size.width / bars
        val barWidth = spacing * 0.45f
        for (i in 0 until bars) {
            val factor = if (isListening) {
                0.28f + 0.68f * ((sin(phase + i * 0.55f) + 1f) / 2f)
            } else {
                0.15f + 0.12f * ((sin(phase + i * 0.4f) + 1f) / 2f)
            }
            val barH = size.height * factor
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barH) / 2f
            drawLine(
                color = accentColor.copy(alpha = 0.4f + 0.6f * factor),
                start = Offset(x + barWidth / 2f, y),
                end = Offset(x + barWidth / 2f, y + barH),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AnimatedMessageReveal(
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) +
            slideInVertically(
                initialOffsetY = { it / 5 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        content()
    }
}

@Composable
fun GlowingStreamingContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "stream_glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianCard)
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NeuralCyan.copy(alpha = glowAlpha),
                        LiquidGold.copy(alpha = glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            content()
        }
    }
}
