package ru.domovoy

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.domovoy.core.TokenStore
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.panel.AcTileList
import ru.domovoy.panel.BulbTileList
import ru.domovoy.panel.CurtainTileList
import ru.domovoy.panel.YandexPoll
import ru.domovoy.panel.pollPausingForCalls
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Yandex publishes no push mechanism, so the panel polls. One `/v1.0/user/info` call is the whole
 * house; 15 s is inside the rate limit Yandex's other APIs allow, though nothing is published for
 * this one — see docs/yandex.md.
 */
private val POLL_INTERVAL = 15.seconds

/** One store for the panel, not one per vendor: every vendor's credentials land in this file. */
private const val SECRETS_FILE = "domovoy-secrets"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val yandexToken = yandexToken(applicationContext)
        setContent {
            MaterialTheme {
                Surface {
                    Panel(yandexToken)
                }
            }
        }
    }
}

/**
 * Opens the encrypted store and answers with the function the client reads the token through.
 *
 * On a fresh install the store is empty, so the value that came from `local.properties` through
 * `BuildConfig` seeds it once — that is the only way a token gets onto the tablet today. From then
 * on the store is the source of truth and the build-time value is ignored, so a token written
 * there later is not undone by the stale one in the APK.
 */
private fun yandexToken(context: Context): () -> String {
    val store =
        runCatching { TokenStore(encryptedPrefs(context)) }.getOrElse { failure ->
            // A keystore the tablet lost — a restored backup, a wiped key — must not take the
            // panel down on a wall no-one is watching. The bulb tiles say this instead.
            return { error("secure storage unavailable: ${failure.message}") }
        }
    store.seedYandexToken(BuildConfig.YANDEX_OAUTH_TOKEN)
    return store::yandexToken
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
private fun Panel(yandexToken: () -> String) {
    val client =
        remember {
            YandexClient(
                http = OkHttpClient(),
                token = yandexToken,
                householdId = BuildConfig.YANDEX_HOUSEHOLD_ID,
            )
        }
    val poll = remember(client) { YandexPoll(client) }
    val tiles = poll.bulbs
    val curtains = poll.curtains
    val acs = poll.acs
    val state by tiles.state.collectAsState()
    val curtainState by curtains.state.collectAsState()
    val acState by acs.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    // One `/v1.0/user/info` call per interval feeds all three groups; each still holds its own
    // tiles, its own ages and its own error.
    LaunchedEffect(poll) {
        pollPausingForCalls(domonapCalls.state, POLL_INTERVAL, poll::refresh)
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

    Column {
        AcTileList(
            state = acState,
            now = now,
            onToggle = { id -> scope.launch { acs.toggle(id) } },
            onSetTemperature = { id, celsius -> scope.launch { acs.setTemperature(id, celsius) } },
        )
        CurtainTileList(
            state = curtainState,
            now = now,
            onSetOpen = { id, percent -> scope.launch { curtains.setOpen(id, percent) } },
        )
        BulbTileList(
            state = state,
            now = now,
            modifier = Modifier.weight(1f),
            onToggle = { id -> scope.launch { tiles.toggle(id) } },
        )
    }
}
