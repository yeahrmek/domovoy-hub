package ru.domovoy

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.tuya.TuyaLanClient
import ru.domovoy.integrations.tuya.TuyaLanDevice
import ru.domovoy.integrations.tuya.TuyaLanNetwork
import ru.domovoy.integrations.tuya.tuyaLanDevices
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.panel.AdaptivePolling
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

    override fun onStart() {
        super.onStart()
        foreground.value = true
    }

    override fun onStop() {
        foreground.value = false
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
        val secrets = secrets(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) panelDarkScheme else panelLightScheme,
                typography = panelTypography,
            ) {
                Surface {
                    Panel(secrets, interactions, foreground)
                }
            }
        }
    }
}

/** Credential readers and the cached recuperator inventory. */
private class PanelSecrets(
    val yandexToken: () -> String,
    val tuya: () -> List<TuyaLanDevice>,
    /** Device ids are apartment-identifying, so the inventory is encrypted too. */
    val recuperators: KnownRecuperators,
)

/** Seeds an empty encrypted store from BuildConfig once; subsequent launches use stored values. */
private fun secrets(context: Context): PanelSecrets {
    val prefs =
        runCatching { encryptedPrefs(context) }.getOrElse { failure ->
            // Lost keystore: keep the panel running and expose the failure through each vendor.
            val reason = { error("secure storage unavailable: ${failure.message}") }
            return PanelSecrets(yandexToken = reason, tuya = reason, recuperators = KnownRecuperators(null))
        }
    val store = TokenStore(prefs)
    store.seedYandexToken(BuildConfig.YANDEX_OAUTH_TOKEN)
    store.seedTuyaLanDevices(BuildConfig.TUYA_LOCAL_DEVICES)
    return PanelSecrets(
        yandexToken = store::yandexToken,
        tuya = { tuyaLanDevices(store.tuyaLanDevices()) },
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
private fun Panel(secrets: PanelSecrets, touches: MutableSharedFlow<Unit>, foreground: StateFlow<Boolean>) {
    val context = LocalContext.current
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
    val poll = remember(client) { YandexPoll(client) }
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
    LaunchedEffect(poll, tuyaPolling, touches) {
        touches.collect {
            poll.polling.touch(refresh = false)
            tuyaPolling.touch(refresh = false)
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
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touches.tryEmit(Unit)
                        poll.polling.touch(refresh = event.type == PointerEventType.Press)
                    }
                }
            },
        scroll = scroll,
        openSheet = openSheet,
        roomPickerOpen = roomPickerOpen,
        launcherErrors = launcherErrors,
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
            val failure = openVendorApp(context, packageName)
            launcherErrors = if (failure == null) launcherErrors - packageName else launcherErrors + (packageName to failure)
        },
    )
}

/** Opens the vendor app; an absent package or launch failure must not crash the panel. */
internal fun openVendorApp(
    context: Context,
    packageName: String,
): String? {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return "Приложение недоступно"
    return runCatching { context.startActivity(intent) }.fold(
        onSuccess = { null },
        onFailure = { failure ->
            Log.w("DomovoyHub", "could not open $packageName", failure)
            "Не удалось открыть приложение"
        },
    )
}
