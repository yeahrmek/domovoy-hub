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
    private val allowedApps = setOf(
        "ru.domovoy",
        "com.google.android.googlequicksearchbox",
        "com.domonap.app",
        "com.xiaomi.smarthome",
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "com.sec.android.app.camera",
        "org.amnezia.vpn.family",
        "com.sec.android.gallery3d",
    )
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
    fun `missing PIN prevents enabling protection but allowed apps need no authentication`() {
        shadowOf(dpm).setDeviceOwner(admin)
        registerApp("com.xiaomi.smarthome")
        var failure: String? = "not launched"
        kiosk.openApp("com.xiaomi.smarthome") { failure = it }
        assertNull(failure)
        assertEquals("com.xiaomi.smarthome", shadowOf(activity).nextStartedActivity.component?.packageName)
        kiosk.enable()
        assertTrue(authentication.isEmpty())
        assertFalse(kiosk.state.value.enabled)
        assertNotNull(kiosk.state.value.problem)
    }

    @Test
    fun `authentication is required before enabling and restores the permanent application allowlist`() {
        prepareOwner()
        kiosk.enable()
        assertFalse(kiosk.state.value.enabled)
        authentication.single()(null)
        assertTrue(kiosk.state.value.enabled)
        assertTrue(shadowOf(activity).isLockTask)
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        val features = dpm.getLockTaskFeatures(admin)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS != 0)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_HOME != 0)
        assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK != 0)
        assertEquals(0, features and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW)

        dpm.setLockTaskPackages(admin, arrayOf("ru.domovoy", "com.android.settings"))
        val restored = PanelKiosk(activity, { _, _ -> error("restore needs no authentication") }, {}, { false })
        restored.resume()
        assertTrue(restored.state.value.enabled)
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        restored.destroy()
    }

    @Test
    fun `cancelling or rejecting authentication never enables protection`() {
        prepareOwner()
        kiosk.enable()
        authentication.single()("Проверка отменена")
        assertFalse(kiosk.state.value.enabled)
        assertFalse(shadowOf(activity).isLockTask)
        assertNotNull(kiosk.state.value.error)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `call cancels pending exit and late success cannot leave protection`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        kiosk.exit()
        callActive = true
        kiosk.onCallStarted()
        callActive = false
        authentication.last()(null)
        assertTrue(kiosk.state.value.enabled)
        assertTrue(shadowOf(activity).isLockTask)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `duplicate or superseded authentication success is never reused`() {
        prepareOwner()
        kiosk.enable()
        kiosk.enable()
        authentication.first()(null)
        assertFalse(kiosk.state.value.enabled)
        authentication.last()(null)
        assertTrue(kiosk.state.value.enabled)
        kiosk.exit()
        authentication[1](null)
        assertTrue(kiosk.state.value.enabled)
        assertNull(shadowOf(activity).nextStartedActivity)
        authentication.last()(null)
        assertFalse(kiosk.state.value.enabled)
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        authentication.last()(null)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `every permitted application opens without authentication with protection disabled`() {
        allowedApps.filter { it != activity.packageName && it != "com.google.android.googlequicksearchbox" }.forEach { packageName ->
            registerApp(packageName)
            var failure: String? = "not launched"
            kiosk.openApp(packageName) { failure = it }
            assertNull(failure, packageName)
            assertEquals(packageName, shadowOf(activity).nextStartedActivity.component?.packageName)
        }
        assertTrue(authentication.isEmpty())
    }

    @Test
    fun `allowed applications remain available without PIN after returning home`() {
        prepareOwner()
        kiosk.enable()
        authentication.single()(null)
        allowedApps.filter { it != activity.packageName && it != "com.google.android.googlequicksearchbox" }.forEach { packageName ->
            registerApp(packageName)
            var failure: String? = "not launched"
            kiosk.openApp(packageName) { failure = it }
            assertNull(failure, packageName)
            assertEquals(packageName, shadowOf(activity).nextStartedActivity.component?.packageName)
            kiosk.background()
            kiosk.resume()
            assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
            assertTrue(shadowOf(activity).isLockTask)
        }
        assertEquals(1, authentication.size)
    }

    @Test
    fun `Gemini opens without PIN and its Google host is permanently allowed`() {
        prepareOwner()
        registerApp("com.google.android.apps.bard")
        kiosk.enable()
        authentication.single()(null)
        var failure: String? = null
        kiosk.openApp("com.google.android.apps.bard") { failure = it }
        assertNull(failure)
        assertEquals("com.google.android.apps.bard", shadowOf(activity).nextStartedActivity.component?.packageName)
        assertTrue("com.google.android.googlequicksearchbox" in dpm.getLockTaskPackages(admin))
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        assertEquals(1, authentication.size)
    }

    @Test
    fun `an application outside the allowlist is rejected without opening it`() {
        registerApp("com.android.settings")
        var failure: String? = null
        kiosk.openApp("com.android.settings") { failure = it }
        assertNotNull(failure)
        assertTrue(authentication.isEmpty())
        assertNull(shadowOf(activity).nextStartedActivity)
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
    fun `a missing allowed application leaves the permanent policy unchanged`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        var error: String? = null
        kiosk.openApp("com.xiaomi.smarthome") { error = it }
        assertNotNull(error)
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        assertEquals(1, authentication.size)
        assertTrue(shadowOf(activity).isLockTask)
    }

    @Test
    fun `a background authentication success never exits protection`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        kiosk.exit()
        kiosk.background()
        authentication.last()(null)
        kiosk.resume()
        assertTrue(kiosk.state.value.enabled)
        assertTrue(shadowOf(activity).isLockTask)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `allowed applications do not interrupt calls or a locked background or destroyed panel`() {
        registerApp("com.xiaomi.smarthome")
        fun assertRejected() {
            var failure: String? = null
            kiosk.openApp("com.xiaomi.smarthome") { failure = it }
            assertNotNull(failure)
            assertTrue(authentication.isEmpty())
            assertNull(shadowOf(activity).nextStartedActivity)
        }
        callActive = true
        assertRejected()
        callActive = false
        val keyguard = shadowOf(activity.getSystemService(KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        assertRejected()
        keyguard.setKeyguardLocked(false)
        kiosk.background()
        assertRejected()
        kiosk.resume()
        kiosk.destroy()
        assertRejected()
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
    fun `home preserves the permanent application allowlist while a call is active`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        callActive = true
        kiosk.onCallStarted()
        kiosk.resume()
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
        callActive = false
        kiosk.onCallEnded()
        assertTrue(shadowOf(activity).isLockTask)
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
    }

    @Test
    fun `leaving then returning to the panel invalidates an earlier authentication`() {
        prepareOwner()
        kiosk.enable()
        authentication.last()(null)
        kiosk.exit()
        kiosk.userLeft()
        kiosk.background()
        kiosk.resume()
        authentication.last()(null)
        assertTrue(kiosk.state.value.enabled)
        assertTrue(shadowOf(activity).isLockTask)
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
        assertEquals(allowedApps, dpm.getLockTaskPackages(admin).toSet())
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

    private fun registerApp(packageName: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "$packageName.MainActivity"
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        }
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
            info,
        )
    }
}
