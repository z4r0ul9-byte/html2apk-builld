package com.html2apk.builder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity riwayat build.
 *
 * `status` berisi "SUCCESS" atau "FAILED".
 * `apkPath` kosong ("") jika status FAILED.
 */
@Entity(tableName = "build_history")
data class BuildHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val apkPath: String,
    val timestamp: Long,
    val status: String
)
