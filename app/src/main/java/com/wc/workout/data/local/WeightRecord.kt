package com.wc.workout.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日体重：dateEpochDay 唯一，一天一条，重复录入走 update（见 Repository upsert） */
@Entity(
    tableName = "weight_records",
    indices = [Index(value = ["dateEpochDay"], unique = true)]
)
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Double,
    val recordedAt: Long
)

/** 自定义动作库；有历史记录的动作只归档不删除 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val isArchived: Boolean = false
)

/** 一次健身会话；endTime == null 表示进行中 */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null
)

/** 某会话中某动作的一组记录 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val exerciseOrder: Int,
    val setOrder: Int
)

/** 组记录带动作名（JOIN 查询结果），用于训练详情页 */
data class SetWithExercise(
    @Embedded val set: WorkoutSet,
    @ColumnInfo(name = "exerciseName") val exerciseName: String
)
