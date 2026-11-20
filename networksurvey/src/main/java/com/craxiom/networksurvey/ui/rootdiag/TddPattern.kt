package com.craxiom.networksurvey.ui.rootdiag

internal data class LteTddPattern(val frames: String, val special: Int) {
    val downlink get() = frames.count { it == 'D' }
    val uplink get() = frames.count { it == 'U' }
    val switching get() = frames.count { it == 'S' }
}

private val lteFrames = listOf(
    "DSUUUDSUUU", "DSUUDDSUUD", "DSUDDDSUDD", "DSUUUDDDDD",
    "DSUUDDDDDD", "DSUDDDDDDD", "DSUUUDSUUD",
)

// 36.211 Table 4.2-2. The special subframe includes DL, guard and UL.
internal fun lteTddPattern(assignment: Int, special: Int): LteTddPattern? =
    lteFrames.getOrNull(assignment)?.takeIf { special in 0..8 }?.let { LteTddPattern(it, special) }

internal data class NrTddAllocation(val downlink: Int, val uplink: Int, val flexible: Int)

// 38.213 section 11.1: common configuration, before dedicated overrides or dynamic slot formats.
internal fun nrTddAllocation(
    scsKhz: Int, extendedCp: Boolean, periodUs: Int,
    dlSlots: Int, dlSymbols: Int, ulSlots: Int, ulSymbols: Int,
): NrTddAllocation? {
    if (scsKhz !in listOf(15, 30, 60, 120) || extendedCp && scsKhz != 60 ||
        periodUs !in 500..10_000 || dlSlots !in 0..80 || ulSlots !in 0..80) return null
    val slots = periodUs * (scsKhz / 15)
    if (slots % 1000 != 0) return null
    val symbolsPerSlot = if (extendedCp) 12 else 14
    if (dlSymbols !in 0 until symbolsPerSlot || ulSymbols !in 0 until symbolsPerSlot) return null
    val total = slots / 1000 * symbolsPerSlot
    val downlink = dlSlots * symbolsPerSlot + dlSymbols
    val uplink = ulSlots * symbolsPerSlot + ulSymbols
    return if (downlink + uplink <= total) NrTddAllocation(downlink, uplink, total - downlink - uplink) else null
}
