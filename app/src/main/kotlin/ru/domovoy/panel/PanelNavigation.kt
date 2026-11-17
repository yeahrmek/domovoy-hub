package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ru.domovoy.R

/** The app brand and fixed shortcuts stay reachable while devices scroll underneath. */
@Composable
internal fun PanelNavigation(
    intercom: LauncherTileState?,
    rooms: List<PanelHeading>,
    roomsOpen: Boolean,
    onRoomsOpen: (Boolean) -> Unit,
    onHome: () -> Unit,
    onRoom: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    /** One-tap household apps after the intercom, in a scrollable row; see [QuickAppStore]. */
    apps: List<LauncherTileState> = emptyList(),
    /** Opens the app picker from the "+" at the end of the row; null hides it. */
    onAddApp: (() -> Unit)? = null,
    /** Receives the new package order after a long-press drag. */
    onReorderApps: (List<String>) -> Unit = {},
    time: String? = null,
    onPanelAccess: (() -> Unit)? = null,
    refreshingDevices: Boolean = false,
    onRefreshDevices: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val effectiveWidth = maxWidth / LocalDensity.current.fontScale
            val brand: @Composable () -> Unit = {
                TextButton(
                    onClick = onHome,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = MIN_TOUCH).semantics { contentDescription = "На главную" },
                ) {
                    PanelBrand(compact = effectiveWidth < 500.dp)
                }
            }
            val utilities: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (time != null) {
                        Text(time, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onRefreshDevices != null) {
                        PanelIconButton(
                            R.drawable.ic_refresh,
                            if (refreshingDevices) "Обновляем устройства…" else "Обновить устройства",
                            onRefreshDevices,
                            busy = refreshingDevices,
                        )
                    }
                    if (onPanelAccess != null) {
                        PanelIconButton(R.drawable.ic_shield, "Защита", onPanelAccess)
                    }
                }
            }
            val headerMinimum = if (onRefreshDevices != null || onPanelAccess != null) 360.dp else 280.dp
            if (effectiveWidth < headerMinimum) {
                Column(Modifier.fillMaxWidth()) {
                    brand()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { utilities() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    brand()
                    Spacer(Modifier.weight(1f))
                    utilities()
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints {
            val wide = maxWidth / LocalDensity.current.fontScale >= 500.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onHome,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryFixedDim, contentColor = MaterialTheme.colorScheme.onPrimaryFixed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.size(MIN_TOUCH),
                ) {
                    Icon(painterResource(R.drawable.ic_home_outline), contentDescription = "Дом", modifier = Modifier.size(26.dp))
                }
                if (intercom != null) {
                    TextButton(
                        onClick = { onOpenApp(intercom.packageName) },
                        enabled = intercom.openable,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = MIN_TOUCH).widthIn(min = MIN_TOUCH).semantics { contentDescription = "Открыть домофон" },
                    ) {
                        Icon(painterResource(R.drawable.ic_intercom_outline), contentDescription = null, modifier = Modifier.size(24.dp))
                        if (wide || !intercom.openable) {
                            Text(if (intercom.openable) "Домофон" else "Домофон: нет приложения", modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                if (apps.isEmpty() && onAddApp == null) {
                    Spacer(Modifier.weight(1f))
                } else {
                    QuickAppRow(apps, onOpenApp, onAddApp, onReorderApps, Modifier.weight(1f))
                }
                Box {
                    TextButton(
                        onClick = { onRoomsOpen(true) },
                        enabled = rooms.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = if (roomsOpen) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = MIN_TOUCH),
                    ) {
                        Icon(painterResource(R.drawable.ic_rooms_outline), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text("Комнаты", modifier = Modifier.padding(start = 10.dp))
                    }
                    DropdownMenu(expanded = roomsOpen, onDismissRequest = { onRoomsOpen(false) }, modifier = Modifier.heightIn(max = 600.dp)) {
                        rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(if (room.marked) "${room.title} · нет обновлений" else room.title) },
                                onClick = {
                                    onRoomsOpen(false)
                                    onRoom(room.title)
                                },
                                modifier = Modifier.heightIn(min = MIN_TOUCH).semantics { contentDescription = "Перейти: ${room.title}" },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Scrolls sideways when the apps outgrow the header; a long press picks one up to reorder it. */
@Composable
private fun QuickAppRow(
    apps: List<LauncherTileState>,
    onOpenApp: (String) -> Unit,
    onAddApp: (() -> Unit)?,
    onReorderApps: (List<String>) -> Unit,
    modifier: Modifier,
) {
    var preview by remember(apps) { mutableStateOf(apps) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val spacing = with(LocalDensity.current) { 8.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(preview, key = { it.packageName }) { app ->
            val isDragged = dragged == app.packageName
            TextButton(
                onClick = { onOpenApp(app.packageName) },
                enabled = app.openable,
                shape = RoundedCornerShape(10.dp),
                border = if (isDragged) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .then(if (isDragged) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer { translationX = if (isDragged) dragX else 0f }
                    .onSizeChanged { widths[app.packageName] = it.width }
                    .heightIn(min = MIN_TOUCH)
                    .widthIn(min = MIN_TOUCH)
                    .semantics { contentDescription = "Открыть ${app.name}" }
                    .pointerInput(app.packageName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragged = app.packageName
                                dragX = 0f
                            },
                            onDragEnd = {
                                val order = preview.map { it.packageName }
                                dragged = null
                                dragX = 0f
                                if (order != apps.map { it.packageName }) onReorderApps(order)
                            },
                            onDragCancel = {
                                dragged = null
                                dragX = 0f
                                preview = apps
                            },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount.x
                            val index = preview.indexOfFirst { it.packageName == app.packageName }
                            val next = preview.getOrNull(index + 1)
                            val previous = preview.getOrNull(index - 1)
                            // Swap once the dragged button covers half of its neighbour.
                            if (next != null && dragX > (widths[next.packageName] ?: 0) / 2f + spacing) {
                                dragX -= (widths[next.packageName] ?: 0) + spacing
                                preview = preview.moved(index, index + 1)
                            } else if (previous != null && dragX < -((widths[previous.packageName] ?: 0) / 2f + spacing)) {
                                dragX += (widths[previous.packageName] ?: 0) + spacing
                                preview = preview.moved(index, index - 1)
                            }
                        }
                    },
            ) {
                Text(if (app.openable) app.name else "${app.name}: нет приложения")
            }
        }
        if (onAddApp != null) {
            item(key = "add") {
                TextButton(
                    onClick = onAddApp,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(MIN_TOUCH).semantics { contentDescription = "Добавить приложение" },
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
