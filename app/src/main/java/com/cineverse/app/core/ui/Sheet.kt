package com.cineverse.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme

/**
 * Every sheet in the app, so they are the same sheet.
 *
 * One container colour, one corner radius, one grab handle, one navigation-bar
 * inset. A sheet that is 2dp rounder than the last one is the kind of thing
 * nobody can name and everybody feels.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CvSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CvTheme.colors
    // The sheet state is created INSIDE rather than taken as a parameter: an
    // experimental type in the signature would force every caller to opt in,
    // which is how one experimental API becomes twelve.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface1,
        contentColor = colors.text,
        shape = CvShape.Sheet,
        scrimColor = colors.scrim,
        dragHandle = { CvHandle() },
        modifier = modifier,
    ) {
        // A sheet is a window of its own, and a shared element cannot travel
        // between windows: a poster in a sheet that tried to crashed the app
        // ("layouts are not part of the same hierarchy"). Inside, posters are
        // plain posters.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSharedTransitionScope provides null,
            LocalNavAnimatedScope provides null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 10.dp)
                    .navigationBarsPadding(),
                content = content,
            )
        }
    }
}

@Composable
fun CvHandle() {
    val colors = CvTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CvShape.Pill)
                .background(colors.text3.copy(alpha = 0.4f))
        )
    }
}
