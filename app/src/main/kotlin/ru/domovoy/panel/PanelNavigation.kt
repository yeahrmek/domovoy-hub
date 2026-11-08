package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    time: String? = null,
    onPanelAccess: (() -> Unit)? = null,
    refreshingDevices: Boolean = false,
    onRefreshDevices: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val brand: @Composable () -> Unit = {
                TextButton(
                    onClick = onHome,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = MIN_TOUCH).semantics { contentDescription = "На главную" },
                ) {
                    PanelBrand()
                }
            }
            val refresh: @Composable () -> Unit = {
                if (onRefreshDevices != null) {
                    TextButton(
                        onClick = onRefreshDevices,
                        enabled = !refreshingDevices,
                        modifier = Modifier.heightIn(min = MIN_TOUCH),
                    ) {
                        Text(if (refreshingDevices) "Обновляем устройства…" else "Обновить устройства")
                    }
                }
            }
            val compactHeader = maxWidth / LocalDensity.current.fontScale < if (onRefreshDevices == null) 320.dp else 600.dp
            if (compactHeader) {
                Column(Modifier.fillMaxWidth()) {
                    brand()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (onRefreshDevices == null) Arrangement.End else Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        refresh()
                        if (time != null) {
                            Text(time, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    brand()
                    Spacer(Modifier.weight(1f))
                    refresh()
                    if (time != null) {
                        Text(time, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints {
            val wide = maxWidth / LocalDensity.current.fontScale >= 500.dp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onHome,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryFixedDim, contentColor = MaterialTheme.colorScheme.onPrimaryFixed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.heightIn(min = MIN_TOUCH),
                ) {
                    Icon(painterResource(R.drawable.ic_home_outline), contentDescription = null, modifier = Modifier.size(24.dp))
                    Text("Дом", modifier = Modifier.padding(start = 10.dp))
                }
                if (intercom != null) {
                    TextButton(
                        onClick = { onOpenApp(intercom.packageName) },
                        enabled = intercom.openable,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = MIN_TOUCH).semantics { contentDescription = "Открыть домофон" },
                    ) {
                        Icon(painterResource(R.drawable.ic_intercom_outline), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(if (intercom.openable) "Домофон" else "Домофон: нет приложения", modifier = Modifier.padding(start = 10.dp))
                    }
                }
                if (wide) Spacer(Modifier.weight(1f))
                if (onPanelAccess != null) {
                    TextButton(
                        onClick = onPanelAccess,
                        modifier = Modifier.heightIn(min = MIN_TOUCH),
                    ) {
                        Text("Защита")
                    }
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
