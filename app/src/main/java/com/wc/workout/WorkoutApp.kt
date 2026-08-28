package com.wc.workout

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wc.workout.data.local.AppDatabase

class WorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "workout.db").build()
}
