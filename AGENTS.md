# AGENTS.md

**Domovoy Hub** — Android wall-panel for a hallway tablet, one always-on screen for the flat.
Not every vendor is an HTTP integration, and that is deliberate:

- **Yandex** — air conditioner, curtains, bulbs. Full integration; the reference one.
- **Aqara** — door lock. Full integration, **read-only**: state, battery, events.
- **Tuya** — recuperators. Full integration on a metered monthly allowance; poll slowly.
- **Xiaomi** — vacuum, humidifier. No credentials to be had; the panel hosts Xiaomi's own widget.
- **Domonap** — intercom. Launcher tile; the incoming call is detected from its notification.

Nothing pushes state to the tablet: every tile is polled. One vendor failing — hung Tuya call,
expired Yandex token, unreachable Aqara hub — degrades that tile only. The rest keeps working.

Domonap's call is the one thing that interrupts: it goes on top of whatever is on screen — who is
calling, video, accept and decline — at maximum volume. That screen is Domonap's own; our job is
to yield to it instantly and come back afterwards. See `docs/domonap.md`.

Target device: wall-mounted tablet, always powered, same Wi-Fi, touched a few times
a day. Assume Wi-Fi drops, tokens expire and the tablet reboots unattended.

## Commands

```bash
./gradlew test                  # unit tests — run after every change, must be green
./gradlew ktlintCheck           # formatting
./gradlew lint                  # Android lint
./gradlew assembleDebug         # build APK
./gradlew installDebug          # install on the connected tablet (adb)
./gradlew connectedAndroidTest  # instrumented tests, needs a device
```

Never report a task as done on the strength of "it compiles". `./gradlew test` must pass.

## Stack

<!-- TODO: confirm before the first real commit -->

- Kotlin 2.x, Jetpack Compose (Material 3), single `:app` module
- minSdk 26, targetSdk 36
- Coroutines + Flow; Retrofit + OkHttp + kotlinx-serialization for vendor HTTP
- Tests: JUnit5, kotlin.test, Turbine (flows), MockK; Robolectric only when a framework class is unavoidable
- Gradle Kotlin DSL, versions in `gradle/libs.versions.toml`

## Structure

```
app/src/main/kotlin/ru/domovoy/
  integrations/   one package per vendor: yandex/ aqara/ tuya/ domonap/
  panel/          Compose UI: tiles, hosted widget, launcher tiles, yielding to the call
  core/           shared device model, storage, scheduling
app/src/test/     unit tests, mirroring the main tree
app/src/test/resources/  recorded vendor JSON used as fixtures
docs/             one note per integration; what the API actually does
```

## Testing — TDD

- Write the failing test first, then the code that makes it pass. Same commit.
- A bug fix starts with a test that reproduces the bug.
- Assert on observable behaviour — returned values, emitted UI state — not on which
  collaborator methods were called. A pure refactor must not break a test.
- Nothing in `src/test/` touches the network. Vendor responses come from recorded
  JSON in `src/test/resources/`.

```kotlin
// ✅ asserts what the panel ends up doing
@Test
fun `door release retries once when the first request times out`() = runTest {
    api.enqueue(Timeout, Success)
    assertEquals(DoorState.Opened, intercom.releaseDoor())
}

// ❌ asserts how it was implemented — breaks on any refactor
@Test
fun testDoorRelease() = runTest {
    intercom.releaseDoor()
    verify(exactly = 2) { api.release(any()) }
}
```

## Architecture rules

The default Android layering — repository + use case + mapper + an interface per
feature — is explicitly not wanted here. Instead:

- No interface, base class, wrapper or factory with a single implementation.
  Add the abstraction when the second caller actually exists.
- Vendor clients under `integrations/` stay independent of each other: they map into
  the shared device model and share nothing else. No common base class.

Formatting and naming are ktlint's job and are not documented here.

```kotlin
// ✅ Good — the path from call to HTTP is one file deep
class AqaraClient(private val api: AqaraApi, private val tokens: TokenStore) {
    suspend fun devices(): Result<List<Device>> = runCatching { api.devices().map(::toDevice) }
}

// ❌ Bad — four layers, one implementation each
interface DeviceProvider
abstract class BaseVendorProvider : DeviceProvider
class AqaraProviderFactory(private val cfg: ProviderConfig)
class AqaraProviderImpl : BaseVendorProvider()
```

## Git workflow

- Branches: `feat/domonap-fullscreen`, `fix/tuya-timeout`
- Conventional commits: `feat(aqara): ...`, `fix(domonap): ...`, `test(yandex): ...`
- One concern per commit, one vendor per PR. `test` and `ktlintCheck` green before push.

## Boundaries

✅ **Always**
- run `./gradlew test` before reporting a task finished
- give every network call an explicit timeout and every failure a visible state in the
  UI — no spinner that can spin forever
- record what a vendor API actually returned in `docs/<vendor>.md`
- show how old a tile's state is; a tile that cannot say when it was last read is a bug
- keep the incoming-call takeover intact — on top, video, accept/decline, maximum volume

⚠️ **Ask first**
- adding a dependency, a DI framework, or a new module
- changing `AndroidManifest.xml`, permissions, foreground services, minSdk or signing config
- introducing a new architectural pattern

🚫 **Never**
- commit tokens, account logins, device IDs, local IPs or intercom/apartment identifiers —
  use `local.properties` and `EncryptedSharedPreferences`; fixtures use fake values
- give the lock tile an unlock, open or any other write action — it reports, it does not act
- cover, delay or suppress the Domonap call screen
- delete, skip, `@Ignore` or weaken a test to make the build green — fix the code instead
- write code against a vendor endpoint nobody has verified — say what is unknown instead
- reformat, rename or "tidy" files unrelated to the current task

## Known unknowns

Domonap has no public API. `docs/domonap.md` records what a third-party integration suggests
its backend does — none of it verified, and nothing in the panel calls it. The call path rests
on Domonap posting a notification we can see; confirm that on the tablet before building on it.
Do not invent endpoints.

Also open, each tracked in its `docs/<vendor>.md`: whether Aqara's API exposes anything beyond
state for this lock model, what one panel refresh costs against Tuya's monthly allowance, and
whether Domonap's landscape call screen can be letterboxed on a vertically mounted tablet.

Not affiliated with Yandex, Aqara, Tuya, Xiaomi or Domonap.

## Glossary

- **Panel** — the always-on Compose UI on the wall tablet
- **Tile** — one card on the panel: a device, scene or sensor
- **Hosted widget** — another app's AppWidget embedded in a tile; state we can show but not read
- **Launcher tile** — a tile that only opens the vendor app
- **Intercom / домофон** — the building door-entry system, reached via Domonap
- **Door release** — the "open the door" action during an active intercom call

## Maintaining this file

Keep it under ~150 lines. Add a rule when an agent actually makes a mistake twice;
delete rules that never fire. Vague advice the agent would follow anyway is noise
that crowds out the project-specific facts it cannot infer.