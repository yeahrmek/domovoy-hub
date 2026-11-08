package ru.domovoy

import android.view.WindowManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenTimeoutTest {
    @Test
    fun `panel allows the system to turn off the screen after inactivity`() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.create().get()

            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
