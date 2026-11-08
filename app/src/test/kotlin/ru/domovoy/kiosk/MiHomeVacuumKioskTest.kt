package ru.domovoy.kiosk

import android.app.Activity
import android.app.KeyguardManager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MiHomeVacuumKioskTest {
    private val controller = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = controller.get()
    private var callActive = false
    private val kiosk = PanelKiosk(activity, { _, _ -> error("Opening the vacuum must not request a PIN") }, {}, { callActive }).also { it.resume() }

    @After
    fun close() {
        kiosk.destroy()
        controller.pause().stop().destroy()
    }

    @Test
    fun `vacuum tile opens the specific device without authentication`() {
        var error: String? = "not opened"
        kiosk.openVacuum("123456789") { error = it }
        assertNull(error)
        assertEquals("123456789", shadowOf(activity).nextStartedActivity.getStringExtra("device_id"))
    }

    @Test
    fun `an intercom call prevents the vacuum from covering it`() {
        callActive = true
        var error: String? = null
        kiosk.openVacuum("123456789") { error = it }
        assertNotNull(error)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a locked tablet or background panel cannot launch the vacuum`() {
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        var error: String? = null
        kiosk.openVacuum("123456789") { error = it }
        assertNotNull(error)
        assertNull(shadowOf(activity).nextStartedActivity)
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        kiosk.background()
        kiosk.openVacuum("123456789") { error = it }
        assertNotNull(error)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `missing vacuum configuration produces a visible failure`() {
        var error: String? = null
        kiosk.openVacuum("") { error = it }
        assertEquals("Пылесос не настроен. Откройте Mi Home через меню «Защита».", error)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
