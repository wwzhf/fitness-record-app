package com.wc.workout.data.repository

import androidx.room.withTransaction
import com.wc.workout.data.backup.BackupData
import com.wc.workout.data.backup.ExerciseBackup
import com.wc.workout.data.backup.SessionBackup
import com.wc.workout.data.backup.SetBackup
import com.wc.workout.data.backup.WeightBackup
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ImportSummary(val weights: Int, val exercises: Int, val sessions: Int, val sets: Int)

class BackupRepository(private val db: AppDatabase) {
    private val weightDao = db.weightDao()
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun export(): String {
        val exercises = exerciseDao.getAll()
        val sessions = workoutDao.getAllSessions()
        val data = BackupData(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            weights = weightDao.getAll().map {
                WeightBackup(it.dateEpochDay, it.weightKg, it.recordedAt)
            },
            exercises = exercises.map { ExerciseBackup(it.name, it.createdAt, it.isArchived) },
            sessions = sessions.map { SessionBackup(it.title, it.startTime, it.endTime) },
            sets = workoutDao.getAllSets().mapNotNull { s ->
                val si = sessions.indexOfFirst { it.id == s.sessionId }.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                val ei = exercises.indexOfFirst { it.id == s.exerciseId }.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                SetBackup(si, ei, s.weightKg, s.reps, s.exerciseOrder, s.setOrder)
            }
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** 按日期覆盖合并：文件涉及的日期覆盖当天数据，其余保留；单事务，任一步失败整体回滚 */
    suspend fun import(jsonText: String): ImportSummary = db.withTransaction {
        val data = json.decodeFromString(BackupData.serializer(), jsonText)
        check(data.schemaVersion <= SCHEMA_VERSION) { "备份版本过新（${data.schemaVersion}），请先升级 app" }

        // 动作：按名称匹配则覆盖元信息（保留本地 id），否则新增
        val exerciseIds = data.exercises.map { eb ->
            val existing = exerciseDao.findByName(eb.name)
            if (existing == null) {
                exerciseDao.insert(Exercise(name = eb.name, createdAt = eb.createdAt, isArchived = eb.isArchived))
            } else {
                exerciseDao.overwriteMeta(existing.id, eb.createdAt, eb.isArchived)
                existing.id
            }
        }

        // 体重：按日期 upsert，同一天以文件为准
        data.weights.forEach { w ->
            upsertWeightByDate(
                weightDao,
                com.wc.workout.data.local.WeightRecord(
                    dateEpochDay = w.dateEpochDay, weightKg = w.weightKg, recordedAt = w.recordedAt
                )
            )
        }

        // 训练/组：按 startTime 所在日历日分组，当天先删后插（级联删组），其余日期保留
        data.sets.forEach { bs ->
            require(bs.sessionIndex in data.sessions.indices) { "备份文件损坏：sessionIndex 越界" }
        }
        val zone = ZoneId.systemDefault()
        val setsBySession = data.sets.groupBy { it.sessionIndex }
        val byDay = sortedMapOf<LocalDate, MutableList<Pair<Int, SessionBackup>>>()
        data.sessions.forEachIndexed { si, sb ->
            val localDate = Instant.ofEpochMilli(sb.startTime).atZone(zone).toLocalDate()
            byDay.getOrPut(localDate) { mutableListOf() }.add(si to sb)
        }
        val sessionIds = mutableMapOf<Int, Long>()
        for ((localDate, entries) in byDay) {
            val dayStart = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            workoutDao.deleteSessionsBetween(dayStart, dayEnd)
            for ((si, sb) in entries) {
                val newId = workoutDao.insertSession(
                    WorkoutSession(title = sb.title, startTime = sb.startTime, endTime = sb.endTime)
                )
                sessionIds[si] = newId
                for (bs in setsBySession[si].orEmpty()) {
                    require(bs.exerciseIndex in exerciseIds.indices) { "备份文件损坏：exerciseIndex 越界" }
                    workoutDao.insertSet(
                        WorkoutSet(
                            sessionId = newId,
                            exerciseId = exerciseIds[bs.exerciseIndex],
                            weightKg = bs.weightKg, reps = bs.reps,
                            exerciseOrder = bs.exerciseOrder, setOrder = bs.setOrder
                        )
                    )
                }
            }
        }

        ImportSummary(data.weights.size, data.exercises.size, data.sessions.size, data.sets.size)
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
