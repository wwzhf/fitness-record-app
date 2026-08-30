package com.wc.workout.ui.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseHistoryViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var exerciseRepo: ExerciseRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepo = WorkoutRepository(db)
        exerciseRepo = ExerciseRepository(db)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * Room 流/查询跑在真实执行器上，不能靠 advanceUntilIdle：
     * 用有界轮询等待 StateFlow 满足条件，最多等 5 秒。
     */
    private suspend fun awaitState(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("等待 ViewModel 状态超时（${timeoutMs}ms）")
            }
            delay(25)
        }
    }

    @Test
    fun entriesRefreshWhenSetsChange() = runBlocking {
        val exerciseId = (exerciseRepo.addExercise("引体向上") as ExerciseNameResult.Success).id
        val sessionId = workoutRepo.startSession("测试")
        val viewModel = ExerciseHistoryViewModel(workoutRepo, exerciseRepo, exerciseId)
        // stateIn(WhileSubscribed) 需要活跃订阅者，上游 Room 流才会开始生产
        val collector = launch { viewModel.entries.collect {} }
        try {
            awaitState { viewModel.entries.value.isEmpty() }

            workoutRepo.addSet(sessionId, exerciseId, 0.0, 10)   // 自重组（0kg）
            awaitState {
                viewModel.entries.value.size == 1 &&
                    viewModel.entries.value[0].sets.firstOrNull()?.weightKg == 0.0
            }

            workoutRepo.deleteSet(viewModel.entries.value[0].sets[0].id)
            awaitState { viewModel.entries.value.isEmpty() }
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun exerciseRefreshesOnRename() = runBlocking {
        val exerciseId = (exerciseRepo.addExercise("引体向上") as ExerciseNameResult.Success).id
        val viewModel = ExerciseHistoryViewModel(workoutRepo, exerciseRepo, exerciseId)
        val collector = launch { viewModel.exercise.collect {} }
        try {
            awaitState { viewModel.exercise.value?.name == "引体向上" }
            exerciseRepo.rename(exerciseId, "负重引体向上")
            awaitState { viewModel.exercise.value?.name == "负重引体向上" }
        } finally {
            collector.cancelAndJoin()
        }
    }
}
