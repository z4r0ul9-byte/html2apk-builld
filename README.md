# HTML2APK Builder

Project Android berbasis **Jetpack Compose (Material 3)** untuk membangun aplikasi Android dari file HTML/ZIP.

> **Status: Level 1 — UI & Pipeline.**
> Pada tahap ini hanya tersedia kerangka UI dan pipeline build APK.
> Logika repack APK, signing, ARSCLib, Room, Hilt, dan cloud fallback
> **belum diimplementasikan** dan akan ditambahkan pada level berikutnya.

## Fitur Level 1

- Tombol **Pilih File HTML/ZIP** menggunakan `ActivityResultContracts.GetContent()`.
- Menampilkan nama file yang dipilih.
- Tombol **Mulai Build** (aktif setelah file dipilih) yang menampilkan Toast:
  `"Fitur build akan diimplementasikan di Level 2"`.
- Pipeline GitHub Actions untuk compile `assembleDebug` dan upload artefak APK.

## Spesifikasi Teknis

| Item | Nilai |
|------|-------|
| Bahasa | Kotlin 1.9.22 |
| JVM Target | 17 |
| Compose BOM | 2024.02.00 |
| Material | Material 3 |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| Gradle | 8.5 (Kotlin DSL) |
| AGP | 8.2.2 |

## Build via GitHub Actions

1. Push project ini ke repository GitHub (branch `main`).
2. Buka tab **Actions** → workflow **Build Builder APK**.
3. Workflow berjalan otomatis saat push/PR, atau jalankan manual lewat
   tombol **Run workflow** (`workflow_dispatch`).
4. Setelah selesai, unduh artefak **`html2apk-builder-debug`** dari halaman run.
5. Ekstrak artefak untuk mendapatkan `app-debug.apk`.

### Detail Workflow

- Runner: `ubuntu-latest`
- JDK: Temurin 17
- Gradle: 8.5 (disediakan oleh `gradle/actions/setup-gradle@v3`)
- Perintah: `gradle :app:assembleDebug --no-daemon`
- Artefak: `app/build/outputs/apk/debug/*.apk`

## Build Lokal (Opsional)

Tanpa Gradle Wrapper, gunakan Gradle 8.5 yang terpasang di sistem:

```bash
gradle :app:assembleDebug
