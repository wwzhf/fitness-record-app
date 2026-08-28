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

    /** 单事务导入，任一步失败整体回滚。合并策略见 spec 第 10 节 */
    suspend fun import(jsonText: String): ImportSummary = db.withTransaction {
        val data = json.decodeFromString(BackupData.serializer(), jsonText)
        check(data.schemaVersion <= SCHEMA_VERSION) { "备份版本过新（${data.schemaVersion}），请先升级 app" }

        val referencedExerciseIdx = data.sets.map { it.exerciseIndex }.toSet()
        val exerciseIds = data.exercises.mapIndexed { idx, eb ->
            val existing = exerciseDao.findByName(eb.name)
            val id = existing?.id
                ?: exerciseDao.insert(Exercise(name = eb.name, createdAt = eb.createdAt))
            if (existing != null && existing.isArchived && idx in referencedExerciseIdx) {
                exerciseDao.setArchived(id, false)
            }
            id
        }

        data.weights.forEach { w ->
            upsertWeightByDate(
                weightDao,
                com.wc.workout.data.local.WeightRecord(
                    dateEpochDay = w.dateEpochDay, weightKg = w.weightKg, recordedAt = w.recordedAt
                )
            )
        }

        val sessionIds = data.sessions.map { s ->
            workoutDao.insertSession(
                WorkoutSession(title = s.title, startTime = s.startTime, endTime = s.endTime)
            )
        }

        data.sets.forEach { sb ->
            require(sb.sessionIndex in sessionIds.indices) { "备份文件损坏：sessionIndex 越界" }
            require(sb.exerciseIndex in exerciseIds.indices) { "备份文件损坏：exerciseIndex 越界" }
            workoutDao.insertSet(
                WorkoutSet(
                    sessionId = sessionIds[sb.sessionIndex],
                    exerciseId = exerciseIds[sb.exerciseIndex],
                    weightKg = sb.weightKg, reps = sb.reps,
                    exerciseOrder = sb.exerciseOrder, setOrder = sb.setOrder
                )
            )
        }

        ImportSummary(data.weights.size, data.exercises.size, data.sessions.size, data.sets.size)
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
