package com.craxiom.networksurvey.ui.bandlock

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal enum class LockAvailability { Checking, Ready, Unavailable }
internal data class BandLockState(
    val availability: LockAvailability = LockAvailability.Checking,
    val busy: Boolean = false,
    val channel: String = "",
    val caps: List<Pair<String, Boolean>> = emptyList(),
    val lteBackup: Boolean = false,
    val nrBackup: Boolean = false,
    val result: Int? = null,
    val log: String = "",
)

internal class BandLockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val mutableState = MutableStateFlow(BandLockState())
    val state = mutableState.asStateFlow()
    val device = "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SOC_MODEL}"
    @Volatile private var process: Process? = null

    init { refresh() }

    fun refresh() = run(BandLockCommand.probe(Build.MODEL, Build.SOC_MODEL))
    fun clearLog() { if (!mutableState.value.busy) mutableState.value = mutableState.value.copy(log = "") }

    fun run(command: BandLockCommand, fromConsole: Boolean = false) {
        if (mutableState.value.busy) return
        val probe = command.arguments.firstOrNull() == "probe"
        if (!probe && mutableState.value.availability != LockAvailability.Ready) return
        mutableState.value = mutableState.value.copy(busy = true, result = null)
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) { execute(command) }
                val current = mutableState.value
                val ready = outcome.exit == 0 && outcome.output.lineSequence().any { it == "RESULT ready" }
                val accepted = outcome.exit == 0 && outcome.output.lineSequence().any {
                    it == "RESULT accepted" || it == "RESULT restart_sent"
                }
                mutableState.value = current.copy(
                    availability = if (probe) {
                        if (ready) LockAvailability.Ready else LockAvailability.Unavailable
                    } else current.availability,
                    channel = if (probe) outcome.output.lineSequence()
                        .firstOrNull { it.startsWith("CHANNEL ") }
                        ?.removePrefix("CHANNEL ")?.trim().orEmpty() else current.channel,
                    caps = if (probe) parseCaps(outcome.output) else current.caps,
                    lteBackup = if (probe && ready) outcome.output.contains("BACKUP lte 1") else
                        current.lteBackup || (accepted && command.arguments.first() == "lte-band"),
                    nrBackup = if (probe && ready) outcome.output.contains("BACKUP nr 1") else
                        current.nrBackup || (accepted && command.arguments.first() == "nr-band"),
                    result = when {
                        fromConsole -> null
                        probe && ready -> R.string.lock_ready
                        probe -> R.string.lock_unavailable
                        accepted && command.restartsModem -> R.string.lock_restart_sent
                        accepted -> R.string.lock_accepted
                        else -> R.string.lock_failed
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appendLog(error.message ?: error.javaClass.simpleName)
                mutableState.value = mutableState.value.copy(
                    availability = if (probe) LockAvailability.Unavailable else mutableState.value.availability,
                    result = if (probe) R.string.lock_unavailable else R.string.lock_failed,
                )
            } finally {
                process?.destroy()
                process = null
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    /** Command console entry: run a raw native command line and stream its
     * output to the log without overwriting the standard result line. */
    fun runRaw(text: String) {
        run(BandLockCommand.console(text), fromConsole = true)
    }

    private data class Outcome(val exit: Int, val output: String)

    private suspend fun execute(command: BandLockCommand): Outcome {
        val helper = File(app.applicationInfo.nativeLibraryDir, "libwireless_band_lock.so")
        if (!helper.isFile) throw IOException("The lock helper is unavailable for this ABI.")
        val directory = File(app.noBackupFilesDir, "band-lock")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot open the private backup directory.")
        fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"
        val arguments = listOf(helper.absolutePath, "--state-dir", directory.absolutePath) + command.arguments
        appendLog("${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} > ${command.arguments.joinToString(" ")}")
        val running = ProcessBuilder("su", "-c", "exec " + arguments.joinToString(" ", transform = ::quote))
            .redirectErrorStream(true).start()
        process = running
        running.outputStream.close()
        val output = StringBuilder()
        val pending = StringBuilder()
        val input = running.inputStream
        val bytes = ByteArray(4096)
        val started = SystemClock.elapsedRealtime()
        try {
            while (running.isAlive || input.available() > 0) {
                if (SystemClock.elapsedRealtime() - started > 110_000) {
                    running.destroyForcibly()
                    throw IOException("Command timed out. Changes may be partial; refresh the device status.")
                }
                val available = input.available()
                if (available == 0) { delay(80); continue }
                val count = input.read(bytes, 0, minOf(bytes.size, available))
                if (count <= 0) break
                val chunk = String(bytes, 0, count, Charsets.UTF_8)
                output.append(chunk)
                pending.append(chunk)
                if (output.length > 48_000) throw IOException("Unexpected helper output size.")
                var end = pending.indexOf("\n")
                while (end >= 0) {
                    appendLog(pending.substring(0, end).trimEnd('\r'))
                    pending.delete(0, end + 1)
                    end = pending.indexOf("\n")
                }
            }
            if (pending.isNotEmpty()) appendLog(pending.toString())
            return Outcome(running.waitFor(), output.toString())
        } finally { input.close() }
    }

    private fun parseCaps(output: String): List<Pair<String, Boolean>> =
        output.lineSequence().filter { it.startsWith("CAP ") }.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val key = parts.getOrNull(1) ?: return@mapNotNull null
            key to (parts.getOrNull(2) == "1")
        }.toList()

    private fun appendLog(line: String) {
        mutableState.value = mutableState.value.copy(log = (mutableState.value.log + line + "\n").takeLast(24_000))
    }

    override fun onCleared() {
        process?.destroy()
        super.onCleared()
    }
}
