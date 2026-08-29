package ru.domovoy

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.domovoy.core.KnownRecuperators
import ru.domovoy.core.TokenStore
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.tuya.TuyaClient
import ru.domovoy.integrations.tuya.TuyaCredentials
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.panel.PanelRooms
import ru.domovoy.panel.TuyaPoll
import ru.domovoy.panel.YandexPoll
import ru.domovoy.panel.launcherTiles
import ru.domovoy.panel.pollPausingForCalls
import ru.domovoy.panel.recuperatorRooms
import ru.domovoy.panel.resetAfterIdle
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Yandex publishes no push mechanism, so the panel polls. One `/v1.0/user/info` call is the whole
 * house; 15 s is inside the rate limit Yandex's other APIs allow, though nothing is published for
 * this one — see docs/yandex.md.
 */
private val POLL_INTERVAL = 15.seconds

/**
 * The recuperators are polled far more slowly, and on their own timer: a Tuya refresh is five calls
 * — the inventory plus one per device, because the batch shadow route is not authorised — against
 * an allowance denominated in money. ~54,000 calls a month is a refresh every ~4 minutes around
 * the clock, so 6 leaves room for the token refreshes and for taps, each of which costs a command
 * and a re-read. See docs/tuya.md.
 */
private val TUYA_POLL_INTERVAL = 6.minutes

/**
 * How long the panel stays on the tab it was left on. A wall panel is walked up to by someone who
 * did not leave it there, so after this much quiet it goes back to Главная. Two minutes is a guess
 * and is a single constant; see docs/ui.md, "Open".
 */
private val IDLE_RESET = 2.minutes

/** One store for the panel, not one per vendor: every vendor's credentials land in this file. */
private const val SECRETS_FILE = "domovoy-secrets"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val secrets = secrets(applicationContext)
        setContent {
            // The panel's own palette, and the system's own answer to which of the two. The tablet
            // switches itself at 19:00 and back at 07:00, so this is the wall going dark for the
            // night rather than a setting anybody touches. No dynamic colour: see [PanelTheme].
            //
            // And the panel's own type scale, in the same call, because a `MaterialTheme` given only
            // a scheme runs on Material's baseline typography — a scale drawn for a phone held 30 cm
            // from the face, which is what put every status line on this wall at 12sp. See
            // [panelTypography], which also records what it assumes about reading distance.
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) panelDarkScheme else panelLightScheme,
                typography = panelTypography,
            ) {
                Surface {
                    Panel(secrets)
                }
            }
        }
    }
}

/** How each vendor's client reads its credentials, both out of the one encrypted store. */
private class PanelSecrets(
    val yandexToken: () -> String,
    val tuya: () -> TuyaCredentials,
    /**
     * Who the recuperators were on the last run. Out of the same store, and for the same reason it
     * is encrypted: device ids identify the flat. See [KnownRecuperators].
     */
    val recuperators: KnownRecuperators,
)

/**
 * Opens the encrypted store and answers with the functions the clients read their credentials
 * through.
 *
 * On a fresh install the store is empty, so the values that came from `local.properties` through
 * `BuildConfig` seed it once — that is the only way a credential gets onto the tablet today. From
 * then on the store is the source of truth and the build-time values are ignored, so anything
 * written there later is not undone by the stale copy in the APK.
 */
private fun secrets(context: Context): PanelSecrets {
    val prefs =
        runCatching { encryptedPrefs(context) }.getOrElse { failure ->
            // A keystore the tablet lost — a restored backup, a wiped key — must not take the
            // panel down on a wall no-one is watching. Every tile group says this instead, and the
            // panel comes up remembering nothing rather than not coming up.
            val reason = { error("secure storage unavailable: ${failure.message}") }
            return PanelSecrets(yandexToken = reason, tuya = reason, recuperators = KnownRecuperators(null))
        }
    val store = TokenStore(prefs)
    store.seedYandexToken(BuildConfig.YANDEX_OAUTH_TOKEN)
    store.seedTuyaCredentials(
        clientId = BuildConfig.TUYA_CLIENT_ID,
        clientSecret = BuildConfig.TUYA_CLIENT_SECRET,
        uid = BuildConfig.TUYA_UID,
    )
    return PanelSecrets(
        yandexToken = store::yandexToken,
        tuya = { TuyaCredentials(store.tuyaClientId(), store.tuyaClientSecret(), store.tuyaUid()) },
        recuperators = KnownRecuperators(prefs),
    )
}

// Jetpack Security Crypto is deprecated upstream and 1.1.0 is its last release; the choice to
// use it anyway is recorded in gradle/libs.versions.toml and docs/yandex.md, so the nine
// warnings it emits here are noise rather than news.
@Suppress("DEPRECATION")
private fun encryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    SECRETS_FILE,
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)

@Composable
private fun Panel(secrets: PanelSecrets) {
    // One OkHttp client for both vendors: the connection pool and dispatcher are the point of
    // sharing it, and neither client knows about the other.
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
    val tuyaPoll =
        remember(http) {
            TuyaPoll(
                client = TuyaClient(http = http, credentials = secrets.tuya),
                // Tuya's API names no room, so this is where the flat's own answer comes in. It is
                // not a credential and does not go through the encrypted store: it is only in
                // local.properties because device ids are apartment-identifying. Unset is a
                // working panel — the recuperators simply show up unplaced.
                rooms = recuperatorRooms(BuildConfig.TUYA_ROOMS),
                // A Tuya refresh is five calls every 6 minutes, so a tablet that rebooted into a
                // Wi-Fi that was not up yet would stand there with one line of error where five
                // tiles belong. It puts up who it read last time instead, values blank.
                known = secrets.recuperators,
            )
        }
    val tiles = poll.bulbs
    val curtains = poll.curtains
    val acs = poll.acs
    val strips = poll.strips
    val recuperators = tuyaPoll.recuperators
    val state by tiles.state.collectAsState()
    val curtainState by curtains.state.collectAsState()
    val acState by acs.state.collectAsState()
    val stripState by strips.state.collectAsState()
    val recuperatorState by recuperators.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    // Where the wall is scrolled to, and every touch that says somebody is still standing there.
    // The flow is what the idle reset watches; DROP_OLDEST because a touch arriving while one is
    // still queued says nothing new — both mean a hand is on the panel — and the pointer handler
    // must not suspend to say it.
    //
    // The rooms are one scroll rather than a strip of tabs, so going back to Главная is going back
    // to the top: the state is held here because the reset drives it from outside the composable.
    val scroll = rememberLazyGridState()
    val touches = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST) }
    LaunchedEffect(Unit) {
        resetAfterIdle(touches, IDLE_RESET) { scope.launch { scroll.scrollToItem(0) } }
    }

    // The launcher tiles hold no vendor state, so there is nothing here to poll — but whether an
    // app is installed *can* change under the panel, and a tablet that has to be restarted before
    // a freshly installed Mi Home stops reading "not installed" is a wall that lies. Keyed on
    // `now`, so the check is redone on the same tick that ages every other tile: two
    // `getLaunchIntentForPackage` calls a quarter-minute, against nobody's allowance.
    val context = LocalContext.current
    val launchers =
        remember(now) {
            launcherTiles { packageName -> context.packageManager.getLaunchIntentForPackage(packageName) != null }
        }

    // One `/v1.0/user/info` call per interval feeds every group; each still holds its own
    // tiles, its own ages and its own error.
    LaunchedEffect(poll) {
        pollPausingForCalls(domonapCalls.state, POLL_INTERVAL, poll::refresh)
    }

    // Its own loop, at its own interval: Tuya is metered and Yandex is not, and one timer for
    // both would mean either paying for Yandex's cadence or waiting for Tuya's.
    LaunchedEffect(tuyaPoll) {
        pollPausingForCalls(domonapCalls.state, TUYA_POLL_INTERVAL, tuyaPoll::refresh)
    }

    // The age on every tile has to keep climbing between polls, not freeze at the value the last
    // read produced — and it has to keep climbing through an intercom call too, when polling is
    // paused. A tile coming back from a call saying "just now" would be a lie.
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(POLL_INTERVAL)
        }
    }

    PanelRooms(
        acs = acState,
        curtains = curtainState,
        strips = stripState,
        recuperators = recuperatorState,
        bulbs = state,
        launchers = launchers,
        now = now,
        // Both intervals, because staleness is judged against the poll that produced the reading:
        // 15 s for the Yandex groups and 6 minutes for the recuperators. One number for both would
        // either call every recuperator stale or never call a bulb stale.
        yandexInterval = POLL_INTERVAL,
        tuyaInterval = TUYA_POLL_INTERVAL,
        // Every touch on the panel, seen on the way down and not consumed: this watches the
        // gestures, it does not take them. A tap that reached a switch must still flip it. Over the
        // whole screen rather than the tiles, so a touch on the empty half of a short room still
        // says somebody is standing there.
        modifier =
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touches.tryEmit(Unit)
                }
            }
        },
        scroll = scroll,
        onToggleAc = { id -> scope.launch { acs.toggle(id) } },
        onSetTemperature = { id, celsius -> scope.launch { acs.setTemperature(id, celsius) } },
        onSetOpen = { id, percent -> scope.launch { curtains.setOpen(id, percent) } },
        onToggleStrip = { id -> scope.launch { strips.toggle(id) } },
        onSetBrightness = { id, percent -> scope.launch { strips.setBrightness(id, percent) } },
        onToggleRecuperator = { id -> scope.launch { recuperators.toggle(id) } },
        onToggleBulb = { id -> scope.launch { tiles.toggle(id) } },
        onOpenApp = { packageName -> open(context, packageName) },
    )
}

/**
 * Opens another app's launcher activity. The intent is resolved again at the tap rather than kept
 * from the tile: the app can be uninstalled between the two, and a stale intent would throw.
 *
 * Nothing here can take the panel down. An app that disappeared resolves to null and the tap does
 * nothing — the tile will say "not installed" on the next tick — and anything the framework throws
 * on the way out is logged rather than raised: a wall panel that dies because somebody tapped a
 * shortcut is worse than one that fails to open it.
 */
private fun open(
    context: Context,
    packageName: String,
) {
    // getLaunchIntentForPackage already sets FLAG_ACTIVITY_NEW_TASK, so the app opens as its own
    // task and the panel is what the back gesture returns to.
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(intent) }
        .onFailure { failure -> Log.w("DomovoyHub", "could not open $packageName", failure) }
}
