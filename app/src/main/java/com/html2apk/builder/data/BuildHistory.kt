package com.html2apk.builder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity untuk menyimpan riwayat build.
 *
 * Level 8:
 * - Tambah `versionCode` & `versionName` agar auto-increment bisa
 *   membaca versi terakhir dari Room Database.
 */
@Entity(tableName = "build_history")
data class BuildHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val apkPath: String,
    val timestamp: Long,
    val status: String, // "SUCCESS" atau "FAILED"
    val versionCode: Int = 1,
    val versionName: String = "1.0"
)
