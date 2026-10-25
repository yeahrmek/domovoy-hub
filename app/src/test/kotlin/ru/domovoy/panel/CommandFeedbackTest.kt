package ru.domovoy.panel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandFeedbackTest {
    @Test
    fun `each Yandex device stays pending through its confirming read and ignores another tap`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = yandexFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow()
            val polledAt = Instant.EPOCH
            var entered = CompletableDeferred<Unit>()
            var release = CompletableDeferred<Unit>()
            val reread: suspend () -> Unit = {
                entered.complete(Unit)
                release.await()
            }
            val acs = AcTiles(client, reread).also { it.show(devices, polledAt) }
            val bulbs = BulbTiles(client, reread).also { it.show(devices, polledAt) }
            val curtains = CurtainTiles(client, reread).also { it.show(devices, polledAt) }
            val strips = LightStripTiles(client, reread).also { it.show(devices, polledAt) }
            val cases = listOf(
                Case("ac-01", { acs.toggle("ac-01") }, { acs.state.value.pending }, { acs.show(devices, polledAt) }),
                Case("light-01", { bulbs.toggle("light-01") }, { bulbs.state.value.pending }, { bulbs.show(devices, polledAt) }),
                Case("curtain-01", { curtains.setOpen("curtain-01", 50.0) }, { curtains.state.value.pending }, { curtains.show(devices, polledAt) }),
                Case("light-strip-01", { strips.toggle("light-strip-01") }, { strips.state.value.pending }, { strips.show(devices, polledAt) }),
            )
            for (case in cases) {
                entered = CompletableDeferred()
                release = CompletableDeferred()
                server.enqueue(MockResponse(body = """{"status":"ok","request_id":"test"}"""))
                val job = launch(start = CoroutineStart.UNDISPATCHED) { case.command() }
                assertEquals(setOf(case.id), case.pending())
                case.command()
                entered.await()
                case.show()
                assertEquals(setOf(case.id), case.pending(), "a poll must not dismiss an outstanding command")
                release.complete(Unit)
                job.join()
                assertTrue(case.pending().isEmpty())
            }
            assertEquals(5, server.requestCount, "one initial read and one command per device")
        }
    }

    @Test
    fun `cancelling a command during confirmation clears pending without showing an error`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = yandexFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val bulbs = BulbTiles(client) {
                entered.complete(Unit)
                release.await()
            }.also { it.show(devices, Instant.EPOCH) }
            server.enqueue(MockResponse(body = """{"status":"ok","request_id":"test"}"""))
            val job = launch { bulbs.toggle("light-01") }
            entered.await()
            assertEquals(setOf("light-01"), bulbs.state.value.pending)

            job.cancelAndJoin()

            assertTrue(bulbs.state.value.pending.isEmpty())
            assertTrue(bulbs.state.value.commandErrors.isEmpty())
        }
    }

    @Test
    fun `a rejected command belongs to that device and remains visible across unrelated polling`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = yandexFixture()))
            val client = server.yandexClient()
            val devices = client.devices().getOrThrow()
            val bulbs = BulbTiles(client) {}.also { it.show(devices, Instant.EPOCH) }
            server.enqueue(MockResponse(code = 500, body = "failed"))

            bulbs.toggle("light-01")
            bulbs.show(devices, Instant.EPOCH.plusSeconds(60))

            assertNull(bulbs.state.value.error)
            assertEquals(setOf("light-01"), bulbs.state.value.commandErrors.keys)
            assertTrue(bulbs.state.value.pending.isEmpty())
            server.enqueue(MockResponse(body = """{"status":"ok","request_id":"retry"}"""))
            bulbs.toggle("light-01")
            assertTrue(bulbs.state.value.commandErrors.isEmpty())
        }
    }

    private data class Case(
        val id: String,
        val command: suspend () -> Unit,
        val pending: () -> Set<String>,
        val show: () -> Unit,
    )
}
