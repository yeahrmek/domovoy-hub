package ru.domovoy.kiosk

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.domovoy.integrations.domonap.DomonapCallListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PanelKioskTest {
    private val activityController = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = activityController.get()
    private val dpm = activity.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(activity, PanelDeviceAdmin::class.java)
    private var callActive = false
    private val authentication = mutableListOf<(String?) -> Unit>()
    private val kiosk = PanelKiosk(activity, { _, result -> authentication.add(result) }, {}, { callActive }).also { it.resume() }

    @After
    fun close() {
        kiosk.destroy()
        activityController.pause().stop().destroy()
    }

    @Test
    fun `enabling without device owner never falls back to screen pinning`() {
        secureDevice()
        kiosk.enable()
        assertFalse(kiosk.state.value.enabled)
        assertFalse(shadowOf(activity).isLockTask)
        assertTrue(authentication.isEmpty())
        assertNotNull(kiosk.state.value.problem)
    }

    @Test
    fun `missing PIN fails closed for kiosk and vendor apps`() {
        shadowOf(dpm).setDeviceOwner(admin)
        var failure: String? = null
        kiosk.openApp("com.xiaomi.smarthome") { failure = it }
        kiosk.enable()
        assertTrue(authentication.isEmpty())
        assertNotNull(failure)
        assertFalse(kiosk.state.value.enabled)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `authentication is required before enabling and restoring only permits panel and calls`() {
        prepareOwner()
        kiosk.enable()
        assertFalse(kiosk.state.value.enabled)
        authentication.single()(null)
        assertTrue(kiosk.state.value.enabled)
        assertTrue(shadowOf(activity).isLockTask)
        assertEquals(setOf("ru.domovoy", "com.domonap.app"), dpm.getLockTaskPackages(admin).toSet())
        val features = dpm.getLockTaskFeatures(admin)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS != 0)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_HOME != 0)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK != 0)
        assertEquals(0, features and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW)

        dpm.setLockTaskPackages(admin, arrayOf("ru.domovoy", "com.domonap.app", "com.xiaomi.smarthome"))
        val restored = PanelKiosk(activity, { _, _ -> error("restore needs no authentication") }, {}, { false })
        restored.resume()
        assertTrue(restored.state.value.enabled)
        assertEquals(setOf("ru.domovoy", "com.domonap.app"), dpm.getLockTaskPackages(admin).toSet())
        restored.destroy()
    }

    @Test
    fun `cancelling or rejecting authentication never opens an app`() {
        secureDevice()
        registerXiaomi()
        var failure: String? = null
        kiosk.openApp("com.xiaomi.smarthome") { failure = it }
        authentication.single()("Проверка отменена")
        assertNotNull(failure)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `call cancels pending authentication and late success cannot open an app`() {
        secureDevice()
        registerXiaomi()
        kiosk.openApp("com.xiaomi.smarthome") {}
        callActive = true
        kiosk.onCallStarted()
        callActive = false
        authentication.single()(null)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `duplicate or superseded authentication success is never reused`() {
        secureDevice()
        registerXiaomi()
        kiosk.openApp("com.xiaomi.smarthome") {}
        kiosk.openApp("com.xiaomi.smarthome") {}
        authentication.first()(null)
        assertNull(shadowOf(activity).nextStartedActivity)
        authentication.last()(null)
        assertEquals("com.xiaomi.smarthome", shadowOf(activity).nextStartedActivity.component?.packageName)
        authentication.last()(null)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `an authenticated vendor permission is revoked when home resumes`() {
        prepareOwner()
        registerXiaomi()
        kiosk.enable()
        authentication.last()(null)
        kiosk.openApp("com.xiaomi.smarthome") {}
        assertFalse("com.xiaomi.smarthome" in dpm.getLockTaskPackages(admin))
        authentication.last()(null)
        assertTrue("com.xiaomi.smarthome" in dpm.getLockTaskPackages(admin))
        assertEquals("com.xiaomi.smarthome", shadowOf(activity).nextStartedActivity.component?.packageName)
        kiosk.resume()
        assertFalse("com.xiaomi.smarthome" in dpm.getLockTaskPackages(admin))
        assertTrue(shadowOf(activity).isLockTask)
    }

    @Test
    fun `exit requires fresh authentication and removes the persisted kiosk policy`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        kiosk.exit()
        assertTrue(kiosk.state.value.enabled)
        authentication.last()(null)
        assertFalse(kiosk.state.value.enabled)
        assertFalse(shadowOf(activity).isLockTask)
        assertTrue(dpm.getLockTaskPackages(admin).isEmpty())
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        val restored = PanelKiosk(activity, { _, _ -> error("unexpected authentication") }, {}, { false })
        assertFalse(restored.state.value.enabled)
        restored.destroy()
    }

    @Test
    fun `active calls and destroyed activities never consume an authentication success`() {
        prepareOwner()
        kiosk.enable()
        callActive = true
        authentication.last()(null)
        assertFalse(kiosk.state.value.enabled)
        callActive = false
        kiosk.enable()
        kiosk.destroy()
        authentication.last()(null)
        assertFalse(kiosk.state.value.enabled)
    }

    @Test
    fun `a missing vendor app leaves no temporary kiosk permission`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        var error: String? = null
        kiosk.openApp("com.xiaomi.smarthome") { error = it }
        authentication.last()(null)
        assertNotNull(error)
        assertFalse("com.xiaomi.smarthome" in dpm.getLockTaskPackages(admin))
    }

    @Test
    fun `a background authentication success never opens another app`() {
        secureDevice()
        registerXiaomi()
        kiosk.openApp("com.xiaomi.smarthome") {}
        kiosk.background()
        authentication.last()(null)
        kiosk.resume()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a call already in progress blocks both requests and kiosk restoration`() {
        prepareOwner()
        callActive = true
        kiosk.enable()
        kiosk.openApp("com.domonap.app") {}
        assertTrue(authentication.isEmpty())
        assertFalse(shadowOf(activity).isLockTask)
    }

    @Test
    fun `kiosk cannot start without call notification access`() {
        secureDevice()
        shadowOf(dpm).setDeviceOwner(admin)
        kiosk.enable()
        assertTrue(authentication.isEmpty())
        assertFalse(shadowOf(activity).isLockTask)
        assertNotNull(kiosk.state.value.problem)
    }

    @Test
    @Config(sdk = [29])
    fun `older Android never silently enables a weaker kiosk mode`() {
        prepareOwner()
        kiosk.enable()
        assertTrue(authentication.isEmpty())
        assertFalse(shadowOf(activity).isLockTask)
        assertFalse(kiosk.state.value.enabled)
    }

    @Test
    fun `home revokes Xiaomi permission even while a call is active`() {
        prepareOwner()
        registerXiaomi()
        kiosk.enable()
        authentication.last()(null)
        kiosk.openApp("com.xiaomi.smarthome") {}
        authentication.last()(null)
        assertTrue("com.xiaomi.smarthome" in dpm.getLockTaskPackages(admin))
        callActive = true
        kiosk.onCallStarted()
        kiosk.resume()
        assertEquals(setOf("ru.domovoy", "com.domonap.app"), dpm.getLockTaskPackages(admin).toSet())
        callActive = false
        kiosk.onCallEnded()
        assertTrue(shadowOf(activity).isLockTask)
        assertEquals(setOf("ru.domovoy", "com.domonap.app"), dpm.getLockTaskPackages(admin).toSet())
    }

    @Test
    fun `leaving then returning to the panel invalidates an earlier authentication`() {
        secureDevice()
        registerXiaomi()
        kiosk.openApp("com.xiaomi.smarthome") {}
        kiosk.userLeft()
        kiosk.background()
        kiosk.resume()
        authentication.last()(null)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `persisted intent restores a kiosk interrupted before OS policy was applied`() {
        prepareOwner()
        activity.getSharedPreferences("panel-kiosk", Activity.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        assertTrue(dpm.getLockTaskPackages(admin).isEmpty())
        val restored = PanelKiosk(activity, { _, _ -> error("restore does not authenticate") }, {}, { false })
        restored.resume()
        assertTrue(shadowOf(activity).isLockTask)
        assertEquals(setOf("ru.domovoy", "com.domonap.app"), dpm.getLockTaskPackages(admin).toSet())
        restored.destroy()
    }

    @Test
    fun `a second Home task exit cannot be undone by resuming an older panel task`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        // Another Activity instance has authenticated exit and cleared the OS policy.
        activity.getSharedPreferences("panel-kiosk", Activity.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        dpm.setLockTaskPackages(admin, emptyArray())
        activity.stopLockTask()
        kiosk.resume()
        assertFalse(kiosk.state.value.enabled)
        assertFalse(shadowOf(activity).isLockTask)
        assertTrue(dpm.getLockTaskPackages(admin).isEmpty())
    }

    private fun secureDevice() {
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
    }

    private fun prepareOwner() {
        secureDevice()
        shadowOf(dpm).setDeviceOwner(admin)
        shadowOf(activity.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(activity, DomonapCallListener::class.java), true)
    }

    private fun registerXiaomi() {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "com.xiaomi.smarthome.MainActivity"
                packageName = "com.xiaomi.smarthome"
                applicationInfo = ApplicationInfo().apply { packageName = "com.xiaomi.smarthome" }
            }
        }
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.xiaomi.smarthome"),
            info,
        )
    }
}
