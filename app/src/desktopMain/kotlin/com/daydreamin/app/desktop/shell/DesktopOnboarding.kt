package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.resources.Res
import com.daydreamin.app.resources.splash_logo
import com.daydreamin.app.titleBarHitTest
import com.daydreamin.app.ui.screens.onboarding.OnboardingScreen
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import org.jetbrains.compose.resources.painterResource

/**
 * First-run setup on a computer: Daydreamin introduced on the left, the setup steps (the phone's
 * own, on a card of their size) on the right. On a narrow window just the card, in the middle.
 */
@Composable
fun DesktopOnboarding(onDone: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(BrandViolet.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = size.maxDimension * 0.8f))
                drawRect(Brush.radialGradient(listOf(BrandPink.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.95f, size.height), radius = size.maxDimension * 0.7f))
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TitleStrip()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 40.dp, top = 8.dp)) {
                val wide = maxWidth >= 940.dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally)) {
                    if (wide) Intro(Modifier.weight(1f).widthIn(max = 520.dp))
                    Box(
                        Modifier
                            .width(460.dp)
                            .heightIn(max = 720.dp)
                            .fillMaxHeight()
                            .shadow(24.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)),
                    ) { OnboardingScreen(onDone = onDone) }
                }
            }
        }
    }
}

@Composable
private fun Intro(modifier: Modifier) {
    Column(modifier) {
        Image(painterResource(Res.drawable.splash_logo), contentDescription = null, modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(24.dp))
        Text("Music from\neverywhere.", style = DType.hero.copy(fontSize = 56.sp, lineHeight = 60.sp))
        Spacer(Modifier.height(14.dp))
        Text("Daydreamin plays it beautifully on your computer: mixes made for you, lyrics that follow along, and songs you can keep.", style = DType.body.copy(fontSize = 18.sp, lineHeight = 26.sp, color = Color.White.copy(alpha = 0.75f)))
        Spacer(Modifier.height(32.dp))
        Fact(Icons.Rounded.Lock, "No account", "Nothing to sign up for. Your library stays on this computer.")
        Fact(Icons.Rounded.DoNotDisturbOn, "No ads", "Just the music, start to finish.")
        Fact(Icons.Rounded.CloudOff, "Works offline", "Download songs and playlists to play without a connection.")
    }
}

@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = DType.itemTitleStrong.copy(fontSize = 15.sp))
            Text(body, style = DType.body)
        }
    }
}

/** The top of the window, where the window buttons are: drags the window like a title bar. */
@Composable
private fun TitleStrip() {
    val titleBar = LocalTitleBar.current
    val window = LocalWindowScope.current
    Box(Modifier.fillMaxWidth().height(DSize.topBarHeight).then(if (titleBar != null) Modifier.titleBarHitTest(titleBar) else Modifier)) {
        if (titleBar == null && window != null) with(window) { WindowDraggableArea(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) } }
    }
}
