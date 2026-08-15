package ru.domovoy

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.domovoy.core.TokenStore
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.tuya.TuyaClient
import ru.domovoy.integrations.tuya.TuyaCredentials
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.panel.PanelRooms
import ru.domovoy.panel.TuyaPoll
import ru.domovoy.panel.YandexPoll
import ru.domovoy.panel.pollPausingForCalls
import ru.domovoy.panel.recuperatorRooms
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

/** One store for the panel, not one per vendor: every vendor's credentials land in this file. */
private const val SECRETS_FILE = "domovoy-secrets"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val secrets = secrets(applicationContext)
        setContent {
            MaterialTheme {
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
    val store =
        runCatching { TokenStore(encryptedPrefs(context)) }.getOrElse { failure ->
            // A keystore the tablet lost — a restored backup, a wiped key — must not take the
            // panel down on a wall no-one is watching. Every tile group says this instead.
            val reason = { error("secure storage unavailable: ${failure.message}") }
            return PanelSecrets(yandexToken = reason, tuya = reason)
        }
    store.seedYandexToken(BuildConfig.YANDEX_OAUTH_TOKEN)
    store.seedTuyaCredentials(
        clientId = BuildConfig.TUYA_CLIENT_ID,
        clientSecret = BuildConfig.TUYA_CLIENT_SECRET,
        uid = BuildConfig.TUYA_UID,
    )
    return PanelSecrets(
        yandexToken = store::yandexToken,
        tuya = { TuyaCredentials(store.tuyaClientId(), store.tuyaClientSecret(), store.tuyaUid()) },
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
        now = now,
        onToggleAc = { id -> scope.launch { acs.toggle(id) } },
        onSetTemperature = { id, celsius -> scope.launch { acs.setTemperature(id, celsius) } },
        onSetOpen = { id, percent -> scope.launch { curtains.setOpen(id, percent) } },
        onToggleStrip = { id -> scope.launch { strips.toggle(id) } },
        onSetBrightness = { id, percent -> scope.launch { strips.setBrightness(id, percent) } },
        onToggleRecuperator = { id -> scope.launch { recuperators.toggle(id) } },
        onToggleBulb = { id -> scope.launch { tiles.toggle(id) } },
    )
}
