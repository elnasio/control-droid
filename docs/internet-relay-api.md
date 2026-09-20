# HTTP API Relay Internet (kontrak Controller, backend belum dibangun)

## 1. Status

Dokumen ini mendefinisikan kontrak yang sudah diimplementasikan `InternetRelayClient`
(`core/networking/InternetRelayClient.kt`) dari sisi app. **Belum ada backend yang mengimplementasikan
kontrak ini.** Client-nya sudah "siap disambungkan" (*ready to wire*): begitu ada layanan relay
nyata di suatu base URL HTTPS, cukup arahkan `ConstantValue.INTERNET_RELAY_BASE_URL` ke sana (atau
suntikkan `baseUrl` lain ke `InternetRelayClient`), kontrol via Internet langsung berfungsi tanpa
perubahan kode app lagi.

Sebelum itu ada, setiap panggilan relay akan gagal dengan network/connection error, yang oleh UI
Controller sudah diperlakukan sama seperti Target Wi-Fi yang tidak reachable: `checkStatus`
melaporkan `false`, dan `DeviceControlScreen` menampilkan "Tidak terhubung" dengan ikon toolbar
merah.

Untuk requirement backend yang lebih lengkap (arsitektur koneksi Target↔backend, model data,
keamanan, dsb — bukan cuma kontrak HTTP sisi Controller ini), lihat `docs/backend-requirements.md`.

**Catatan**: dokumen ini menjelaskan kontrak yang **saat ini sudah diimplementasikan**
`InternetRelayClient` apa adanya, termasuk kebijakan kredensial di §4 yang sudah final
(token-only — lihat `docs/backend-requirements.md` §1.1). Bagian lain di luar kredensial (mis.
routing key device, model akun) masih bisa berubah — lihat keputusan terbuka yang tersisa di
`docs/backend-requirements.md` §8.

## 2. Kenapa bentuknya begini

Kontrak Wi-Fi lokal (`docs/http-api.md`) mengalamatkan Target langsung lewat IP, yang hanya
berfungsi pada jaringan yang sama. Kontrak relay ini mempertahankan kosakata perintah yang sama
(`DeviceAction`, `GestureRequest`, `ClipboardRequest`, byte screenshot) tapi merutekan setiap
panggilan lewat path backend yang dikunci oleh id device yang stabil, bukan IP — jadi Controller
dan Target tidak perlu lagi berada di jaringan yang sama. Asumsinya, backend memegang koneksi
persisten ke Target (mis. WebSocket atau long-poll) dan meneruskan request ke sana.

Kunci routing: path memakai `PairedDevice.id` (UUID lokal yang sama yang sudah dipakai
`PairedDeviceStore`), bukan identifier baru. Ini asumsi sementara — kalau backend pairing nyata
nanti menerbitkan routing id terpisah, cukup ubah `InternetRelayClient.authorizedRequest`; bagian
app lain tidak terpengaruh karena semua pemanggil hanya melihat interface `DeviceControlClient`.

Kontrak ini juga mengasumsikan `PairedDevice` (berikut `accessToken`-nya, lihat §4) sudah ada dari
pairing lokal lewat Wi-Fi sebelum toggle "Kontrol via Internet" pernah dipakai — lihat
`docs/backend-requirements.md` §2.1. Kontrak di sini tidak mencakup skenario pairing yang murni
terjadi lewat internet.

## 3. Base URL dan versioning

```
{baseUrl}/v1/devices/{deviceId}/...
```

`baseUrl` default ke `ConstantValue.INTERNET_RELAY_BASE_URL`, saat ini placeholder
`https://relay.controldroid.example` (TLD `.example` memang dicadangkan untuk dokumentasi, RFC
2606, sehingga tidak akan pernah bisa resolve ke host sungguhan). Ganti konstanta ini — atau
suntikkan `baseUrl` lain — begitu relay nyata sudah di-deploy.

## 4. Header autentikasi

Dikirim di setiap request:

| Header | Nilai | Catatan |
|---|---|---|
| `Authorization` | `Bearer {accessToken}` | Satu-satunya kredensial yang diterima relay. Hanya dikirim kalau `PairedDevice.accessToken` tidak kosong. |

**Beda dari kontrak lokal, disengaja**: `InternetRelayClient` **tidak pernah** mengirim
`X-Control-Pin` ke relay, walaupun `TargetHttpServer.hasValidCredentials()` di Wi-Fi lokal
menerima PIN ATAU token. PIN 4-digit yang cukup aman di dalam batas kepercayaan jaringan lokal
jadi rawan brute-force sebagai kredensial internet-facing — lihat alasan lengkap di
`docs/backend-requirements.md` §1.1. Backend **wajib** menolak (`401`) request relay yang tidak
membawa `Authorization` valid, meski PIN turut dikirim.

Konsekuensi: device yang dipasangkan lewat alur PIN legacy saja (`accessToken` kosong, tidak
pernah scan QR) tidak bisa memakai mode Internet. `DeviceControlScreen` menonaktifkan toggle
"Kontrol via Internet" untuk kasus ini dan menjelaskan ke user bahwa perlu pairing ulang lewat QR.

## 5. Endpoint

### `GET /v1/devices/{deviceId}/status`

Pure credential/reachability check, mencerminkan `/status` lokal. Tanpa body.

- `200` — backend punya koneksi hidup ke Target ini dan kredensial valid.
- `401` — kredensial tidak valid.
- `404` / `502` / `503` — Target sedang tidak terhubung ke backend, atau backend tidak reachable.

Response non-2xx apa pun, atau kegagalan koneksi, dilaporkan ke UI sebagai "Tidak terhubung".

### `POST /v1/devices/{deviceId}/action`

Body (JSON — berbeda dari `/action` API lokal yang raw-text, dipilih JSON di sini supaya konsisten
dengan endpoint relay lain yang semuanya JSON):

```json
{ "command": "global_back" }
```

`command` adalah `DeviceAction.command` (`global_back`, `global_home`, `global_recent`,
`capture_screen`). Response: `200` sukses, `401` kredensial salah, `404`/`502`/`503` kalau Target
tidak reachable lewat backend.

### `POST /v1/devices/{deviceId}/gesture`

Body: `GestureRequest` di-serialize apa adanya oleh Gson, bentuknya identik dengan body `/gesture`
lokal:

```json
{ "type": "TAP", "startX": 0.5, "startY": 0.5, "endX": 0.5, "endY": 0.5, "durationMs": 80 }
```

### `POST /v1/devices/{deviceId}/clipboard`

Body: `ClipboardRequest`, bentuknya identik dengan body `/clipboard` lokal:

```json
{ "text": "hello", "paste": false }
```

### `GET /v1/devices/{deviceId}/screenshot`

Mengembalikan screenshot terbaru Target sebagai raw PNG bytes, semantik identik dengan
`/screenshot` lokal: `200` dengan body PNG, `404` kalau Target belum pernah menghasilkan frame,
atau status error kalau tidak reachable.

## 6. Timeout

`InternetRelayClient` memakai OkHttp call timeout 8 detik (dibanding 3 detik milik
`DeviceHttpClient` untuk panggilan lokal), karena satu hop relay menambah latensi di atas waktu
respons Target sendiri.

## 7. Titik integrasi sisi client

`DeviceControlScreen` menyimpan satu `DeviceHttpClient` dan satu `InternetRelayClient` per paired
device, lalu memilih salah satunya sesuai toggle "Kontrol via Internet"
(`core/model/ControlTransportMode`) sebagai satu-satunya `DeviceControlClient` yang dipakai untuk
semua panggilan aksi, gesture, clipboard, dan live-preview di screen itu. `RemotePreviewViewModel`
transport-agnostic — hanya bergantung pada `DeviceControlClient.fetchScreenshot()`, jadi live
preview berjalan di transport manapun tanpa perubahan begitu polling di-(re)start lewat
`RemotePreviewEvent.StartPolling`.

## 8. Yang masih butuh backend nyata

- Benar-benar menerima koneksi dari Target (mis. WebSocket persisten) dan meneruskan panggilan
  HTTP di atas ke sana.
- Menentukan routing key device yang sesungguhnya (lihat §2) dan bagaimana itu dipertukarkan saat
  pairing.
- Rate limiting, terminasi TLS, dan model akun/kepemilikan untuk menentukan Controller mana yang
  boleh menjangkau Target mana.
- Proteksi replay attack (nonce/timestamp/idempotency key) — belum ada di kontrak ini, jadi
  murni tanggung jawab backend, bukan sesuatu yang perlu dikirim Controller hari ini.
- Mekanisme supaya "Hapus akses" Controller di Target (lokal) benar-benar memutus sesi relay yang
  sedang berjalan, bukan cuma lokal.
- Pemberitahuan/consent eksplisit ke user Target bahwa mode Internet merutekan layar dan clipboard
  lewat server pihak ketiga — ini keputusan produk, bukan bagian dari kontrak HTTP ini.

Detail masing-masing poin, plus checklist penerimaan backend dan protokol sisi Target yang juga
belum diimplementasikan, ada di `docs/backend-requirements.md`. Tidak ada satupun dari ini yang
diimplementasikan di sisi client selain kontrak di atas; `InternetRelayClient` adalah HTTP client
tipis terhadap kontrak ini, strukturnya identik dengan `DeviceHttpClient`.
