package com.html2apk.builder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildHistoryDao {

    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BuildHistory>>

    @Insert
    suspend fun insertHistory(history: BuildHistory): Long

    /**
     * Ambil versionCode terakhir yang sukses untuk package tertentu.
     * Dipakai untuk fitur auto-increment versionCode.
     */
    @Query("SELECT MAX(versionCode) FROM build_history WHERE packageName = :packageName AND status = 'SUCCESS'")
    suspend fun getLatestVersionCode(packageName: String): Int?
}
