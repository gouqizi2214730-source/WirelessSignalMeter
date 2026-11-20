package com.craxiom.networksurvey.ui.rootdiag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TddPatternTest {
    @Test fun lteAllAssignmentsKeepSpecialSubframesSeparate() {
        val ratios = listOf(
            Triple(2, 6, 2), Triple(4, 4, 2), Triple(6, 2, 2), Triple(6, 3, 1),
            Triple(7, 2, 1), Triple(8, 1, 1), Triple(3, 5, 2),
        )
        ratios.forEachIndexed { assignment, ratio ->
            val pattern = checkNotNull(lteTddPattern(assignment, 7))
            assertEquals(10, pattern.frames.length)
            assertEquals(ratio, Triple(pattern.downlink, pattern.uplink, pattern.switching))
            assertEquals(7, pattern.special)
        }
        assertNull(lteTddPattern(7, 0))
        assertNull(lteTddPattern(0, 15))
    }

    @Test fun nrNormalAndExtendedCpAndPatternPeriod() {
        assertEquals(NrTddAllocation(48, 18, 4), nrTddAllocation(30, false, 2500, 3, 6, 1, 4))
        assertEquals(NrTddAllocation(16, 12, 20), nrTddAllocation(60, true, 1000, 1, 4, 1, 0))
        assertEquals(NrTddAllocation(28, 28, 0), nrTddAllocation(120, false, 500, 2, 0, 2, 0))
        assertEquals(NrTddAllocation(14, 14, 42), nrTddAllocation(120, false, 625, 1, 0, 1, 0))
    }

    @Test fun nrRejectsInconsistentPeriodSlotAndSymbolAllocations() {
        assertNull(nrTddAllocation(15, false, 625, 0, 0, 0, 0))
        assertNull(nrTddAllocation(30, false, 2500, 4, 4, 1, 0))
        assertNull(nrTddAllocation(30, true, 2500, 3, 6, 1, 4))
        assertNull(nrTddAllocation(60, true, 2500, 3, 12, 1, 4))
    }
}
