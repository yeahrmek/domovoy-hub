package ru.domovoy.panel

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.domovoy.core.Device
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

/** Home and rooms share one scroll and card layout; favourites and order are user preferences. */
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
    onPanelAccess: (() -> Unit)? = null,
    accessMessage: String? = null,
    deviceRefresh: DeviceRefreshState = DeviceRefreshState(),
    onRefreshDevices: (() -> Unit)? = null,
    cardPreferences: CardPreferences = CardPreferences(),
    onCardPreferencesChange: (CardPreferences) -> Unit = {},
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
    onToggleDiscovered: (String) -> Unit = {},
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
    // Launchers exist before either poll returns; reserve the initial order after both attempts.
    val inventoryReady = (polls.acs != null || errors.acs != null || polls.discovered != null || errors.discovered != null) &&
        (polls.recuperators != null || errors.recuperators != null)
    val headings = panelHeadings(sections, errors, polls, now, yandexInterval, tuyaInterval).let { headings ->
        if (headings.any { it.title == "Главная" }) headings else listOf(PanelHeading("Главная", RoomSection(null), false)) + headings
    }
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
    val groupedPreferences = cardPreferences.mergeKeys(
        discovered.intercomGroups.flatMap { (id, entries) -> entries.map { "discovered:${it.id}" to "discovered:$id" } }.toMap(),
    )
    var preferences by remember(groupedPreferences) { mutableStateOf(groupedPreferences) }
    LaunchedEffect(groupedPreferences) {
        if (groupedPreferences != cardPreferences) onCardPreferencesChange(groupedPreferences)
    }
    fun saveCards(value: CardPreferences) {
        preferences = value
        onCardPreferencesChange(value)
    }
    val defaultKeys = sections.flatMap { it.cardKeys() } +
        sections.flatMap { it.launchers }.map { "launcher:${it.packageName}" } +
        listOfNotNull("breezers".takeIf { recuperators.tiles.isNotEmpty() })
    val cardBounds = remember { mutableMapOf<String, MutableMap<String, Rect>>() }
    val dragPointer = remember { mutableStateOf<Offset?>(null) }
    val viewport = remember { mutableStateOf(Rect.Zero) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(scroll, edge) {
        snapshotFlow {
            val pointer = dragPointer.value
            val velocity = pointer?.let { cardDragScrollVelocity(it.y, viewport.value, edge) } ?: 0f
            (velocity < 0f && scroll.canScrollBackward) || (velocity > 0f && scroll.canScrollForward)
        }.collectLatest { active ->
            if (!active) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val pointer = dragPointer.value ?: break
                val seconds = (frame - previousFrame).coerceAtMost(50_000_000L) / 1_000_000_000f
                previousFrame = frame
                val velocity = cardDragScrollVelocity(pointer.y, viewport.value, edge)
                if (velocity != 0f) scroll.scrollBy(velocity * seconds)
            }
        }
    }
    val sheetIsOpen = openSheet.value?.let { id ->
        acs.tiles.any { it.id == id } ||
            curtains.tiles.any { it.id == id } ||
            strips.tiles.any { it.id == id } ||
            recuperators.tiles.any { it.id == id } ||
            bulbs.tiles.any { it.id == id } ||
            discovered.tiles.any { it.id == id } ||
            discovered.intercomGroups.values.any { entries -> entries.any { it.id == id } }
    } == true
    // Keep the mosaic mounted beneath the sheet.
    BoxWithConstraints(modifier = modifier) {
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
                onPanelAccess = onPanelAccess,
                refreshingDevices = deviceRefresh.refreshing,
                onRefreshDevices = onRefreshDevices,
            )
            val refreshMessage = when {
                deviceRefresh.refreshing -> null
                deviceRefresh.error != null -> "Не удалось обновить устройства: ${deviceRefresh.error}. Нажмите ещё раз."
                deviceRefresh.updated -> "Устройства обновлены"
                else -> null
            }
            refreshMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deviceRefresh.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            accessMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            intercom?.let { launcherErrors[it.packageName] }?.let { reason ->
                Text(reason, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            // Full-span rows preserve the hoisted scroll state.
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = scroll,
                modifier = Modifier.weight(1f).onGloballyPositioned { viewport.value = it.boundsInRoot() },
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
                        text = failure.substringBefore(":") + ": —",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp).semantics { stateDescription = failure },
                    )
                }
                headings.forEach { heading ->
                    val isHome = heading.title == "Главная"
                    val section = if (isHome) {
                        RoomSection(
                            room = null,
                            acs = acs.tiles,
                            curtains = curtains.tiles,
                            strips = strips.tiles,
                            recuperators = recuperators.tiles,
                            bulbs = bulbs.tiles,
                            launchers = launchers,
                            discovered = discovered.tiles,
                        )
                    } else {
                        heading.section
                    }
                    // Prefix keys by section because a device can appear in both Главная and its room.
                    val room = heading.title
                    roomIndices[room] = itemIndex++
                    val showRoom = true
                    fun bulbEntry(tile: BulbTileState, prefix: String) = PanelTile(
                        "$room/$prefix:${tile.id}",
                        if (tile.brightnessBounds != null) TileFormat.Control else TileFormat.Compact,
                        label = "${tile.name}, ${tile.room.orEmpty()}",
                    ) { modifier ->

                        HomeDeviceTile(
                            anatomy = anatomy(tile, now, bulbs.commandErrors[tile.id]),
                            paint = paint(tile, bulbs.error),
                            polledAt = bulbs.lastPolledAt,
                            commandError = bulbs.commandErrors[tile.id],
                            now = now,
                            interval = yandexInterval,
                            pending = tile.id in bulbs.pending,
                            modifier = modifier,
                            onOpen = { openSheet.value = tile.id },
                            showValue = tile.brightnessBounds == null,
                            controls = {
                                tile.brightnessBounds?.let { bounds ->
                                    LevelControl(tile.id, tile.brightnessPercent, bounds, onSetValue = { onSetBulbBrightness(tile.id, it) }, label = "Яркость", enabled = tile.id !in bulbs.pending, prominentValue = true)
                                }
                            },
                            toggle = {
                                TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, bulbs.error).mood, { onToggleBulb(tile.id) }, enabled = tile.id !in bulbs.pending)
                            },
                        )
                    }
                    fun breatherEntry(tile: RecuperatorTileState): PanelTile = PanelTile("$room/recuperator:${tile.id}", TileFormat.Control, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->

                        HomeDeviceTile(
                            anatomy = anatomy(tile, now, recuperators.commandErrors[tile.id]).let { anatomy ->
                                anatomy.copy(status = anatomy.status.takeIf { tile.online == false || tile.isOn == null || tile.speeds.isEmpty() }.orEmpty())
                            },
                            paint = paint(tile, recuperators.error),
                            polledAt = recuperators.lastPolledAt,
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
                                        compact = true,
                                    )
                                }
                            },
                            toggle = {
                                TilePowerButton(tile.name, tile.room, tile.isOn == true, paint(tile, recuperators.error).mood, { onToggleRecuperator(tile.id) }, enabled = tile.id !in recuperators.pending)
                            },
                        )
                    }

                    val entries = buildList {
                        section.acs.forEach { tile ->
                            add(
                                PanelTile("$room/ac:${tile.id}", TileFormat.Featured, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->

                                    HomeDeviceTile(
                                        anatomy = anatomy(tile, now, acs.commandErrors[tile.id]),
                                        paint = paint(tile, acs.error),
                                        polledAt = acs.lastPolledAt,
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
                                },
                            )
                        }
                        section.curtains.forEach { tile ->
                            add(
                                PanelTile("$room/curtain:${tile.id}", TileFormat.Wide, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->

                                    HomeDeviceTile(
                                        anatomy = anatomy(tile, now, curtains.commandErrors[tile.id]),
                                        paint = paint(tile, now, curtains.error),
                                        polledAt = curtains.lastPolledAt,
                                        commandError = curtains.commandErrors[tile.id],
                                        now = now,
                                        interval = yandexInterval,
                                        pending = tile.id in curtains.pending,
                                        modifier = modifier,
                                        onOpen = { openSheet.value = tile.id },
                                        horizontalValue = true,
                                        controls = {
                                            tile.bounds?.let { bounds ->
                                                val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
                                                    LevelControl(
                                                        id = tile.id,
                                                        value = position(tile)?.percent,
                                                        bounds = bounds,
                                                        label = "Открытие",
                                                        showBounds = false,
                                                        enabled = tile.id !in curtains.pending,
                                                        modifier = sliderModifier,
                                                        onSetValue = { onSetOpen(tile.id, it) },
                                                    )
                                                }
                                                BoxWithConstraints {
                                                    if (maxWidth / LocalDensity.current.fontScale >= 500.dp) {
                                                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                            androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.min) }, enabled = tile.id !in curtains.pending, modifier = Modifier.heightIn(min = MIN_TOUCH)) { Text("Закрыть") }
                                                            slider(Modifier.weight(1f))
                                                            androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.max) }, enabled = tile.id !in curtains.pending, modifier = Modifier.heightIn(min = MIN_TOUCH)) { Text("Открыть") }
                                                        }
                                                    } else {
                                                        Column {
                                                            slider(Modifier.fillMaxWidth())
                                                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                                                                androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.min) }, enabled = tile.id !in curtains.pending, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH)) { Text("Закрыть") }
                                                                androidx.compose.material3.OutlinedButton(onClick = { onSetOpen(tile.id, bounds.max) }, enabled = tile.id !in curtains.pending, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH)) { Text("Открыть") }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    )
                                },
                            )
                        }
                        section.strips.forEach { tile ->
                            add(
                                PanelTile("$room/strip:${tile.id}", TileFormat.Control, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->

                                    HomeDeviceTile(
                                        anatomy = anatomy(tile, now, strips.commandErrors[tile.id]),
                                        paint = paint(tile, strips.error),
                                        polledAt = strips.lastPolledAt,
                                        commandError = strips.commandErrors[tile.id],
                                        now = now,
                                        interval = yandexInterval,
                                        pending = tile.id in strips.pending,
                                        modifier = modifier,
                                        onOpen = { openSheet.value = tile.id },
                                        showValue = tile.bounds == null,
                                        controls = {
                                            tile.bounds?.let { bounds ->
                                                LevelControl(
                                                    id = tile.id,
                                                    value = tile.brightnessPercent,
                                                    bounds = bounds,
                                                    label = "Яркость",
                                                    prominentValue = true,
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
                                },
                            )
                        }
                        if (section.recuperators.isNotEmpty()) {
                            val groupKey = "$room/breezers"
                            val open = groupKey in openLamps
                            add(
                                PanelTile(groupKey, TileFormat.Control, label = "Бризеры", children = if (open) section.recuperators.map(::breatherEntry) else emptyList()) { modifier ->
                                    BreezerGroupTile(
                                        tiles = section.recuperators,
                                        state = recuperators,
                                        now = now,
                                        interval = tuyaInterval,
                                        open = open,
                                        modifier = modifier,
                                        onOpen = { openLamps = if (open) openLamps - groupKey else openLamps + groupKey },
                                        onToggle = onToggleRecuperator,
                                        onOpenDevice = { openSheet.value = it },
                                    )
                                },
                            )
                        }
                        section.bulbs.groupBy { it.room }.forEach { (bulbRoom, roomBulbs) ->
                            val individual = roomBulbs.filter { "bulb:${it.id}" in preferences.favourites.orEmpty() }
                            val grouped = roomBulbs - individual.toSet()
                            val usual = bulbGroup(grouped)
                            // Home groups depend on device identity, never the current power reading.
                            val group = if (isHome || "lamps:${bulbRoom.orEmpty()}" in preferences.favourites.orEmpty()) {
                                usual.copy(lamps = grouped, brokenOut = individual, on = grouped.count { it.isOn == true }, oldest = oldest(grouped.map { it.lastUpdated }))
                            } else {
                                usual.copy(brokenOut = individual + usual.brokenOut)
                            }
                            group.brokenOut.forEach { add(bulbEntry(it, "bulb")) }
                            if (group.lamps.isNotEmpty()) {
                                val groupKey = "$room/lamps:${bulbRoom.orEmpty()}"
                                val open = groupKey in openLamps
                                add(
                                    PanelTile(
                                        groupKey,
                                        TileFormat.Control,
                                        label = "Свет, ${bulbRoom.orEmpty()}",
                                        children = if (open) group.lamps.map { bulbEntry(it, "lamp") } else emptyList(),
                                    ) { modifier ->
                                        val commandError = group.lamps.firstNotNullOfOrNull { lamp -> bulbs.commandErrors[lamp.id]?.let { "${lamp.name}: $it" } }
                                        val knownLamps = group.lamps.filter { it.isOn != null }
                                        val knownOn = knownLamps.isNotEmpty() && knownLamps.all { it.isOn == true }
                                        val sharedFailure = notUpdating(bulbs.error, bulbs.lastPolledAt, now, yandexInterval)

                                        HomeDeviceTile(
                                            anatomy = anatomy(group, now, commandError, notUpdating(null, bulbs.lastPolledAt, now, yandexInterval), open).copy(
                                                promoted = null,
                                                status = "",
                                                detail = commandError,
                                            ),
                                            paint = paint(group, bulbs.error),
                                            polledAt = bulbs.lastPolledAt,
                                            commandError = commandError,
                                            now = now,
                                            interval = yandexInterval,
                                            pending = group.lamps.any { it.id in bulbs.pending },
                                            modifier = modifier,
                                            showValue = false,
                                            controls = { BulbGroupControls(group, bulbs, sharedFailure, onToggleBulb) },
                                            toggle = {
                                                TilePowerButton(
                                                    "Свет",
                                                    bulbRoom,
                                                    knownOn,
                                                    paint(group, bulbs.error).mood,
                                                    modifier = Modifier.semantics { stateDescription = if (group.lamps.any { it.isOn == null }) "Нет данных" else "Включено ${group.on} из ${group.lamps.size}" },
                                                    onToggle = {
                                                        knownLamps.filter { it.isOn == knownOn && it.id !in bulbs.pending }.forEach { onToggleBulb(it.id) }
                                                    },
                                                    enabled = !sharedFailure && group.lamps.none { it.id in bulbs.pending } && group.lamps.any { it.isOn != null },
                                                )
                                            },
                                            openLabel = if (open) "Свернуть" else "Показать лампы",
                                            expanded = open,
                                            onOpen = { openLamps = if (open) openLamps - groupKey else openLamps + groupKey },
                                        )
                                    },
                                )
                            }
                        }
                        fun discoveredEntry(tile: Device): PanelTile {
                            val light = switchLight(tile, bulbs.tiles)
                            val format = if (tile.vendorType == "devices.types.sensor.climate") TileFormat.Wide else TileFormat.Reading
                            return PanelTile("$room/discovered:${tile.id}", format, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->
                                DiscoveredDeviceTile(
                                    tile = tile,
                                    now = now,
                                    modifier = modifier,
                                    controlledLight = light,
                                    pending = if (light != null) light.id in bulbs.pending else tile.id in discovered.pending,
                                    commandError = if (light != null) bulbs.commandErrors[light.id] else discovered.commandErrors[tile.id],
                                    onToggle = { if (light != null) onToggleBulb(light.id) else onToggleDiscovered(tile.id) },
                                    error = updateFailure(
                                        if (light != null) bulbs.error ?: discovered.error else discovered.error,
                                        if (light != null) bulbs.lastPolledAt else discovered.lastPolledAt,
                                        now,
                                        yandexInterval,
                                    ),
                                    showRoom = showRoom,
                                    onOpen = { openSheet.value = tile.id },
                                )
                            }
                        }
                        section.discovered.forEach { tile ->
                            val intercoms = discovered.intercomGroups[tile.id]
                            if (intercoms == null) {
                                add(discoveredEntry(tile))
                            } else {
                                val groupKey = "$room/discovered:${tile.id}"
                                val open = groupKey in openLamps
                                add(
                                    PanelTile(
                                        groupKey,
                                        TileFormat.Reading,
                                        label = "${tile.name}, ${tile.room.orEmpty()}",
                                        children = if (open) intercoms.map(::discoveredEntry) else emptyList(),
                                    ) { modifier ->
                                        IntercomGroupTile(
                                            tile = tile,
                                            state = discovered,
                                            now = now,
                                            interval = yandexInterval,
                                            open = open,
                                            modifier = modifier,
                                            onOpen = { openLamps = if (open) openLamps - groupKey else openLamps + groupKey },
                                        )
                                    },
                                )
                            }
                        }
                        section.launchers.filterNot {
                            (showRoom || section.discovered.any { device -> device.id in discovered.intercomGroups }) && it.packageName == intercom?.packageName
                        }.forEach { tile ->
                            add(
                                PanelTile("$room/launcher:${tile.packageName}", TileFormat.Launcher, label = "${tile.name}, ${tile.room.orEmpty()}") { modifier ->
                                    LauncherTile(tile, modifier = modifier, showRoom = showRoom, onOpen = onOpenApp, error = launcherErrors[tile.packageName])
                                },
                            )
                        }
                    }
                    val available = entries.sortedWith(compareBy<PanelTile> { cardOrder(it.id) }.thenBy { it.label }.thenBy { it.id })
                    val availableKeys = available.map { it.id }
                    item(key = "heading:$room", span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(room, availableKeys, inventoryReady) {
                            val pinned = preferences.rememberOrder(room, availableKeys)
                            if (inventoryReady && pinned != preferences) saveCards(pinned)
                        }
                        SectionHeading(title = room, marked = heading.marked)
                    }
                    val selected = available.filter { !isHome || preferences.isFavourite(it.id, defaultKeys) }
                    val byId = selected.associateBy { it.id }
                    val ordered = preferences.ordered(room, selected.map { it.id }).mapNotNull { byId[it] }
                    itemIndex += homeTiles(
                        room,
                        ordered,
                        preferences,
                        defaultKeys,
                        cardBounds.getOrPut(room) { mutableMapOf() },
                        onDragPosition = { dragPointer.value = it },
                        onFavourite = { id -> saveCards(preferences.toggleFavourite(id, defaultKeys)) },
                        onMove = { id, target -> saveCards(preferences.move(room, id, target, ordered.map { it.id })) },
                        onReorder = { order -> saveCards(preferences.reorder(room, order)) },
                    )
                    if (isHome && selected.isEmpty()) {
                        item(key = "home-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text("Добавьте карточки из комнат в избранное", modifier = Modifier.padding(16.dp))
                        }
                        itemIndex++
                    }
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
            onToggleDiscovered = onToggleDiscovered,
            onSetBulbBrightness = onSetBulbBrightness,
            onSetBulbScene = onSetBulbScene,
            onSetBulbRgb = onSetBulbRgb,
        )
    }
}

/** Card identity is shared by Home and rooms; section prefixes only distinguish Compose keys. */
private class PanelTile(
    val key: String,
    val format: TileFormat,
    val label: String,
    val children: List<PanelTile> = emptyList(),
    val content: @Composable (Modifier) -> Unit,
) {
    val id: String get() = key.substringAfter("/")
}

private fun RoomSection.cardKeys(): List<String> = buildList {
    addAll(acs.map { "ac:${it.id}" })
    addAll(curtains.map { "curtain:${it.id}" })
    addAll(strips.map { "strip:${it.id}" })
    addAll(bulbs.map { "lamps:${it.room.orEmpty()}" }.distinct())
    addAll(discovered.map { "discovered:${it.id}" })
    addAll(launchers.map { "launcher:${it.packageName}" })
}

private fun cardOrder(id: String): Int = when (id.substringBefore(":")) {
    "breezers" -> 0
    "ac" -> 1
    "strip", "lamps", "bulb" -> 2
    "curtain" -> 3
    "launcher" -> 4
    else -> 5
}

/** Both Home and rooms share the same card widths and independent content heights. */
private fun LazyGridScope.homeTiles(
    prefix: String,
    entries: List<PanelTile>,
    preferences: CardPreferences,
    defaultKeys: List<String>,
    bounds: MutableMap<String, Rect>,
    onDragPosition: (Offset?) -> Unit,
    onFavourite: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onReorder: (List<String>) -> Unit,
): Int {
    if (entries.isEmpty()) return 0
    // A stable parent keeps the pointer handler alive when preview positions cross columns or wide rows.
    item(key = "$prefix/cards", span = { GridItemSpan(maxLineSpan) }) {
        var dragged by remember { mutableStateOf<String?>(null) }
        var preview by remember { mutableStateOf<List<String>?>(null) }
        var pointer by remember { mutableStateOf<Offset?>(null) }
        var sectionOrigin by remember { mutableStateOf<Offset?>(null) }
        var hoverArea by remember { mutableStateOf<Rect?>(null) }
        val keys = entries.map { it.id }
        fun previewAt(position: Offset) {
            val source = dragged ?: return
            if (hoverArea?.contains(position) == true) return
            if (bounds[source]?.contains(position) == true) return
            val order = (preview.orEmpty() + keys).distinct().filter { it in keys }
            val target = order.firstOrNull { it != source && bounds[it]?.contains(position) == true } ?: return
            val from = order.indexOf(source)
            val to = order.indexOf(target)
            if (from < 0 || from == to) return
            hoverArea = bounds[target]
            preview = order.toMutableList().apply { add(to, removeAt(from)) }
        }
        fun clearPreview() {
            dragged = null
            preview = null
            pointer = null
            hoverArea = null
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().testTag("section:$prefix").layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0, zIndex = if (dragged != null) 1f else 0f)
                }
            },
        ) {
            val columns = if (maxWidth / LocalDensity.current.fontScale >= 600.dp) 2 else 1
            Layout(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                    val origin = it.localToRoot(Offset.Zero)
                    if (sectionOrigin != origin) {
                        sectionOrigin = origin
                        hoverArea = null
                        pointer?.let(::previewAt)
                    }
                },
                content = {
                    entries.forEachIndexed { index, tile ->
                        key(tile.key) {
                            EditablePanelCard(
                                cardKey = tile.id, label = tile.label,
                                favourite = preferences.isFavourite(tile.id, defaultKeys),
                                canMoveUp = index > 0, canMoveDown = index < entries.lastIndex,
                                onFavourite = { onFavourite(tile.id) },
                                onMoveUp = { entries.getOrNull(index - 1)?.let { onMove(tile.id, it.id) } },
                                onMoveDown = { entries.getOrNull(index + 1)?.let { onMove(tile.id, it.id) } },
                                onDrop = {
                                    preview?.takeIf { it != keys }?.let(onReorder)
                                    clearPreview()
                                },
                                onDragCancel = { clearPreview() },
                                dragBounds = bounds,
                                onDragPosition = { position ->
                                    if (position != null) {
                                        if (dragged == null) {
                                            dragged = tile.id
                                            preview = keys
                                        }
                                        pointer = position
                                        previewAt(position)
                                    }
                                    onDragPosition(position)
                                },
                            ) { actions ->
                                tile.content(Modifier.fillMaxWidth().then(actions))
                                tile.children.forEach { child -> key(child.key) { child.content(Modifier.fillMaxWidth()) } }
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val columnWidth = width / columns
                val placeables = entries.mapIndexed { index, tile ->
                    tile.id to measurables[index].measure(Constraints.fixedWidth(if (tile.format == TileFormat.Wide) width else columnWidth))
                }.toMap()
                val byId = entries.associateBy { it.id }
                val order = (preview.orEmpty() + keys).distinct().filter { it in byId }
                val heights = IntArray(columns)
                val positions = mutableMapOf<String, IntOffset>()
                var lane = 0
                order.forEach { id ->
                    val tile = byId.getValue(id)
                    val card = placeables.getValue(id)
                    if (tile.format == TileFormat.Wide) {
                        val top = heights.max()
                        positions[id] = IntOffset(0, top)
                        heights.fill(top + card.height)
                        lane = 0
                    } else {
                        val column = lane++ % columns
                        positions[id] = IntOffset(column * columnWidth, heights[column])
                        heights[column] += card.height
                    }
                }
                layout(width, heights.max()) {
                    entries.forEach { tile ->
                        val position = positions.getValue(tile.id)
                        placeables.getValue(tile.id).placeRelative(position.x, position.y)
                    }
                }
            }
        }
    }
    return 1
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
    onToggleDiscovered: (String) -> Unit,
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
        ?: discovered.intercomGroups.values.firstNotNullOfOrNull { entries -> entries.firstOrNull { it.id == id } }
    val controlledLight = other?.let { switchLight(it, bulbs.tiles) }
    when {
        other != null -> DeviceSheet(
            sheet = sheet(
                other,
                now,
                updateFailure(
                    if (controlledLight != null) bulbs.error ?: discovered.error else discovered.error,
                    if (controlledLight != null) bulbs.lastPolledAt else discovered.lastPolledAt,
                    now,
                    yandexInterval,
                ),
                controlledLight,
            ),
            onDismiss = dismiss,
            onToggle = { if (controlledLight != null) onToggleBulb(controlledLight.id) else onToggleDiscovered(id) },
            pending = if (controlledLight != null) controlledLight.id in bulbs.pending else id in discovered.pending,
            commandError = if (controlledLight != null) bulbs.commandErrors[controlledLight.id] else discovered.commandErrors[id],
        )
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
