package ru.domovoy.integrations.xiaomi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.core.TokenStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MiHomeDeviceLaunchTest {
    @Test
    fun `vacuum opens the vendor device entry with its configured identifier`() {
        val intent = miHomeDeviceIntent("123456789")
        assertEquals("com.xiaomi.smarthome.action.viewdevice", intent.action)
        assertEquals(MI_HOME_PACKAGE, intent.component?.packageName)
        assertEquals("com.xiaomi.smarthome.device.utils.DeviceLauncher2", intent.component?.className)
        assertEquals("123456789", intent.getStringExtra("device_id"))
        assertEquals("short_cut", intent.getStringExtra("source_tag"))
        assertTrue(intent.getBooleanExtra("from_main_activity", false))
    }

    @Test
    fun `an unconfigured vacuum never silently opens another device`() {
        assertFailsWith<IllegalArgumentException> { miHomeDeviceIntent(" ") }
    }

    @Test
    fun `an APK seed preserves the already configured vacuum identifier`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("vacuum-seed-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = TokenStore(prefs)
        store.seedXiaomiVacuumDeviceId(" 123456789 ")
        store.seedXiaomiVacuumDeviceId("987654321")
        assertEquals("123456789", store.xiaomiVacuumDeviceId())
    }
}
