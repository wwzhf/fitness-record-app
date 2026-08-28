package com.wc.workout

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.repository.BackupRepository
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository

class WorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "workout.db").build()

    val weightRepository = WeightRepository(database)
    val exerciseRepository = ExerciseRepository(database)
    val workoutRepository = WorkoutRepository(database)
    val backupRepository = BackupRepository(database)
}
