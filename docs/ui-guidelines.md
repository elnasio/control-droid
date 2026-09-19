# UI Guidelines ControlDroid

## 1. Tujuan

UI harus membuat user memahami tiga hal dengan cepat:

1. Perangkat atau mode apa yang sedang aktif.
2. Apakah koneksi/permission siap.
3. Aksi utama berikutnya yang harus dilakukan.

Gunakan Material 3 yang sudah menjadi baseline project. Hindari layout padat, tombol kecil tanpa konteks, dan status yang hanya disampaikan lewat warna.

## 2. Screen structure

Screen dengan navigation hierarchy memakai pola:

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
        AppToolbar(
            title = "Screen title",
            onBackClick = onBackClick
        )
    }
) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // content
    }
}
```

Root `MainActivity` sudah menerapkan edge-to-edge inset. Jangan menggandakan padding system bar.

## 3. Reusable components

### `AppToolbar`

- Gunakan untuk semua screen yang bisa kembali atau memiliki hierarchy.
- `onBackClick` menampilkan arrow back standar.
- `navigationIcon` boleh dipakai untuk override khusus.
- Actions harus icon button dengan content description jika action bersifat icon-only.

### `DeviceCard`

- Menampilkan nama dan IP.
- Seluruh kartu clickable.
- Jangan menaruh network call langsung di dalam card.
- Callback harus disediakan oleh screen/container.

### `StatusBadge`

- Gunakan untuk status koneksi, PIN, polling, atau readiness.
- Jangan hanya mengandalkan warna; label harus menjelaskan status.
- `active=true` hanya jika ada bukti state aktif dari source of truth.

### `ControlActionButton`

- Gunakan untuk aksi kontrol yang berulang.
- Beri label yang jelas dan konsisten.
- Gunakan `enabled` saat capability atau koneksi belum siap.
- Gunakan `modifier` untuk full-width/weight sesuai layout.

### Dialog dan preview surface

- `PinDialog` hanya mengelola input dan callback, bukan menyimpan PIN.
- `RemotePreviewSurface` hanya mengelola render image dan gesture normalization, bukan HTTP.
- `PairingQrCard` hanya menampilkan bitmap dan callback regenerate.

## 4. Spacing dan hierarchy

- Gunakan kelipatan 4/8 dp untuk spacing.
- Padding screen umum: 16 dp horizontal, 16–24 dp vertical.
- Jarak antar section: 16 dp.
- Padding card: 16–20 dp.
- Gunakan `headlineSmall` untuk judul screen, `titleLarge` untuk judul card, dan `bodyMedium` untuk penjelasan.
- Letakkan primary action setelah penjelasan yang menjelaskan dampaknya.

## 5. State UI wajib

Screen yang melakukan network, scan, polling, atau permission harus memikirkan:

- Initial/loading state.
- Success/ready state.
- Empty state.
- Error state dengan bahasa user-friendly.
- Disabled state ketika prerequisite belum siap.
- Retry atau jalur pemulihan jika operasi bisa diulang.

Contoh mapping:

| Kondisi | UI |
|---|---|
| Tidak ada paired device | Empty card + tombol Tambah perangkat. |
| Scan berjalan | Progress indicator + teks penjelasan. |
| Device unreachable | Status badge nonaktif + tombol terblokir/feedback error. |
| Screenshot belum tersedia | Empty/loading card, bukan layar kosong. |
| PIN belum diatur | Status badge + CTA Atur PIN. |

## 6. Accessibility

- Semua icon-only button wajib memiliki `contentDescription`.
- Icon dekoratif memakai `contentDescription = null`.
- Jangan menyampaikan status penting hanya lewat warna.
- Pastikan teks tombol mengandung kata kerja jelas: `Scan QR pairing`, `Buka live preview`, `Kirim dan tempel`.
- Ukuran target touch mengikuti default Material button/icon button.
- Periksa screen dengan font besar dan TalkBack jika ada device.

## 7. Preview requirement

Setiap public composable di `:ui-components` wajib memiliki preview dalam file yang sama atau file preview khusus yang dekat dengan implementasi.

Preview harus:

- Memakai `ControldroidTheme(dynamicColor = false)` agar deterministik.
- Menyediakan callback no-op yang valid.
- Menyediakan sample data yang realistis.
- Menampilkan state default yang paling penting.
- Tidak menjalankan network, SharedPreferences, camera, AccessibilityService, atau MediaProjection.

## 8. Testing UI component

Komponen dengan interaction penting perlu Compose smoke test untuk:

- Render label/title.
- Click callback.
- Enabled/disabled state jika relevan.
- Content description untuk icon action.

Screen feature test tidak boleh dipindahkan ke `:ui-components` hanya untuk memudahkan test; logic screen tetap dimiliki `:app`.
