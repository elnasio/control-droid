# Dokumentasi ControlDroid

Dokumentasi ini melengkapi [README utama](../README.md) dan menjadi referensi operasional untuk pengembangan, pengujian, serta troubleshooting ControlDroid.

## Dokumen

| Dokumen | Isi |
|---|---|
| [Architecture](architecture.md) | Modul, dependency direction, lifecycle Controller/Target, navigation, persistence, dan data flow. |
| [HTTP API](http-api.md) | Kontrak endpoint lokal, header autentikasi, payload, response, dan batasan protokol. |
| [Operations](operations.md) | Setup developer, setup dua perangkat, pairing, permission, penggunaan fitur, dan checklist acceptance. |
| [Testing](testing.md) | Test inventory, command Gradle, coverage yang terbukti, serta validasi yang masih memerlukan device. |
| [Troubleshooting](troubleshooting.md) | Diagnosis masalah build, pairing, jaringan, Accessibility, screen capture, clipboard, preview, dan UI. |
| [UI Guidelines](ui-guidelines.md) | Aturan redesign Material 3, reusable component, preview, inset, accessibility, dan responsive layout. |
| [Feature delivery log](2026-09-19-remote-control-features.md) | Catatan perubahan fitur, modularisasi, testing, dan redesign pada 19 September 2026. |

## Cara membaca

- Kontributor baru sebaiknya membaca `README.md`, lalu `architecture.md` dan `operations.md`.
- Perubahan endpoint atau autentikasi wajib memperbarui `http-api.md` dan test contract di `core/src/test`.
- Perubahan screen atau reusable component wajib mengikuti `ui-guidelines.md` dan menambahkan/menyesuaikan `@Preview`.
- Jika hasil verifikasi hanya berasal dari build atau unit test, jangan menyebutnya sebagai bukti runtime device. Gunakan matriks di `testing.md` untuk membedakan keduanya.
