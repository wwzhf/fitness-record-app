package com.wc.workout.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WeightRecord::class, Exercise::class, WorkoutSession::class, WorkoutSet::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        /** v1 的 Excel 导入 bug 写入了 toordinal() 值（公元纪年天数）当作 dateEpochDay，一次性清除 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM weight_records WHERE dateEpochDay > 100000")
            }
        }

        /** v3 新增会话备注列，旧数据默认空串 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
