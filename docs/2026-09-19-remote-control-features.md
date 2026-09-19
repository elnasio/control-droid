# Remote control feature delivery

## Implemented MVP

- Target pairing QR uses a per-installation access token and keeps PIN authentication for backward compatibility.
- Controller can send normalized tap and swipe gestures through the existing local HTTP architecture.
- MediaProjection capture runs from a foreground service and keeps the existing screenshot endpoint for compatibility.
- Controller can send clipboard text to the Target and request an accessibility paste operation.
- Controllers can save navigation macros and run them across all paired Target devices; Home broadcast is also available.

## Scope and limitations

- QR discovery still requires both devices to be reachable on the same local network; the QR carries the Target IP and port.
- Live preview remains PNG polling. The foreground service improves capture lifecycle reliability but does not yet provide a video stream.
- Clipboard paste depends on the Target accessibility service having a focused editable field.
- Macros currently contain navigation actions only. Gesture and clipboard steps can be added after the protocol is stable.
- Existing PIN pairing remains available for compatibility with previously stored devices.

## Modularization

The project is organized into three Gradle modules:

- `:core`: Android infrastructure and domain-facing APIs without Compose.
- `:ui-components`: reusable Compose theme and stateless UI components without core/app dependencies.
- `:app`: installable APK, navigation, feature screens, ViewModels, manifest, and service entrypoints.

The dependency direction is `:app` → `:core` and `:app` → `:ui-components`. Existing package names, application ID, persistence keys, routes, and HTTP contracts remain unchanged.

## Test coverage

- `:core` now covers pairing state, PIN and role persistence, paired-device and macro storage, QR generation, and HTTP request contracts using Robolectric and MockWebServer.
- `:app` covers navigation route construction and route parsing.
- `:ui-components` includes Compose instrumentation smoke tests for reusable action and device-card components.
- `:ui-components` now also exposes `AppToolbar`, a reusable Material 3 app bar with title, automatic arrow-back support through `onBackClick`, custom navigation slot override, and action slot.
- Every public Compose component in `:ui-components` now has a local `@Preview` sample, including the theme, dialog, toolbar, cards, buttons, and remote-preview surface.
- `AddDeviceScreen` now uses the reusable `AppToolbar` with the standard arrow-back navigation.
- `MacroScreen` now uses the same reusable `AppToolbar` and root-inset handling.
- Shared UI now includes clearer device cards, filled-tonal control buttons, and a reusable connection `StatusBadge` for the screen redesign.
- Added a reusable `ControlPad` with a Controller-side toggle; directional buttons send normalized swipe gestures through the existing `/gesture` contract.
- Toolbar screens disable duplicate system-bar insets because `MainActivity` already applies the root window inset; this removes the extra top gap above the toolbar.
- Device hardware behavior, MediaProjection capture, and real cross-device networking still require instrumentation or manual device validation.

## Release-build crash fix and on-device diagnostics

- Fixed a release-only (R8/ProGuard) crash: `PairedDeviceStore`/`MacroStore` build `object : TypeToken<List<X>>() {}` at runtime for Gson, and R8 could strip/merge that anonymous subclass even with `Signature` kept, throwing `IllegalStateException: TypeToken must be created with a type argument` the instant any screen called `getAll()`. This reproduced as an immediate force-close after choosing the Target role (which navigates through `getDeviceById`), and was invisible to `./gradlew test`/`assembleDebug` since neither runs R8. Fixed with explicit `-keep` rules for `TypeToken` and its subclasses in `app/proguard-rules.pro`; verified against a real `assembleRelease` APK installed on a physical device, not just debug builds.
- Added `CrashLogger` (`:core`), a `Thread.UncaughtExceptionHandler` installed in `MainActivity.onCreate` that saves any uncaught crash anywhere in the app (time, thread, device model/API, full stack trace) to a local file before delegating to the previous handler. `MainActivity` shows the last saved crash as a scrollable dialog on next launch. This made the release-only crash above diagnosable from a screenshot on a device with no adb/computer access.
- See `docs/troubleshooting.md` #11 and `docs/testing.md` §6 for the general lesson: `test`/`assembleDebug` passing does not prove a release build is safe; R8-specific bugs require testing `assembleRelease` directly.

## Documentation completion

Dokumentasi repo dilengkapi dengan:

- `docs/README.md` sebagai indeks dokumentasi.
- `docs/architecture.md` untuk module boundary, navigation, lifecycle, persistence, dan data flow.
- `docs/http-api.md` untuk endpoint, payload, authentication, dan status response.
- `docs/operations.md` untuk setup developer, setup dua device, penggunaan fitur, acceptance, dan release checklist.
- `docs/testing.md` untuk inventory test, level evidence, coverage gap, dan interpretasi hasil.
- `docs/troubleshooting.md` untuk diagnosis build, jaringan, pairing, Accessibility, MediaProjection, clipboard, preview, dan inset UI.
- `docs/ui-guidelines.md` untuk aturan Material 3, reusable component, preview, state, spacing, accessibility, dan responsive layout.
- `README.md` dan `CLAUDE.md` diperbarui agar menunjuk ke struktur modular dan dokumentasi baru.
