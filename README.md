# ControlDroid

ControlDroid adalah aplikasi Android peer-to-peer yang memungkinkan satu perangkat untuk mengontrol perangkat lain melalui jaringan Wi-Fi lokal tanpa root dan tanpa membuka developer options.

## ✨ Fitur Utama

- Scan perangkat target di jaringan lokal
- Pairing cepat melalui QR code dan access token
- Kontrol navigasi global: Home, Back, Recent
- Kontrol tap dan swipe dari remote preview
- Clipboard dan paste teks ke perangkat target
- Macro navigasi dan broadcast ke beberapa target
- Screenshot target melalui MediaProjection
- Autentikasi access token atau PIN pada jaringan lokal

## 📚 Dokumentasi lengkap

Dokumentasi terperinci tersedia di [docs/README.md](docs/README.md), termasuk:

- [Arsitektur dan data flow](docs/architecture.md)
- [HTTP API dan autentikasi](docs/http-api.md)
- [Operations runbook](docs/operations.md)
- [Testing dan batas coverage](docs/testing.md)
- [Troubleshooting](docs/troubleshooting.md)
- [UI guidelines](docs/ui-guidelines.md)

## 📱 Arsitektur

```
+------------------+         Wi-Fi          +------------------+
|  Controller App  | <--------------------> |   Target App     |
+------------------+                       +------------------+
| - HTTP Client                           | - NanoHTTPD Server
| - Device Scanner                        | - AccessibilityService
| - Action, Gesture, Clipboard Sender     | - MediaProjection Screenshot
| - QR Pairing and Macro                  | - Screen Capture Foreground Service
| - RemotePreviewScreen                   | - Screenshot Endpoint
```

## 🧱 Modul Gradle

```text
:app ───────► :core
  │
  └─────────► :ui-components
```

- `:core`: Android library tanpa Compose untuk model, auth, storage, networking, HTTP server, AccessibilityService controller, screen capture manager, dan macro runner.
- `:ui-components`: Compose library tanpa dependency ke `:app` atau `:core`. Berisi theme dan komponen UI reusable seperti `AppToolbar`, `DeviceCard`, `StatusBadge`, PIN dialog, pairing QR card, action button, dan remote preview surface. Setiap component memiliki `@Preview`.
- `:app`: APK utama yang berisi Activity, navigation, feature screen, ViewModel, manifest, service entrypoint, dan wiring dependency.

Build utama tetap menghasilkan satu APK dari `:app`:

```bash
./gradlew :core:test :ui-components:test :app:test
./gradlew :app:assembleDebug
./gradlew lint
# Full pre-merge check
./gradlew test lint :app:assembleDebug
```

`applicationId`, package source, SharedPreferences key, route, dan kontrak HTTP dipertahankan agar refactor tidak mengubah behavior runtime.

## 🧩 Modul

### Controller
- `AddDeviceScreen.kt`: Scan dan pilih device
- `DeviceScanner.kt`: Deteksi IP dalam satu subnet
- `DeviceHttpClient.kt`: Kirim perintah HTTP, gesture, dan clipboard ke target
- `RemotePreviewScreen.kt`: Lihat tangkapan layar dari target
- `MacroScreen.kt`: Simpan dan jalankan macro navigasi
- `PairedDeviceStore.kt`: Simpan target yang sudah dipasangkan

### Target
- `TargetHttpServer.kt`: NanoHTTPD untuk menerima perintah
- `AccessibilityController.kt`: Lakukan aksi navigasi global
- `ScreenCaptureManager.kt`: Tangkap screenshot dan simpan PNG
- `ScreenCaptureService.kt`: Menjaga sesi MediaProjection di foreground
- `TargetWaitingScreen.kt`: Status siap dikontrol dan menampilkan QR pairing
- `PairingTokenStore.kt`: Simpan access token pairing target

## 🔐 Keamanan dan batasan

- Koneksi hanya dilakukan antar perangkat di jaringan Wi-Fi lokal
- QR pairing menggunakan access token acak per instalasi
- PIN tetap tersedia untuk kompatibilitas pairing lama
- Endpoint sensitif menolak request tanpa token atau PIN yang valid
- Transport saat ini masih HTTP cleartext di jaringan lokal; belum menggunakan TLS
- Tidak menggunakan developer options atau root

### Model autentikasi

- QR membawa token random 32 byte yang disimpan oleh Target.
- `/pair` hanya menerima token QR.
- `/action`, `/gesture`, `/clipboard`, dan `/screenshot` menerima token valid atau PIN valid.
- Regenerate token membatalkan token sebelumnya.
- PIN saat ini disimpan lokal dan belum di-hash; gunakan hanya pada jaringan lokal yang dipercaya.
- HTTP cleartext berarti siapa pun yang dapat mengakses jaringan dan mengetahui credential dapat mencoba mengirim request. Jangan gunakan pada jaringan publik/tidak dipercaya.

Detail request/response ada di [docs/http-api.md](docs/http-api.md).

## 🚀 Cara Menjalankan

1. Install aplikasi di dua perangkat Android.
2. Pilih peran `Target` pada perangkat yang akan dikontrol.
3. Hubungkan kedua perangkat ke jaringan Wi-Fi lokal yang sama.
4. Pada Target, aktifkan layanan Aksesibilitas.
5. Pada Target, tekan `Izinkan screen capture` jika ingin memakai preview layar.
6. Pilih `Atur PIN` jika ingin mengaktifkan pairing PIN legacy.
7. Pilih peran `Controller` pada perangkat pengendali.
8. Buka `Add Device`, lalu scan QR pairing yang tampil pada Target.
9. Setelah pairing, buka perangkat dari Home untuk mengirim aksi atau melihat preview.
10. Untuk gesture, lakukan tap atau swipe langsung pada gambar preview.
11. Untuk clipboard, isi teks pada panel kontrol lalu pilih `Kirim Clipboard` atau `Kirim + Tempel`.
12. Untuk macro, buka `Macros`, pilih aksi navigasi, simpan, lalu pilih `Jalankan semua`.

Pairing QR membawa alamat IP, port, nama Target, dan access token. Karena alamat IP lokal dapat berubah, pairing perlu diulang atau perangkat perlu diperbarui jika Target mendapat IP baru.

## 🌐 HTTP API Lokal

Semua endpoint berjalan pada port `8080`.

| Method | Endpoint | Auth | Fungsi |
|---|---|---|---|
| GET | `/ping` | Tidak | Discovery perangkat |
| GET | `/pair` | `X-Control-Token` | Validasi token QR |
| POST | `/action` | Token atau PIN | Back, Home, Recent, capture screen |
| POST | `/gesture` | Token atau PIN | Tap/swipe dengan koordinat ternormalisasi |
| POST | `/clipboard` | Token atau PIN | Mengatur clipboard dan optional paste |
| GET | `/screenshot` | Token atau PIN | Mengambil PNG terakhir |

Header autentikasi:

- `X-Control-Token`: access token dari QR pairing.
- `X-Control-Pin`: PIN legacy yang disimpan pada Target.

Preview saat ini menggunakan polling PNG setiap sekitar dua detik, bukan video streaming.

## ⚠️ Catatan

- Target harus mengizinkan `MediaProjection` untuk screenshot
- Port `8080` harus tersedia di kedua perangkat
- Pastikan `ACCESSIBILITY_SERVICE` aktif di target
- Fitur paste memerlukan field teks yang sedang fokus pada Target
- Gesture dan aksi navigasi bergantung pada AccessibilityService yang aktif
- Pengujian dua perangkat nyata belum dilakukan otomatis oleh project

## 🛠️ Dependencies

- NanoHTTPD WebSocket artifact: `org.nanohttpd:nanohttpd-websocket:2.3.1` (server memakai plain HTTP API dari artifact ini)
- OkHttp: `com.squareup.okhttp3:okhttp`
- Gson: `com.google.code.gson:gson`
- ZXing Android Embedded: QR scan dan QR generation
- Jetpack Compose
- Kotlin Coroutines

## 🧪 Rencana Pengembangan

- Streaming layar real-time (MJPEG/WebSocket)
- Mode koneksi via Internet (relay server)
- Enkripsi PIN & autentikasi lanjutan

Fitur MVP tambahan yang sudah tersedia:

- Pairing QR dengan access token
- Tap dan swipe dari remote preview
- Clipboard dan paste ke target
- Macro navigasi dan broadcast ke semua target berpasangan

## 📂 Struktur Proyek

```
control-droid/
├── app/src/main/java/com/mories/control_droid/
│   ├── MainActivity.kt
│   ├── features/
│   │   ├── controller/
│   │   ├── target/
│   │   └── viewmodel/
│   └── ui/
│       ├── screen/
│       └── NavigationTarget.kt
├── core/src/main/java/com/mories/control_droid/core/
│   ├── auth/
│   ├── control/
│   ├── model/
│   ├── networking/
│   ├── server/
│   └── storage/
├── ui-components/src/main/java/com/mories/control_droid/ui/
│   ├── components/
│   └── theme/
├── docs/
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── README.md
```

## 🧭 Alur utama pengguna

### Controller

```text
Role selection
      │
      ▼
Home ──► Add Device ──► QR validation ──► Device Control
  │                                      │
  ├──► Macros                           ├──► Clipboard
  └──► Broadcast Home                   └──► Live Preview + Gesture
```

### Target

```text
Role selection
      │
      ▼
Target mode ──► Set PIN / Show QR
      ├───────► Enable Accessibility
      └───────► Grant MediaProjection
```

### State dan capability

Pairing, PIN, role, dan macro tersimpan lokal. Reachability, Accessibility, dan MediaProjection adalah capability runtime yang terpisah; paired device yang tersimpan tidak otomatis berarti Target sedang reachable atau siap menerima semua aksi.

## ✅ Status verifikasi

Saat ini yang sudah diverifikasi otomatis:

- Unit test `:core` untuk auth, persistence, QR, dan HTTP client contract.
- Unit test `:app` untuk route helper.
- Compose instrumentation source `:ui-components` berhasil dikompilasi.
- Lint seluruh module.
- Debug APK berhasil diassemble.

Yang masih membutuhkan emulator/perangkat nyata:

- Camera QR scan.
- Pairing dan HTTP antar dua device.
- Accessibility global action, gesture, dan paste.
- MediaProjection serta foreground service.
- Visual layout/inset pada berbagai ukuran layar.

Gunakan [docs/testing.md](docs/testing.md) dan [docs/operations.md](docs/operations.md) sebagai checklist validasi.

---

Made with ❤️ by Mories Deo Hutapea.
