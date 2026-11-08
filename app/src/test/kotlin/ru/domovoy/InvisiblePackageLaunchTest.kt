package ru.domovoy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InvisiblePackageLaunchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun installLauncherBinder() {
        val pm = context.packageManager
        val original = ReflectionHelpers.getField<Any>(pm, "mPM")
        val binder = Proxy.newProxyInstance(original.javaClass.classLoader, original.javaClass.interfaces) { _, method, args ->
            if (method.name == "getLaunchIntentSenderForPackage") {
                val packageName = args!![0] as String
                val launch = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(Intent.ACTION_MAIN).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                if (packageName != "test.hidden") launch.cancel()
                launch.intentSender
            } else {
                method.invoke(original, *(args ?: emptyArray()))
            }
        }
        ReflectionHelpers.setField(pm, "mPM", binder)
    }

    @Test
    fun `an installed package hidden from queries opens through the system launcher`() {
        assertNull(context.packageManager.getLaunchIntentForPackage("test.hidden"))
        assertNull(openVendorApp(context, "test.hidden"))
        assertEquals("test.hidden", shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.`package`)
    }

    @Test
    fun `a cancelled system launcher returns a visible unavailable state`() {
        assertEquals("Приложение недоступно", openVendorApp(context, "test.missing"))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }
}
