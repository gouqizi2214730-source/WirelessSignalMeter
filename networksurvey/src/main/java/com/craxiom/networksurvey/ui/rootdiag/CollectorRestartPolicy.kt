package com.craxiom.networksurvey.ui.rootdiag

/**
 * Limited reconnect policy for the root DIAG collector.
 * Denied root or a missing libdiag is not retried automatically.
 * Transient drops after a successful start, or diag-router init/register
 * failures, back off instead of spinning.
 */
internal object CollectorRestartPolicy {
    const val MAX_ATTEMPTS = 6
    const val BASE_DELAY_MS = 2_000L
    const val MAX_DELAY_MS = 60_000L

    fun isTransient(status: RootDiagStatus, errorCode: Int?, hadBeenRunning: Boolean): Boolean {
        if (status == RootDiagStatus.Denied || status == RootDiagStatus.Unavailable) return false
        if (errorCode == 2 || errorCode == 3) return false
        if (hadBeenRunning) return true
        return status == RootDiagStatus.Unsupported && errorCode in setOf(4, 5, 6)
    }

    fun shouldRetry(
        status: RootDiagStatus,
        errorCode: Int?,
        hadBeenRunning: Boolean,
        attempt: Int,
    ): Boolean = attempt < MAX_ATTEMPTS && isTransient(status, errorCode, hadBeenRunning)

    fun delayMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 5)
        val delay = BASE_DELAY_MS shl shift
        return if (delay > MAX_DELAY_MS) MAX_DELAY_MS else delay
    }

    fun collectorReason(status: RootDiagStatus, errorCode: Int?): String = when (status) {
        RootDiagStatus.Unavailable -> "no_su"
        RootDiagStatus.Denied -> "root_denied"
        RootDiagStatus.Unsupported -> when (errorCode) {
            2 -> "no_libdiag"
            3 -> "missing_libdiag_symbols"
            4 -> "diag_lsm_init_failed"
            5 -> "dci_register_failed"
            6 -> "dci_stream_or_mask_failed"
            else -> "collector_stopped"
        }
        RootDiagStatus.Stopped -> "stopped"
        RootDiagStatus.Running, RootDiagStatus.Starting, RootDiagStatus.Checking -> "ok"
    }
}
