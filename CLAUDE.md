# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

ControlDroid is a native Android (Kotlin + Jetpack Compose) peer-to-peer remote-control app. One device acts as **Controller**, another as **Target**, both on the same local Wi-Fi network — no root, no developer options required. The Controller can pair with a Target through a QR access token or the legacy subnet/PIN flow, send navigation and gesture commands, exchange clipboard text, run macros, and pull screenshots. Package/namespace: `com.mories.control_droid`.

## Build & run

- Build debug APK: `./gradlew assembleDebug`
- Install on a connected device/emulator: `./gradlew installDebug`
- Run unit tests: `./gradlew test`
- Run a single unit test: `./gradlew test --tests "com.mories.control_droid.ExampleUnitTest"`
- Run instrumented tests (needs a connected device/emulator): `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

There is currently no dedicated CI config in the repo — these are the standard Gradle entry points.

### Gradle modules

The repository is split into three modules:

- `:core` — Android library without Compose. Owns models, auth, storage, HTTP networking/server, accessibility control, screen capture manager, QR generation, and macro execution.
- `:ui-components` — Compose library with no dependency on `:app` or `:core`. Owns theme and stateless reusable UI components.
- `:app` — the installable application. Owns `MainActivity`, navigation, feature screens/ViewModels, manifest/resources, and Android entrypoint services such as `ScreenCaptureService`.

Dependency direction is `:app` → `:core` and `:app` → `:ui-components`; there must be no reverse or cyclic dependency. Package declarations remain under `com.mories.control_droid.*` for compatibility.

## Architecture

### Two roles, one codebase

The app ships a single APK; on first launch the user picks a role via `RoleSelectionScreen`, persisted by `core/auth/RoleManager` (SharedPreferences `device_role`). `MainActivity` reads this role at startup to pick the Compose Navigation start destination:
- `CONTROLLER` → `Home` (list of paired devices from `PairedDeviceStore`)
- `TARGET` → `Preview/local` → falls back to `TargetWaitingScreen` (no paired device stored locally)

All navigation is a single `NavHost` in `MainActivity.kt` using routes defined in `ui/NavigationTarget.kt` (an enum whose `route`/`withParam`/`withArg` build path-parameterized routes like `control/{id}`).

### Target side (the controlled device)

- `TargetWaitingScreen` boots `core/server/TargetHttpServer` (a singleton `NanoHTTPD` on port `8080`, see `core/ConstantValue.PORT_VALUE`) and shows a QR pairing code. Screen capture is started by `features/target/ScreenCaptureService` after user consent.
- `TargetHttpServer` exposes:
  - `GET /ping` → identifies itself as `"ControlDroid"` (used by the Controller's scanner)
  - `GET /pair` → validates the QR access token
  - `POST /action` → body is a raw `DeviceAction.command` string; dispatched either to `core/control/AccessibilityController` (Back/Home/Recent) or `core/control/ScreenCaptureManager` (screenshot capture)
  - `POST /gesture` → receives normalized tap/swipe coordinates and dispatches them through `AccessibilityController`
  - `POST /clipboard` → updates the Target clipboard and optionally requests an accessibility paste
  - `GET /screenshot` → streams the last saved `cacheDir/screenshot.png`
- `core/control/AccessibilityController` is an `AccessibilityService` (declared in `AndroidManifest.xml`, config in `res/xml/accessibility_config.xml`) that performs global actions, normalized tap/swipe gestures, and paste operations. It keeps a static `instance` set in `onServiceConnected`/cleared in `onDestroy` so the HTTP server (a different component) can reach it.
- Screen capture has a single implementation: `core/control/ScreenCaptureManager` (on-demand + polling `startAutoCapture`, 2s interval, using `MediaProjection` initialized via `setProjection(...)`, writes `cacheDir/screenshot.png`). `features/target/ScreenCaptureService` owns the foreground MediaProjection session and starts the capture loop; `TargetHttpServer`'s `CAPTURE_SCREEN` action also goes through the same manager.
- `features/target/ScreenPermissionActivity` is the transient activity that requests the `MediaProjection` capture intent from the user and, on success, starts `ScreenCaptureService`, which promotes itself to a `mediaProjection` foreground service before initializing `ScreenCaptureManager`.

### Controller side

- `features/controller/AddDeviceScreen` supports QR pairing with a Target access token and retains the subnet scanner as a fallback. The scanner derives the local `/24` subnet from the device's own IP and fans out concurrent `GET /ping` requests (300ms timeout each) across all 254 hosts to find devices responding `"ControlDroid"`.
- Paired devices are persisted locally via `core/storage/PairedDeviceStore` (Gson-serialized `List<PairedDevice>` in SharedPreferences `paired_devices`) — pairing state is per-Controller-device, not synced. `MainActivity`'s Home route re-reads the store on every entry (not just once), so newly paired devices show up immediately after returning from pairing.
- `features/controller/DeviceControlScreen` sends `DeviceAction`s, clipboard requests, and starts remote preview through `core/networking/DeviceHttpClient`. `RemotePreviewScreen` polls `GET /screenshot` and also sends tap/swipe gestures. There is no WebSocket path in this app; `TargetHttpServer` never implements `/ws`, so the client doesn't attempt one.
- `features/controller/RemotePreviewScreen` (backed by `features/viewmodel/RemotePreviewViewModel`, `RemotePreviewUiState`, `RemotePreviewEvent`) polls `GET /screenshot` from the Target with the paired token and/or PIN, and sends normalized tap/swipe gestures over the same local HTTP connection.
- `features/controller/MacroScreen` and `core/storage/MacroStore` manage navigation-only macros. `core/control/MacroRunner` executes a macro sequentially across all paired Target devices; Home also exposes a direct broadcast Home action.

### PIN / auth model

PIN/token enforcement is wired end-to-end:
- **Target**: `TargetWaitingScreen` lets the device owner set/change a PIN via `core/auth/PinVerifier` (`setPin`/`isPinSet`, backed by SharedPreferences `security_prefs`) and displays a QR containing the token from `core/auth/PairingTokenStore`. `TargetHttpServer` requires `X-Control-Token` on `/pair` and accepts either a matching `X-Control-Token` or legacy `X-Control-Pin` on `/action`, `/gesture`, `/clipboard`, and `/screenshot`; unauthorized requests return `401 Unauthorized`. `GET /ping` stays open (needed for discovery, no sensitive data).
- **Controller**: `AddDeviceScreen` can scan a Target QR code, validates it through `GET /pair`, and stores its access token on `PairedDevice`; the legacy scanner still prompts for a PIN. `DeviceHttpClient` and `RemotePreviewViewModel` attach `X-Control-Token` and/or the legacy `X-Control-Pin` header as available.
- Caveat: the token/PIN flow is verified by code review + `assembleDebug`/`test`/`lint` only — it has not been exercised over a real two-device network yet.

### Networking constants

`core/ConstantValue.PORT_VALUE` (8080) is the single source of truth for the HTTP port, used by both `TargetHttpServer` (Target) and `DeviceScanner`/`DeviceHttpClient`/`RemotePreviewViewModel` (Controller). `res/xml/network_security_config.xml` governs cleartext traffic policy for local HTTP.

### Feature limitations

- QR pairing contains the Target's current local IP, so it must be repeated or the stored device updated after an IP change.
- Remote preview remains PNG polling at roughly two-second intervals; there is no MJPEG/WebSocket video stream.
- Clipboard paste depends on a focused editable field and an active AccessibilityService.
- Macros currently contain navigation actions only; gesture and clipboard steps are not yet macro steps.
- The token/PIN protocol has build and unit-test coverage but has not been exercised over a real two-device network.

## Git workflow rules

- Never run `git commit` or create a new branch on your own initiative. Always ask the user for explicit confirmation first and wait for a clear "yes" before committing or branching.
- This applies even if the requested task implies committing (e.g. "fix this bug and commit it") — implement the change, then stop and confirm before running the commit/branch command.

## Dependencies of note

- `org.nanohttpd:nanohttpd-websocket` — embedded HTTP server on the Target (`NanoWSD`'s WebSocket features are not used; only the plain `NanoHTTPD` request handling in `TargetHttpServer`).
- ZXing Android Embedded — QR pairing scan and QR generation support.
- OkHttp — HTTP client on the Controller (`DeviceHttpClient`, `RemotePreviewViewModel`).
- Gson — used for `PairedDevice` (de)serialization in `PairedDeviceStore`.
- Jetpack Navigation Compose — single-`NavHost` app navigation.
- The `com.google.gms.google-services` plugin is applied and `app/google-services.json` is present, but no Firebase library is currently declared in `app/build.gradle.kts` — there's no active Firebase integration yet.

## Documentation source of truth

Before changing behavior, consult the relevant document under `docs/`:

- `docs/architecture.md`: module boundaries, lifecycle, navigation, persistence, and data flow.
- `docs/http-api.md`: local HTTP contract, credentials, payloads, and status codes.
- `docs/operations.md`: setup, two-device flow, acceptance checklist, and release checklist.
- `docs/testing.md`: test inventory, evidence levels, and untested runtime boundaries.
- `docs/troubleshooting.md`: diagnosis flow and log tags.
- `docs/ui-guidelines.md`: Material 3, reusable components, previews, inset, state, and accessibility rules.
- `docs/2026-09-19-remote-control-features.md`: chronological delivery notes for the current workday.

When code changes affect behavior, update the relevant documentation in the same workday. Keep protocol documentation and tests synchronized; an endpoint change is incomplete until its request/response documentation and contract test are updated.

## Development workflow

1. Confirm the exact module and ownership before editing.
2. Preserve existing package names, SharedPreferences keys, route names, and HTTP commands unless the task explicitly changes a contract.
3. Keep business logic in `:core`, screen orchestration in `:app`, and reusable stateless Compose UI in `:ui-components`.
4. For toolbar screens, use `AppToolbar` and the established zero-inset Scaffold pattern; do not add duplicate status-bar padding.
5. For each reusable public composable, add a deterministic `@Preview` using `ControldroidTheme(dynamicColor = false)`.
6. Add or update the smallest relevant unit/contract/UI smoke test.
7. Run `./gradlew test lint :app:assembleDebug` and `git diff --check`.
8. Separate build evidence from device-runtime evidence in the handoff.

## Verification boundaries

Do not claim complete runtime support based only on `test`, `lint`, or `assembleDebug`. The following require an emulator/device, and pairing/control requires two devices on the same local network:

- Camera QR scanning.
- `/ping`, `/pair`, and authenticated endpoint communication over Wi-Fi.
- Accessibility global actions, gestures, and paste.
- MediaProjection consent, foreground service, and screenshot file generation.
- Screenshot polling and coordinate accuracy.
- Edge-to-edge visual layout across screen sizes and font scales.

The current automated suite covers critical core state/network contracts and selected navigation/UI smoke behavior, not every method or every screen.
