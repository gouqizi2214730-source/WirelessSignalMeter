package com.craxiom.networksurvey.ui.rootdiag

import java.io.File

/** App-owned files; only prepare or retain after the native writer has closed its descriptor. */
internal class QmdlRecordingFiles(private val directory: File) {
    val active = File(directory, "active.qmdl")
    private val latest = File(directory, "latest.qmdl")

    fun prepare(): Boolean =
        (directory.isDirectory || directory.mkdirs()) && retain() && active.createNewFile()

    fun retain(): Boolean = when {
        active.length() > 0 -> active.renameTo(latest)
        active.exists() -> active.delete()
        else -> true
    }

    fun exportFile(): File? =
        active.takeIf { it.isFile && it.length() > 0 } ?: latest.takeIf { it.isFile && it.length() > 0 }
}
