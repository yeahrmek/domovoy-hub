package ru.domovoy.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.domovoy.MainActivity
import ru.domovoy.integrations.domonap.CallState
import ru.domovoy.integrations.domonap.DomonapCallListener
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.xiaomi.MI_HOME_PACKAGE
import ru.domovoy.integrations.xiaomi.miHomeDeviceIntent
import ru.domovoy.openVendorApp

/** The household apps available without leaving protection. */
internal val kioskApps = mapOf(
    "com.domonap.app" to "Domonap",
    "com.xiaomi.smarthome" to "Mi Home",
    "com.openai.chatgpt" to "ChatGPT",
    "com.google.android.apps.bard" to "Gemini",
    "com.sec.android.app.camera" to "Камера",
    "org.amnezia.vpn.family" to "Amnezia VPN",
    "com.sec.android.gallery3d" to "Галерея",
)

internal data class KioskState(
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val deviceOwner: Boolean = false,
    val problem: String? = null,
    val error: String? = null,
)

/** Owns the kiosk policy and one-shot authorizations. No call notification or intent is modified. */
internal class PanelKiosk(
    private val activity: Activity,
    private val authenticate: (String, (String?) -> Unit) -> Unit,
    private val cancelAuthentication: () -> Unit,
    private val callActive: () -> Boolean = { domonapCalls.state.value == CallState.Active },
) {
    private val dpm = activity.getSystemService(DevicePolicyManager::class.java)
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private val admin = ComponentName(activity, PanelDeviceAdmin::class.java)
    private val allowedPackages = (listOf(activity.packageName, "com.google.android.googlequicksearchbox") + kioskApps.keys).toTypedArray()
    private val preferences = activity.getSharedPreferences("panel-kiosk", Activity.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(KioskState(enabled = preferences.getBoolean("enabled", false)))
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var destroyed = false
    private var foreground = false
    private var pendingResult: ((String?) -> Unit)? = null

    fun resume() {
        foreground = true
        // Android can create a separate Home task. Another panel task may have exited protection.
        mutableState.value = state.value.copy(enabled = preferences.getBoolean("enabled", false))
        // Restore the same household allowlist after returning Home or process death.
        if (state.value.enabled) {
            attempt {
                if (callActive()) {
                    // Restore allowed packages without taking the foreground away from the call.
                    dpm.setLockTaskPackages(admin, allowedPackages)
                } else {
                    check(availabilityProblem() == null) { "Настройки защиты изменились" }
                    applyPolicy()
                    startLocked()
                }
            }
        }
        refresh()
    }

    fun background() {
        foreground = false
    }

    fun userLeft() {
        foreground = false
        cancelPending("Проверка отменена при выходе из панели")
    }

    fun refresh() {
        mutableState.value = state.value.copy(
            locked = activity.getSystemService(ActivityManager::class.java).lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED,
            deviceOwner = dpm.isDeviceOwnerApp(activity.packageName),
            problem = availabilityProblem(),
        )
    }

    fun enable() {
        refresh()
        val problem = state.value.problem
        if (problem != null) {
            mutableState.value = state.value.copy(error = problem)
            return
        }
        authorize("Включить защиту панели") { failure ->
            if (failure == null) {
                val applied = attempt {
                    check(availabilityProblem() == null) { "Настройки устройства изменились. Повторите включение защиты." }
                    // Persist intent before OS policy: a killed process must restore protection.
                    check(preferences.edit().putBoolean("enabled", true).commit()) { "Не удалось сохранить защиту" }
                    mutableState.value = state.value.copy(enabled = true, error = null)
                    applyPolicy()
                    startLocked()
                }
                if (!applied) {
                    // Keep enabled=true if rollback fails, so the next launch retries recovery.
                    runCatching {
                        releasePolicy()
                        check(preferences.edit().putBoolean("enabled", false).commit())
                        mutableState.value = state.value.copy(enabled = false)
                    }.onFailure { Log.w("PanelKiosk", "kiosk rollback failed", it) }
                }
            } else {
                mutableState.value = state.value.copy(error = failure)
            }
            refresh()
        }
    }

    fun exit() {
        authorize("Выйти из панели") { failure ->
            if (failure != null) {
                mutableState.value = state.value.copy(error = failure)
            } else {
                attempt {
                    // Preserve the enabled preference on policy failures, so recovery can retry.
                    releasePolicy()
                    check(preferences.edit().putBoolean("enabled", false).commit()) { "Не удалось сохранить выход из панели" }
                    mutableState.value = state.value.copy(enabled = false, error = null)
                    activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            refresh()
        }
    }

    @Suppress("DEPRECATION")
    fun removeOwner() {
        authorize("Снять управление планшетом") { failure ->
            if (failure != null) {
                mutableState.value = state.value.copy(error = failure)
            } else {
                attempt {
                    check(dpm.isDeviceOwnerApp(activity.packageName)) { "Панель уже не управляет планшетом" }
                    releasePolicy()
                    dpm.getUserRestrictions(admin).keySet().toList().forEach { dpm.clearUserRestriction(admin, it) }
                    check(dpm.getUserRestrictions(admin).isEmpty) { "Не удалось снять ограничения управления" }
                    // Persist the ordinary mode before giving up the privileges needed to restore it.
                    check(preferences.edit().putBoolean("enabled", false).commit()) { "Не удалось сохранить отключение защиты" }
                    mutableState.value = state.value.copy(enabled = false)
                    dpm.clearDeviceOwnerApp(activity.packageName)
                    check(!dpm.isDeviceOwnerApp(activity.packageName)) { "Права управления остались активны" }
                    // Android finishes disabling the admin through an asynchronous ordered broadcast.
                    activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            refresh()
        }
    }

    fun openApp(packageName: String, result: (String?) -> Unit) {
        openApp(packageName, result) { openVendorApp(activity, packageName) }
    }

    fun openVacuum(deviceId: String, result: (String?) -> Unit) {
        openApp(MI_HOME_PACKAGE, result) {
            if (deviceId.isBlank()) {
                "Пылесос не настроен. Откройте Mi Home через меню «Защита»."
            } else {
                activity.startActivity(miHomeDeviceIntent(deviceId))
                null
            }
        }
    }

    private fun openApp(packageName: String, result: (String?) -> Unit, launch: () -> String?) {
        if (packageName !in kioskApps) {
            result("Приложение не разрешено")
            return
        }
        if (destroyed || !foreground || callActive() || keyguard.isKeyguardLocked) {
            result("Сейчас приложение недоступно. Повторите после звонка или разблокировки планшета.")
            return
        }
        val error = try {
            if (state.value.enabled) {
                check(availabilityProblem() == null) { "Защита панели недоступна" }
                dpm.setLockTaskPackages(admin, allowedPackages)
            }
            launch()
        } catch (failure: Exception) {
            Log.w("PanelKiosk", "vendor launch failed", failure)
            "Не удалось открыть приложение"
        }
        result(error)
    }

    fun onCallStarted() {
        cancelPending("Входящий звонок: проверка отменена")
    }

    fun onCallEnded() {
        if (foreground) resume()
    }

    fun destroy() {
        destroyed = true
        cancelPending("Проверка отменена")
    }

    private fun authorize(title: String, result: (String?) -> Unit) {
        cancelPending("Проверка отменена")
        if (destroyed || !foreground || callActive() || keyguard.isKeyguardLocked) {
            result("Сейчас проверка недоступна. Повторите после звонка или разблокировки планшета.")
            return
        }
        if (!keyguard.isDeviceSecure) {
            result("Сначала задайте PIN-код в настройках блокировки экрана планшета.")
            return
        }
        mutableState.value = state.value.copy(error = null)
        val request = generation
        pendingResult = result
        try {
            authenticate(title) { failure ->
                if (generation != request || destroyed) return@authenticate
                val completion = pendingResult ?: return@authenticate
                pendingResult = null
                generation++
                val blocked = !foreground || callActive() || keyguard.isKeyguardLocked
                completion(if (blocked) "Проверка отменена. Повторите действие в панели." else failure)
            }
        } catch (failure: Exception) {
            Log.w("PanelKiosk", "authentication unavailable", failure)
            cancelPending("Не удалось проверить доступ. Повторите действие.")
        }
    }

    private fun cancelPending(reason: String) {
        generation++
        val completion = pendingResult
        pendingResult = null
        cancelAuthentication()
        completion?.invoke(reason)
    }

    private fun availabilityProblem(): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "Защита панели требует Android 11 или новее."
        !dpm.isDeviceOwnerApp(activity.packageName) -> "Нужна однократная настройка планшета через компьютер."
        !keyguard.isDeviceSecure -> "Сначала задайте PIN-код в настройках блокировки экрана планшета."
        !activity.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(activity, DomonapCallListener::class.java)) ->
            "Разрешите Domovoy Hub доступ к уведомлениям, чтобы входящие звонки отменяли проверку доступа."
        else -> null
    }

    private fun applyPolicy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) error("Требуется Android 11")
        check(dpm.isDeviceOwnerApp(activity.packageName)) { "Нужна настройка владельца устройства" }
        // NOTIFICATIONS needs HOME. Keep Domonap heads-up notifications, keyguard and power menu.
        // Blocking activities inside existing tasks prevents vendor links escaping the allowlist.
        dpm.setLockTaskFeatures(
            admin,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK,
        )
        dpm.setLockTaskPackages(admin, allowedPackages)
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
    }

    private fun releasePolicy() {
        activity.stopLockTask()
        if (dpm.isDeviceOwnerApp(activity.packageName)) {
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            dpm.setLockTaskPackages(admin, emptyArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
        }
    }

    private fun startLocked() {
        check(!keyguard.isKeyguardLocked) { "Сначала разблокируйте планшет" }
        check(dpm.isLockTaskPermitted(activity.packageName)) { "Панель не разрешена в режиме киоска" }
        // Without this check Android silently uses user-dismissible screen pinning.
        activity.startLockTask()
    }

    private fun attempt(action: () -> Unit): Boolean = try {
        action()
        mutableState.value = state.value.copy(error = null)
        true
    } catch (failure: Exception) {
        Log.w("PanelKiosk", "kiosk policy failed", failure)
        mutableState.value = state.value.copy(error = "Не удалось применить защиту панели. Повторите действие или проверьте настройку планшета.")
        false
    }
}
