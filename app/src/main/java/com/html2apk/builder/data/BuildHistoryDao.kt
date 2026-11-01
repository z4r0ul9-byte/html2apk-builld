package com.html2apk.builder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildHistoryDao {

    @Insert
    suspend fun insertHistory(history: BuildHistory)

    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BuildHistory>>
}
