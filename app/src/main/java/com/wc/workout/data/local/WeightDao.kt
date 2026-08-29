package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_records WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay")
    fun observeBetween(start: Long, end: Long): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records")
    suspend fun getAll(): List<WeightRecord>

    @Query("SELECT * FROM weight_records WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getByDate(dateEpochDay: Long): WeightRecord?

    @Insert
    suspend fun insert(record: WeightRecord): Long

    @Update
    suspend fun update(record: WeightRecord)

    @Query("DELETE FROM weight_records WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteByDate(dateEpochDay: Long)
}
