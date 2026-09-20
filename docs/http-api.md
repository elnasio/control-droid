# HTTP API Lokal ControlDroid

## 1. Konvensi umum

- Base URL: `http://<target-ip>:8080`.
- Port berasal dari `core/ConstantValue.PORT_VALUE`.
- Transport adalah HTTP cleartext di jaringan lokal.
- Body JSON memakai `application/json`.
- Endpoint `/action` memakai body plain text.
- Header autentikasi yang digunakan client:
  - `X-Control-Token`
  - `X-Control-Pin`
  - `X-Controller-Id` — UUID persisten milik instalasi Controller (lihat `ControllerIdentityStore`), wajib untuk `/pair`.
  - `X-Controller-Name` — nama tampilan Controller (mis. `Build.MANUFACTURER + Build.MODEL`), dipakai pada dialog approval dan daftar Controller terpercaya di Target.

## 2. Auth policy

| Endpoint | Token/PIN | Controller-Id | Keterangan |
|---|---:|---:|---|
| `GET /ping` | Tidak | Tidak | Discovery terbuka. |
| `GET /pair` | Wajib salah satu valid | Wajib | Lihat alur approval di bawah. |
| `GET /status` | Wajib salah satu valid | Tidak | Cek cepat tanpa approval; dipakai Controller untuk status "Terhubung" yang jujur. |
| `POST /action` | Salah satu valid | Tidak | OR, bukan AND. |
| `POST /gesture` | Salah satu valid | Tidak | OR, bukan AND. |
| `POST /clipboard` | Salah satu valid | Tidak | OR, bukan AND. |
| `GET /screenshot` | Salah satu valid | Tidak | OR, bukan AND. |

Token yang valid adalah token yang sedang tersimpan pada Target. Regenerasi token langsung membuat token lama tidak valid. PIN hanya valid jika sudah diatur melalui Target screen.

Trust (lihat `TrustedControllerStore`) hanya menggerbangi `/pair` — begitu sebuah `X-Controller-Id` disetujui, `/pair` berikutnya dari id yang sama langsung sukses tanpa approval lagi. Endpoint kontrol nyata (`/action`, `/gesture`, `/clipboard`, `/screenshot`, `/status`) tetap digerbangi murni oleh Token/PIN seperti sebelumnya; mengganti PIN di Target langsung membuat Controller lama gagal di endpoint-endpoint ini walau `X-Controller-Id`-nya masih tercatat trusted.

## 3. Endpoint

### `GET /ping`

Dipakai scanner untuk menemukan Target.

Response sukses:

```text
HTTP/1.1 200 OK
ControlDroid
```

Endpoint ini sengaja tidak memakai auth supaya discovery dapat dilakukan sebelum pairing. Jangan menambahkan data sensitif ke response `/ping`.

### `GET /pair`

Memvalidasi access token (QR) atau PIN (scan subnet legacy), lalu — jika `X-Controller-Id` belum pernah disetujui — menahan koneksi ini (blocking, di thread NanoHTTPD milik koneksi tersebut saja, tidak memblokir request lain) sampai user menekan Terima/Tolak di dialog "Permintaan pairing" pada Target, atau timeout.

Request (QR):

```http
GET /pair HTTP/1.1
Host: 192.168.1.20:8080
X-Control-Token: <access-token>
X-Controller-Id: <uuid-controller>
X-Controller-Name: Pixel 3a
```

Request (PIN legacy) memakai `X-Control-Pin` alih-alih `X-Control-Token`, header `X-Controller-Id`/`X-Controller-Name` tetap wajib.

Alur server:

1. Token/PIN tidak valid, atau `X-Controller-Id` tidak dikirim → langsung `401`, tidak ada approval.
2. `X-Controller-Id` sudah ada di `TrustedControllerStore` → langsung `200`, tanpa dialog (inilah yang membuat Controller yang sama tidak perlu pairing ulang).
3. `X-Controller-Id` baru → tampilkan dialog approval di Target (lihat `PairingApprovalGate`), tunggu maksimum 45 detik:
   - Ditekan **Terima** → id disimpan ke `TrustedControllerStore`, response `200`.
   - Ditekan **Tolak**, atau 45 detik lewat tanpa respons → response `401`.

Karena approval bisa menahan koneksi hingga puluhan detik, client (`DeviceHttpClient.verifyPairing`/`verifyPin`) memakai `OkHttpClient` terpisah dengan timeout 50 detik — jangan pakai timeout pendek biasa (3 detik) untuk memanggil endpoint ini.

Response disetujui/sudah trusted:

```json
{"name":"ControlDroid"}
```

Response ditolak/timeout/kredensial salah:

```text
401 Unauthorized
Pairing ditolak atau tidak dikonfirmasi tepat waktu di Target
```

(atau body `Unauthorized` polos jika kredensial/`X-Controller-Id` yang gagal, bukan keputusan user).

### `GET /status`

Cek cepat (tanpa approval, tanpa `X-Controller-Id`) apakah Token/PIN yang tersimpan pada `PairedDevice` saat ini masih valid — dipakai `DeviceControlScreen` untuk menampilkan status "Terhubung" yang benar-benar mencerminkan kredensial, bukan sekadar `/ping` yang cuma membuktikan server menyala.

Request:

```http
GET /status HTTP/1.1
Host: 192.168.1.20:8080
X-Control-Pin: 1234
```

Response valid: `200` `{"name":"ControlDroid"}`. Response tidak valid: `401` `Unauthorized`. Endpoint ini tidak pernah memicu dialog approval — Controller yang kredensialnya sudah tidak valid (mis. PIN diganti di Target) akan langsung dilaporkan sebagai tidak terhubung, bukan digantung menunggu approval.

### `POST /action`

Mengirim command navigasi atau capture screen.

Request contoh:

```http
POST /action HTTP/1.1
Content-Type: text/plain
X-Control-Token: <access-token>

global_home
```

Command yang didefinisikan oleh `DeviceAction`:

| Command | Efek |
|---|---|
| `global_back` | Accessibility global Back |
| `global_home` | Accessibility global Home |
| `global_recent` | Accessibility global Recent |
| `capture_screen` | Capture sekali dan simpan frame jika MediaProjection siap |

Response credential valid:

```text
200 OK
OK
```

Perhatian: response `OK` menunjukkan request diterima server, bukan jaminan bahwa AccessibilityService atau capture hardware berhasil. Hasil capability ditentukan oleh service state dan log device.

### `POST /gesture`

Body memakai `GestureRequest`:

```json
{
  "type":"TAP",
  "startX":0.5,
  "startY":0.4,
  "endX":0.5,
  "endY":0.4,
  "durationMs":80
}
```

Untuk swipe:

```json
{
  "type":"SWIPE",
  "startX":0.2,
  "startY":0.8,
  "endX":0.8,
  "endY":0.2,
  "durationMs":350
}
```

Koordinat diharapkan ternormalisasi `0..1`. Target melakukan clamp sebelum mengubahnya menjadi pixel. Durasi dibatasi `1..5000` ms oleh `AccessibilityController`.

### `POST /clipboard`

Body:

```json
{
  "text":"Teks dari controller",
  "paste":false
}
```

Jika `paste=true`, Target mengatur clipboard lalu mencoba `ACTION_PASTE` pada input yang sedang fokus. Response sukses tidak menjamin ada field editable yang fokus.

### `GET /screenshot`

Mengambil file `cacheDir/screenshot.png` terakhir.

Response frame tersedia:

```text
200 OK
Content-Type: image/png
<binary PNG>
```

Jika belum ada frame:

```text
404 Not Found
No Screenshot
```

Client melakukan polling sekitar setiap 2 detik. API ini bukan video streaming.

## 4. Error dan status

| Status | Arti |
|---:|---|
| `200` | Request diterima atau data tersedia. Capability hardware tetap perlu dicek. |
| `401` | Token/PIN tidak valid atau tidak tersedia. |
| `404` | Endpoint tidak dikenal atau screenshot belum tersedia. |
| `500` | Context server belum tersedia. |
| Network failure | IP/port tidak reachable, firewall, Wi-Fi isolation, atau server belum berjalan. |

## 5. Perubahan API

Jika mengubah endpoint, header, command, atau model JSON:

1. Update dokumen ini.
2. Update model di `:core`.
3. Update client dan server secara berpasangan.
4. Tambahkan/update contract test dengan MockWebServer atau test server.
5. Jalankan `./gradlew test lint :app:assembleDebug`.
6. Lakukan acceptance test dua device sebelum menyebut fitur selesai.
