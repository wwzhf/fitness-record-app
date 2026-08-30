package com.wc.workout.ui.library

import com.wc.workout.data.local.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseHistoryPrTest {

    private fun set(weightKg: Double, reps: Int, setOrder: Int = reps) =
        WorkoutSet(
            sessionId = 1, exerciseId = 1,
            weightKg = weightKg, reps = reps,
            exerciseOrder = 1, setOrder = setOrder
        )

    @Test
    fun bestWeightSetPrefersMostRepsOnWeightTie() {
        // 用户报告的场景：35kg×5 在列表前、35kg×6 在后，头部应展示 ×6
        val best = bestWeightSetOf(listOf(set(30.0, 6), set(35.0, 5), set(35.0, 6)))
        assertEquals(35.0, best!!.weightKg, 0.001)
        assertEquals(6, best.reps)
    }

    @Test
    fun bestWeightSetKeepsHeavierOverMoreReps() {
        val best = bestWeightSetOf(listOf(set(30.0, 10), set(35.0, 2)))
        assertEquals(35.0, best!!.weightKg, 0.001)
        assertEquals(2, best.reps)
    }

    @Test
    fun bestWeightSetReturnsNullForEmpty() {
        assertNull(bestWeightSetOf(emptyList()))
    }
}
