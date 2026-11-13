package com.craxiom.networksurvey.ui.cellular

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.craxiom.networksurvey.model.CellularProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val SIGNAL_WINDOW_MS = 120_000L

internal data class CellularSignalSample(
    val time: Long,
    val dbm: Int?,
)

internal data class CellularSignalHistory(
    val title: String = "RSSI",
    val samples: List<CellularSignalSample> = emptyList(),
)

class CellularChartViewModel : ViewModel() {
    private val historyState = MutableStateFlow(CellularSignalHistory())
    internal val history = historyState.asStateFlow()

    private var protocol = CellularProtocol.NONE

    private fun CellularProtocol.isRsrp() = this == CellularProtocol.NR || this == CellularProtocol.LTE

    @Synchronized
    fun recordSignal(protocol: CellularProtocol, dbm: Int?) {
        val previous = historyState.value
        val now = SystemClock.elapsedRealtime()
        val reading = dbm?.takeIf { it in -160..-20 }
        val changedProtocol = protocol != CellularProtocol.NONE && protocol != this.protocol
        val samples = if (!changedProtocol || (protocol.isRsrp() && this.protocol.isRsrp())) {
            previous.samples
        } else {
            emptyList()
        }
        if (protocol != CellularProtocol.NONE) this.protocol = protocol
        if (!changedProtocol && reading == null && samples.lastOrNull()?.dbm == null) return
        val recentSamples = samples.dropWhile { it.time < now - SIGNAL_WINDOW_MS }
        historyState.value = CellularSignalHistory(
            title = when (protocol) {
                CellularProtocol.NR -> "SS-RSRP"
                CellularProtocol.LTE -> "RSRP"
                CellularProtocol.UMTS -> "RSCP"
                else -> previous.title.takeIf { protocol == CellularProtocol.NONE } ?: "RSSI"
            },
            samples = if (reading == null && recentSamples.isEmpty()) {
                emptyList()
            } else {
                recentSamples + CellularSignalSample(now, reading)
            },
        )
    }
}
