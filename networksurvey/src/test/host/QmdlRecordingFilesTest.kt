package com.craxiom.networksurvey.ui.rootdiag

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QmdlRecordingFilesTest {
    @Test fun savesNonEmptyCaptureAndDoesNotReplaceItWithAnEmptyCapture() = withFiles { files ->
        assertNull(files.exportFile())
        assertTrue(files.prepare())
        val data = byteArrayOf(0x10, 0x7d, 0x5e, 0x7e)
        files.active.writeBytes(data)
        assertTrue(files.retain())
        assertFalse(files.active.exists())
        assertArrayEquals(data, files.exportFile()!!.readBytes())
        assertTrue(files.prepare())
        assertTrue(files.retain())
        assertTrue(files.retain())
        assertArrayEquals(data, files.exportFile()!!.readBytes())
    }

    @Test fun recoversInterruptedRecordingBeforeStartingAnother() = withFiles { files ->
        assertTrue(files.prepare())
        val data = byteArrayOf(0x10, 0x7e)
        files.active.writeBytes(data)
        assertArrayEquals(data, files.exportFile()!!.readBytes())
        assertTrue(files.prepare())
        assertArrayEquals(data, files.exportFile()!!.readBytes())
        val newer = byteArrayOf(0x10, 0x11, 0x7e)
        files.active.writeBytes(newer)
        assertTrue(files.retain())
        assertArrayEquals(newer, files.exportFile()!!.readBytes())
    }

    private fun withFiles(test: (QmdlRecordingFiles) -> Unit) {
        val directory = Files.createTempDirectory("qmdl-recording-test").toFile()
        try {
            test(QmdlRecordingFiles(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
