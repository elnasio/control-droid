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
- Toolbar screens disable duplicate system-bar insets because `MainActivity` already applies the root window inset; this removes the extra top gap above the toolbar.
- Device hardware behavior, MediaProjection capture, and real cross-device networking still require instrumentation or manual device validation.

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
