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

### 1.1 Keputusan yang sudah diambil

- **Kredensial mode Internet: token-only.** `InternetRelayClient` **tidak pernah** mengirim PIN
  legacy ke relay — hanya `Authorization: Bearer {accessToken}` (dari `PairedDevice.accessToken`,
  hasil pairing QR). PIN 4-digit tetap berlaku seperti biasa untuk mode Wi-Fi lokal
  (`TargetHttpServer`/`DeviceHttpClient` tidak berubah), tapi tidak pernah dikirim ke endpoint
  relay sama sekali. Konsekuensinya: device yang dipasangkan lewat alur PIN legacy saja (tanpa QR,
  sehingga `accessToken` kosong) **tidak bisa** memakai mode Internet sampai dipasangkan ulang
  lewat QR. `DeviceControlScreen` sudah menonaktifkan toggle "Kontrol via Internet" untuk kasus ini
  dengan keterangan yang menjelaskan alasannya ke user.
  - Alasan: PIN pendek yang cukup aman di dalam batas kepercayaan Wi-Fi lokal jadi rawan
    brute-force kalau diterima sebagai kredensial internet-facing (lihat riwayat diskusi di §5.3
    dan §8, item 6, sebelum keputusan ini diambil).
  - Backend **wajib** memvalidasi hanya `Authorization: Bearer` untuk seluruh endpoint relay dan
    menolak (`401`) request tanpa header itu, meski `X-Control-Pin` kebetulan ikut terkirim dari
    client lama/rusak.

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

### 2.1 Asumsi: pairing awal tetap lewat Wi-Fi lokal

Dokumen ini **mengasumsikan** toggle "Kontrol via Internet" hanya muncul untuk device yang sudah
berhasil dipasangkan lewat alur lokal (QR/`PairingApprovalGate`/PIN legacy — lihat
`docs/architecture.md` bagian "Pairing approval"), sehingga `PairedDevice.id`, PIN, dan
`accessToken` sudah ada sebelum mode Internet pernah dipakai. Backend **tidak perlu** mendukung
pairing awal antara dua device yang belum pernah berada di jaringan yang sama.

Kalau ke depannya dibutuhkan pairing murni lewat internet (dua device yang tidak pernah satu
Wi-Fi), itu fitur terpisah yang butuh alur approval baru yang setara `PairingApprovalGate` tapi
dijalankan lewat backend — **belum dirancang di dokumen ini** dan sebaiknya jadi keputusan
eksplisit sebelum dikerjakan (lihat §8.5).

## 3. Kontrak yang sudah ditentukan (sisi Controller)

Backend **wajib** melayani persis kontrak di `docs/internet-relay-api.md`:

- `GET /v1/devices/{deviceId}/status`
- `POST /v1/devices/{deviceId}/action`
- `POST /v1/devices/{deviceId}/gesture`
- `POST /v1/devices/{deviceId}/clipboard`
- `GET /v1/devices/{deviceId}/screenshot`
- Header `Authorization: Bearer {accessToken}` — satu-satunya kredensial yang diterima (lihat
  §1.1); tidak ada `X-Control-Pin` di kontrak relay.

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
   - **Diputuskan (§1.1): token-only.** Backend memvalidasi `Authorization: Bearer {accessToken}`
     saja untuk seluruh endpoint relay. `X-Control-Pin` tidak pernah dikirim `InternetRelayClient`
     dan **wajib ditolak** kalau backend menerimanya tanpa `Authorization` yang valid — beda dari
     `TargetHttpServer.hasValidCredentials()` lokal yang menerima PIN ATAU token.
   - **Kenapa dibatasi**: PIN 4-digit yang cukup aman di dalam batas kepercayaan Wi-Fi lokal jadi
     target brute-force kalau diterima sebagai kredensial internet-facing (di internet, jangkauan
     penyerang tidak dibatasi "siapa yang ada di jaringan yang sama").
   - Backend **tetap wajib** menerapkan rate-limit/lockout pada percobaan autentikasi token yang
     gagal per `deviceId` (terpisah dari rate-limit umum di §4) — token 32-byte jauh lebih sulit
     ditebak dibanding PIN, tapi endpoint autentikasi tetap perlu proteksi brute-force sebagai
     lapisan pertahanan berlapis.
   - Backend juga perlu tahu Controller mana yang **boleh** mengontrol `deviceId` tersebut — ini
     setara `TrustedControllerStore` yang saat ini hanya hidup lokal di Target
     (lihat `PairingApprovalGate`/approval Terima-Tolak). Backend perlu versi servernya sendiri:
     entah mereplikasi daftar trusted controller dari Target saat online, atau memakai model akun
     terpisah. **Ini keputusan desain yang belum diambil** — lihat §8.
   - **Revocation, mekanisme konkret**: kalau user menekan "Hapus akses" di Target (lokal), efeknya
     harus juga memutus sesi relay yang sedang berjalan untuk Controller tersebut, bukan cuma
     lokal. Dua pendekatan yang perlu dipilih backend:
     1. *Push dari Target*: begitu `TrustedControllerStore.revoke()` dipanggil lokal, Target (kalau
        sedang online ke backend) mengirim pesan lewat koneksi WebSocket yang sama
        (`{"type": "revoke", "controllerId": "..."}`) supaya backend langsung menolak request
        Controller itu berikutnya.
     2. *Re-validasi per request*: backend tidak menyimpan cache "trusted" jangka panjang sama
        sekali — setiap request diteruskan ke Target untuk divalidasi ulang (Target yang jadi
        sumber kebenaran final soal siapa yang trusted), sehingga revoke lokal otomatis berlaku
        tanpa mekanisme sinkronisasi tambahan, dengan trade-off menambah satu round-trip.
     Pendekatan 2 lebih sederhana dan tidak butuh state trust duplikat di backend; pendekatan 1
     lebih cepat tapi butuh backend menyimpan cache trust yang bisa basi. Belum diputuskan yang
     mana — lihat §8.7.
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
- **Proteksi replay attack**: request yang di-capture (mis. lewat proxy/MITM di jaringan publik)
  tidak boleh bisa diputar ulang begitu saja untuk memicu aksi yang sama lagi. Backend perlu salah
  satu dari: nonce sekali-pakai per request, timestamp dengan jendela toleransi kecil yang ditolak
  kalau kedaluwarsa, atau memanfaatkan `requestId` (§4.2) sebagai idempotency key sehingga
  `requestId` yang sama tidak pernah dieksekusi dua kali oleh Target.
- **Privasi data**: screenshot dan teks clipboard yang lewat backend sangat sensitif. Backend
  **tidak boleh menyimpan/log isi payload ini** — relay murni in-memory/in-transit, tanpa
  persistence, tanpa masuk ke log aplikasi maupun log infrastruktur (mis. access log yang
  mencatat body request).
- **Kredensial**: PIN/token tidak boleh disimpan plaintext kalau backend perlu menyimpannya sama
  sekali (idealnya backend hanya meneruskan kredensial untuk divalidasi Target, tidak menyimpan
  salinan permanen). Lihat juga §5.3 soal PIN sebagai kredensial internet-facing.
- **Consent dan kepatuhan**: fitur ini merutekan konten layar dan clipboard Target — berpotensi
  berisi data sangat pribadi (pesan, foto, kredensial yang sedang diketik) — lewat server pihak
  ketiga yang secara arsitektur *bisa* melihat isinya sekilas saat relay, meskipun diwajibkan
  tidak menyimpan/log. Ini beda signifikan dari mode Wi-Fi lokal yang murni peer-to-peer tanpa
  perantara. Sebelum mode Internet dirilis ke pengguna nyata:
  - User di sisi Target sebaiknya mendapat pemberitahuan eksplisit (terpisah dari dialog approval
    pairing lokal yang sudah ada) bahwa mengaktifkan mode Internet berarti perintah dan tangkapan
    layar melewati server pihak ketiga, bukan hanya jaringan lokal.
  - Kalau backend dioperasikan pihak lain (bukan pemilik app), perlu ada perjanjian/pernyataan
    privasi yang menyebutkan data apa yang transit lewat backend dan kebijakan no-storage di atas
    secara mengikat, bukan cuma requirement teknis di dokumen internal ini.
  - Ini keputusan produk/legal, bukan cuma teknis — dokumen ini hanya menandai bahwa hal itu
    dibutuhkan, bukan menentukan bentuknya.
- **Latensi**: perintah navigasi (`/action`, `/gesture`) perlu terasa responsif — targetkan round
  trip Controller→backend→Target→backend→Controller di bawah ~1–2 detik pada kondisi jaringan
  normal, supaya pengalaman kontrol tidak terasa jauh lebih lambat dibanding mode Wi-Fi lokal.
- **Skalabilitas**: belum ada target angka resmi dari produk ini (aplikasi belum punya basis
  pengguna nyata untuk mode Internet). Sampai ada angka resmi, backend sebaiknya didesain agar
  gampang di-scale-out secara horizontal (tidak menyimpan state koneksi WebSocket hanya di memori
  satu instance tanpa cara reroute), dengan asumsi kerja awal di kisaran puluhan–ratusan koneksi
  Target bersamaan. **Angka ini eksplisit TBD** — konfirmasi ke pemilik produk sebelum
  commit ke desain yang sulit diubah (mis. arsitektur single-node).
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
5. **Pairing murni lewat internet** (§2.1) — apakah ini akan dibutuhkan di masa depan, dan kalau
   ya, siapa yang merancang alur approval-nya (setara `PairingApprovalGate` tapi lewat backend).
6. ~~**Kebijakan PIN untuk mode Internet**~~ — **sudah diputuskan, lihat §1.1**: token-only, PIN
   legacy ditolak untuk seluruh endpoint relay.
7. **Mekanisme propagasi revoke** (§5.3) — push dari Target saat online vs re-validasi per
   request ke Target. Menentukan apakah backend perlu menyimpan cache trust sama sekali.

## 9. Di luar cakupan (non-goals)

- Video streaming real-time berkualitas tinggi (MJPEG/WebRTC) — kontrak saat ini tetap polling PNG
  seperti mode lokal, bukan video stream.
- Kontrol lintas-platform (iOS, desktop) — backend ini hanya perlu melayani app Android yang sudah
  ada.
- Dashboard admin/manajemen device — di luar kebutuhan fungsional app saat ini.

## 10. Checklist penerimaan backend

Sebelum backend dianggap siap dipakai app Controller yang sudah ada (tanpa mengubah
`InternetRelayClient`), pastikan:

- [ ] Semua endpoint di §3 melayani persis path, method, dan status code sesuai
      `docs/internet-relay-api.md`.
- [ ] `GET /status` mengembalikan `200` hanya kalau backend benar-benar punya koneksi hidup ke
      Target itu (bukan cuma "device pernah dikenal") — lihat §6 poin Reliability.
- [ ] Request ke `deviceId` yang Target-nya sedang offline mengembalikan `502`/`503` dalam batas
      timeout §4.4, bukan menggantung tanpa batas waktu.
- [ ] `Authorization` yang salah/kosong mengembalikan `401` pada seluruh endpoint, bukan hanya
      `/status`; `X-Control-Pin` yang dikirim tanpa `Authorization` valid tetap ditolak `401`
      (§1.1) — token-only benar-benar ditegakkan, bukan cuma didokumentasikan.
- [ ] Rate-limit percobaan autentikasi gagal (§5.3) aktif dan teruji — beberapa kali token salah
      berturut-turut memicu lockout sementara.
- [ ] Revoke Controller di Target (lokal) terbukti memutus akses relay Controller itu dalam waktu
      wajar (bergantung mekanisme yang dipilih di §8.7).
- [ ] Tidak ada payload `/gesture`, `/clipboard`, atau `/screenshot` yang muncul di log aplikasi
      maupun access log infrastruktur (audit manual sebelum go-live).
- [ ] Reconnect Target (matikan-nyalakan jaringan) pulih otomatis tanpa duplikasi sesi untuk
      `deviceId` yang sama.
- [ ] Metrics dasar (online device count, latency, error rate) tersedia di dashboard operasional.

## 11. Referensi

- `docs/internet-relay-api.md` — kontrak HTTP Controller↔backend yang sudah diimplementasikan dan
  wajib dipatuhi backend.
- `docs/http-api.md` — kontrak HTTP lokal Wi-Fi yang jadi acuan kosakata perintah/model data
  (`DeviceAction`, `GestureRequest`, `ClipboardRequest`) yang dipertahankan sama di kontrak relay.
- `docs/architecture.md` bagian "Pairing approval" — model trust/approval lokal (`TrustedControllerStore`,
  `PairingApprovalGate`) yang jadi acuan kalau backend butuh model otorisasi setara secara online.
