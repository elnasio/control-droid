# Troubleshooting

## 1. Build dan Gradle

### `Gradle ... zip.lck` atau lock error

Kemungkinan ada proses Gradle lain, daemon lama, atau file cache lock yang sedang dipakai.

Langkah aman:

1. Pastikan tidak ada build lain berjalan di Android Studio/terminal.
2. Tunggu daemon selesai lalu jalankan ulang command.
3. Jika tetap terjadi, restart Android Studio/terminal.
4. Jangan menghapus workspace atau menjalankan cleanup recursive yang tidak terarah.

### Unresolved reference dari module

Periksa dependency direction:

- `:app` boleh memakai `:core` dan `:ui-components`.
- `:core` tidak boleh mengimpor Compose atau `:app`.
- `:ui-components` tidak boleh mengimpor `:core` atau `:app`.

Jalankan:

```bash
./gradlew :core:compileDebugKotlin :ui-components:compileDebugKotlin :app:compileDebugKotlin
```

### APK build sukses tetapi fitur runtime gagal

Build hanya memeriksa source/resource. Lanjutkan diagnosis sesuai bagian device, permission, atau network di bawah.

## 2. Device tidak muncul

Periksa:

1. Kedua device benar-benar pada jaringan Wi-Fi yang sama.
2. Target screen sudah dibuka dan server sudah di-start.
3. Port 8080 tidak diblokir router/firewall.
4. Guest Wi-Fi/client isolation tidak aktif.
5. IP lokal Controller bisa dibaca.
6. Jaringan sesuai asumsi scanner `/24`.

Untuk diagnosis manual dari jaringan yang sama:

```bash
curl -i http://<target-ip>:8080/ping
```

Expected body: `ControlDroid`. Jika tidak ada response, masalah berada di server, IP, port, atau jaringan; bukan di pairing token.

## 3. QR gagal dipairing

Gejala dan penyebab umum:

- **QR invalid**: hasil scanner bukan prefix `controldroid://pair?` atau payload rusak.
- **401 dari `/pair`**: token/PIN salah, token sudah diregenerate, QR terlalu lama, atau user menekan **Tolak** pada dialog approval di Target.
- **Controller tampak menggantung "Menunggu persetujuan di Target..." lalu gagal setelah ~45 detik**: dialog "Permintaan pairing" muncul di layar Target tapi tidak ada yang menekan Terima/Tolak dalam 45 detik (lihat `PairingApprovalGate`, `PAIRING_APPROVAL_TIMEOUT_MS`). Buka layar Target dan tekan Terima sebelum timeout; kalau Target sedang terkunci/di background, dialog tidak akan terlihat sampai layar dibuka lagi meski request tetap menunggu di background selama itu.
- **Timeout**: IP yang dibawa QR sudah berubah atau device tidak reachable.
- **Pair berhasil tetapi control gagal**: token tersimpan salah, PIN legacy kosong/salah, atau endpoint sensitif tidak reachable. Cek `GET /status` (lihat `docs/http-api.md`) untuk memastikan kredensial yang tersimpan Controller masih valid — ini juga yang menentukan status "Terhubung" di `DeviceControlScreen`.

Regenerate QR/token pada Target, lalu scan ulang. Token lama memang harus ditolak setelah regenerate.

Controller yang **sudah pernah disetujui** (tercatat di kartu "Controller terpercaya" pada Target) tidak akan diminta approval lagi selama entrinya belum dihapus lewat tombol "Hapus akses" di Target. Kalau perlu memutus akses sebuah Controller, hapus dari daftar itu — bukan dari sisi Controller.

## 4. Aksi Back/Home/Recent tidak bekerja

Periksa Accessibility:

1. Buka Target mode.
2. Buka Accessibility Settings.
3. Aktifkan service ControlDroid.
4. Kembali ke aplikasi dan ulangi aksi.

Jika request HTTP sukses tetapi aksi tidak terlihat, response `200` hanya berarti server menerima request. Periksa log Android untuk `AccessibilityController` dan status service.

## 5. Tap/swipe tidak tepat

Kemungkinan:

- Preview memiliki aspect ratio berbeda dari display Target.
- Gesture dikirim pada gambar yang belum frame terbaru.
- AccessibilityService belum aktif.
- Target memakai UI dengan coordinate transform/inset berbeda.

Gesture saat ini menormalisasi posisi terhadap ukuran composable, lalu mengalikannya dengan `displayMetrics.widthPixels/heightPixels`. Belum ada transform khusus untuk letterboxing atau cutout/inset.

## 6. Screenshot/live preview kosong

Periksa:

1. User sudah menyetujui MediaProjection.
2. Foreground service masih berjalan.
3. Notification screen sharing terlihat.
4. `ScreenCaptureManager.isReady()` true.
5. File `cacheDir/screenshot.png` berhasil dibuat.
6. Controller memakai IP/token yang benar.

Status `404 No Screenshot` berarti endpoint reachable tetapi belum ada frame. Status HTTP error atau timeout berarti masalah auth/network/client.

## 7. Clipboard terkirim tetapi paste tidak terjadi

`POST /clipboard` mengatur clipboard terlebih dahulu. Paste hanya dilakukan jika:

- AccessibilityService aktif.
- Ada input editable yang sedang fokus.
- Target UI mengizinkan `ACTION_PASTE`.

Uji dengan membuka field teks pada Target, memberi fokus, lalu kirim ulang. Clipboard transfer dan paste capability harus dibedakan.

## 8. Macro tidak menjalankan semua langkah

Macro saat ini:

- Hanya berisi `DeviceAction` dari form navigasi.
- Dijalankan per device melalui coroutine terpisah.
- Memberi jeda 250 ms antar aksi.
- Belum menampilkan hasil sukses/gagal per device.

Periksa AccessibilityService pada setiap Target. Jika satu target gagal, jangan menganggap callback global sebagai bukti semua target berhasil.

## 9. Toolbar atau content memiliki ruang kosong atas

Arsitektur edge-to-edge saat ini membuat root `MainActivity` menangani system inset. Screen dengan toolbar harus:

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = { AppToolbar(title = "...") }
) { padding ->
    Content(modifier = Modifier.padding(padding))
}
```

Jangan menambahkan `statusBarsPadding()` kedua kali pada toolbar screen tanpa alasan.

## 10. Checklist log

Gunakan tag berikut ketika membaca logcat:

| Tag | Area |
|---|---|
| `TargetHttpServer` | Server, endpoint, body/error, screenshot availability. |
| `DeviceHttpClient` | Request client, callback failure, action response. |
| `DeviceScanner` | IP lokal, subnet, host discovery. |
| `Accessibility` | Service connection dan global action/gesture. |
| `ScreenCapture` | Projection, capture, save PNG. |
| `RemotePreviewViewModel` | Polling URL, stop polling, HTTP/decode error. |
| `AddDeviceScreen` | Hasil scan perangkat. |

## 11. Force close di build release, tapi normal di build debug

Build debug tidak menjalankan R8 (`isMinifyEnabled = false`), jadi bug yang hanya muncul akibat shrinking/obfuscation **tidak akan pernah terlihat** kalau hanya menguji `assembleDebug`/`installDebug`. Kalau ada laporan force close yang tidak bisa direproduksi di build debug, build dan uji `:app:assembleRelease` secara langsung sebelum menyimpulkan apa pun.

Kasus nyata yang pernah terjadi: `PairedDeviceStore`/`MacroStore` membangun `object : TypeToken<List<X>>() {}` saat runtime supaya Gson tahu tipe generic-nya. Di build release, R8 bisa menghapus/menggabungkan anonymous class tersebut walau `-keepattributes Signature` sudah ada, sehingga Gson melempar:

```
java.lang.IllegalStateException: TypeToken must be created with a type argument: new TypeToken<...>() {};
When using code shrinkers (ProGuard, R8, ...) make sure that generic signatures are preserved.
```

Efeknya persis seperti "klik X langsung force close tanpa tampilan apa pun", karena crash terjadi di tengah composable pertama yang memanggil `store.getAll()`/`macroStore.getAll()` (mis. navigasi ke `Preview/local` setelah `Pilih Target`). Fix-nya ada di `app/proguard-rules.pro`:

```
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
```

Kalau menambah pola `TypeToken<...>() {}` baru di tempat lain, rule di atas sudah menutupinya (rule-nya generik, bukan per-kelas) — tapi tetap wajib diverifikasi dengan `assembleRelease` + install nyata, bukan cuma `assembleDebug`.

`CrashLogger` (lihat `core/diagnostics/CrashLogger.kt`) sangat membantu untuk kasus begini: dia menangkap *seluruh* uncaught exception di app (bukan cuma di satu layar) dan menampilkannya sebagai dialog scrollable saat app dibuka lagi, jadi device yang tidak bisa disambungkan ke `adb`/komputer pun bisa "melapor sendiri" stack trace persisnya lewat screenshot.
