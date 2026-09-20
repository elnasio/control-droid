# Requirement Backend Relay Internet ControlDroid

## 1. Tujuan dokumen

Dokumen ini adalah spesifikasi kebutuhan untuk tim/pihak yang akan membangun **backend relay
internet** ControlDroid — bagian yang saat ini **sama sekali belum ada**. Ini bukan dokumen
kontrak HTTP (itu ada di `docs/internet-relay-api.md`, dan sudah diimplementasikan penuh di sisi
Controller lewat `InternetRelayClient`); dokumen ini menjelaskan **apa yang harus dibangun di
sisi server** supaya kontrak tersebut benar-benar berfungsi, plus bagian yang belum pernah
dispesifikasikan sama sekali: protokol antara Target dan backend.

Status implementasi hari ini:

| Komponen | Status |
|---|---|
| Controller → Backend (HTTP client) | **Sudah ada** — `InternetRelayClient`, lihat `docs/internet-relay-api.md`. |
| Backend | **Belum ada sama sekali.** |
| Target → Backend (koneksi outbound) | **Belum ada sama sekali** — Target app saat ini hanya bisa jadi server lokal (`TargetHttpServer`), tidak punya kode untuk terhubung keluar ke backend manapun. |

## 2. Masalah yang harus diselesaikan backend

Di mode Wi-Fi, Target menjalankan HTTP server (`TargetHttpServer`, NanoHTTPD) dan Controller
terhubung langsung ke IP-nya — keduanya harus berada di jaringan lokal yang sama. Ini tidak bisa
dipertahankan untuk kontrol lewat internet, karena:

- Kedua device kemungkinan besar berada di belakang NAT/carrier-grade NAT (data seluler), sehingga
  tidak bisa saling membuka koneksi masuk begitu saja.
- IP Target bisa berubah kapan saja (device Wi-Fi ke seluler, roaming, dsb).

Karena itu **arah koneksi harus dibalik**: Target harus jadi pihak yang membuka koneksi keluar
(outbound) ke backend dan mempertahankannya tetap hidup, sementara Controller memanggil backend
lewat HTTP request/response biasa (kontrak yang sudah ada). Backend berperan sebagai **relay/
message broker** yang menjembatani dua model komunikasi yang berbeda ini:

```
Controller  --HTTP request/response (sinkron)-->  Backend
                                                      |
                                                      | forward + tunggu balasan
                                                      v
Target      <---------- koneksi persisten (async, mis. WebSocket) ----------> Backend
```

Setiap `POST/GET` dari Controller ke backend harus diterjemahkan backend menjadi satu pesan lewat
koneksi persisten ke Target yang bersangkutan, lalu backend menunggu balasan Target dan baru
menjawab HTTP request Controller yang masih terbuka — pola *request/response over async transport*
dengan timeout, bukan fire-and-forget.

## 3. Kontrak yang sudah ditentukan (sisi Controller)

Backend **wajib** melayani persis kontrak di `docs/internet-relay-api.md`:

- `GET /v1/devices/{deviceId}/status`
- `POST /v1/devices/{deviceId}/action`
- `POST /v1/devices/{deviceId}/gesture`
- `POST /v1/devices/{deviceId}/clipboard`
- `GET /v1/devices/{deviceId}/screenshot`
- Header `Authorization: Bearer {accessToken}` dan/atau `X-Control-Pin: {pin}`.

Ini sudah final dari sisi app Controller (kode client sudah ada dan sudah ada unit test-nya) —
kalau backend memerlukan bentuk lain, ubahnya ada di `InternetRelayClient`, bukan hanya di backend.

## 4. Protokol yang BELUM ditentukan (sisi Target) — perlu dirancang backend

Tidak ada kode Target sama sekali untuk ini hari ini. Backend perlu mendefinisikan dan
mengimplementasikan sisi servernya; app Target akan menambahkan client yang mengikuti spesifikasi
ini nanti. Rekomendasi bentuknya:

### 4.1 Koneksi

- WebSocket (atau setara — MQTT, gRPC bidi-stream) yang dibuka Target saat masuk mode Target dan
  dipertahankan selama app berjalan, mirip `TargetWaitingScreen` menjalankan
  `TargetHttpServer.startServer` hari ini.
- **Handshake**: Target mengirim `deviceId` (`PairedDevice.id`/identitas Target-nya sendiri —
  perlu diputuskan, lihat §8), PIN/token, dan versi app saat connect. Backend memvalidasi lalu
  menandai device itu "online".
- **Heartbeat**: ping/pong berkala (mis. tiap 30 detik) supaya backend tahu koneksi masih hidup
  dan bisa membedakan "Target offline" vs "jaringan lambat".
- **Reconnect**: Target harus reconnect otomatis dengan backoff kalau koneksi putus (device
  masuk background lalu balik, jaringan berpindah Wi-Fi↔seluler, dsb). Backend harus menerima
  device yang sama connect ulang dan menggantikan sesi lama (tidak ada dua sesi valid bersamaan
  untuk `deviceId` yang sama).

### 4.2 Pesan backend → Target (perintah)

Backend meneruskan setiap request Controller sebagai satu pesan ke Target, dengan `requestId`
untuk mengorelasikan balasannya:

```json
{
  "requestId": "uuid",
  "type": "action" | "gesture" | "clipboard" | "screenshot" | "status",
  "payload": { ... }
}
```

`payload` untuk `action`/`gesture`/`clipboard` sama persis dengan body yang sudah didefinisikan di
`docs/internet-relay-api.md` (`{"command": ...}`, `GestureRequest`, `ClipboardRequest`); untuk
`screenshot`/`status` payload boleh kosong.

### 4.3 Pesan Target → backend (balasan)

```json
{
  "requestId": "uuid",
  "success": true,
  "data": null | "<base64 PNG untuk screenshot>"
}
```

Backend mencocokkan `requestId` ke request Controller yang masih menunggu, lalu mengembalikan HTTP
response yang sesuai (lihat §3) ke Controller.

### 4.4 Timeout

Kalau Target tidak membalas dalam batas waktu (disarankan selaras dengan
`InternetRelayClient`'s call timeout, 8 detik, dikurangi overhead jaringan backend↔Controller),
backend mengembalikan `502`/`503` ke Controller — persis kasus "Target tidak reachable" yang sudah
ditangani UI sebagai "Tidak terhubung".

## 5. Requirement fungsional

1. **Device registry & presence** — tabel/state yang memetakan `deviceId` ke sesi koneksi Target
   yang sedang aktif (atau "offline" kalau tidak ada). `GET /status` dan setiap forward request
   bergantung pada ini.
2. **Request/response correlation** — setiap pesan ke Target dan balasannya harus dikorelasikan
   lewat `requestId` dengan timeout per-request, bukan asumsi balasan datang berurutan.
3. **Autentikasi & otorisasi**:
   - Backend memvalidasi `X-Control-Pin`/`Authorization` Controller sama seperti
     `TargetHttpServer.hasValidCredentials()` di lokal (PIN ATAU token, salah satu valid cukup).
   - Backend juga perlu tahu Controller mana yang **boleh** mengontrol `deviceId` tersebut — ini
     setara `TrustedControllerStore` yang saat ini hanya hidup lokal di Target
     (lihat `PairingApprovalGate`/approval Terima-Tolak). Backend perlu versi servernya sendiri:
     entah mereplikasi daftar trusted controller dari Target saat online, atau memakai model akun
     terpisah. **Ini keputusan desain yang belum diambil** — lihat §8.
   - Revocation: kalau user menghapus akses Controller lewat "Hapus akses" di Target (lokal),
     idealnya efeknya juga berlaku ke sesi relay yang sedang berjalan, bukan hanya lokal.
4. **Rate limiting** — batasi frekuensi request per Controller dan per Target untuk mencegah
   penyalahgunaan (mis. spam `/action` atau `/screenshot`).
5. **Screenshot relay** — endpoint `/screenshot` mengembalikan PNG mentah, berpotensi ratusan KB
   per frame, dipoll tiap ~2 detik oleh Controller saat live preview aktif (lihat
   `RemotePreviewViewModel`). Backend harus menangani throughput ini per device aktif tanpa
   membebani koneksi Target↔backend secara berlebihan (pertimbangkan kompresi/resize di sisi
   Target sebelum dikirim, di luar cakupan backend tapi relevan untuk anggaran bandwidth).
6. **Multi-controller** — TrustedControllerStore lokal sudah mendukung banyak Controller
   terpercaya per Target; backend idealnya juga mendukung lebih dari satu Controller memanggil
   `deviceId` yang sama (tidak harus bersamaan, tapi tidak boleh saling mengunci).

## 6. Requirement non-fungsional

- **Keamanan transport**: HTTPS/WSS wajib di semua koneksi (Controller↔backend dan
  Target↔backend); tidak ada cleartext seperti mode Wi-Fi lokal yang memang sengaja mengizinkan
  HTTP untuk jaringan lokal saja (`network_security_config.xml`).
- **Privasi data**: screenshot dan teks clipboard yang lewat backend sangat sensitif. Backend
  **tidak boleh menyimpan/log isi payload ini** — relay murni in-memory/in-transit, tanpa
  persistence, tanpa masuk ke log aplikasi maupun log infrastruktur (mis. access log yang
  mencatat body request).
- **Kredensial**: PIN/token tidak boleh disimpan plaintext kalau backend perlu menyimpannya sama
  sekali (idealnya backend hanya meneruskan kredensial untuk divalidasi Target, tidak menyimpan
  salinan permanen).
- **Latensi**: perintah navigasi (`/action`, `/gesture`) perlu terasa responsif — targetkan round
  trip Controller→backend→Target→backend→Controller di bawah ~1–2 detik pada kondisi jaringan
  normal, supaya pengalaman kontrol tidak terasa jauh lebih lambat dibanding mode Wi-Fi lokal.
- **Skalabilitas**: harus menahan banyak koneksi Target persisten bersamaan (satu per Target
  device yang sedang mode Internet), plus request rate dari Controller-nya masing-masing.
- **Reliability**: koneksi Target yang terputus harus terdeteksi dalam waktu wajar (lewat
  heartbeat) supaya `GET /status` tidak melaporkan "online" secara salah untuk device yang
  sebenarnya sudah putus.
- **Observability**: metrics (device online count, request latency, error rate per endpoint,
  request timeout rate) dan alerting kalau relay down — tanpa melanggar butir privasi di atas.

## 7. Model data minimum yang dibutuhkan

- **Device record**: `deviceId`, status koneksi (online/offline), waktu terakhir terlihat
  (`lastSeenAt`), identitas sesi koneksi aktif saat ini.
- **Kredensial**: cara memvalidasi PIN/token yang dikirim Controller terhadap device yang dituju
  (lihat §5.3 soal siapa yang menjadi sumber kebenaran kredensial ini).
- **Trust/otorisasi Controller↔Target** (kalau modelnya bukan sekadar "siapapun yang punya
  PIN/token boleh coba"): daftar Controller yang diizinkan per Target, setara
  `TrustedControllerStore` lokal.
- **Request korelasi sementara**: `requestId` → Controller HTTP request yang masih menunggu
  (in-memory, berumur pendek sesuai timeout, tidak perlu persisten).

## 8. Keputusan terbuka untuk tim backend

Hal-hal berikut belum diputuskan dan perlu disepakati sebelum/selagi membangun backend — dampaknya
kecil ke app Controller (biasanya cuma ubah `InternetRelayClient`) tapi menentukan desain backend:

1. **Routing key device** — dokumen ini berasumsi memakai `PairedDevice.id` (UUID lokal Controller)
   apa adanya. Kalau backend perlu id routing terpisah (mis. hasil registrasi Target ke backend
   dengan id sendiri), perlu ada langkah pairing tambahan yang menukar/menerbitkan id ini ke
   Controller — belum ada alurnya sama sekali hari ini.
2. **Model akun/kepemilikan** — apakah perlu akun user (login) yang memiliki Target/Controller,
   atau cukup model "siapapun yang tahu PIN/token boleh kontrol" seperti mode Wi-Fi lokal
   sekarang? Ini menentukan apakah perlu sistem auth pengguna penuh atau tidak.
3. **Membangunkan Target yang di-background** — di mode Wi-Fi, `TargetHttpServer` hanya jalan
   selagi `TargetWaitingScreen` terbuka. Untuk mode Internet supaya benar-benar berguna (Target
   tidak harus dibuka terus), kemungkinan perlu push notification (FCM) untuk membangunkan
   Target app dan membuka koneksi ke backend saat ada request masuk — ini fitur besar terpisah,
   belum ada di scope `InternetRelayClient` maupun dokumen ini secara detail.
4. **Kompresi/kualitas screenshot** untuk mode Internet, mengingat bandwidth seluler lebih mahal
   dan tidak stabil dibanding Wi-Fi lokal.

## 9. Di luar cakupan (non-goals)

- Video streaming real-time berkualitas tinggi (MJPEG/WebRTC) — kontrak saat ini tetap polling PNG
  seperti mode lokal, bukan video stream.
- Kontrol lintas-platform (iOS, desktop) — backend ini hanya perlu melayani app Android yang sudah
  ada.
- Dashboard admin/manajemen device — di luar kebutuhan fungsional app saat ini.

## 10. Referensi

- `docs/internet-relay-api.md` — kontrak HTTP Controller↔backend yang sudah diimplementasikan dan
  wajib dipatuhi backend.
- `docs/http-api.md` — kontrak HTTP lokal Wi-Fi yang jadi acuan kosakata perintah/model data
  (`DeviceAction`, `GestureRequest`, `ClipboardRequest`) yang dipertahankan sama di kontrak relay.
- `docs/architecture.md` bagian "Pairing approval" — model trust/approval lokal (`TrustedControllerStore`,
  `PairingApprovalGate`) yang jadi acuan kalau backend butuh model otorisasi setara secara online.
