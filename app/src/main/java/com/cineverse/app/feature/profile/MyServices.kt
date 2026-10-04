package com.cineverse.app.feature.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.data.tmdb.ProviderDto

/**
 * "My services" on the profile: the streaming services you pay for. Shown as
 * their logos in a row, and set from a sheet of every service in your
 * region. Where to watch puts them first and says "Included with your
 * Netflix"; Gemini's picks lean towards what you can actually play.
 */
@Composable
internal fun MyServicesCard(viewModel: ProfileViewModel, mine: Set<Int>, region: String) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var picking by remember { mutableStateOf(false) }
    var all by remember(region) { mutableStateOf<List<ProviderDto>?>(null) }
    LaunchedEffect(region, mine.isNotEmpty() || picking) {
        if (all == null && (mine.isNotEmpty() || picking)) all = viewModel.streamingServices()
    }
    val chosen = remember(all, mine) { all.orEmpty().filter { it.providerId in mine } }

    Column(
        Modifier
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 22.dp)
            .glass(CvShape.XLarge)
            .clickableNoRipple { haptics?.play(Haptic.Tap); picking = true }
            .semantics { role = Role.Button }
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LiveTv, null, tint = colors.text2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("My services", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    when (mine.size) {
                        0 -> "Tell CineVerse what you subscribe to"
                        1 -> "1 service"
                        else -> "${mine.size} services"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = colors.text3, modifier = Modifier.size(15.dp))
        }
        if (chosen.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = chosen.size > 6,
            ) {
                items(chosen, key = { it.providerId }) { service ->
                    CvImage(
                        Img.provider(service.logoPath),
                        service.providerName,
                        Modifier
                            .size(40.dp)
                            .clip(CvShape.Small)
                            .border(1.dp, colors.hairline, CvShape.Small),
                    )
                }
            }
        }
    }

    if (picking) {
        ServicesSheet(
            all = all,
            mine = mine,
            onToggle = { id ->
                haptics?.play(if (id in mine) Haptic.Untick else Haptic.Tick)
                viewModel.setMyServices(if (id in mine) mine - id else mine + id)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ServicesSheet(
    all: List<ProviderDto>?,
    mine: Set<Int>,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    CvSheet(onDismiss = onDismiss) {
        Text("My services", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap the ones you subscribe to. They come first wherever a title can be watched.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text3,
        )
        Spacer(Modifier.height(16.dp))
        when {
            all == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.text2, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            all.isEmpty() -> Text(
                "Couldn't load the services in your region. Check the connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            else -> {
                // Yours first, in the order they were when the sheet opened: a
                // tile does not run away from your thumb as you tap it.
                val order = remember(all) { all.sortedByDescending { it.providerId in mine } }
                LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(order, key = { it.providerId }) { service ->
                    ServiceTile(service, service.providerId in mine, Modifier) { onToggle(service.providerId) }
                }
            }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ServiceTile(service: ProviderDto, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val pop by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "servicePick",
    )
    Column(
        modifier
            .clickableNoRipple(onClick)
            .semantics(mergeDescendants = true) { selected = on; role = Role.Checkbox },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        // A press-and-settle as it is picked; dimmed when not.
                        val s = 0.92f + 0.08f * pop
                        scaleX = s; scaleY = s
                        alpha = 0.55f + 0.45f * pop
                    }
                    .clip(CvShape.Medium)
                    .border(if (on) 2.dp else 1.dp, if (on) colors.green else colors.hairline, CvShape.Medium),
            ) {
                CvImage(Img.provider(service.logoPath), null, Modifier.fillMaxSize())
            }
            if (pop > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .graphicsLayer { scaleX = pop; scaleY = pop }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = colors.ink, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            service.providerName,
            style = MaterialTheme.typography.labelSmall,
            color = if (on) colors.text else colors.text3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
