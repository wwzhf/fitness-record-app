package com.wc.workout.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatSetSummaryShowsBodyweightForZeroKg() {
        assertEquals("自重×12", formatSetSummary(0.0, 12))
        assertEquals("60kg×8", formatSetSummary(60.0, 8))
        assertEquals("72.5kg×8", formatSetSummary(72.5, 8))
    }

    @Test
    fun formatSetRowShowsBodyweightForZeroKg() {
        assertEquals("自重 × 12 次", formatSetRow(0.0, 12))
        assertEquals("60kg × 8 次", formatSetRow(60.0, 8))
    }
}
