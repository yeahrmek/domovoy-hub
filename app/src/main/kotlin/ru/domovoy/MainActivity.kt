package ru.domovoy

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
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.domovoy.integrations.domonap.domonapCalls
import ru.domovoy.integrations.yandex.YandexClient
import ru.domovoy.panel.BulbTileList
import ru.domovoy.panel.BulbTiles
import ru.domovoy.panel.pollPausingForCalls
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Yandex publishes no push mechanism, so the panel polls. One `/v1.0/user/info` call is the whole
 * house; 15 s is inside the rate limit Yandex's other APIs allow, though nothing is published for
 * this one — see docs/yandex.md.
 */
private val POLL_INTERVAL = 15.seconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Panel()
                }
            }
        }
    }
}

@Composable
private fun Panel() {
    val tiles =
        remember {
            BulbTiles(
                YandexClient(
                    http = OkHttpClient(),
                    token = BuildConfig.YANDEX_OAUTH_TOKEN,
                    householdId = BuildConfig.YANDEX_HOUSEHOLD_ID,
                ),
            )
        }
    val state by tiles.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tiles) {
        pollPausingForCalls(domonapCalls.state, POLL_INTERVAL) { tiles.refresh() }
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

    BulbTileList(
        state = state,
        now = now,
        modifier = Modifier,
        onToggle = { id -> scope.launch { tiles.toggle(id) } },
    )
}
