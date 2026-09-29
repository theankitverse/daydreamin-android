package com.daydreamin.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.update.UpdateChecker
import com.daydreamin.app.data.update.UpdateStatus
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * A quiet, dismissible card on Home that appears only once a newer build than this one has
 * actually been published — the only "push an update" a sideloaded app can have. The check that
 * feeds it already ran in the background at launch (see [UpdateChecker]); this composable only
 * reads the result, so it costs nothing on every other screen and on every launch where there's
 * nothing new.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val state by UpdateChecker.state.collectAsState()
    val remote = state.remote
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dismissedHere by remember(remote?.versionCode) { mutableStateOf(false) }
    var alreadyDismissed by remember(remote?.versionCode) { mutableStateOf(true) } // assume dismissed until proven otherwise, so it never flashes on
    LaunchedEffect(remote?.versionCode) {
        alreadyDismissed = remote?.let { UpdateChecker.isDismissed(it.versionCode) } ?: true
    }
    val visible = state.status == UpdateStatus.AVAILABLE && remote != null && !alreadyDismissed && !dismissedHere

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring()) + expandVertically(spring()),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        if (remote != null) {
            Row(
                Modifier
                    .padding(horizontal = Space.gutter)
                    .fillMaxWidth()
                    .glass(Radius.panelShape, Glass.Clear)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Update available — v${remote.versionName}", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        remote.notes?.takeIf { it.isNotBlank() } ?: "Tap to get the latest version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Update",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                    modifier = Modifier
                        .pressable(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(remote.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        })
                        .clip(Radius.pill)
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(32.dp).pressable(onClick = {
                        dismissedHere = true
                        scope.launch { UpdateChecker.dismiss(remote.versionCode) }
                    }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
