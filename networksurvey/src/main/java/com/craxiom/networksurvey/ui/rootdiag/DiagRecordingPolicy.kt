package com.craxiom.networksurvey.ui.rootdiag

import java.io.File

/**
 * Live viewing and test-task recording are separate.
 * A background-collection preference only keeps an already running
 * test task recording after the UI is left; it never starts a session.
 */
internal object DiagRecordingPolicy {
    enum class Mode { Idle, LiveView, TaskRecording }

    fun mode(
        taskRunning: Boolean,
        foreground: Boolean,
        backgroundAllowed: Boolean,
        paused: Boolean,
    ): Mode {
        if (paused) return Mode.Idle
        if (taskRunning && (foreground || backgroundAllowed)) return Mode.TaskRecording
        if (foreground) return Mode.LiveView
        return Mode.Idle
    }

    fun shouldListen(mode: Mode): Boolean = mode != Mode.Idle

    fun shouldRecord(mode: Mode): Boolean = mode == Mode.TaskRecording

    fun shouldHoldService(mode: Mode, overlayWanted: Boolean): Boolean = when (mode) {
        Mode.TaskRecording -> true
        Mode.LiveView -> overlayWanted
        Mode.Idle -> false
    }

    fun overlayStatus(mode: Mode): String = when (mode) {
        Mode.TaskRecording -> "task"
        Mode.LiveView -> "listening"
        Mode.Idle -> "standby"
    }
}

/**
 * One test task owns one recording session's files.
 * Live viewing must not create files in [root]. Writes go to a single
 * active file that is only created in [start] and renamed in [finish].
 */
internal class DiagRecordingSession(private val root: File) {
    private var openTaskId: String? = null
    private var active: File? = null

    val activeFile get() = File(root, ACTIVE_NAME)

    fun isOpen(): Boolean = openTaskId != null

    fun openTaskId(): String? = openTaskId

    fun recordings(): List<File> = root.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".qmdl") && it.name != ACTIVE_NAME }
        ?.sortedBy { it.name }
        ?: emptyList()

    fun latestRecording(): File? {
        val committed = recordings()
        val current = active?.takeIf { it.exists() && it.length() > 0 }
        return (committed + listOfNotNull(current)).maxByOrNull { it.lastModified() }
    }

    /** Live view or created-but-not-started tasks must leave the directory empty of new session files. */
    fun ensureLiveOnly() {
        if (openTaskId != null) return
        if (activeFile.exists() && activeFile.length() == 0L) activeFile.delete()
    }

    fun start(taskId: String): File {
        val id = taskId.trim()
        require(id.isNotEmpty()) { "task id required" }
        if (openTaskId == id && active?.exists() == true) return active!!
        if (openTaskId != null) finish()
        if (!root.exists()) root.mkdirs()
        val file = activeFile
        if (file.exists()) {
            if (file.length() > 0) {
                val leftover = uniqueCommitted(openTaskId ?: "orphan")
                if (!file.renameTo(leftover) && file.exists()) file.delete()
            } else {
                file.delete()
            }
        }
        if (!file.createNewFile()) {
            throw java.io.IOException("Unable to create $file")
        }
        openTaskId = id
        active = file
        return file
    }

    fun finish(): File? {
        val id = openTaskId
        val file = active
        openTaskId = null
        active = null
        if (file == null) return null
        if (!file.exists()) return null
        if (file.length() == 0L) {
            file.delete()
            return null
        }
        val dest = uniqueCommitted(id ?: "orphan")
        return if (file.renameTo(dest)) dest else file.takeIf { it.exists() }
    }

    fun cannotWrite(): Boolean = openTaskId == null || active?.exists() != true

    private fun uniqueCommitted(taskId: String): File {
        val safe = taskId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var dest = File(root, "$safe.qmdl")
        var index = 2
        while (dest.exists()) {
            dest = File(root, "$safe-$index.qmdl")
            index++
        }
        return dest
    }

    companion object {
        const val ACTIVE_NAME = "active.qmdl"
    }
}
