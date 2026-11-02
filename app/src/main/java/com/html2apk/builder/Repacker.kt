import java.util.zip.CRC32
// ... (impor lain tetap sama)

class Repacker(private val context: Context) {

    // ... (kode sebelumnya tetap sama)

    /**
     * Menulis entry ZIP tanpa kompresi (STORED) dan memastikan data sejajar 4-byte.
     * Ini adalah kunci perbaikan untuk error "Parse Error" di Android 11+.
     */
    private fun writeStoredAlignedEntry(zipOut: ZipOutputStream, name: String, data: ByteArray, time: Long) {
        val crc = CRC32().apply { update(data) }
        val entry = ZipEntry(name).apply {
            this.time = time
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc.value
        }

        // 1. Dapatkan posisi saat ini di dalam file ZIP.
        //    ZipOutputStream tidak menyediakan cara langsung, jadi kita hitung
        //    berdasarkan ukuran yang sudah ditulis (menggunakan refleksi atau
        //    dengan mengandalkan bahwa output adalah FileOutputStream).
        val currentOffset = getCurrentOffset(zipOut)

        // 2. Tulis header lokal entry.
        zipOut.putNextEntry(entry)

        // 3. Hitung padding yang dibutuhkan.
        //    Offset data = currentOffset (posisi awal header) + ukuran header lokal + nama file.
        //    Ukuran header lokal minimal 30 byte + panjang nama.
        val headerSize = 30 + name.toByteArray(Charsets.UTF_8).size
        val dataOffset = currentOffset + headerSize
        val padding = (4 - (dataOffset % 4)) % 4

        // 4. Tulis padding (byte 0) jika diperlukan.
        if (padding > 0) {
            zipOut.write(ByteArray(padding))
        }

        // 5. Tulis data sebenarnya.
        zipOut.write(data)
        zipOut.closeEntry()
    }

    /**
     * Mendapatkan offset penulisan saat ini dari ZipOutputStream.
     * Ini adalah trik karena ZipOutputStream tidak expose posisi file secara langsung.
     */
    private fun getCurrentOffset(zipOut: ZipOutputStream): Long {
        // Karena kita tahu output adalah FileOutputStream, kita bisa cast dan gunakan channel.
        // Jika tidak, kita perlu membungkusnya atau melacak ukuran secara manual.
        return try {
            val channel = (zipOut as? java.util.zip.ZipOutputStream)?.let {
                // Refleksi untuk mengakses field 'out' (OutputStream) di ZipOutputStream.
                // Ini agak rapuh, tapi bekerja untuk sebagian besar implementasi JDK/Android.
                val outField = java.util.zip.ZipOutputStream::class.java.getDeclaredField("out")
                outField.isAccessible = true
                val outStream = outField.get(zipOut) as? java.io.FileOutputStream
                outStream?.channel?.position() ?: 0L
            } ?: 0L
            channel
        } catch (e: Exception) {
            // Fallback: jika gagal, kembalikan 0 (alignment mungkin tidak sempurna).
            0L
        }
    }
}