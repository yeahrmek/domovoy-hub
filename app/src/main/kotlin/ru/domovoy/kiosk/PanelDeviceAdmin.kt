package ru.domovoy.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

/** Device Owner enrolment is explicit; installing the APK does not enable device management. */
class PanelDeviceAdmin : DeviceAdminReceiver() {
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        lockTaskChanges.tryEmit(Unit)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        lockTaskChanges.tryEmit(Unit)
    }
}

/** Lock task changes asynchronously after startLockTask returns. */
internal val lockTaskChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
