# Operations Runbook

## 1. Prasyarat developer

- macOS/Linux/Windows dengan Android SDK dan JDK yang sesuai Android Gradle Plugin.
- Android Studio atau command-line SDK.
- Android SDK compile/target API 35.
- Dua perangkat Android atau emulator untuk uji end-to-end.
- Kedua device berada pada Wi-Fi lokal yang sama untuk pairing dan HTTP.

Tidak ada backend yang harus dijalankan. Semua komunikasi fitur dilakukan langsung antar aplikasi.

## 2. Command developer

```bash
# Semua unit test di seluruh modul
./gradlew test

# Test module tertentu
./gradlew :core:test
./gradlew :app:test

# Compile Compose instrumentation test tanpa device
./gradlew :ui-components:compileDebugAndroidTestKotlin

# Lint semua module
./gradlew lint

# Build APK debug
./gradlew :app:assembleDebug

# Full pre-merge verification
./gradlew test lint :app:assembleDebug
git diff --check

# Instrumented test; membutuhkan device/emulator aktif
./gradlew connectedAndroidTest
```

APK debug berada di `app/build/outputs/apk/debug/app-debug.apk` setelah build berhasil.

## 2.1 Release signing

`app` memiliki `release` signing config yang menggunakan keystore lokal berikut sebagai default:

```text
/Users/morieshutapea/AndroidStudioProjects/control-droid/control-droid.jks
```

Keystore diabaikan oleh Git melalui `*.jks`. Alias dan password disimpan di `local.properties`, yang juga diabaikan oleh Git. Konfigurasi lokal yang digunakan saat ini:

```properties
controlDroid.keystorePath=/Users/morieshutapea/AndroidStudioProjects/control-droid/control-droid.jks
controlDroid.storePassword=<keystore-password>
controlDroid.keyAlias=<key-alias>
controlDroid.keyPassword=<key-password>
```

Untuk checkout lain atau CI, nilai yang sama dapat diberikan melalui Gradle properties atau environment variable `CONTROL_DROID_KEYSTORE_PATH`, `CONTROL_DROID_STORE_PASSWORD`, `CONTROL_DROID_KEY_ALIAS`, dan `CONTROL_DROID_KEY_PASSWORD`. Jangan commit `local.properties` atau menyalin password ke dokumentasi publik.

Build release setelah seluruh nilai tersedia:

```bash
./gradlew :app:assembleRelease
```

Nama output release mengikuti format `control-droid-<versionName>-<versionCode>.apk`, contohnya `control-droid-1.0-1.apk`, di bawah `app/build/outputs/apk/release/`.

Jika alias atau password belum disediakan, build debug tetap dapat dijalankan, tetapi build release tidak boleh dianggap signed sampai Gradle berhasil memakai keystore tersebut.

## 3. Setup dua perangkat

### 3.1 Target

1. Install APK.
2. Buka aplikasi dan pilih **Target**.
3. Pastikan perangkat terhubung ke Wi-Fi yang sama dengan Controller.
4. Buka pengaturan Accessibility dari screen Target.
5. Aktifkan layanan ControlDroid Accessibility.
6. Pilih **Izinkan screen capture** jika ingin memakai remote preview.
7. Terima dialog MediaProjection.
8. Atur PIN jika membutuhkan fallback PIN legacy.
9. Biarkan halaman Target terbuka agar server tetap diinisialisasi dan QR terlihat.

### 3.2 Controller

1. Install APK pada perangkat kedua.
2. Buka aplikasi dan pilih **Controller**.
3. Buka **Tambah perangkat**.
4. Scan QR pada Target.
5. Tunggu validasi `/pair` selesai.
6. Buka device yang tersimpan dari Home.

### 3.3 Pairing legacy

Jika QR tidak dapat digunakan:

1. Pastikan Target merespons `/ping` di subnet yang sama.
2. Jalankan discovery dari Add Device.
3. Pilih device hasil scan.
4. Masukkan PIN Target.

Discovery menggunakan prefix `/24`; jika jaringan tidak sesuai asumsi ini, gunakan QR atau input flow yang akan ditambahkan di masa depan.

## 4. Penggunaan fitur

### Home

- `+` membuka Add Device.
- ikon list membuka Macro.
- kartu device membuka control screen.
- aksi Home semua perangkat mengirim `global_home` ke semua device tersimpan.

### Device Control

- Status badge menunjukkan hasil ping awal.
- Tombol Back/Home/Recent mengirim aksi global.
- Live preview hanya aktif jika Target reachable dan capture permission sudah diberikan.
- Clipboard mengirim teks tanpa paste.
- Kirim dan tempel membutuhkan input editable yang sedang fokus pada Target.

### Macro

- Macro saat ini hanya mendukung aksi navigasi yang tersedia pada form.
- Macro disimpan lokal pada Controller.
- `Jalankan` mengirim aksi secara berurutan dengan jeda 250 ms.
- Broadcast dijalankan per device dan belum menyediakan ringkasan sukses/gagal per target.

### Target mode

- QR berisi IP saat ini, port, nama, dan token.
- Regenerate token membatalkan QR/token lama.
- PIN adalah fallback dan bukan pengganti token QR pada endpoint `/pair`.
- Accessibility diperlukan untuk aksi global, gesture, dan paste.
- MediaProjection diperlukan untuk screenshot.

## 5. Acceptance checklist dua device

- [ ] Kedua device berada pada Wi-Fi yang sama.
- [ ] Target muncul di discovery atau QR berhasil dipindai.
- [ ] Pairing token divalidasi.
- [ ] Target tersimpan di Home Controller.
- [ ] Ping status berubah ke reachable.
- [ ] Back, Home, dan Recent benar-benar mengubah UI Target.
- [ ] Tap pada preview memicu tap di koordinat yang sesuai.
- [ ] Swipe pada preview memicu gesture yang sesuai.
- [ ] Screenshot berubah setelah screen Target berubah.
- [ ] Clipboard sampai ke Target.
- [ ] Paste berhasil pada input editable yang fokus.
- [ ] Macro mengirim semua langkah dalam urutan yang benar.
- [ ] Broadcast menjangkau semua Target yang tersimpan.
- [ ] Token lama gagal setelah regenerate.
- [ ] Request tanpa token/PIN mendapat `401` pada endpoint sensitif.
- [ ] Tombol back dan spacing toolbar benar pada device kecil dan besar.

## 6. Release checklist

- [ ] `./gradlew test lint :app:assembleDebug` berhasil.
- [ ] `git diff --check` bersih.
- [ ] Tidak ada secret, token hasil pairing, atau IP device nyata di source/docs.
- [ ] README dan dokumen API diperbarui jika behavior berubah.
- [ ] Instrumentation test dijalankan jika device/emulator tersedia.
- [ ] Acceptance checklist dua device selesai.
- [ ] Limitasi yang belum diverifikasi ditulis eksplisit pada release note.
