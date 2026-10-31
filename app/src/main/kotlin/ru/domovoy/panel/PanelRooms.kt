package ru.domovoy.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

/** Room failures use both an error colour and a dot so the signal survives colour filters. */
private const val MARK = " •"

/** Extra space above attaches each heading to the tiles below it. */
private val HEADING_TOP = 20.dp

/** Under a heading, before the first row of its tiles. */
private val HEADING_BOTTOM = 8.dp

/** Align room headings with tile text: 4 dp outer padding plus 12 dp content padding. */
private val HEADING_INDENT = 16.dp

/** All rooms share one scroll. Главная repeats favourites and surfaces stale or failing devices first. */
@Composable
fun PanelRooms(
    acs: AcPanelState,
    curtains: CurtainPanelState,
    strips: LightStripPanelState,
    recuperators: RecuperatorPanelState,
    bulbs: BulbPanelState,
    launchers: List<LauncherTileState>,
    now: Instant,
    /** The interval the Yandex groups are polled on, which is what makes their readings stale. */
    yandexInterval: Duration,
    /** Tuya LAN polling has its own active and idle intervals. */
    tuyaInterval: Duration,
    modifier: Modifier = Modifier,
    discovered: DiscoveredPanelState = DiscoveredPanelState(),
    /** Hoisted so idle reset can return the panel to Главная. */
    scroll: LazyGridState = rememberLazyGridState(),
    /** Open device id survives polling updates. Idle reset and intercom calls close the sheet externally. */
    openSheet: MutableState<String?> = remember { mutableStateOf(null) },
    roomPickerOpen: MutableState<Boolean> = remember { mutableStateOf(false) },
    launcherErrors: Map<String, String> = emptyMap(),
    onToggleAc: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
    onSetAcMode: (String, String, String) -> Unit = { _, _, _ -> },
    onSetAcToggle: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSetOpen: (String, Double) -> Unit = { _, _ -> },
    onToggleStrip: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
    onToggleRecuperator: (String) -> Unit = {},
    onSetRecuperatorSpeed: (String, FanSpeed) -> Unit = { _, _ -> },
    onToggleBulb: (String) -> Unit = {},
    onSetBulbBrightness: (String, Double) -> Unit = { _, _ -> },
    onSetBulbScene: (String, String) -> Unit = { _, _ -> },
    onSetBulbRgb: (String, Int) -> Unit = { _, _ -> },
    onOpenApp: (String) -> Unit = {},
) {
    val sections =
        roomSections(acs.tiles, curtains.tiles, strips.tiles, recuperators.tiles, bulbs.tiles, launchers, discovered.tiles)
    val errors =
        GroupErrors(
            acs = acs.error,
            curtains = curtains.error,
            strips = strips.error,
            recuperators = recuperators.error,
            bulbs = bulbs.error,
            discovered = discovered.error,
        )
    // Keep polling ages separate so a stopped group cannot borrow another group’s freshness.
    val polls =
        GroupPolls(
            acs = acs.lastPolledAt,
            curtains = curtains.lastPolledAt,
            strips = strips.lastPolledAt,
            recuperators = recuperators.lastPolledAt,
            bulbs = bulbs.lastPolledAt,
            discovered = discovered.lastPolledAt,
        )
    val headings = panelHeadings(sections, errors, polls, now, yandexInterval, tuyaInterval)
    val scope = rememberCoroutineScope()
    val roomIndices = remember { mutableMapOf<String, Int>() }
    val intercom = launchers.firstOrNull { it.packageName == "com.domonap.app" }
    // Return to the top when polled devices first arrive after boot; later device-count changes preserve scroll.
    // Launchers are excluded because they exist before the first poll.
    val polled = sections.sumOf { it.tileCount() - it.launchers.size } > 0
    var wasPolled by remember { mutableStateOf(polled) }
    LaunchedEffect(polled) {
        if (polled && !wasPolled) scroll.scrollToItem(0)
        wasPolled = polled
    }
    // Rooms expand independently and start collapsed after a reboot. Idle reset only scrolls to Главная.
    var openLamps by remember { mutableStateOf(emptySet<String>()) }
    val sheetIsOpen = openSheet.value?.let { id ->
        acs.tiles.any { it.id == id } ||
            curtains.tiles.any { it.id == id } ||
            strips.tiles.any { it.id == id } ||
            recuperators.tiles.any { it.id == id } ||
            bulbs.tiles.any { it.id == id } ||
            discovered.tiles.any { it.id == id }
    } == true
    // Keep the mosaic mounted beneath the sheet.
    Box(modifier = modifier) {
        Column(modifier = if (sheetIsOpen) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier) {
            PanelNavigation(
                intercom = intercom,
                time = now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                rooms = headings.filter { it.title != "Главная" },
                roomsOpen = roomPickerOpen.value,
                onRoomsOpen = { roomPickerOpen.value = it },
                onHome = { scope.launch { returnToHome(scroll, openSheet) } },
                onRoom = { room -> roomIndices[room]?.let { index -> scope.launch { scroll.scrollToItem(index) } } },
                onOpenApp = onOpenApp,
            )
            intercom?.let { launcherErrors[it.packageName] }?.let { reason ->
                Text(reason, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            // Full-span rows keep card formats separate and preserve the hoisted scroll state.
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = scroll,
                modifier = Modifier.weight(1f),
            ) {
                roomIndices.clear()
                var itemIndex = groupFailures(acs, curtains, strips, recuperators, bulbs, discovered).size
                // Groups that fail before loading any tiles need a full-width message above the rooms.
                items(
                    groupFailures(acs, curtains, strips, recuperators, bulbs, discovered),
                    key = { "failure:$it" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { failure ->
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                headings.forEach { heading ->
                    val section = heading.section
                    // Prefix keys by section because a device can appear in both Главная and its room.
                    val room = heading.title
                    roomIndices[room] = itemIndex++
                    item(key = "heading:$room", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeading(title = room, marked = heading.marked)
                    }
                    val noticeSection = if (heading.title == "Главная") {
                        RoomSection(null, acs.tiles, curtains.tiles, strips.tiles, recuperators.tiles, bulbs.tiles, discovered = discovered.tiles)
                    } else {
                        section
                    }
                    items(sectionFailures(noticeSection, errors), key = { "$room/failure:$it" }, span = { GridItemSpan(maxLineSpan) }) { failure ->
                        Text(
                            failure,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = HEADING_INDENT, vertical = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    itemIndex += sectionFailures(noticeSection, errors).size
                    val showRoom = heading.title == "Главная"
                    fun bulbEntry(tile: BulbTileState, prefix: String) = PanelTile(
                        "$room/$prefix:${tile.id}",
                        if (tile.brightnessBounds != null) TileFormat.Control else TileFormat.Compact,
                    ) { modifier ->
                        if (showRoom) {
                            HomeDeviceTile(
                                anatomy = anatomy(tile, now, bulbs.commandErrors[tile.id]).copy(status = power(tile.isOn)),
                                paint = paint(tile, bulbs.error),
                                polledAt = bulbs.lastPolledAt,
                                reportedAt = oldest(tile.readings()),
                                commandError = bulbs.commandErrors[tile.id],
                                now = now,
                                interval = yandexInterval,
                                pending = tile.id in bulbs.pending,
                                modifier = modifier,
                                onOpen = { openSheet.value = tile.id },
                                controls = {
                                    tile.brightnessBounds?.let { bounds ->
                                        LevelControl(tile.id, tile.brightnessPercent, bounds, onSetValue = { onSetBulbBrightness(tile.id, it) }, label = "Яркость", enabled = tile.id !in bulbs.pending)
                                    }
                                },
                                toggle = {
                                    TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, bulbs.error).mood, { onToggleBulb(tile.id) }, enabled = tile.id !in bulbs.pending)
                                },
                            )
                        } else {
                            BulbTile(
                                tile = tile,
                                now = now,
                                modifier = modifier,
                                error = bulbs.commandErrors[tile.id],
                                pending = tile.id in bulbs.pending,
                                showRoom = showRoom,
                                onOpen = { openSheet.value = tile.id },
                                onToggle = onToggleBulb,
                                onSetBrightness = onSetBulbBrightness,
                            )
                        }
                    }
                    val entries = buildList {
                        section.acs.forEach { tile ->
                            add(
                                PanelTile("$room/ac:${tile.id}", if (showRoom) TileFormat.Featured else TileFormat.Control) { modifier ->
                                    if (showRoom) {
                                        HomeDeviceTile(
                                            anatomy = anatomy(tile, now, acs.commandErrors[tile.id]).copy(status = power(tile.isOn)),
                                            paint = paint(tile, acs.error),
                                            polledAt = acs.lastPolledAt,
                                            reportedAt = oldest(tile.readings()),
                                            commandError = acs.commandErrors[tile.id],
                                            now = now,
                                            interval = yandexInterval,
                                            pending = tile.id in acs.pending,
                                            modifier = modifier,
                                            onOpen = { openSheet.value = tile.id },
                                            primaryControl = {
                                                val bounds = tile.bounds
                                                if (bounds != null) {
                                                    OrbitTemperatureControl(
                                                        id = tile.id,
                                                        value = tile.targetTemperature,
                                                        bounds = bounds,
                                                        enabled = tile.id !in acs.pending,
                                                        unit = if (tile.unit == "unit.temperature.celsius") "°C" else "",
                                                        onSetValue = { onSetTemperature(tile.id, it) },
                                                    )
                                                } else {
                                                    PromotedValue(promoted(tile))
                                                }
                                            },
                                            toggle = {
                                                TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, acs.error).mood, { onToggleAc(tile.id) }, enabled = tile.id !in acs.pending)
                                            },
                                        )
                                    } else {
                                        AcTile(
                                            tile = tile,
                                            now = now,
                                            modifier = modifier,
                                            error = acs.commandErrors[tile.id],
                                            pending = tile.id in acs.pending,
                                            showRoom = showRoom,
                                            onOpen = { openSheet.value = tile.id },
                                            onToggle = onToggleAc,
                                            onSetTemperature = onSetTemperature,
                                            onSetMode = onSetAcMode,
                                        )
                                    }
                                },
                            )
                        }
                        section.curtains.forEach { tile ->
                            add(
                                PanelTile("$room/curtain:${tile.id}", if (showRoom) TileFormat.Wide else TileFormat.Control) { modifier ->
                                    if (showRoom) {
                                        HomeDeviceTile(
                                            anatomy = anatomy(tile, now, curtains.commandErrors[tile.id]).copy(status = "Положение шторы"),
                                            paint = paint(tile, now, curtains.error),
                                            polledAt = curtains.lastPolledAt,
                                            reportedAt = position(tile)?.reading,
                                            commandError = curtains.commandErrors[tile.id],
                                            now = now,
                                            interval = yandexInterval,
                                            pending = tile.id in curtains.pending,
                                            modifier = modifier,
                                            onOpen = { openSheet.value = tile.id },
                                            horizontalValue = true,
                                            controls = {
                                                tile.bounds?.let { bounds ->
                                                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                        androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.min) }, enabled = tile.id !in curtains.pending, modifier = Modifier.heightIn(min = MIN_TOUCH)) { Text("Закрыть") }
                                                        LevelControl(
                                                            id = tile.id,
                                                            value = position(tile)?.percent,
                                                            bounds = bounds,
                                                            label = "Открытие",
                                                            enabled = tile.id !in curtains.pending,
                                                            modifier = Modifier.weight(1f),
                                                            onSetValue = { onSetOpen(tile.id, it) },
                                                        )
                                                        androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.max) }, enabled = tile.id !in curtains.pending, modifier = Modifier.heightIn(min = MIN_TOUCH)) { Text("Открыть") }
                                                    }
                                                }
                                            },
                                        )
                                    } else {
                                        CurtainTile(
                                            tile = tile,
                                            now = now,
                                            modifier = modifier,
                                            error = curtains.commandErrors[tile.id],
                                            pending = tile.id in curtains.pending,
                                            showRoom = showRoom,
                                            onOpen = { openSheet.value = tile.id },
                                            onSetOpen = onSetOpen,
                                        )
                                    }
                                },
                            )
                        }
                        section.strips.forEach { tile ->
                            add(
                                PanelTile("$room/strip:${tile.id}", TileFormat.Control) { modifier ->
                                    if (showRoom) {
                                        HomeDeviceTile(
                                            anatomy = anatomy(tile, now, strips.commandErrors[tile.id]).copy(status = power(tile.isOn)),
                                            paint = paint(tile, strips.error),
                                            polledAt = strips.lastPolledAt,
                                            reportedAt = oldest(tile.readings()),
                                            commandError = strips.commandErrors[tile.id],
                                            now = now,
                                            interval = yandexInterval,
                                            pending = tile.id in strips.pending,
                                            modifier = modifier,
                                            onOpen = { openSheet.value = tile.id },
                                            controls = {
                                                tile.bounds?.let { bounds ->
                                                    LevelControl(
                                                        id = tile.id,
                                                        value = tile.brightnessPercent,
                                                        bounds = bounds,
                                                        label = "Яркость",
                                                        unit = if (tile.unit == "unit.percent") "%" else "",
                                                        enabled = tile.id !in strips.pending,
                                                        onSetValue = { onSetBrightness(tile.id, it) },
                                                    )
                                                }
                                            },
                                            toggle = {
                                                TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, strips.error).mood, { onToggleStrip(tile.id) }, enabled = tile.id !in strips.pending)
                                            },
                                        )
                                    } else {
                                        LightStripTile(
                                            tile = tile,
                                            now = now,
                                            modifier = modifier,
                                            error = strips.commandErrors[tile.id],
                                            pending = tile.id in strips.pending,
                                            showRoom = showRoom,
                                            onOpen = { openSheet.value = tile.id },
                                            onToggle = onToggleStrip,
                                            onSetBrightness = onSetBrightness,
                                        )
                                    }
                                },
                            )
                        }
                        section.recuperators.forEach { tile ->
                            add(
                                PanelTile("$room/recuperator:${tile.id}", if (showRoom) TileFormat.Control else TileFormat.Reading) { modifier ->
                                    if (showRoom) {
                                        HomeDeviceTile(
                                            anatomy = anatomy(tile, now, recuperators.commandErrors[tile.id]).copy(status = if (tile.online == false) "Нет связи" else listOfNotNull(power(tile.isOn), speedLabel(tile).takeUnless { it == "Нет данных" }).joinToString(" · ")),
                                            paint = paint(tile, recuperators.error),
                                            polledAt = recuperators.lastPolledAt,
                                            reportedAt = oldest(tile.readings()),
                                            commandError = recuperators.commandErrors[tile.id],
                                            now = now,
                                            interval = tuyaInterval,
                                            pending = tile.id in recuperators.pending,
                                            modifier = modifier,
                                            onOpen = { openSheet.value = tile.id },
                                            controls = {
                                                if (tile.online != false && tile.error == null) {
                                                    FanSpeedControls(
                                                        speeds = FanSpeed.entries,
                                                        selected = tile.speeds,
                                                        enabled = tile.id !in recuperators.pending && tile.isOn == true,
                                                        onSetSpeed = { onSetRecuperatorSpeed(tile.id, it) },
                                                    )
                                                }
                                            },
                                            toggle = {
                                                TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, recuperators.error).mood, { onToggleRecuperator(tile.id) }, enabled = tile.id !in recuperators.pending)
                                            },
                                        )
                                    } else {
                                        RecuperatorTile(
                                            tile = tile,
                                            now = now,
                                            modifier = modifier,
                                            groupError = recuperators.commandErrors[tile.id],
                                            pending = tile.id in recuperators.pending,
                                            showRoom = showRoom,
                                            onOpen = { openSheet.value = tile.id },
                                            onToggle = onToggleRecuperator,
                                            onSetSpeed = onSetRecuperatorSpeed,
                                        )
                                    }
                                },
                            )
                        }
                        section.bulbs.groupBy { it.room }.forEach { (bulbRoom, roomBulbs) ->
                            val group = bulbGroup(roomBulbs)
                            group.brokenOut.forEach { add(bulbEntry(it, "bulb")) }
                            if (group.lamps.isNotEmpty()) {
                                val groupKey = "$room/lamps:${bulbRoom.orEmpty()}"
                                val open = groupKey in openLamps
                                add(
                                    PanelTile(
                                        groupKey,
                                        if (showRoom) TileFormat.Control else TileFormat.Reading,
                                        children = if (open) group.lamps.map { bulbEntry(it, "lamp") } else emptyList(),
                                    ) { modifier ->
                                        val commandError = group.lamps.firstNotNullOfOrNull { lamp -> bulbs.commandErrors[lamp.id]?.let { "${lamp.name}: $it" } }
                                        if (showRoom) {
                                            HomeDeviceTile(
                                                anatomy = anatomy(group, now, commandError, notUpdating(null, bulbs.lastPolledAt, now, yandexInterval), open).copy(
                                                    status = if (open) "Свернуть" else "Показать лампы",
                                                    detail = commandError,
                                                ),
                                                paint = paint(group, bulbs.error),
                                                polledAt = bulbs.lastPolledAt,
                                                reportedAt = group.oldest,
                                                commandError = commandError,
                                                now = now,
                                                interval = yandexInterval,
                                                pending = group.lamps.any { it.id in bulbs.pending },
                                                modifier = modifier,
                                                toggle = {
                                                    TilePowerButton(
                                                        "Свет",
                                                        bulbRoom,
                                                        group.on == group.lamps.size,
                                                        paint(group, bulbs.error).mood,
                                                        modifier = Modifier.semantics { stateDescription = "Включено ${group.on} из ${group.lamps.size}" },
                                                        onToggle = {
                                                            val target = group.on < group.lamps.size
                                                            group.lamps.filter { it.isOn != target && it.id !in bulbs.pending }.forEach { onToggleBulb(it.id) }
                                                        },
                                                        enabled = group.lamps.none { it.id in bulbs.pending },
                                                    )
                                                },
                                                openLabel = if (open) "Свернуть" else "Показать лампы",
                                                onOpen = { openLamps = if (open) openLamps - groupKey else openLamps + groupKey },
                                            )
                                        } else {
                                            BulbGroupTile(
                                                group = group,
                                                now = now,
                                                modifier = modifier,
                                                error = group.lamps.firstNotNullOfOrNull { lamp ->
                                                    bulbs.commandErrors[lamp.id]?.let { "${lamp.name}: $it" }
                                                },
                                                pending = group.lamps.any { it.id in bulbs.pending },
                                                showRoom = showRoom,
                                                notUpdating = bulbs.error == null && notUpdating(null, bulbs.lastPolledAt, now, yandexInterval),
                                                open = open,
                                                onOpen = { openLamps = if (open) openLamps - groupKey else openLamps + groupKey },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        section.discovered.forEach { tile ->
                            add(
                                PanelTile("$room/discovered:${tile.id}", TileFormat.Reading) { modifier ->
                                    DiscoveredDeviceTile(
                                        tile = tile,
                                        now = now,
                                        modifier = modifier,
                                        error = discovered.error ?: "Обновления не поступают".takeIf {
                                            notUpdating(null, discovered.lastPolledAt, now, yandexInterval)
                                        },
                                        showRoom = showRoom,
                                        onOpen = { openSheet.value = tile.id },
                                    )
                                },
                            )
                        }
                        section.launchers.filterNot { showRoom && it.packageName == intercom?.packageName }.forEach { tile ->
                            add(
                                PanelTile("$room/launcher:${tile.packageName}", TileFormat.Launcher) { modifier ->
                                    LauncherTile(tile, modifier = modifier, showRoom = showRoom, onOpen = onOpenApp, error = launcherErrors[tile.packageName])
                                },
                            )
                        }
                    }
                    itemIndex += tileRows(room, entries)
                }
            }
        }
        OpenSheet(
            openSheet = openSheet,
            acs = acs,
            curtains = curtains,
            strips = strips,
            recuperators = recuperators,
            bulbs = bulbs,
            discovered = discovered,
            now = now,
            yandexInterval = yandexInterval,
            tuyaInterval = tuyaInterval,
            onToggleAc = onToggleAc,
            onSetTemperature = onSetTemperature,
            onSetAcMode = onSetAcMode,
            onSetAcToggle = onSetAcToggle,
            onSetOpen = onSetOpen,
            onToggleStrip = onToggleStrip,
            onSetBrightness = onSetBrightness,
            onToggleRecuperator = onToggleRecuperator,
            onSetRecuperatorSpeed = onSetRecuperatorSpeed,
            onToggleBulb = onToggleBulb,
            onSetBulbBrightness = onSetBulbBrightness,
            onSetBulbScene = onSetBulbScene,
            onSetBulbRgb = onSetBulbRgb,
        )
    }
}

/** UI entries retain device keys while rows are packed by their content format. */
private class PanelTile(
    val key: String,
    val format: TileFormat,
    val children: List<PanelTile> = emptyList(),
    val content: @Composable (Modifier) -> Unit,
)

/** Each row uses its tallest card; expanded lamps stay directly below their summary row. */
private fun LazyGridScope.tileRows(prefix: String, entries: List<PanelTile>): Int {
    var count = 0
    TileFormat.entries.forEach { format ->
        entries.filter { it.format == format }.chunked(format.columns).forEach { row ->
            val rowKey = "$prefix/row:${row.joinToString { it.key }}"
            item(key = rowKey, span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEach { tile ->
                        key(tile.key) { tile.content(Modifier.weight(1f).fillMaxHeight()) }
                    }
                    repeat(format.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            count++
            val expanded = row.flatMap { it.children }
            if (expanded.isNotEmpty()) count += tileRows("$rowKey/expanded", expanded)
        }
    }
    return count
}

/**
 * Look up the current tile by id so each poll refreshes the open sheet. Missing devices draw nothing.
 * A sheet never starts extra polling; each vendor keeps its own scheduler.
 */
@Composable
private fun OpenSheet(
    openSheet: MutableState<String?>,
    acs: AcPanelState,
    curtains: CurtainPanelState,
    strips: LightStripPanelState,
    recuperators: RecuperatorPanelState,
    bulbs: BulbPanelState,
    discovered: DiscoveredPanelState,
    now: Instant,
    yandexInterval: Duration,
    tuyaInterval: Duration,
    onToggleAc: (String) -> Unit,
    onSetTemperature: (String, Double) -> Unit,
    onSetAcMode: (String, String, String) -> Unit,
    onSetAcToggle: (String, String, Boolean) -> Unit,
    onSetOpen: (String, Double) -> Unit,
    onToggleStrip: (String) -> Unit,
    onSetBrightness: (String, Double) -> Unit,
    onToggleRecuperator: (String) -> Unit,
    onSetRecuperatorSpeed: (String, FanSpeed) -> Unit,
    onToggleBulb: (String) -> Unit,
    onSetBulbBrightness: (String, Double) -> Unit,
    onSetBulbScene: (String, String) -> Unit,
    onSetBulbRgb: (String, Int) -> Unit,
) {
    val id = openSheet.value ?: return
    val dismiss = { openSheet.value = null }
    val ac = acs.tiles.firstOrNull { it.id == id }
    val curtain = curtains.tiles.firstOrNull { it.id == id }
    val strip = strips.tiles.firstOrNull { it.id == id }
    val recuperator = recuperators.tiles.firstOrNull { it.id == id }
    val bulb = bulbs.tiles.firstOrNull { it.id == id }
    val other = discovered.tiles.firstOrNull { it.id == id }
    when {
        other != null -> DeviceSheet(sheet = sheet(other, now, updateFailure(discovered.error, discovered.lastPolledAt, now, yandexInterval)), onDismiss = dismiss)
        ac != null ->
            DeviceSheet(
                sheet = sheet(ac, now, updateFailure(acs.error, acs.lastPolledAt, now, yandexInterval)),
                commandError = acs.commandErrors[id],
                pending = id in acs.pending,
                onDismiss = dismiss,
                onToggle = { onToggleAc(id) },
                onSetLevel = { celsius -> onSetTemperature(id, celsius) },
                onSetMode = { instance, value -> onSetAcMode(id, instance, value) },
                onSetToggle = { instance, on -> onSetAcToggle(id, instance, on) },
            )
        curtain != null ->
            DeviceSheet(
                sheet = sheet(curtain, now, updateFailure(curtains.error, curtains.lastPolledAt, now, yandexInterval)),
                commandError = curtains.commandErrors[id],
                pending = id in curtains.pending,
                onDismiss = dismiss,
                onSetLevel = { percent -> onSetOpen(id, percent) },
            )
        strip != null ->
            DeviceSheet(
                sheet = sheet(strip, now, updateFailure(strips.error, strips.lastPolledAt, now, yandexInterval)),
                commandError = strips.commandErrors[id],
                pending = id in strips.pending,
                onDismiss = dismiss,
                onToggle = { onToggleStrip(id) },
                onSetLevel = { percent -> onSetBrightness(id, percent) },
            )
        recuperator != null ->
            DeviceSheet(
                sheet = sheet(recuperator, now, updateFailure(recuperators.error, recuperators.lastPolledAt, now, tuyaInterval)),
                commandError = recuperators.commandErrors[id],
                pending = id in recuperators.pending,
                onDismiss = dismiss,
                onToggle = { onToggleRecuperator(id) },
                onSetSpeed = { speed -> onSetRecuperatorSpeed(id, speed) },
            )
        bulb != null ->
            DeviceSheet(
                sheet = sheet(bulb, now, updateFailure(bulbs.error, bulbs.lastPolledAt, now, yandexInterval)),
                commandError = bulbs.commandErrors[id],
                pending = id in bulbs.pending,
                onDismiss = dismiss,
                onToggle = { onToggleBulb(id) },
                onSetLevel = { percent -> onSetBulbBrightness(id, percent) },
                onSetScene = { scene -> onSetBulbScene(id, scene) },
                onSetRgb = { rgb -> onSetBulbRgb(id, rgb) },
            )
    }
}

/** Room names lead the visual hierarchy. Failures add both [MARK] and the error colour. */
@Composable
internal fun SectionHeading(
    title: String,
    marked: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (marked) title + MARK else title,
        style = MaterialTheme.typography.headlineSmall,

        fontWeight = FontWeight.Bold,
        color = if (marked) MaterialTheme.colorScheme.error else LocalContentColor.current,
        modifier =
        modifier.semantics {
            heading()
            if (marked) stateDescription = "Есть устройства без обновлений"
        }.padding(
            start = HEADING_INDENT,
            end = HEADING_INDENT,
            top = HEADING_TOP,
            bottom = HEADING_BOTTOM,
        ),
    )
}

private fun updateFailure(error: String?, polledAt: Instant?, now: Instant, interval: Duration): String? = error ?: "Обновления не поступают".takeIf { notUpdating(null, polledAt, now, interval) }
