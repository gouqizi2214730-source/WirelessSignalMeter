package com.craxiom.networksurvey.util

/**
 * Carrier aggregation and EN-DC classification.
 *
 * ServiceState.getCellBandwidths() is an untyped list of serving-cell bandwidths. It can mix
 * LTE and NR on EN-DC, does not identify PCC/SCC, and is not proof that a carrier is scheduled.
 * TelephonyDisplayInfo overrides such as LTE_CA or NR_ADVANCED are branding and must never be
 * used as a carrier count. Android CellInfo neighbors must not be treated as secondary carriers;
 * only ConnectionStatus.SECONDARY_SERVING (or DIAG serving evidence) qualifies.
 */
object CarrierAggregation {
    const val LTE = "LTE"
    const val NR = "NR"
    const val ROLE_PCC = "PCC"
    const val ROLE_SCC = "SCC"
    const val ROLE_UNKNOWN = "UNKNOWN"

    enum class Kind {
        NONE,
        /** Two or more ServiceState bandwidths, RAT mix unknown. */
        MULTI_BANDWIDTH,
        LTE_CA,
        NR_CA,
        EN_DC,
    }

    enum class Evidence {
        NONE,
        SERVICE_STATE_BANDWIDTHS,
        CELLINFO_SECONDARY_SERVING,
        DIAG_B825_CONFIGURED,
        DIAG_B193_MEASURED_SERVING,
    }

    data class Component(
        val rat: String,
        val role: String,
        val pci: Int? = null,
        val arfcn: Long? = null,
        val band: Int? = null,
        val bandwidthKhz: Int? = null,
        val evidence: Evidence,
    )

    data class Snapshot(
        val kind: Kind,
        val evidence: Evidence,
        val components: List<Component> = emptyList(),
        val aggregateKhz: Int = 0,
        val claimCarrierAggregation: Boolean = false,
    ) {
        val visible: Boolean get() = kind != Kind.NONE && (components.size > 1 || bandwidthCount > 1)
        val bandwidthCount: Int get() = components.mapNotNull { it.bandwidthKhz }.filter { it > 0 }.size
    }

    data class DiagNrCell(
        val cc: Int,
        val pci: Int,
        val band: Int,
        val dlArfcn: Long,
        val ssbArfcn: Long,
        val bwMhz: Int,
    )

    data class DiagNrConfig(
        val state: String,
        val ncells: Int,
        val cells: List<DiagNrCell>,
        val receivedAt: Long,
    )

    data class DiagLteServingSample(
        val earfcn: Long,
        val pci: Int,
        val isServing: Boolean,
        val receivedAt: Long,
    )

    /**
     * Classify an untyped ServiceState bandwidth list. EN-DC (NSA) must not be labeled as
     * carrier aggregation. A single valid bandwidth is not aggregation.
     */
    @JvmStatic
    fun fromServiceState(bandwidthsKhz: IntArray?, nrMode: TelephonyStateUtils.NrMode): Snapshot {
        val valid = CellularBandwidthUtils.validBandwidthsKhz(bandwidthsKhz)
        if (valid.size <= 1) return Snapshot(Kind.NONE, Evidence.NONE)
        val components = valid.map {
            Component(rat = "UNKNOWN", role = ROLE_UNKNOWN, bandwidthKhz = it, evidence = Evidence.SERVICE_STATE_BANDWIDTHS)
        }
        val total = CellularBandwidthUtils.aggregateBandwidthKhz(valid)
        return when (nrMode) {
            TelephonyStateUtils.NrMode.NON_STANDALONE -> Snapshot(
                Kind.EN_DC, Evidence.SERVICE_STATE_BANDWIDTHS, components, total,
                claimCarrierAggregation = false,
            )
            TelephonyStateUtils.NrMode.STANDALONE -> Snapshot(
                Kind.NR_CA, Evidence.SERVICE_STATE_BANDWIDTHS, components, total,
                claimCarrierAggregation = false,
            )
            TelephonyStateUtils.NrMode.NONE -> Snapshot(
                Kind.LTE_CA, Evidence.SERVICE_STATE_BANDWIDTHS, components, total,
                claimCarrierAggregation = false,
            )
        }
    }

    /**
     * Prefer CellInfo SECONDARY_SERVING identities over an untyped bandwidth list when at least
     * one secondary carrier is present. Neighbors must not be passed in.
     */
    @JvmStatic
    fun mergeWithCellInfo(
        serviceState: Snapshot,
        serving: Component?,
        lteSecondaries: List<Component>,
        nrSecondaries: List<Component>,
    ): Snapshot {
        val secondaries = lteSecondaries + nrSecondaries
        if (secondaries.isEmpty()) return serviceState
        val servingRat = serving?.rat
        val lte = lteSecondaries.isNotEmpty()
        val nr = nrSecondaries.isNotEmpty()
        val kind = when {
            lte && nr -> Kind.EN_DC
            lte && servingRat == LTE -> Kind.LTE_CA
            nr && servingRat == NR -> Kind.NR_CA
            lte && servingRat == NR -> Kind.EN_DC
            nr && servingRat == LTE -> Kind.EN_DC
            servingRat == LTE && nr -> Kind.EN_DC
            lte -> Kind.LTE_CA
            else -> Kind.NR_CA
        }
        // EN-DC secondary NR is not LTE/NR CA; still show the identified legs without calling it CA.
        val claim = kind == Kind.LTE_CA || kind == Kind.NR_CA
        val components = listOfNotNull(serving?.copy(role = ROLE_PCC, evidence = Evidence.CELLINFO_SECONDARY_SERVING)) +
            secondaries.map { it.copy(role = ROLE_SCC) }
        val total = components.mapNotNull { it.bandwidthKhz }.filter { it > 0 }.sum()
        return Snapshot(kind, Evidence.CELLINFO_SECONDARY_SERVING, components, total, claim)
    }

    /**
     * B825 is RRC configuration: multiple cells while connected are configured component
     * carriers, not proof of activation or scheduling. Idle or a later empty list clears CA.
     */
    @JvmStatic
    fun fromDiagNrConfig(config: DiagNrConfig?, nowMs: Long, maxAgeMs: Long = 15_000L): Snapshot {
        if (config == null) return Snapshot(Kind.NONE, Evidence.NONE)
        if (nowMs - config.receivedAt !in 0..maxAgeMs) return Snapshot(Kind.NONE, Evidence.NONE)
        if (!config.state.equals("connected", ignoreCase = true)) return Snapshot(Kind.NONE, Evidence.NONE)
        val cells = config.cells.filter { it.pci in 0..1007 }
            .distinctBy { Triple(it.cc, it.pci, it.ssbArfcn.takeIf { arfcn -> arfcn > 0 } ?: it.dlArfcn) }
        if (cells.size <= 1) return Snapshot(Kind.NONE, Evidence.NONE)
        val components = cells.map { cell ->
            Component(
                rat = NR,
                role = if (cell.cc == 0) ROLE_PCC else ROLE_SCC,
                pci = cell.pci,
                arfcn = cell.ssbArfcn.takeIf { it > 0 } ?: cell.dlArfcn.takeIf { it > 0 },
                band = cell.band.takeIf { it > 0 },
                bandwidthKhz = cell.bwMhz.takeIf { it > 0 }?.times(1_000),
                evidence = Evidence.DIAG_B825_CONFIGURED,
            )
        }
        return Snapshot(
            Kind.NR_CA,
            Evidence.DIAG_B825_CONFIGURED,
            components,
            components.mapNotNull { it.bandwidthKhz }.sum(),
            claimCarrierAggregation = false,
        )
    }

    /**
     * B193 serving cells on two or more EARFCNs that actually overlap in time. A brief
     * handover boundary (one slice only) is not treated as LTE CA.
     */
    @JvmStatic
    fun fromDiagLteServing(
        samples: List<DiagLteServingSample>,
        nowMs: Long,
        sliceMs: Long = 500L,
        minOverlapSlices: Int = 3,
        lookbackMs: Long = 4_000L,
    ): Snapshot {
        val serving = samples.filter {
            it.isServing && it.pci in 0..503 && it.earfcn in 0..262_143L && nowMs - it.receivedAt in 0..lookbackMs
        }
        if (serving.isEmpty()) return Snapshot(Kind.NONE, Evidence.NONE)
        val slices = serving.groupBy { it.receivedAt / sliceMs }
        val concurrent = slices.filter { (_, rows) -> rows.map { it.earfcn }.toSet().size >= 2 }
        if (concurrent.size < minOverlapSlices) return Snapshot(Kind.NONE, Evidence.NONE)
        val carriers = concurrent.values.flatten().distinctBy { it.earfcn to it.pci }
            .groupBy { it.earfcn }
            .map { (earfcn, rows) ->
                val pci = rows.maxBy { it.receivedAt }.pci
                Component(
                    rat = LTE,
                    role = ROLE_UNKNOWN,
                    pci = pci,
                    arfcn = earfcn,
                    evidence = Evidence.DIAG_B193_MEASURED_SERVING,
                )
            }
        if (carriers.size <= 1) return Snapshot(Kind.NONE, Evidence.NONE)
        return Snapshot(Kind.LTE_CA, Evidence.DIAG_B193_MEASURED_SERVING, carriers, 0, claimCarrierAggregation = false)
    }

    /** DIAG evidence wins over ServiceState when it actually identifies two or more carriers. */
    @JvmStatic
    fun preferDiag(diag: Snapshot, android: Snapshot?): Snapshot {
        if (diag.visible) return diag
        return android?.takeIf { it.visible } ?: Snapshot(Kind.NONE, Evidence.NONE)
    }

    @JvmStatic
    fun chipLabel(component: Component): String {
        val band = component.band?.let { if (component.rat == NR) "n$it" else "B$it" }
        val bw = component.bandwidthKhz?.takeIf { it > 0 }?.let { "${CellularBandwidthUtils.formatBandwidthMhz(it)} MHz" }
        val pci = component.pci?.let { "PCI $it" }
        val arfcn = component.arfcn?.takeIf { it >= 0 }?.let { if (component.rat == NR) "SSB $it" else "EARFCN $it" }
        val parts = listOfNotNull(band, bw, pci, arfcn)
        return parts.take(3).joinToString(" · ").ifBlank { bw ?: ROLE_UNKNOWN }
    }

    /**
     * English summary for tests and as a fallback. The UI should prefer localized strings.
     * Never claims activation or an exact marketed carrier-aggregation badge from overrides.
     */
    @JvmStatic
    fun englishSummary(snapshot: Snapshot): String {
        if (!snapshot.visible) return ""
        val n = maxOf(snapshot.components.size, snapshot.bandwidthCount)
        val mhz = if (snapshot.aggregateKhz > 0) "${CellularBandwidthUtils.formatBandwidthMhz(snapshot.aggregateKhz)} MHz" else null
        val suffix = mhz?.let { " · $it" } ?: ""
        return when (snapshot.evidence) {
            Evidence.CELLINFO_SECONDARY_SERVING -> when (snapshot.kind) {
                Kind.EN_DC -> "EN-DC (CellInfo secondary) · $n legs$suffix"
                Kind.LTE_CA -> "LTE CA (CellInfo secondary) · $n carriers$suffix"
                Kind.NR_CA -> "NR CA (CellInfo secondary) · $n carriers$suffix"
                else -> "Secondary serving cells · $n$suffix"
            }
            Evidence.DIAG_B825_CONFIGURED -> "NR configured carriers (B825) · $n$suffix"
            Evidence.DIAG_B193_MEASURED_SERVING -> "LTE multi-serving EARFCN (B193) · $n$suffix"
            Evidence.SERVICE_STATE_BANDWIDTHS -> when (snapshot.kind) {
                Kind.EN_DC -> "LTE+NR reported bandwidths · $n$suffix (possible EN-DC, not a CA carrier count)"
                Kind.NR_CA -> "NR reported bandwidths · $n$suffix (ServiceState, not activation)"
                Kind.LTE_CA -> "LTE reported bandwidths · $n$suffix (ServiceState, not activation)"
                else -> "Reported bandwidths · $n$suffix (untyped, not activation)"
            }
            Evidence.NONE -> ""
        }
    }
}
