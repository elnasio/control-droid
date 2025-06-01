# ControlDroid

ControlDroid adalah aplikasi Android peer-to-peer yang memungkinkan satu perangkat untuk mengontrol perangkat lain melalui jaringan Wi-Fi lokal tanpa root dan tanpa membuka developer options.

## ✨ Fitur Utama

- Scan perangkat target di jaringan lokal
- Kontrol navigasi global: Home, Back, Recent
- Tangkap screenshot perangkat target secara real-time
- Koneksi aman menggunakan PIN verifikasi
- Arsitektur modular dan scalable

## 📱 Arsitektur

```
+------------------+         Wi-Fi          +------------------+
|  Controller App  | <--------------------> |   Target App     |
+------------------+                       +------------------+
| - WebSocket Client                      | - NanoHTTPD Server
| - Device Scanner                        | - AccessibilityService
| - Action Sender                         | - MediaProjection Screenshot
| - RemotePreviewScreen                   | - Screenshot Endpoint
```

## 🧩 Modul

### Controller
- `AddDeviceScreen.kt`: Scan dan pilih device
- `DeviceScanner.kt`: Deteksi IP dalam satu subnet
- `LocalWebSocketClient.kt`: Kirim perintah HTTP ke target
- `RemotePreviewScreen.kt`: Lihat tangkapan layar dari target

### Target
- `TargetHttpServer.kt`: NanoHTTPD untuk menerima perintah
- `AccessibilityController.kt`: Lakukan aksi navigasi global
- `ScreenCaptureManager.kt`: Tangkap screenshot dan simpan PNG
- `TargetWaitingScreen.kt`: Status siap dikontrol

## 🔐 Keamanan

- Koneksi hanya dilakukan antar perangkat di jaringan Wi-Fi lokal
- Setiap device harus memasukkan PIN agar bisa dikontrol
- Tidak menggunakan developer options atau root

## 🚀 Cara Menjalankan

1. Install aplikasi di dua perangkat Android
2. Pilih peran: Controller atau Target
3. Pastikan dua perangkat dalam 1 Wi-Fi
4. Di target, aktifkan layanan Aksesibilitas
5. Di controller, lakukan scan dan pilih device
6. Kontrol layar dan navigasi langsung

## ⚠️ Catatan

- Target harus mengizinkan `MediaProjection` untuk screenshot
- Port `8080` harus tersedia di kedua perangkat
- Pastikan `ACCESSIBILITY_SERVICE` aktif di target

## 🛠️ Dependencies

- NanoHTTPD: `org.nanohttpd:nanohttpd:2.3.1`
- OkHttp: `com.squareup.okhttp3:okhttp`
- Jetpack Compose
- Kotlin Coroutines

## 🧪 Rencana Pengembangan

- Streaming layar real-time (MJPEG/WebSocket)
- Kontrol sentuhan jarak jauh
- Mode koneksi via Internet (relay server)
- Enkripsi PIN & autentikasi lanjutan

## 📂 Struktur Proyek

```
com/
└── mories/
    └── controldroid/
        ├── core/
        │   ├── network/
        │   ├── storage/
        │   └── util/
        ├── controller/
        └── target/
```

---

Made with ❤️ by Mories Deo Hutapea.