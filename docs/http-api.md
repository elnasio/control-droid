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

## 2. Auth policy

| Endpoint | Token | PIN | Keterangan |
|---|---:|---:|---|
| `GET /ping` | Tidak | Tidak | Discovery terbuka. |
| `GET /pair` | Wajib valid | Tidak diterima | Hanya token dari QR. |
| `POST /action` | Salah satu valid | Salah satu valid | OR, bukan AND. |
| `POST /gesture` | Salah satu valid | Salah satu valid | OR, bukan AND. |
| `POST /clipboard` | Salah satu valid | Salah satu valid | OR, bukan AND. |
| `GET /screenshot` | Salah satu valid | Salah satu valid | OR, bukan AND. |

Token yang valid adalah token yang sedang tersimpan pada Target. Regenerasi token langsung membuat token lama tidak valid. PIN hanya valid jika sudah diatur melalui Target screen.

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

Memvalidasi access token dari QR.

Request:

```http
GET /pair HTTP/1.1
Host: 192.168.1.20:8080
X-Control-Token: <access-token>
```

Response valid:

```json
{"name":"ControlDroid"}
```

Response token invalid:

```text
401 Unauthorized
Unauthorized
```

PIN legacy tidak dapat digunakan untuk endpoint pairing QR.

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
