package com.cineverse.app.feature.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.UserList

/**
 * Which lists a title belongs to.
 *
 * The watchlist is always there and cannot be removed — it is the list the
 * Bookmark button writes to, and a title that is in no list at all is a title
 * that has quietly vanished. Everything else is the user own lists, with
 * create, rename and delete in the same sheet so naming a new list never costs
 * a trip to another screen.
 *
 * Changes are written as they are made, not on a Done button. A sheet that can
 * be swiped away must not be able to lose work by being swiped away.
 */
@Composable
fun ListSheet(
    title: String,
    allLists: List<UserList>,
    membership: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The website stores the watchlist as a list of its own; it has a fixed
    // row above, and a second row with the same key would crash the column.
    val lists = remember(allLists) { allLists.filter { it.id != "watchlist" } }
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }

    CvSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SAVE TO", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 2,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.glass)
                    .clickableNoRipple { haptics?.play(Haptic.Tap); creating = !creating; renaming = null },
                contentAlignment = Alignment.Center,
            ) {
                val spin by animateFloatAsState(
                    targetValue = if (creating) 45f else 0f,
                    animationSpec = Motion.lively(),
                    label = "plus",
                )
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = if (creating) "Cancel" else "New list",
                    tint = colors.text,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = spin },
                )
            }
        }

        AnimatedVisibility(
            visible = creating,
            enter = expandVertically(Motion.size()) + fadeIn(),
            exit = shrinkVertically(Motion.size()) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                NameField(
                    initial = "",
                    placeholder = "Name your list",
                    onDone = { name ->
                        if (name.isNotBlank()) {
                            haptics?.play(Haptic.Success)
                            onCreate(name.trim())
                        }
                        creating = false
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // The sheet is capped rather than allowed to grow: forty lists must not
        // push the create field off the top of a phone.
        LazyColumn(
            Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "watchlist") {
                val checked = membership.contains("watchlist")
                ListRow(
                    name = "Watchlist",
                    sub = null,
                    checked = checked,
                    locked = false,
                    onToggle = {
                        haptics?.play(if (checked) Haptic.Untick else Haptic.Tick)
                        onToggle("watchlist", !checked)
                    },
                )
            }
            items(lists, key = { it.id }) { list ->
                if (renaming == list.id) {
                    NameField(
                        initial = list.name,
                        placeholder = "List name",
                        onDone = { name ->
                            if (name.isNotBlank() && name.trim() != list.name) {
                                haptics?.play(Haptic.Success)
                                onRename(list.id, name.trim())
                            }
                            renaming = null
                        },
                    )
                } else {
                    val checked = membership.contains(list.id)
                    ListRow(
                        name = list.name,
                        sub = null,
                        checked = checked,
                        locked = false,
                        armed = confirming == list.id,
                        onToggle = {
                            haptics?.play(if (checked) Haptic.Untick else Haptic.Tick)
                            onToggle(list.id, !checked)
                        },
                        onRename = { haptics?.play(Haptic.Tap); renaming = list.id; creating = false },
                        onDelete = {
                            // Two taps, not a dialog: the first arms the row and
                            // turns it red, the second commits. A modal over a
                            // sheet is a stack nobody enjoys.
                            if (confirming == list.id) {
                                haptics?.play(Haptic.Drop)
                                onDelete(list.id)
                                confirming = null
                            } else {
                                haptics?.play(Haptic.Warning)
                                confirming = list.id
                            }
                        },
                    )
                }
            }
            if (lists.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No lists yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }

    // Arming a delete must not outlive the row it armed.
    LaunchedEffect(lists.size) { confirming = null }
}

@Composable
private fun ListRow(
    name: String,
    sub: String?,
    checked: Boolean,
    locked: Boolean,
    armed: Boolean = false,
    onToggle: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = CvTheme.colors
    val tint = if (armed) colors.pink else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Medium)
            .background(if (armed) colors.pink.copy(alpha = 0.12f) else colors.glass)
            .border(
                1.dp,
                if (armed) colors.pink.copy(alpha = 0.5f) else colors.hairline,
                CvShape.Medium,
            )
            .clickableNoRipple(onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CvShape.Tiny)
                .background(if (checked) colors.text else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) colors.text else colors.text3.copy(alpha = 0.5f),
                    CvShape.Tiny,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (armed) "Delete this list?" else name,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 1,
            )
            if (sub != null || armed) {
                Text(
                    if (armed) "Titles stay in your watchlist" else sub.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (armed) colors.pink.copy(alpha = 0.8f) else colors.text3,
                )
            }
        }
        if (locked) {
            Icon(
                Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = colors.text3,
                modifier = Modifier.size(17.dp),
            )
        } else {
            if (onRename != null && !armed) {
                IconTap(Icons.Rounded.Edit, "Rename", colors.text3, onRename)
                Spacer(Modifier.width(4.dp))
            }
            if (onDelete != null) {
                IconTap(
                    if (armed) Icons.Rounded.Delete else Icons.Rounded.Close,
                    if (armed) "Confirm delete" else "Delete",
                    if (armed) colors.pink else colors.text3,
                    onDelete,
                )
            }
        }
    }
}

@Composable
private fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** One text field, focused on arrival, committing on the keyboard Done key. */
@Composable
private fun NameField(initial: String, placeholder: String, onDone: (String) -> Unit) {
    val colors = CvTheme.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Medium)
            .background(colors.surface2)
            .border(1.dp, colors.text.copy(alpha = 0.22f), CvShape.Medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.text3)
            }
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 60) text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .height(32.dp)
                .clip(CvShape.Pill)
                .background(if (text.isBlank()) colors.glass else colors.text)
                .clickableNoRipple { onDone(text) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Done",
                style = MaterialTheme.typography.labelMedium,
                color = if (text.isBlank()) colors.text3 else colors.ink,
            )
        }
    }
}
