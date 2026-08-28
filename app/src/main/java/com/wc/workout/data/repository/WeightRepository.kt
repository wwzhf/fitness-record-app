package com.wc.workout.data.repository

import androidx.room.withTransaction
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.WeightDao
import com.wc.workout.data.local.WeightRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 按 dateEpochDay 覆盖式保存；BackupRepository 在自己的事务内复用 */
suspend fun upsertWeightByDate(dao: WeightDao, record: WeightRecord) {
    val existing = dao.getByDate(record.dateEpochDay)
    if (existing == null) {
        dao.insert(record)
    } else {
        dao.update(existing.copy(weightKg = record.weightKg, recordedAt = record.recordedAt))
    }
}

class WeightRepository(private val db: AppDatabase) {
    private val dao = db.weightDao()

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<WeightRecord>> =
        dao.observeBetween(start.toEpochDay(), end.toEpochDay())

    fun observeAll(): Flow<List<WeightRecord>> = dao.observeAll()

    suspend fun getByDate(date: LocalDate): WeightRecord? = dao.getByDate(date.toEpochDay())

    suspend fun saveWeight(date: LocalDate, weightKg: Double, recordedAt: Long = System.currentTimeMillis()) {
        db.withTransaction {
            upsertWeightByDate(dao, WeightRecord(dateEpochDay = date.toEpochDay(), weightKg = weightKg, recordedAt = recordedAt))
        }
    }
}
