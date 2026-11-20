package com.craxiom.networksurvey.util

object CarrierAggregation {
    const val LTE = "LTE"
    const val NR = "NR"

    enum class Kind { NONE, MULTI_BANDWIDTH, LTE_CA, NR_CA, EN_DC }

    data class Component(val rat: String, val pci: Int, val arfcn: Int, val band: Int, val bandwidthKhz: Int) {
        internal val validIdentity get() = when (rat) {
            LTE -> pci in 0..503 && arfcn in 0..262_143
            NR -> pci in 0..1007 && arfcn in 1..3_279_165
            else -> false
        }
    }

    data class Snapshot(val kind: Kind, val components: List<Component> = emptyList()) {
        val visible: Boolean get() = components.size >= 2
        val aggregateKhz: Int get() = components.sumOf { it.bandwidthKhz }
    }

    data class B193Sample(
        val report: Long, val cc: Int, val pci: Int, val earfcn: Int,
        val serving: Boolean, val receivedAt: Long,
    )

    @JvmStatic
    fun fromServiceState(bandwidthsKhz: IntArray?): Snapshot {
        val bandwidths = CellularBandwidthUtils.validBandwidthsKhz(bandwidthsKhz)
        if (bandwidths.size < 2) return Snapshot(Kind.NONE)
        return Snapshot(Kind.MULTI_BANDWIDTH, bandwidths.map { Component("", -1, -1, 0, it) })
    }

    @JvmStatic
    fun mergeWithCellInfo(
        fallback: Snapshot, primary: Component?, lteSecondaries: List<Component>, nrSecondaries: List<Component>,
    ): Snapshot {
        if (primary?.validIdentity != true) return fallback
        fun Component.key() = Triple(rat, pci, arfcn)
        val components = (listOf(primary) + lteSecondaries + nrSecondaries)
            .filter { it.validIdentity }.distinctBy { it.key() }
        if (components.size < 2) return fallback
        val kind = when {
            components.any { it.rat != primary.rat } -> Kind.EN_DC
            primary.rat == LTE -> Kind.LTE_CA
            else -> Kind.NR_CA
        }
        return Snapshot(kind, components)
    }

    /** A current PCC and its SCCs must come from the same verified report, not overlapping timestamps. */
    internal fun b193Secondaries(
        samples: List<B193Sample>, primaryPci: Int, primaryEarfcn: Int, now: Long,
    ): List<B193Sample> {
        val latest = samples.filter { it.report > 0 && now - it.receivedAt in 0..2_000L }
        val report = latest.maxOfOrNull { it.report } ?: return emptyList()
        val rows = latest.filter { it.report == report && it.serving }
        val primary = rows.filter { it.cc == 0 }.singleOrNull() ?: return emptyList()
        if (primary.pci != primaryPci || primary.earfcn != primaryEarfcn) return emptyList()
        return rows.filter { it.cc in 1..7 && it.pci in 0..503 && it.earfcn in 0..262_143 &&
            it.earfcn != primary.earfcn }
            .groupBy { it.cc }.values.mapNotNull { it.singleOrNull() }.distinctBy { it.earfcn }
    }

    @JvmStatic
    fun chipLabel(component: Component): String = listOfNotNull(
        component.band.takeIf { it > 0 }?.let { if (component.rat == NR) "n$it" else "B$it" },
        component.bandwidthKhz.takeIf { it > 0 }?.let { "${CellularBandwidthUtils.formatBandwidthMhz(it)} MHz" },
        component.pci.takeIf { it >= 0 }?.let { "PCI $it" },
        component.arfcn.takeIf { it >= 0 }?.let { if (component.rat == NR) "NR-ARFCN $it" else "EARFCN $it" },
    ).joinToString(" \u00b7 ")
}
