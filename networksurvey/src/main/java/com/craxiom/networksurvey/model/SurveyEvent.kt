package com.craxiom.networksurvey.model

internal data class SurveyTask(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startedElapsedMs: Long? = null,
    val clockId: String? = null,
    val needLog: Boolean = false,
)

internal data class SurveyEvent(
    val id: String,
    val taskId: String,
    val time: Long,
    val elapsedMs: Long,
    val clockId: String,
    val source: String,
    val kind: String,
    val title: String,
    val detail: String = "",
    val subscriptionId: Int? = null,
    val simSlot: Int? = null,
    val rat: String? = null,
    val pci: Int? = null,
    val arfcn: Int? = null,
    val sourceTimeMs: Long? = null,
    val timeBasis: String = "received",
)

internal fun SurveyTask.ownsDiagnostic(sourceWallMs: Long, receivedWallMs: Long, receivedElapsedMs: Long,
                                      receivedClockId: String): Boolean {
    if (sourceWallMs <= 0 || sourceWallMs > receivedWallMs) return false
    return if (clockId == receivedClockId && startedElapsedMs != null) {
        receivedElapsedMs - (receivedWallMs - sourceWallMs) >= startedElapsedMs
    } else sourceWallMs >= startedAt
}

internal data class ObservedCell(
    val taskId: String,
    val subscriptionId: Int,
    val simSlot: Int?,
    val time: Long,
    val elapsedMs: Long,
    val clockId: String,
    val rat: String,
    val cellId: Long?,
    val pci: Int?,
    val arfcn: Int?,
    val plmn: String?,
)

/** Public API observations cannot distinguish handover from idle reselection. */
internal class ObservedCellEvents {
    private val previous = mutableMapOf<Int, ObservedCell>()

    fun accept(cell: ObservedCell): List<SurveyEvent> {
        if (cell.subscriptionId < 0) return emptyList()
        val old = previous.put(cell.subscriptionId, cell) ?: return emptyList()
        if (old.taskId != cell.taskId || old.clockId != cell.clockId ||
            cell.elapsedMs - old.elapsedMs !in 0..30_000) return emptyList()
        val ratChanged = old.rat != cell.rat
        val identityChanged = old.cellId != null && cell.cellId != null && old.cellId != cell.cellId
        val radioChanged = old.pci != null && cell.pci != null && old.pci != cell.pci ||
            old.arfcn != null && cell.arfcn != null && old.arfcn != cell.arfcn
        val operatorChanged = old.plmn != null && cell.plmn != null && old.plmn != cell.plmn
        if (!ratChanged && !identityChanged && !radioChanged && !operatorChanged) return emptyList()
        fun label(c: ObservedCell) = listOfNotNull(c.rat, c.plmn, c.cellId?.let { "CID $it" },
            c.pci?.let { "PCI $it" }, c.arfcn?.let { "ARFCN $it" }).joinToString(" · ")
        return listOf(SurveyEvent(
            java.util.UUID.randomUUID().toString(), cell.taskId, cell.time, cell.elapsedMs, cell.clockId,
            "android", if (ratChanged) "rat_change" else "cell_change",
            if (ratChanged) "制式变化（观测）" else "服务小区变化（观测）",
            "${label(old)} → ${label(cell)}", cell.subscriptionId, cell.simSlot, cell.rat, cell.pci, cell.arfcn,
        ))
    }
}
