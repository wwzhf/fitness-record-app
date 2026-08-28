package com.wc.workout.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

// Temporary scaffold placeholder: Room requires at least one entity per @Database.
// Later tasks should replace this with the real workout entities and bump the version.
@Entity(tableName = "placeholder")
data class PlaceholderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0)

@Database(entities = [PlaceholderEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase()
