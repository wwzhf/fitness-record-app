package com.wc.workout.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int,
    val exportedAt: Long,
    val weights: List<WeightBackup>,
    val exercises: List<ExerciseBackup>,
    val sessions: List<SessionBackup>,
    val sets: List<SetBackup>
)

@Serializable
data class WeightBackup(val dateEpochDay: Long, val weightKg: Double, val recordedAt: Long)

@Serializable
data class ExerciseBackup(val name: String, val createdAt: Long, val isArchived: Boolean)

@Serializable
data class SessionBackup(val title: String, val startTime: Long, val endTime: Long?)

@Serializable
data class SetBackup(
    val sessionIndex: Int,
    val exerciseIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val exerciseOrder: Int,
    val setOrder: Int
)
