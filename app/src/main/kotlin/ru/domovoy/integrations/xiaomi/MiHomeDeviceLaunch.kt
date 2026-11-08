package ru.domovoy.integrations.xiaomi

import android.content.ComponentName
import android.content.Intent

/** Matches the device shortcut created by the installed Mi Home, verified on the Samsung tablet. */
internal fun miHomeDeviceIntent(deviceId: String): Intent {
    require(deviceId.isNotBlank()) { "Пылесос не настроен" }
    return Intent("com.xiaomi.smarthome.action.viewdevice")
        .setComponent(ComponentName(MI_HOME_PACKAGE, "com.xiaomi.smarthome.device.utils.DeviceLauncher2"))
        .putExtra("device_id", deviceId.trim())
        .putExtra("source_tag", "short_cut")
        .putExtra("user_model", "deerma.vacuum.a2506")
        .putExtra("timestamp", System.currentTimeMillis())
        .putExtra("from_main_activity", true)
}
