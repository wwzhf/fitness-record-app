package com.wc.workout.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WeightRecord::class, Exercise::class, WorkoutSession::class, WorkoutSet::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
}
