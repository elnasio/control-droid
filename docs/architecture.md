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
4. Controller decode payload, lalu melakukan `GET /pair` dengan `X-Control-Token` plus `X-Controller-Id`/`X-Controller-Name` dari `ControllerIdentityStore` (id UUID persisten per instalasi Controller).
5. Jika `X-Controller-Id` ini belum pernah disetujui Target, request tersebut menahan koneksi (lihat `PairingApprovalGate` di bagian Target flow) sampai user menekan Terima/Tolak di Target atau timeout 45 detik.
6. Jika sukses (baik karena sudah trusted maupun baru disetujui), Controller menyimpan `PairedDevice` dengan access token. Re-pairing IP yang sama memakai ulang `id` entri lama (`PairedDeviceStore.saveDevice`) alih-alih menambah baris baru.
7. Controller kembali ke route `control/{id}`.

### Discovery fallback

1. `DeviceScanner` membaca IPv4 lokal.
2. Scanner mengambil prefix `/24` dari IP tersebut.
3. Host `1..254` dipanggil secara paralel ke `GET /ping` dengan timeout 300 ms.
4. Response yang mengandung `ControlDroid` dianggap sebagai Target.
5. User memilih device; jika IP tersebut belum pernah dipasangkan, memasukkan PIN legacy yang divalidasi via `DeviceHttpClient.verifyPin` (`GET /pair` dengan `X-Control-Pin`, tunduk pada alur approval yang sama seperti QR) sebelum disimpan. Jika IP sudah ada di `PairedDeviceStore`, Controller langsung memakai kredensial tersimpan tanpa dialog PIN baru.

Discovery hanya menemukan device dalam asumsi subnet `/24`; router, VPN, guest isolation, firewall, atau jaringan non-/24 dapat membuat device tidak ditemukan.

### Control

`DeviceControlScreen` tidak lagi bicara langsung ke satu client konkret — ia bergantung pada
`core/networking/DeviceControlClient`, sebuah interface transport-agnostic (`checkStatus`,
`sendAction`, `sendGesture`, `sendClipboard`, `fetchScreenshot`). Screen ini membuat **dua**
instance sekaligus, `DeviceHttpClient` (Wi-Fi lokal, dari IP/port/PIN/access token) dan
`InternetRelayClient` (relay internet, dari `PairedDevice.id`/PIN/access token — lihat
`docs/internet-relay-api.md`), lalu memilih salah satunya sebagai `activeClient` berdasarkan
toggle "Kontrol via Internet" (`core/model/ControlTransportMode`). Semua pemanggilan di bawah ini
selalu lewat `activeClient`, jadi berpindah Wi-Fi↔Internet tidak mengubah kode pemanggilnya sama
sekali:

- Back, Home, Recent → `sendAction` (di Wi-Fi: `POST /action` raw text; di relay: `POST /action`
  JSON `{"command": ...}`), dengan feedback hasil kirim ditampilkan sebagai Toast. Ketiga tombol
  ini dipindah ke `bottomBar` Scaffold (sticky), bukan bagian dari `LazyColumn` yang scroll.
- Live preview → toggle Switch inline (bukan navigasi ke screen terpisah): saat dinyalakan,
  mengirim `sendAction(CAPTURE_SCREEN)` lalu memulai `RemotePreviewViewModel` polling; gambar,
  status, dan gesture tap/swipe dirender langsung di kartu yang sama.
- Clipboard → `sendClipboard`; field teksnya punya ikon tempel (ambil teks dari clipboard sistem
  Controller sendiri, lewat `LocalClipboardManager`) dan ikon X (kosongkan field), terpisah dari
  tombol "Kirim clipboard"/"Kirim dan tempel" yang benar-benar mengirim ke Target.

Status "Terhubung" berasal dari `checkStatus` milik `activeClient` (di Wi-Fi: `GET /status`; di
relay: `GET /v1/devices/{id}/status`) — endpoint ringan yang memvalidasi kredensial tanpa memicu
approval, bukan dari `/ping`, supaya status tidak pernah salah melaporkan "Terhubung" saat
kredensial sebenarnya sudah tidak valid. `checkStatus` dijalankan ulang setiap kali toggle
transport berubah. Toolbar menampilkan `ConnectionStatusIcon` (`ui-components`): hijau (Wi-Fi
terhubung), kuning (Internet terhubung), merah (tidak terhubung pada mode manapun) — turunan
murni dari `connected` dan `transportMode`, tidak ada state independen ketiga.

Tombol Back/Home/Recent dan Control pad hanya aktif jika `checkStatus` awal berhasil. Arah atas/bawah/kiri/kanan Control pad dipetakan menjadi `GestureRequest` swipe normalized dari area tengah layar Target, lalu dikirim melalui `activeClient.sendGesture`.

### Remote preview

`RemotePreviewViewModel` transport-agnostic: `RemotePreviewEvent.StartPolling` membawa
`DeviceControlClient` yang sedang aktif (bukan lagi IP/PIN/token mentah), sehingga polling
otomatis mengikuti transport Wi-Fi/Internet yang dipilih di `DeviceControlScreen`. Polling dimulai
saat toggle live-preview dan/atau `transportMode` berubah, dan dibatalkan saat toggle dimatikan
atau screen dilepas:

1. `client.fetchScreenshot()`.
2. Decode response bytes menjadi Bitmap.
3. Update `RemotePreviewUiState`.
4. Delay 2 detik.

Gesture pada `RemotePreviewSurface` dikonversi menjadi koordinat `0..1` berdasarkan ukuran composable dan dikirim sebagai `GestureRequest` lewat `activeClient`.

## 5. Target flow

### HTTP server

`TargetHttpServer` adalah singleton NanoHTTPD pada port `8080`. Server di-start oleh `TargetWaitingScreen` dan menggunakan application context provider. Endpoint sensitif memeriksa token atau PIN sebelum menjalankan aksi.

### Pairing approval

NanoHTTPD menangani setiap koneksi di thread-nya sendiri, sehingga menahan satu request `/pair` (menunggu keputusan user) tidak memblokir request lain yang sedang berjalan (`/ping`, `/action`, dsb dari Controller lain).

- `PairingApprovalGate` (singleton, `:core`) menjembatani thread HTTP tersebut dengan Compose UI: `requestApproval(controllerId, controllerName, timeoutMs)` mem-block via `CountDownLatch` sambil mem-publish `PendingPairingRequest` ke `StateFlow` yang diobservasi `TargetWaitingScreen`; `approve()`/`reject()` dipanggil dari tombol dialog di UI thread. Hanya satu approval yang tampil sekaligus — request kedua menunggu giliran lewat `ReentrantLock`.
- `TrustedControllerStore` (`:core/auth`) menyimpan daftar `TrustedController(id, name, approvedAt)` yang sudah disetujui, di-render sebagai kartu "Controller terpercaya" (dengan tombol "Hapus akses" per entri) di `TargetWaitingScreen`.
- `ControllerIdentityStore` (`:core/auth`, sisi Controller) meng-generate dan menyimpan UUID + nama tampilan sekali per instalasi, dikirim sebagai `X-Controller-Id`/`X-Controller-Name` pada setiap panggilan `/pair`.

Lihat `docs/http-api.md` §3 untuk detail alur request/response `/pair` dan `/status`.

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

`ScreenCaptureManager.isSharing` (`StateFlow<Boolean>`) melacak status aktif/tidaknya proyeksi secara reaktif — `true` sejak `setProjection(...)`, kembali `false` setelah `releaseProjection()`. `TargetWaitingScreen` men-observe ini untuk menonaktifkan tombol "Izinkan screen capture" dan menampilkan tombol merah "Hentikan screen share" selama sharing aktif. Ada dua jalur untuk menghentikannya, keduanya berakhir di `ScreenCaptureService.onDestroy()` → `ScreenCaptureManager.releaseProjection()`:

- Tombol "Hentikan screen share" di `TargetWaitingScreen` → `context.stopService(Intent(context, ScreenCaptureService::class.java))`.
- Tombol "Stop" pada notifikasi ongoing `ScreenCaptureService` (`ACTION_STOP` → `stopSelf()`), tanpa perlu membuka app.

Jika permission dicabut atau service berhenti, preview tidak mendapatkan frame baru.

## 6. Persistence

| Store | Preferences file | Key | Isi |
|---|---|---|---|
| `RoleManager` | `device_role` | `role` | `CONTROLLER` atau `TARGET` |
| `PinVerifier` | `security_prefs` | `secure_pin` | PIN plaintext lokal saat ini |
| `PairingTokenStore` | `pairing_security` | `access_token` | token random 32 byte Base64 URL-safe |
| `PairedDeviceStore` | `paired_devices` | `devices` | JSON list paired device |
| `MacroStore` | `macros` | `saved_macros` | JSON list macro |
| `ControllerIdentityStore` (Controller) | `controller_identity` | `controller_id` | UUID persisten instalasi Controller |
| `TrustedControllerStore` (Target) | `trusted_controllers` | `controllers` | JSON list `TrustedController` yang sudah disetujui |

Data pairing dan macro bersifat lokal per installation. Tidak ada sinkronisasi antar device atau backup cloud yang dikelola aplikasi. `android:allowBackup="false"` di `AndroidManifest.xml` juga menonaktifkan Android Auto Backup/`adb backup` bawaan OS untuk seluruh data app ini — PIN, access token, dan daftar trusted controller di atas tidak boleh ikut ter-backup/dipulihkan ke device lain di luar kendali app.

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
