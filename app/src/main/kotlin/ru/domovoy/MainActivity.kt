package ru.domovoy

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.domovoy.core.KnownRecuperators
import ru.domovoy.core.TokenStore
import ru.domovoy.integrations.domonap.CallState
import ru.domovoy.integrations.domonap.DomonapCallListener
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.tuya.TuyaLanClient
import ru.domovoy.integrations.tuya.TuyaLanDevice
import ru.domovoy.integrations.tuya.TuyaLanNetwork
import ru.domovoy.integrations.tuya.tuyaLanDevices
import ru.domovoy.integrations.xiaomi.MI_HOME_PACKAGE
import ru.domovoy.integrations.xiaomi.MiHomeNotificationStatus
import ru.domovoy.integrations.xiaomi.XiaomiLanClient
import ru.domovoy.integrations.xiaomi.XiaomiLanDevice
import ru.domovoy.integrations.xiaomi.miHomeNotifications
import ru.domovoy.integrations.xiaomi.xiaomiLanDevices
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.kiosk.KioskSettings
import ru.domovoy.kiosk.PanelAuthentication
import ru.domovoy.kiosk.PanelKiosk
import ru.domovoy.kiosk.lockTaskChanges
import ru.domovoy.panel.AdaptivePolling
import ru.domovoy.panel.CardPreferencesStore
import ru.domovoy.panel.MiHomeMessages
import ru.domovoy.panel.PanelRooms
import ru.domovoy.panel.TuyaPoll
import ru.domovoy.panel.YandexPoll
import ru.domovoy.panel.closeOnCall
import ru.domovoy.panel.launcherTiles
import ru.domovoy.panel.recuperatorRooms
import ru.domovoy.panel.resetAfterIdle
import ru.domovoy.panel.returnToHome
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Advances displayed reading ages independently of network polling. */
private val AGE_TICK = 15.seconds

/** LAN polls do not consume Tuya Cloud allowance. */
private val TUYA_POLL_INTERVAL = 15.seconds

/** Return to Главная after two minutes without touch input. */
private val IDLE_RESET = 2.minutes

/** All vendor credentials share one encrypted store. */
private const val SECRETS_FILE = "domovoy-secrets"

class MainActivity : ComponentActivity() {
    private val foreground = MutableStateFlow(false)
    private lateinit var kiosk: PanelKiosk
    private var accessOpen by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        kiosk.resume()
        refreshMiHomeNotificationAccess()
    }

    private fun refreshMiHomeNotificationAccess() {
        val component = ComponentName(this, DomonapCallListener::class.java)
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        miHomeNotifications.onAccessChanged(granted)
        if (granted && miHomeNotifications.status.value != MiHomeNotificationStatus.Connected) {
            NotificationListenerService.requestRebind(component)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        kiosk.userLeft()
    }

    override fun onDestroy() {
        kiosk.destroy()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        foreground.value = true
    }

    override fun onStop() {
        foreground.value = false
        kiosk.background()
        super.onStop()
    }

    private val interactions = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    override fun onUserInteraction() {
        super.onUserInteraction()
        interactions.tryEmit(Unit)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) interactions.tryEmit(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authentication = PanelAuthentication(this)
        kiosk = PanelKiosk(this, authentication::request, authentication::cancel)
        lifecycleScope.launch {
            domonapCalls.state.collect { call ->
                if (call == CallState.Active) {
                    accessOpen = false
                    kiosk.onCallStarted()
                } else {
                    kiosk.onCallEnded()
                }
            }
        }
        lifecycleScope.launch { lockTaskChanges.collect { kiosk.refresh() } }
        val secrets = secrets(applicationContext)
        setContent {
            var appLaunchError by remember { mutableStateOf<String?>(null) }
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) panelDarkScheme else panelLightScheme,
                typography = panelTypography,
            ) {
                Surface {
                    val protection by kiosk.state.collectAsState()
                    // Keep the Home task alive; sheets and menus retain their own Back handlers.
                    BackHandler(enabled = protection.enabled) { }
                    Panel(
                        secrets,
                        interactions,
                        foreground,
                        onPanelAccess = {
                            appLaunchError = null
                            kiosk.refresh()
                            accessOpen = domonapCalls.state.value != CallState.Active
                        },
                        accessMessage = protection.error ?: if (protection.enabled && !protection.locked) {
                            protection.problem ?: "Защита панели сейчас не действует. Откройте «Защита» и проверьте настройки."
                        } else {
                            null
                        },
                        openApp = { packageName, result ->
                            if (packageName == MI_HOME_PACKAGE) {
                                kiosk.openVacuum(runCatching { secrets.vacuumDeviceId() }.getOrDefault(""), result)
                            } else {
                                kiosk.openApp(packageName, result)
                            }
                        },
                    )
                    if (accessOpen) {
                        KioskSettings(
                            state = protection.copy(error = appLaunchError ?: protection.error),
                            onEnable = {
                                accessOpen = false
                                kiosk.enable()
                            },
                            onExit = {
                                accessOpen = false
                                kiosk.exit()
                            },
                            onDismiss = { accessOpen = false },
                            onOpenApp = { packageName ->
                                kiosk.openApp(packageName) { failure ->
                                    appLaunchError = failure
                                    if (failure == null) accessOpen = false
                                }
                            },
                            onRemoveOwner = {
                                appLaunchError = null
                                accessOpen = false
                                kiosk.removeOwner()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Credential readers and the cached recuperator inventory. */
private class PanelSecrets(
    val vacuumDeviceId: () -> String,
    val yandexToken: () -> String,
    val tuya: () -> List<TuyaLanDevice>,
    val xiaomi: () -> List<XiaomiLanDevice>,
    /** Device ids are apartment-identifying, so the inventory is encrypted too. */
    val recuperators: KnownRecuperators,
)

/** Seeds an empty encrypted store from BuildConfig once; subsequent launches use stored values. */
private fun secrets(context: Context): PanelSecrets {
    val prefs =
        runCatching { encryptedPrefs(context) }.getOrElse { failure ->
            // Lost keystore: keep the panel running and expose the failure through each vendor.
            val reason = { error("secure storage unavailable: ${failure.message}") }
            return PanelSecrets(vacuumDeviceId = reason, yandexToken = reason, tuya = reason, xiaomi = reason, recuperators = KnownRecuperators(null))
        }
    val store = TokenStore(prefs)
    store.seedXiaomiVacuumDeviceId(BuildConfig.XIAOMI_VACUUM_DEVICE_ID)
    store.seedYandexToken(BuildConfig.YANDEX_OAUTH_TOKEN)
    store.seedTuyaLanDevices(BuildConfig.TUYA_LOCAL_DEVICES)
    store.seedXiaomiLanDevices(BuildConfig.XIAOMI_LOCAL_DEVICES)
    return PanelSecrets(
        vacuumDeviceId = store::xiaomiVacuumDeviceId,
        yandexToken = store::yandexToken,
        tuya = { tuyaLanDevices(store.tuyaLanDevices()) },
        xiaomi = { xiaomiLanDevices(store.xiaomiLanDevices()) },
        recuperators = KnownRecuperators(prefs),
    )
}

// Required by AGENTS.md; keep credentials encrypted until the storage decision changes.
@Suppress("DEPRECATION")
private fun encryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    SECRETS_FILE,
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)

@Composable
private fun Panel(
    secrets: PanelSecrets,
    touches: MutableSharedFlow<Unit>,
    foreground: StateFlow<Boolean>,
    onPanelAccess: () -> Unit,
    accessMessage: String?,
    openApp: (String, (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val miHomeMessages by miHomeNotifications.notifications.collectAsState()
    val miHomeStatus by miHomeNotifications.status.collectAsState()
    val callState by domonapCalls.state.collectAsState()
    LaunchedEffect(miHomeStatus) {
        if (miHomeStatus == MiHomeNotificationStatus.Connecting) {
            delay(10.seconds)
            if (miHomeNotifications.status.value == MiHomeNotificationStatus.Connecting) miHomeNotifications.onListenerDisconnected()
        }
    }
    val cardStore = remember(context) { CardPreferencesStore(runCatching { encryptedPrefs(context) }.getOrNull()) }
    var cardPreferences by remember(cardStore) { mutableStateOf(cardStore.load()) }
    // Vendor clients share the connection pool, but have independent call timeouts.
    val http = remember { OkHttpClient() }
    val client =
        remember(http) {
            YandexClient(
                http = http,
                token = secrets.yandexToken,
                householdId = BuildConfig.YANDEX_HOUSEHOLD_ID,
            )
        }
    val xiaomi = remember(secrets) { XiaomiLanClient(configuration = secrets.xiaomi) }
    val poll = remember(client, xiaomi) { YandexPoll(client, xiaomi) }
    val xiaomiPolling = remember(poll) {
        AdaptivePolling(activeInterval = 15.seconds, idleInterval = 1.minutes, nightInterval = 5.minutes) {
            poll.discovered.refreshLocal()
            val local = poll.discovered.state.value
            local.localIds.isEmpty() || local.localIds.any { it !in local.readErrors }
        }
    }
    val xiaomiInterval by xiaomiPolling.interval.collectAsState()
    LaunchedEffect(xiaomiPolling, foreground) { xiaomiPolling.run(foreground, domonapCalls.state) }
    val tuyaNetwork = remember(context) { TuyaLanNetwork(context.applicationContext) }
    val tuyaPoll =
        remember(tuyaNetwork) {
            TuyaPoll(
                client = TuyaLanClient(configuration = secrets.tuya, connect = tuyaNetwork::connect, discover = tuyaNetwork::discover),
                rooms = recuperatorRooms(BuildConfig.TUYA_ROOMS),
                known = secrets.recuperators,
            )
        }
    val tuyaPolling = remember(tuyaPoll) {
        AdaptivePolling(activeInterval = TUYA_POLL_INTERVAL, idleInterval = 1.minutes, nightInterval = 5.minutes) {
            tuyaPoll.refresh()
            tuyaPoll.recuperators.state.value.error == null
        }
    }
    LaunchedEffect(poll, tuyaPolling, xiaomiPolling, touches) {
        touches.collect {
            poll.polling.touch(refresh = false)
            tuyaPolling.touch(refresh = false)
            xiaomiPolling.touch(refresh = false)
        }
    }
    val yandexInterval by poll.polling.interval.collectAsState()
    val tuyaInterval by tuyaPolling.interval.collectAsState()
    val tiles = poll.bulbs
    val curtains = poll.curtains
    val acs = poll.acs
    val strips = poll.strips
    val recuperators = tuyaPoll.recuperators
    val state by tiles.state.collectAsState()
    val curtainState by curtains.state.collectAsState()
    val acState by acs.state.collectAsState()
    val stripState by strips.state.collectAsState()
    val discoveredState by poll.discovered.state.collectAsState()
    val deviceRefresh by poll.refreshState.collectAsState()
    val recuperatorState by recuperators.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    val scroll = rememberLazyGridState()
    val openSheet = remember { mutableStateOf<String?>(null) }
    val roomPickerOpen = remember { mutableStateOf(false) }
    val accessibility = remember(context) { requireNotNull(context.getSystemService(AccessibilityManager::class.java)) }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> touchExploration = enabled }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    LaunchedEffect(touchExploration) {
        if (!touchExploration) {
            resetAfterIdle(touches, IDLE_RESET) {
                scope.launch {
                    roomPickerOpen.value = false
                    returnToHome(scroll, openSheet)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        closeOnCall(domonapCalls.state) {
            openSheet.value = null
            roomPickerOpen.value = false
        }
    }

    var launcherErrors by remember { mutableStateOf(emptyMap<String, String>()) }
    // Package installation can change while the panel stays open.
    val launchers =
        remember(now) {
            launcherTiles { packageName -> context.packageManager.getLaunchIntentForPackage(packageName) != null }
        }

    LaunchedEffect(poll, foreground) {
        poll.polling.run(foreground, domonapCalls.state)
    }

    LaunchedEffect(tuyaPolling, foreground) {
        tuyaPolling.run(foreground, domonapCalls.state)
    }

    // Read ages keep advancing while polling is paused for an intercom call.
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(AGE_TICK)
        }
    }

    PanelRooms(
        acs = acState,
        curtains = curtainState,
        strips = stripState,
        recuperators = recuperatorState,
        bulbs = state,
        discovered = discoveredState,
        launchers = launchers,
        now = now,
        yandexInterval = yandexInterval,
        tuyaInterval = tuyaInterval,
        xiaomiInterval = xiaomiInterval,
        modifier =
        Modifier.fillMaxSize()
            .onPreviewKeyEvent {
                touches.tryEmit(Unit)
                poll.polling.touch(refresh = it.type == KeyEventType.KeyDown)
                false
            }
            .onFocusChanged { if (it.hasFocus) touches.tryEmit(Unit) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastActivityAt: Long? = null
                    var pressedAt = 0L
                    var pressPosition = Offset.Zero
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val time = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                        // Movement extends active use; press and release are signalled immediately.
                        if (event.type != PointerEventType.Move || lastActivityAt == null || time - lastActivityAt >= 250L) {
                            lastActivityAt = time
                            touches.tryEmit(Unit)
                        }
                        if (event.type == PointerEventType.Press) {
                            if (event.changes.count { it.pressed } == 1) {
                                pressedAt = time
                                pressPosition = event.changes.first { it.pressed }.position
                                moved = false
                            } else {
                                moved = true
                            }
                        }
                        if (event.changes.any { (it.position - pressPosition).getDistanceSquared() > viewConfiguration.touchSlop * viewConfiguration.touchSlop }) {
                            moved = true
                        }
                        if (event.type == PointerEventType.Release && event.changes.none { it.pressed }) {
                            // Sorting and scrolling do not request extra vendor reads during the gesture.
                            if (!moved && time - pressedAt in 0 until viewConfiguration.longPressTimeoutMillis) poll.polling.touch()
                        }
                    }
                }
            },
        scroll = scroll,
        openSheet = openSheet,
        roomPickerOpen = roomPickerOpen,
        launcherErrors = launcherErrors,
        notificationContent = {
            if (callState != CallState.Active) MiHomeMessages(miHomeMessages, miHomeStatus)
        },
        onPanelAccess = onPanelAccess,
        accessMessage = accessMessage,
        deviceRefresh = deviceRefresh,
        onRefreshDevices = {
            scope.launch { poll.refreshDevices() }
            scope.launch { xiaomiPolling.refresh(force = true) }
        },
        cardPreferences = cardPreferences,
        onCardPreferencesChange = { value ->
            cardPreferences = value
            cardStore.save(value)
        },
        onToggleAc = { id ->
            touches.tryEmit(Unit)
            scope.launch { acs.toggle(id) }
        },
        onSetTemperature = { id, celsius ->
            touches.tryEmit(Unit)
            scope.launch { acs.setTemperature(id, celsius) }
        },
        onSetAcMode = { id, instance, value ->
            touches.tryEmit(Unit)
            scope.launch { acs.setMode(id, instance, value) }
        },
        onSetAcToggle = { id, instance, on ->
            touches.tryEmit(Unit)
            scope.launch { acs.setToggle(id, instance, on) }
        },
        onSetOpen = { id, percent ->
            touches.tryEmit(Unit)
            scope.launch { curtains.setOpen(id, percent) }
        },
        onToggleStrip = { id ->
            touches.tryEmit(Unit)
            scope.launch { strips.toggle(id) }
        },
        onSetBrightness = { id, percent ->
            touches.tryEmit(Unit)
            scope.launch { strips.setBrightness(id, percent) }
        },
        onToggleRecuperator = { id ->
            touches.tryEmit(Unit)
            scope.launch { recuperators.toggle(id) }
        },
        onSetRecuperatorSpeed = { id, speed ->
            touches.tryEmit(Unit)
            scope.launch { recuperators.setSpeed(id, speed) }
        },
        onToggleBulb = { id ->
            touches.tryEmit(Unit)
            scope.launch { tiles.toggle(id) }
        },
        onToggleDiscovered = { id ->
            touches.tryEmit(Unit)
            scope.launch { poll.discovered.toggle(id) }
        },
        onSetDiscoveredHumidity = { id, percent ->
            touches.tryEmit(Unit)
            scope.launch { poll.discovered.setHumidity(id, percent) }
        },
        onSetDiscoveredMode = { id, instance, value ->
            touches.tryEmit(Unit)
            scope.launch { poll.discovered.setMode(id, instance, value) }
        },
        onSetBulbBrightness = { id, percent ->
            touches.tryEmit(Unit)
            scope.launch { tiles.setBrightness(id, percent) }
        },
        onSetBulbScene = { id, scene ->
            touches.tryEmit(Unit)
            scope.launch { tiles.setScene(id, scene) }
        },
        onSetBulbRgb = { id, rgb ->
            touches.tryEmit(Unit)
            scope.launch { tiles.setRgb(id, rgb) }
        },
        onOpenApp = { packageName ->
            touches.tryEmit(Unit)
            openApp(packageName) { failure ->
                launcherErrors = if (failure == null) launcherErrors - packageName else launcherErrors + (packageName to failure)
            }
        },
    )
}

/** Opens the vendor app; an absent package or launch failure must not crash the panel. */
internal fun openVendorApp(
    context: Context,
    packageName: String,
): String? {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "Приложение недоступно"
        // Android 13 can launch a known package without granting package-query visibility.
        return runCatching {
            context.packageManager.getLaunchIntentSenderForPackage(packageName).sendIntent(context, 0, null, null, null)
        }.fold(
            onSuccess = { null },
            onFailure = { "Приложение недоступно" },
        )
    }
    return runCatching { context.startActivity(intent) }.fold(
        onSuccess = { null },
        onFailure = { failure ->
            Log.w("DomovoyHub", "could not open $packageName", failure)
            "Не удалось открыть приложение"
        },
    )
}
