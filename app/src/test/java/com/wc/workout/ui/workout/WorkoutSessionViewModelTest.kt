package com.wc.workout.ui.workout

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutSessionViewModelTest {

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

    private fun vm(sessionId: Long) =
        WorkoutSessionViewModel(workoutRepo, exerciseRepo, sessionId)

    @Test
    fun sessionLoadsOnInit() = runBlocking {
        val sessionId = workoutRepo.startSession("测试", now = 1_000)
        val viewModel = vm(sessionId)
        awaitState { viewModel.session.value?.id == sessionId }
    }

    @Test
    fun addPendingExerciseDeduplicates() = runBlocking {
        val sessionId = workoutRepo.startSession("测试")
        val exerciseId = (exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success).id
        val viewModel = vm(sessionId)

        assertTrue(viewModel.addPendingExercise(exerciseId))
        assertTrue(viewModel.addPendingExercise(exerciseId))

        assertEquals(listOf(exerciseId), viewModel.pendingExerciseIds.value)
    }

    @Test
    fun filteredExercisesFiltersByQuery() = runBlocking {
        exerciseRepo.addExercise("卧推")
        exerciseRepo.addExercise("深蹲")
        exerciseRepo.addExercise("Bench Press")
        val viewModel = vm(workoutRepo.startSession("测试"))
        // stateIn(WhileSubscribed) 需要活跃订阅者，上游 Room 流才会开始生产
        val collector = launch { viewModel.filteredExercises.collect {} }
        try {
            viewModel.exerciseQuery.value = "推"
            awaitState {
                viewModel.filteredExercises.value.let { it.size == 1 && it[0].name == "卧推" }
            }
            // 拉丁字母名：小写查询命中（覆盖 ignoreCase = true 路径）
            viewModel.exerciseQuery.value = "bench"
            awaitState {
                viewModel.filteredExercises.value.let { it.size == 1 && it[0].name == "Bench Press" }
            }
            // 大写查询同样命中
            viewModel.exerciseQuery.value = "BENCH"
            awaitState {
                viewModel.filteredExercises.value.let { it.size == 1 && it[0].name == "Bench Press" }
            }
            // 空白查询返回全部（中文 + 拉丁）
            viewModel.exerciseQuery.value = " "
            awaitState {
                viewModel.filteredExercises.value.map { it.name }.toSet() ==
                    setOf("卧推", "深蹲", "Bench Press")
            }
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun setDurationMinutesUpdatesEndTime() = runBlocking {
        val sessionId = workoutRepo.startSession("测试", now = 1_000_000)
        val viewModel = vm(sessionId)
        // 先等 init 完成加载，避免并发的旧值覆盖
        awaitState { viewModel.session.value?.id == sessionId }

        viewModel.setDurationMinutes(45)

        awaitState { viewModel.session.value?.endTime == 1_000_000L + 45 * 60_000L }
    }

    @Test
    fun createExerciseReturnsNullOnDuplicate() = runBlocking {
        exerciseRepo.addExercise("卧推")
        val viewModel = vm(workoutRepo.startSession("测试"))

        assertNull(viewModel.createExercise("卧推"))
        assertNotNull(viewModel.createExercise("上斜卧推"))
    }
}
