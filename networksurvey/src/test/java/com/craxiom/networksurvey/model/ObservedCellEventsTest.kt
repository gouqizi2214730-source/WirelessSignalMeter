package com.craxiom.networksurvey.model

import org.junit.Assert.*
import org.junit.Test

class ObservedCellEventsTest {
    private fun cell(sub: Int = 1, ms: Long = 1_000) = ObservedCell(
        "task", sub, sub - 1, 1_795_000_000_000L + ms, ms, "clock", "LTE", 123, 1, 100, "460-01")

    @Test fun noEventForInitialOrUnchangedObservation() {
        val tracker = ObservedCellEvents()
        assertTrue(tracker.accept(cell()).isEmpty())
        assertTrue(tracker.accept(cell(ms = 2_000)).isEmpty())
    }

    @Test fun cellChangeIsObservationAndKeepsBothIdentities() {
        val tracker = ObservedCellEvents()
        tracker.accept(cell())
        val event = tracker.accept(cell(ms = 2_000).copy(cellId = 124, pci = 2)).single()
        assertEquals("cell_change", event.kind)
        assertTrue(event.title.contains("观测"))
        assertTrue(event.detail.contains("CID 123"))
        assertTrue(event.detail.contains("CID 124"))
        assertEquals(1, event.subscriptionId)
        assertEquals("task", event.taskId)
        assertEquals(2, event.pci)
    }

    @Test fun ratChangeIsSingleEventNotHandoverSuccess() {
        val tracker = ObservedCellEvents()
        tracker.accept(cell())
        val event = tracker.accept(cell(ms = 2_000).copy(rat = "NR", arfcn = 627264)).single()
        assertEquals("rat_change", event.kind)
        assertFalse(event.title.contains("成功"))
    }

    @Test fun noSyntheticTransitionAcrossSimTaskClockOrLongGaps() {
        for (next in listOf(cell(sub = 2), cell().copy(taskId = "next"),
            cell().copy(clockId = "restart"), cell(ms = 31_001), cell(ms = 999))) {
            val tracker = ObservedCellEvents()
            tracker.accept(cell())
            assertTrue(tracker.accept(next.copy(pci = 2)).isEmpty())
        }
    }

    @Test fun missingIdentityBecomingKnownIsNotCellChange() {
        val tracker = ObservedCellEvents()
        tracker.accept(cell().copy(cellId = null, pci = null, arfcn = null, plmn = null))
        assertTrue(tracker.accept(cell(ms = 2_000)).isEmpty())
    }

    @Test fun invalidSubscriptionDoesNotMixUnownedCallbacks() {
        val tracker = ObservedCellEvents()
        tracker.accept(cell(sub = -1))
        assertTrue(tracker.accept(cell(sub = -1, ms = 2_000).copy(pci = 2)).isEmpty())
    }

    @Test fun diagnosticBacklogCannotBeAssignedToNewTask() {
        val task = SurveyTask("new", "new", 1_000_000, startedElapsedMs = 10_000, clockId = "clock")
        assertFalse(task.ownsDiagnostic(999_900, 1_000_100, 10_100, "clock"))
        assertTrue(task.ownsDiagnostic(1_000_050, 1_000_100, 10_100, "clock"))
        assertFalse(task.ownsDiagnostic(1_000_101, 1_000_100, 10_100, "clock"))
        assertFalse(task.ownsDiagnostic(0, 1_000_100, 10_100, "clock"))
    }

    @Test fun activeTaskUsesMonotonicBoundaryAfterWallClockChange() {
        val task = SurveyTask("new", "new", 1_000_000, startedElapsedMs = 10_000, clockId = "clock")
        assertTrue(task.ownsDiagnostic(500_050, 500_100, 20_000, "clock"))
        assertFalse(task.ownsDiagnostic(500_050, 500_100, 20_000, "another_process"))
    }
}
