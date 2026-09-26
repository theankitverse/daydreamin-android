package com.daydreamin.app.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import kotlin.random.Random

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "shooting-star")
    val streak by transition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "streak",
    )

    val starFractions = remember {
        val rnd = Random(42)
        List(60) { rnd.nextFloat() to rnd.nextFloat() * 0.7f }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D1224), BgBase, Color.Black)),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            starFractions.forEach { (fx, fy) ->
                drawCircle(Color.White.copy(alpha = 0.5f), radius = 1.5f, center = Offset(fx * size.width, fy * size.height))
            }

            // shooting star streak
            val startX = size.width * streak
            val startY = size.height * 0.18f
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, Color.White, Color.Transparent)),
                start = Offset(startX + 80f, startY - 40f),
                end = Offset(startX, startY),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )

            // mountain silhouette
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * 0.82f)
                lineTo(size.width * 0.25f, size.height * 0.68f)
                lineTo(size.width * 0.45f, size.height * 0.8f)
                lineTo(size.width * 0.7f, size.height * 0.62f)
                lineTo(size.width, size.height * 0.78f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color = Color(0xFF141826))
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Daydreamin", style = MaterialTheme.typography.headlineLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Music for a calmer you", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = BrandPurple, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Text("Good music. brighter days.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
