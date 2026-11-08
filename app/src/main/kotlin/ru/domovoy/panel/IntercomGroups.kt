package ru.domovoy.panel

import ru.domovoy.core.Device
import ru.domovoy.core.EventProperty
import ru.domovoy.core.Reading

/** Domonap entry endpoints in one room share an expandable group, while their original readings stay separate. */
internal fun intercomGroups(devices: List<Device>): Map<String, List<Device>> = devices
    .filter { it.vendorType == "devices.types.openable" && it.name.startsWith("Домофон", ignoreCase = true) }
    .groupBy { it.room }
    .values
    .map { it.sortedBy(Device::id) }
    .associateBy { "intercom:${it.first().room.orEmpty()}" }

internal fun intercomCard(id: String, entries: List<Device>): Device {
    val doors = entries.map { it.events["open"] ?: it.events["door"] }
    val state = when {
        doors.any { it?.value in listOf("opened", "ajar") } -> "some_open"
        entries.indices.all { entries[it].online != false && doors[it]?.value == "closed" && doors[it]?.lastUpdated is Reading.At } -> "all_closed"
        else -> null
    }
    return entries.first().copy(
        id = id,
        name = "Домофоны",
        onOff = null,
        ranges = emptyMap(),
        modes = emptyMap(),
        toggles = emptyMap(),
        color = null,
        properties = emptyMap(),
        events = mapOf("open" to EventProperty(state, oldest(doors.map { it?.lastUpdated ?: Reading.Never }) ?: Reading.Never)),
    )
}
