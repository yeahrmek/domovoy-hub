# Domovoy Hub

Android wall-panel: one always-on screen for the flat, on a wall-mounted, always-powered hallway tablet, on the same Wi-Fi, touched a few times a day. Assume Wi-Fi drops, expired tokens and unattended reboots.

| Vendor | Integration |
| --- | --- |
| Yandex | Air conditioner, curtains, bulbs; full integration, the reference one. |
| Aqara | Door lock; full **read-only** integration: state, battery, events. |
| Tuya | Recuperators; native LAN 3.4 over Wi-Fi, encrypted local keys; no cloud subscription. |
| Xiaomi | Vacuum, humidifier; no credentials available, host Xiaomi's own widget. |
| Domonap | Intercom; launcher tile, incoming call detected from its notification. |

Not every vendor is an HTTP integration; this is deliberate. Every tile is polled; nothing pushes state to the tablet. A hung Tuya call, expired Yandex token or unreachable Aqara hub degrades only its tile; the rest keeps working.

Domonap calls interrupt instantly, above whatever is on screen, showing caller, video, accept/decline, at maximum volume. Yield to Domonap's own screen and return afterwards; see `docs/domonap.md`. Never cover, delay or suppress it.

## Commands

No JDK, Android SDK or `adb` is on system PATH, deliberately. `/usr/bin/java` is the macOS stub ("Unable to locate a Java Runtime"); do not look for a system JDK. Use the gitignored repo-local `.toolchain/` through `scripts/env.sh`, sourced in the **same command** as Gradle because shells do not persist between calls.

If `.toolchain/` is missing, `scripts/toolchain.sh` downloads Temurin 21 and Android SDK into the repo, installing nothing system-wide. Gradle and `adb` bind local sockets and fail in the command sandbox: run them with the sandbox disabled.

```bash
source scripts/env.sh && ./gradlew test                  # unit tests: green after every change and before reporting done
source scripts/env.sh && ./gradlew ktlintCheck           # formatting
source scripts/env.sh && ./gradlew lint                  # Android lint
source scripts/env.sh && ./gradlew assembleDebug         # build APK
source scripts/env.sh && ./gradlew installDebug          # install on connected tablet (adb)
source scripts/env.sh && ./gradlew connectedAndroidTest  # instrumented tests; needs a device
```

Compilation alone is not completion: `./gradlew test` must pass.

## Stack and structure

<!-- TODO: confirm before the first real commit -->

- Kotlin 2.x, Jetpack Compose (Material 3), single `:app`; minSdk 26, targetSdk 36.
- Coroutines + Flow; Retrofit + OkHttp + kotlinx-serialization for vendor HTTP.
- JUnit5, kotlin.test, Turbine (flows), MockK; Robolectric only when a framework class is unavoidable.
- Gradle Kotlin DSL; versions in `gradle/libs.versions.toml`.

```
app/src/main/kotlin/ru/domovoy/
  integrations/   vendor packages: yandex/ aqara/ tuya/ domonap/
  panel/          Compose tiles, hosted widget, launcher tiles, yielding to calls
  core/           shared device model, storage, scheduling
app/src/test/     unit tests mirroring the main tree
app/src/test/resources/  recorded vendor JSON fixtures
docs/             one API-behaviour note per integration
```

## Testing and architecture

- TDD: failing test first, then passing code, in the same commit. Bug fixes start with a reproducer.
- Assert observable behaviour (returned values, emitted UI state), not collaborator calls; pure refactors must not break tests. `src/test/` never touches the network: use recorded JSON from `src/test/resources/`.
- Never delete, skip, `@Ignore` or weaken a test to make the build green; fix the code.
- Default Android repository + use case + mapper + interface-per-feature layering is unwanted. No single-implementation interface, base class, wrapper or factory; add abstraction when the second caller actually exists.
- Vendor clients in `integrations/` are independent: map to the shared device model, share nothing else, no common base class. Formatting and naming belong to ktlint.

```kotlin
// Good: observable outcome
@Test
fun `door release retries once when the first request times out`() = runTest {
    api.enqueue(Timeout, Success)
    assertEquals(DoorState.Opened, intercom.releaseDoor())
}
// Bad: implementation details, fragile under refactoring
@Test
fun testDoorRelease() = runTest {
    intercom.releaseDoor()
    verify(exactly = 2) { api.release(any()) }
}
// Good: call-to-HTTP path one file deep
class AqaraClient(private val api: AqaraApi, private val tokens: TokenStore) {
    suspend fun devices(): Result<List<Device>> = runCatching { api.devices().map(::toDevice) }
}
// Bad: four layers, one implementation each
interface DeviceProvider
abstract class BaseVendorProvider : DeviceProvider
class AqaraProviderFactory(private val cfg: ProviderConfig)
class AqaraProviderImpl : BaseVendorProvider()
```

## Workflow and boundaries

- Branches: `feat/domonap-fullscreen`, `fix/tuya-timeout`. Conventional commits: `feat(aqara): ...`, `fix(domonap): ...`, `test(yandex): ...`. One concern per commit, one vendor per PR; `test` and `ktlintCheck` green before push.
- Every network call needs an explicit timeout; every failure needs visible UI state, never an endless spinner. Show each tile's state age: inability to say when it was last read is a bug.
- Record actual vendor API responses in `docs/<vendor>.md`. Never code against unverified endpoints; state what is unknown.
- Ask first before adding dependencies, a DI framework or module; changing `AndroidManifest.xml`, permissions, foreground services, minSdk or signing config; introducing an architectural pattern.
- Never commit tokens, account logins, device IDs, local IPs or intercom/apartment identifiers. Use `local.properties` and `EncryptedSharedPreferences`; use fake fixture values.
- Never give the lock tile unlock, open or any write action: it only reports.
- Never reformat, rename or tidy files unrelated to the task.

## Known unknowns

Domonap has no public API. `docs/domonap.md` records unverified third-party suggestions about its backend; the panel calls none of them. The call path relies on a detectable Domonap notification: confirm it on the tablet before building on it. Do not invent endpoints.

Also tracked in `docs/<vendor>.md`: whether Aqara exposes anything beyond state for this lock model; Tuya state recovery after a physical restart; whether Domonap's landscape call screen can be letterboxed on a vertical tablet.

Not affiliated with Yandex, Aqara, Tuya, Xiaomi or Domonap.

## Glossary and maintenance

- **Panel**: always-on Compose UI on the wall tablet. **Tile**: a device, scene or sensor card.
- **Hosted widget**: another app's AppWidget in a tile; state can be shown but not read. **Launcher tile**: only opens the vendor app.
- **Intercom / домофон**: building door-entry system via Domonap. **Door release**: "open the door" during an active intercom call.
- Keep this file under ~150 lines. Add a rule after an agent makes the same mistake twice; delete rules that never fire. Omit vague advice agents would follow anyway; retain project-specific facts they cannot infer.
