# Arsitektur ControlDroid

## 1. Gambaran sistem

ControlDroid adalah satu APK dengan dua peran runtime:

- **Controller**: mencari, memasangkan, dan mengirim perintah ke perangkat lain.
- **Target**: membuka server HTTP lokal, menerima perintah, menjalankan AccessibilityService, dan menyediakan screenshot.

Kedua perangkat berkomunikasi langsung melalui Wi-Fi lokal. Tidak ada backend relay, akun cloud, atau WebSocket pada implementasi saat ini.

```text
Controller :app                         Target :app
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ Home / Add Device / Control  │        │ TargetWaitingScreen           │
│ Macro / Remote Preview       │        │ TargetHttpServer :8080        │
└──────────────┬───────────────┘        │ AccessibilityService          │
               │                        │ ScreenCaptureService          │
               ▼                        └──────────────┬───────────────┘
          :core HTTP client ◄──── Wi-Fi ───────────────┘
               │
               ▼
       :ui-components Compose UI
```

## 2. Modul Gradle

```text
:app ───────► :core
  │
  └─────────► :ui-components
```

### `:core`

Android library tanpa Compose. Modul ini memiliki:

- `model`: `PairedDevice`, `DeviceRole`, `DeviceAction`, `GestureRequest`, `ClipboardRequest`, `Macro`, dan `PairingQrPayload`.
- `auth`: `RoleManager`, `PinVerifier`, dan `PairingTokenStore`.
- `storage`: `PairedDeviceStore` dan `MacroStore` berbasis SharedPreferences + Gson.
- `networking`: `DeviceHttpClient` berbasis OkHttp.
- `server`: `TargetHttpServer` berbasis NanoHTTPD.
- `control`: `DeviceScanner`, `AccessibilityController`, `ClipboardController`, `ScreenCaptureManager`, QR generator, dan `MacroRunner`.

### `:ui-components`

Compose library tanpa dependency ke `:app` atau `:core`. Komponen harus stateless dan menerima data/callback dari pemakai:

- `AppToolbar`
- `ControlActionButton`
- `ControlPad`
- `DeviceCard`
- `PairingQrCard`
- `PinDialog`
- `RemotePreviewSurface`
- `StatusBadge`
- `ControldroidTheme` dan typography/color definitions

Setiap composable reusable wajib memiliki `@Preview` lokal. Component tidak boleh mengakses `NavController`, SharedPreferences, HTTP, Android service, atau model domain `:core`.

### `:app`

APK yang menyatukan wiring runtime:

- `MainActivity`: role bootstrap, NavHost, store initialization, dan callback antar-screen.
- `features/controller`: flow Controller.
- `features/target`: flow Target dan service entrypoint.
- `features/viewmodel`: state dan polling remote preview.
- `ui/screen`: role selection dan home.

## 3. Startup dan navigation

`MainActivity` membaca `RoleManager` ketika composition dimulai:

1. Jika belum ada role, start destination `roleSelection`.
2. Jika role `CONTROLLER`, start destination `home`.
3. Jika role `TARGET`, start destination `preview/local`.
4. Route `preview/{id}` membuka `RemotePreviewScreen` jika device ditemukan di `PairedDeviceStore`.
5. Jika `preview/{id}` tidak menemukan device, route tersebut menampilkan `TargetWaitingScreen`.

Route dibangun oleh `NavigationTarget`:

| Enum | Route | Parameter |
|---|---|---|
| `RoleSelection` | `roleSelection` | - |
| `Home` | `home` | - |
| `Pair` | `pair` | - |
| `Control` | `control/{id}` | paired device ID |
| `Preview` | `preview/{id}` | paired device ID atau `local` |
| `Macros` | `macros` | - |

Semua route tetap berada di satu `NavHost`. Screen yang membuka sub-flow menerima callback `onBackClick` dan menggunakan `AppToolbar`.

## 4. Controller flow

### Pairing QR

1. Controller membuka `AddDeviceScreen`.
2. Target membuat payload `controldroid://pair?...` berisi version, name, IP, port, dan token.
3. Controller memindai QR dengan ZXing.
4. Controller decode payload, lalu melakukan `GET /pair` dengan `X-Control-Token`.
5. Jika sukses, Controller menyimpan `PairedDevice` dengan access token.
6. Controller kembali ke route `control/{id}`.

### Discovery fallback

1. `DeviceScanner` membaca IPv4 lokal.
2. Scanner mengambil prefix `/24` dari IP tersebut.
3. Host `1..254` dipanggil secara paralel ke `GET /ping` dengan timeout 300 ms.
4. Response yang mengandung `ControlDroid` dianggap sebagai Target.
5. User memilih device dan memasukkan PIN legacy.

Discovery hanya menemukan device dalam asumsi subnet `/24`; router, VPN, guest isolation, firewall, atau jaringan non-/24 dapat membuat device tidak ditemukan.

### Control

`DeviceControlScreen` membuat `DeviceHttpClient` dari IP, port default 8080, PIN, dan access token. Semua aksi dikirim asynchronous:

- Back, Home, Recent → `POST /action`.
- Live preview → `POST /action` `capture_screen`, lalu navigasi ke preview.
- Clipboard → `POST /clipboard`.
- Clipboard + paste → `POST /clipboard` dengan `paste=true`.

Control pad berada di screen yang sama dan hanya aktif jika ping awal berhasil. Arah atas/bawah/kiri/kanan dipetakan menjadi `GestureRequest` swipe normalized dari area tengah layar Target, lalu dikirim melalui endpoint `/gesture` yang sudah ada.

### Remote preview

`RemotePreviewViewModel` memulai polling ketika screen masuk composition dan membatalkan polling ketika screen dilepas. Polling:

1. `GET /screenshot`.
2. Decode response bytes menjadi Bitmap.
3. Update `RemotePreviewUiState`.
4. Delay 2 detik.

Gesture pada `RemotePreviewSurface` dikonversi menjadi koordinat `0..1` berdasarkan ukuran composable dan dikirim sebagai `GestureRequest`.

## 5. Target flow

### HTTP server

`TargetHttpServer` adalah singleton NanoHTTPD pada port `8080`. Server di-start oleh `TargetWaitingScreen` dan menggunakan application context provider. Endpoint sensitif memeriksa token atau PIN sebelum menjalankan aksi.

### Accessibility

`AccessibilityController` adalah Android `AccessibilityService` dengan instance static yang diisi saat `onServiceConnected`. Server memakai instance tersebut untuk:

- `performGlobalAction` Back/Home/Recent.
- `dispatchGesture` dengan koordinat pixel hasil normalisasi.
- `ACTION_PASTE` pada input yang sedang fokus.

Jika service belum aktif, operasi gagal secara log dan tidak dapat benar-benar mengontrol UI target.

### Screen capture

1. User membuka screen permission dari Target.
2. `ScreenPermissionActivity` meminta consent MediaProjection.
3. Jika disetujui, activity memulai `ScreenCaptureService` sebagai foreground service.
4. Service menginisialisasi `ScreenCaptureManager` dan memulai capture loop setiap 2 detik.
5. Frame terakhir disimpan sebagai `cacheDir/screenshot.png`.
6. Controller mengambil file tersebut melalui `/screenshot`.

Jika permission dicabut atau service berhenti, preview tidak mendapatkan frame baru.

## 6. Persistence

| Store | Preferences file | Key | Isi |
|---|---|---|---|
| `RoleManager` | `device_role` | `role` | `CONTROLLER` atau `TARGET` |
| `PinVerifier` | `security_prefs` | `secure_pin` | PIN plaintext lokal saat ini |
| `PairingTokenStore` | `pairing_security` | `access_token` | token random 32 byte Base64 URL-safe |
| `PairedDeviceStore` | `paired_devices` | `devices` | JSON list paired device |
| `MacroStore` | `macros` | `saved_macros` | JSON list macro |

Data pairing dan macro bersifat lokal per installation. Tidak ada sinkronisasi antar device atau backup cloud yang dikelola aplikasi.

## 7. Window inset dan UI architecture

`MainActivity` menggunakan edge-to-edge dengan `WindowCompat.setDecorFitsSystemWindows(window, false)`. Root `Scaffold` menjadi pemilik inset sistem. Screen yang memiliki toolbar memakai `contentWindowInsets = WindowInsets(0, 0, 0, 0)` dan `AppToolbar` tanpa inset status bar agar status bar tidak dihitung dua kali.

Jika menambah screen dengan toolbar:

1. Gunakan `Scaffold`.
2. Set `contentWindowInsets` ke zero sesuai pola existing.
3. Gunakan `AppToolbar`.
4. Terapkan `padding` dari lambda `Scaffold` ke content.
5. Jangan menambah `statusBarsPadding()` kedua kali tanpa alasan yang terdokumentasi.

## 8. Boundary dan batasan arsitektur

- Jangan memindahkan Compose ke `:core`.
- Jangan membuat `:ui-components` bergantung pada `:core` atau `:app`.
- Jangan menaruh network call langsung di reusable component.
- Jangan membuat screen membaca SharedPreferences langsung jika store/auth abstraction sudah tersedia.
- Jangan menganggap response HTTP 200 sebagai bukti Accessibility atau MediaProjection berhasil; runtime capability tetap harus diverifikasi di device.
