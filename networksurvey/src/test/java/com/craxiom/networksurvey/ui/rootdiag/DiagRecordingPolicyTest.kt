package com.craxiom.networksurvey.ui.rootdiag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class DiagRecordingPolicyTest {
    @Test
    fun noTaskInForegroundIsLiveViewNotRecording() {
        val mode = DiagRecordingPolicy.mode(false, true, true, false)
        assertEquals(DiagRecordingPolicy.Mode.LiveView, mode)
        assertTrue(DiagRecordingPolicy.shouldListen(mode))
        assertFalse(DiagRecordingPolicy.shouldRecord(mode))
        assertEquals("listening", DiagRecordingPolicy.overlayStatus(mode))
    }

    @Test
    fun createdTaskDoesNotRecordUntilStarted() {
        // A dialog that only creates a name does not set taskRunning.
        val mode = DiagRecordingPolicy.mode(taskRunning = false, foreground = true, backgroundAllowed = true, paused = false)
        assertFalse(DiagRecordingPolicy.shouldRecord(mode))
    }

    @Test
    fun runningTaskRecordsInForeground() {
        val mode = DiagRecordingPolicy.mode(true, true, false, false)
        assertEquals(DiagRecordingPolicy.Mode.TaskRecording, mode)
        assertTrue(DiagRecordingPolicy.shouldRecord(mode))
        assertEquals("task", DiagRecordingPolicy.overlayStatus(mode))
    }

    @Test
    fun backgroundSwitchKeepsOnlyARunningTask() {
        val recording = DiagRecordingPolicy.mode(true, false, true, false)
        assertEquals(DiagRecordingPolicy.Mode.TaskRecording, recording)
        assertTrue(DiagRecordingPolicy.shouldHoldService(recording, overlayWanted = false))

        val noTask = DiagRecordingPolicy.mode(false, false, true, false)
        assertEquals(DiagRecordingPolicy.Mode.Idle, noTask)
        assertFalse(DiagRecordingPolicy.shouldListen(noTask))
        assertFalse(DiagRecordingPolicy.shouldRecord(noTask))
        assertFalse(DiagRecordingPolicy.shouldHoldService(noTask, overlayWanted = true))
    }

    @Test
    fun leavingWithoutBackgroundStopsARunningTaskSession() {
        val mode = DiagRecordingPolicy.mode(true, false, false, false)
        assertEquals(DiagRecordingPolicy.Mode.Idle, mode)
        assertFalse(DiagRecordingPolicy.shouldRecord(mode))
        assertFalse(DiagRecordingPolicy.shouldHoldService(mode, true))
    }

    @Test
    fun pauseAndFailureLookIdle() {
        assertEquals(
            DiagRecordingPolicy.Mode.Idle,
            DiagRecordingPolicy.mode(true, true, true, paused = true),
        )
        assertEquals("standby", DiagRecordingPolicy.overlayStatus(DiagRecordingPolicy.Mode.Idle))
    }

    @Test
    fun liveViewHoldsServiceOnlyForOverlay() {
        val live = DiagRecordingPolicy.Mode.LiveView
        assertTrue(DiagRecordingPolicy.shouldHoldService(live, overlayWanted = true))
        assertFalse(DiagRecordingPolicy.shouldHoldService(live, overlayWanted = false))
    }
}

class DiagRecordingSessionTest {
    private fun temp(): File = createTempDirectory("diag-session-").toFile().also { it.deleteOnExit() }

    @Test
    fun liveViewCreatesNoRecordingFiles() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        session.ensureLiveOnly()
        assertEquals(0, dir.listFiles()?.size ?: 0)
        assertFalse(session.isOpen())
        assertTrue(session.cannotWrite())
        assertEquals(0, session.recordings().size)
    }

    @Test
    fun createdButNotStartedCreatesNoRecordingFiles() {
        val dir = temp()
        DiagRecordingSession(dir).ensureLiveOnly()
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun startCreatesOneEmptyActiveFile() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        val file = session.start("task-1")
        assertTrue(file.exists())
        assertEquals(0L, file.length())
        assertEquals(DiagRecordingSession.ACTIVE_NAME, file.name)
        assertEquals("task-1", session.openTaskId())
        assertEquals(0, session.recordings().size)
        assertFalse(session.cannotWrite())
    }

    @Test
    fun finishFlushesOwnershipAndStopsWrites() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        val active = session.start("task-1")
        active.writeBytes(byteArrayOf(1, 2, 3, 4))
        val committed = session.finish()
        assertEquals("task-1.qmdl", committed?.name)
        assertEquals(4L, committed?.length())
        assertFalse(session.isOpen())
        assertTrue(session.cannotWrite())
        assertFalse(session.activeFile.exists())
        assertEquals(listOf("task-1.qmdl"), session.recordings().map { it.name })
        // A late write path must not reopen a session by itself.
        assertEquals(1, session.recordings().size)
        assertTrue(session.cannotWrite())
    }

    @Test
    fun emptyFinishDeletesActiveAndLeavesNoRecording() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        session.start("task-empty")
        assertNull(session.finish())
        assertEquals(0, session.recordings().size)
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun secondTaskDoesNotReuseOrOverwriteTheFirstFile() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        session.start("task-1").writeBytes(byteArrayOf(11))
        val first = session.finish()!!
        session.start("task-2").writeBytes(byteArrayOf(22, 22))
        val second = session.finish()!!
        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertEquals(1L, first.length())
        assertEquals(2L, second.length())
        assertEquals(setOf("task-1.qmdl", "task-2.qmdl"), session.recordings().map { it.name }.toSet())
    }

    @Test
    fun failedArchiveKeepsCaptureExportableAndPreventsOverwrite() {
        val session = DiagRecordingSession(temp()) { _, _ -> false }
        session.start("task-1").writeText("QMDL")
        assertTrue(runCatching { session.finish() }.exceptionOrNull() is IOException)
        assertTrue(runCatching { session.start("task-2") }.exceptionOrNull() is IOException)
        assertEquals("QMDL", session.latestRecording()?.readText())
    }

    @Test
    fun taskSwitchMustFinishPreviousWriterFirst() {
        val session = DiagRecordingSession(temp())
        session.start("task-1").writeText("QMDL")
        assertTrue(runCatching { session.start("task-2") }.exceptionOrNull() is IllegalStateException)
        assertEquals("task-1.qmdl", session.finish()?.name)
    }

    @Test
    fun reconnectOnSameTaskKeepsEarlierPartAndOpensANewFile() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        session.start("task-1").writeBytes(byteArrayOf(1, 2))
        val first = session.finish()!!
        val again = session.start("task-1")
        again.writeBytes(byteArrayOf(3))
        val second = session.finish()!!
        assertTrue(first.exists())
        assertTrue(second.exists())
        assertNotEquals(first.name, second.name)
        assertEquals(2, session.recordings().size)
    }

    @Test
    fun configChangeDoesNotStartASessionByItself() {
        val dir = temp()
        val session = DiagRecordingSession(dir)
        // Turning background collection on or off is not a start() call.
        session.ensureLiveOnly()
        assertFalse(session.isOpen())
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }
}
