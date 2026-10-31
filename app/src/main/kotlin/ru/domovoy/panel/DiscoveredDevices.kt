package ru.domovoy.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import java.time.Instant

/** Newly discovered types report state without gaining unverified controls. */
data class DiscoveredPanelState(
    val tiles: List<Device> = emptyList(),
    val error: String? = null,
    val lastPolledAt: Instant? = null,
)

class DiscoveredDevices {
    private val mutableState = MutableStateFlow(DiscoveredPanelState())
    val state = mutableState.asStateFlow()

    fun show(devices: List<Device>, polledAt: Instant) {
        mutableState.value = DiscoveredPanelState(devices.filter { it.kind == DeviceKind.Other }, lastPolledAt = polledAt)
    }

    fun showFailure(reason: String) {
        mutableState.value = mutableState.value.copy(error = reason)
    }
}
