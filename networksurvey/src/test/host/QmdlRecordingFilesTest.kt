package com.craxiom.networksurvey.ui.rootdiag

import java.nio.file.Files
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QmdlRecordingFilesTest {
    @Test fun savesNonEmptyCaptureAndDoesNotReplaceItWithAnEmptyCapture() = withFiles { files ->
        assertNull(files.latestRecording())
        val data = byteArrayOf(0x10, 0x7d, 0x5e, 0x7e)
        files.start("task-1").writeBytes(data)
        assertTrue(files.finish() != null)
        assertFalse(files.activeFile.exists())
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
        files.start("task-2")
        assertNull(files.finish())
        assertNull(files.finish())
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
    }

    @Test fun recoversInterruptedRecordingBeforeStartingAnother() = withFiles { files ->
        val data = byteArrayOf(0x10, 0x7e)
        files.activeFile.writeBytes(data)
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
        files.start("new-task")
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
        assertTrue(files.recordings().any { it.name == "orphan.qmdl" })
    }

    @Test fun failedArchiveKeepsBytesExportableAndPreventsOverwrite() = withFiles(renameFails = true) { files ->
        val data = byteArrayOf(0x10, 0x7e)
        files.start("task-1").writeBytes(data)
        assertTrue(runCatching { files.finish() }.exceptionOrNull() is IOException)
        assertTrue(runCatching { files.start("task-2") }.exceptionOrNull() is IOException)
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
    }

    @Test fun switchingTasksRequiresClosingPreviousWriterFirst() = withFiles { files ->
        val data = byteArrayOf(0x10, 0x7e)
        files.start("task-1").writeBytes(data)
        assertTrue(runCatching { files.start("task-2") }.exceptionOrNull() is IllegalStateException)
        assertTrue(files.finish()?.name == "task-1.qmdl")
        assertArrayEquals(data, files.latestRecording()!!.readBytes())
    }

    private fun withFiles(renameFails: Boolean = false, test: (DiagRecordingSession) -> Unit) {
        val directory = Files.createTempDirectory("qmdl-recording-test").toFile()
        try {
            test(if (renameFails) DiagRecordingSession(directory) { _, _ -> false } else DiagRecordingSession(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
