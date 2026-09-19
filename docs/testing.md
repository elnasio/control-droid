# Testing dan Verification

## 1. Prinsip verification

Build sukses hanya membuktikan source dapat dikompilasi. Unit test membuktikan rule yang diuji. Keduanya tidak membuktikan AccessibilityService, MediaProjection, kamera, atau jaringan dua device benar-benar berfungsi.

Gunakan tiga level bukti:

1. **Static/build**: compile, lint, manifest/resource processing, APK assembly.
2. **Automated test**: unit test JVM/Robolectric, MockWebServer contract test, dan Compose instrumentation test.
3. **Runtime**: emulator/device, permission, service lifecycle, jaringan lokal, dan acceptance flow dua device.

## 2. Test inventory saat ini

### `:core`

| Test | Coverage |
|---|---|
| `PairingQrPayloadTest` | Encode/decode payload dan invalid prefix. |
| `CoreStateTest` | PIN, pairing token, role, paired device store, macro store. |
| `PairingQrCodeGeneratorTest` | Ukuran bitmap dan adanya pola QR. |
| `DeviceHttpClientTest` | Command, credential headers, gesture JSON, clipboard JSON, screenshot response. |

Robolectric dipakai untuk Android `Context`, SharedPreferences, dan Bitmap. MockWebServer dipakai untuk request contract tanpa device target.

### `:app`

| Test | Coverage |
|---|---|
| `NavigationTargetTest` | Route base, parameter route, dan route parsing. |
| `ExampleUnitTest` | Template test lama. Tidak dianggap feature coverage. |

### `:ui-components`

| Test | Coverage |
|---|---|
| `ComponentsSmokeTest` | Render/click `ControlActionButton`, render `DeviceCard`, render/click `AppToolbar`. |

Test ini berada di `androidTest`, sehingga compile dapat dilakukan tanpa device tetapi eksekusi membutuhkan emulator/perangkat.

## 3. Command dan hasil yang diharapkan

```bash
./gradlew test
```

Menjalankan unit test debug/release module yang memiliki test. Expected: `BUILD SUCCESSFUL` dan tidak ada failure.

```bash
./gradlew lint
```

Memeriksa app, core, dan ui-components. Expected: lint report berhasil tanpa error blocking.

```bash
./gradlew :ui-components:compileDebugAndroidTestKotlin
```

Memastikan Compose instrumentation source bisa dikompilasi. Ini bukan eksekusi test.

```bash
./gradlew connectedAndroidTest
```

Menjalankan instrumentation test pada device/emulator yang terhubung. Perintah ini belum dapat dianggap berhasil jika tidak ada target runtime.

## 4. Coverage yang belum lengkap

Belum semua method dan screen memiliki unit test. Area yang masih memerlukan coverage:

- `TargetHttpServer.serve` secara langsung dengan fake `IHTTPSession` atau embedded server.
- `DeviceScanner` pada variasi IP/subnet/timeout.
- `AccessibilityController` pada AccessibilityService nyata.
- `ScreenCaptureManager` dan `ScreenCaptureService` pada MediaProjection nyata.
- `AddDeviceScreen` flow QR/camera dan callback pairing.
- `RemotePreviewViewModel` polling lifecycle, retry, HTTP error, dan decode error.
- `MacroRunner` concurrency, delay, dan partial failure.
- Semua visual regression/layout pada ukuran layar berbeda.

## 5. Test contract yang wajib ditambahkan saat perubahan

| Perubahan | Minimal test |
|---|---|
| Model QR | Encode/decode round trip dan invalid payload. |
| Header/endpoint | MockWebServer request path, body, header, dan status. |
| Auth | Valid token, token lama setelah regenerate, valid PIN, credential kosong. |
| Store | Save, update, delete, clear, malformed JSON fallback. |
| Navigation | Route builder dan back behavior. |
| Reusable component | `@Preview` dan Compose smoke test jika ada interaction penting. |
| ViewModel | State transitions untuk loading/success/error/cancel. |
| Permission/service | Instrumentation atau manual device acceptance. |

## 6. Interpretasi hasil

- `test` hijau: rule yang diuji berjalan pada environment test.
- `lint` hijau: tidak ada issue lint blocking, bukan jaminan usability.
- `assembleDebug` hijau: APK berhasil dibangun, bukan jaminan install/runtime.
- `compileDebugAndroidTestKotlin` hijau: test instrumentation compile, bukan test executed.
- `connectedAndroidTest` hijau: hanya membuktikan test pada device yang dipakai.
- Dua device acceptance hijau: bukti terbaik untuk pairing, HTTP, Accessibility, dan MediaProjection.
