# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

ControlDroid is a native Android (Kotlin + Jetpack Compose) peer-to-peer remote-control app. One device acts as **Controller**, another as **Target**, both on the same local Wi-Fi network — no root, no developer options required. The Controller scans the subnet, pairs with a Target over an HTTP handshake, and sends navigation commands / pulls screenshots from it. Package/namespace: `com.mories.control_droid`.

## Build & run

- Build debug APK: `./gradlew assembleDebug`
- Install on a connected device/emulator: `./gradlew installDebug`
- Run unit tests: `./gradlew test`
- Run a single unit test: `./gradlew test --tests "com.mories.control_droid.ExampleUnitTest"`
- Run instrumented tests (needs a connected device/emulator): `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

There is currently no dedicated CI config in the repo — these are the standard Gradle entry points.

## Architecture

### Two roles, one codebase

The app ships a single APK; on first launch the user picks a role via `RoleSelectionScreen`, persisted by `core/auth/RoleManager` (SharedPreferences `device_role`). `MainActivity` reads this role at startup to pick the Compose Navigation start destination:
- `CONTROLLER` → `Home` (list of paired devices from `PairedDeviceStore`)
- `TARGET` → `Preview/local` → falls back to `TargetWaitingScreen` (no paired device stored locally)

All navigation is a single `NavHost` in `MainActivity.kt` using routes defined in `ui/NavigationTarget.kt` (an enum whose `route`/`withParam`/`withArg` build path-parameterized routes like `control/{id}`).

### Target side (the controlled device)

- `TargetWaitingScreen` boots `core/server/TargetHttpServer` (a singleton `NanoHTTPD` on port `8080`, see `core/ConstantValue.PORT_VALUE`) and, if screen-capture permission was already granted, starts auto-capture.
- `TargetHttpServer` exposes:
  - `GET /ping` → identifies itself as `"ControlDroid"` (used by the Controller's scanner)
  - `POST /action` → body is a raw `DeviceAction.command` string; dispatched either to `core/control/AccessibilityController` (Back/Home/Recent) or `core/control/ScreenCaptureManager` (screenshot capture)
  - `GET /screenshot` → streams the last saved `cacheDir/screenshot.png`
- `core/control/AccessibilityController` is an `AccessibilityService` (declared in `AndroidManifest.xml`, config in `res/xml/accessibility_config.xml`) that performs global actions (`GLOBAL_ACTION_BACK/HOME/RECENTS`, and `GLOBAL_ACTION_TAKE_SCREENSHOT` on API 30+). It keeps a static `instance` set in `onServiceConnected`/cleared in `onDestroy` so the HTTP server (a different component) can reach it.
- Screen capture has a single implementation: `core/control/ScreenCaptureManager` (on-demand + polling `startAutoCapture`, 2s interval, using `MediaProjection` initialized via `setProjection(...)`, writes `cacheDir/screenshot.png`). Both `TargetWaitingScreen`'s auto-capture and `TargetHttpServer`'s `CAPTURE_SCREEN` action go through it.
- `features/target/ScreenPermissionActivity` is the transient activity that requests the `MediaProjection` capture intent from the user and, on success, calls `ScreenCaptureManager.setProjection(...)` directly (no foreground service involved) before finishing.

### Controller side

- `features/controller/AddDeviceScreen` triggers `core/control/DeviceScanner.scanLocalDevices()`, which derives the local `/24` subnet from the device's own IP and fans out concurrent `GET /ping` requests (300ms timeout each) across all 254 hosts to find devices responding `"ControlDroid"`. When the user taps a found device, a PIN-entry dialog collects the PIN before the device is persisted (see PIN model below).
- Paired devices are persisted locally via `core/storage/PairedDeviceStore` (Gson-serialized `List<PairedDevice>` in SharedPreferences `paired_devices`) — pairing state is per-Controller-device, not synced. `MainActivity`'s Home route re-reads the store on every entry (not just once), so newly paired devices show up immediately after returning from pairing.
- `features/controller/DeviceControlScreen` sends `DeviceAction`s to a paired Target using `core/networking/DeviceHttpClient` — a plain HTTP client (`ping()` for reachability, `sendAction()` for `POST /action`, both against `TargetHttpServer`). There is no WebSocket path in this app; `TargetHttpServer` never implements `/ws`, so the client doesn't attempt one.
- `features/controller/RemotePreviewScreen` (backed by `features/viewmodel/RemotePreviewViewModel`, `RemotePreviewUiState`, `RemotePreviewEvent`) polls `GET /screenshot` from the Target (with the paired PIN attached) to render a live-ish preview.

### PIN / auth model

PIN enforcement is wired end-to-end:
- **Target**: `TargetWaitingScreen` lets the device owner set/change a PIN via `core/auth/PinVerifier` (`setPin`/`isPinSet`, backed by SharedPreferences `security_prefs`). `TargetHttpServer` requires a matching PIN (header `X-Control-Pin`, checked case-insensitively) on `POST /action` and `GET /screenshot`, returning `401 Unauthorized` if the Target has no PIN set or the provided one doesn't match. `GET /ping` stays open (needed for discovery, no sensitive data).
- **Controller**: `AddDeviceScreen` prompts for the PIN when pairing a newly-scanned device and stores it on the `PairedDevice`. `DeviceHttpClient.sendAction()` and `RemotePreviewViewModel`'s screenshot polling both attach it as the `X-Control-Pin` header.
- Caveat: this PIN header flow is verified by code review + `assembleDebug`/`test`/`lint` only — it has not been exercised over a real two-device network yet.

### Networking constants

`core/ConstantValue.PORT_VALUE` (8080) is the single source of truth for the HTTP port, used by both `TargetHttpServer` (Target) and `DeviceScanner`/`DeviceHttpClient`/`RemotePreviewViewModel` (Controller). `res/xml/network_security_config.xml` governs cleartext traffic policy for local HTTP.

## Git workflow rules

- Never run `git commit` or create a new branch on your own initiative. Always ask the user for explicit confirmation first and wait for a clear "yes" before committing or branching.
- This applies even if the requested task implies committing (e.g. "fix this bug and commit it") — implement the change, then stop and confirm before running the commit/branch command.

## Dependencies of note

- `org.nanohttpd:nanohttpd-websocket` — embedded HTTP server on the Target (`NanoWSD`'s WebSocket features are not used; only the plain `NanoHTTPD` request handling in `TargetHttpServer`).
- OkHttp — HTTP client on the Controller (`DeviceHttpClient`, `RemotePreviewViewModel`).
- Gson — used for `PairedDevice` (de)serialization in `PairedDeviceStore`.
- Jetpack Navigation Compose — single-`NavHost` app navigation.
- The `com.google.gms.google-services` plugin is applied and `app/google-services.json` is present, but no Firebase library is currently declared in `app/build.gradle.kts` — there's no active Firebase integration yet.
