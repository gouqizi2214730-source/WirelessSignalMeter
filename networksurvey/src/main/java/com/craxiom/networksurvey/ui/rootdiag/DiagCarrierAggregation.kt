package com.craxiom.networksurvey.ui.rootdiag

import com.craxiom.networksurvey.fragments.model.CarrierAggregationViewState
import com.craxiom.networksurvey.util.CarrierAggregation
import com.craxiom.networksurvey.util.CellularBandwidthUtils
import org.json.JSONObject

/**
 * Maps DIAG records to a CA snapshot. B825 is configuration only; B193 requires overlapping
 * serving EARFCNs. Neither path claims scheduled/activated aggregation.
 */
internal fun diagCarrierAggregation(
    records: Map<String, List<DiagRecord>>,
    nowMs: Long,
): CarrierAggregation.Snapshot {
    val nr = CarrierAggregation.fromDiagNrConfig(diagNrConfig(records), nowMs)
    if (nr.visible) return nr
    return CarrierAggregation.fromDiagLteServing(diagLteServingSamples(records), nowMs)
}

internal fun diagNrConfig(records: Map<String, List<DiagRecord>>): CarrierAggregation.DiagNrConfig? {
    val record = records["nr_cfg"].orEmpty().maxByOrNull { it.id } ?: return null
    val data = record.data
    val cells = data.objects("cells").map { cell ->
        CarrierAggregation.DiagNrCell(
            cc = cell.optInt("cc", -1),
            pci = cell.optInt("pci", -1),
            band = cell.optInt("band"),
            dlArfcn = cell.optLong("dl_arfcn"),
            ssbArfcn = cell.optLong("ssb_arfcn"),
            bwMhz = cell.optInt("bw_mhz"),
        )
    }
    return CarrierAggregation.DiagNrConfig(
        state = data.optString("state"),
        ncells = data.optInt("ncells", cells.size),
        cells = cells,
        receivedAt = record.receivedAt,
    )
}

internal fun diagLteServingSamples(records: Map<String, List<DiagRecord>>): List<CarrierAggregation.DiagLteServingSample> {
    return records["lte_meas"].orEmpty().mapNotNull { record ->
        if (record.source != "B193") return@mapNotNull null
        if (!record.data.has("is_serving")) return@mapNotNull null
        val earfcn = record.data.optLong("earfcn", -1)
        val pci = record.data.optInt("pci", -1)
        CarrierAggregation.DiagLteServingSample(
            earfcn = earfcn,
            pci = pci,
            isServing = record.data.optInt("is_serving") == 1,
            receivedAt = record.receivedAt,
        )
    }
}

internal fun carrierAggregationViewState(
    snapshot: CarrierAggregation.Snapshot,
    localize: (Int, Int, String) -> String,
): CarrierAggregationViewState? {
    if (!snapshot.visible) return null
    val chips = snapshot.components.map(CarrierAggregation::chipLabel).filter { it.isNotBlank() }
    val n = maxOf(snapshot.components.size, snapshot.bandwidthCount)
    val mhz = CellularBandwidthUtils.formatBandwidthMhz(maxOf(snapshot.aggregateKhz, 0))
    val res = when (snapshot.evidence) {
        CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING -> when (snapshot.kind) {
            CarrierAggregation.Kind.EN_DC -> com.craxiom.networksurvey.R.string.ca_endc_cellinfo
            CarrierAggregation.Kind.LTE_CA -> com.craxiom.networksurvey.R.string.ca_lte_cellinfo
            CarrierAggregation.Kind.NR_CA -> com.craxiom.networksurvey.R.string.ca_nr_cellinfo
            else -> 0
        }
        CarrierAggregation.Evidence.DIAG_B825_CONFIGURED -> com.craxiom.networksurvey.R.string.ca_nr_b825
        CarrierAggregation.Evidence.DIAG_B193_MEASURED_SERVING -> com.craxiom.networksurvey.R.string.ca_lte_b193
        CarrierAggregation.Evidence.SERVICE_STATE_BANDWIDTHS -> when (snapshot.kind) {
            CarrierAggregation.Kind.EN_DC -> com.craxiom.networksurvey.R.string.ca_endc_bandwidths
            CarrierAggregation.Kind.LTE_CA -> com.craxiom.networksurvey.R.string.ca_lte_bandwidths
            CarrierAggregation.Kind.NR_CA -> com.craxiom.networksurvey.R.string.ca_nr_bandwidths
            else -> com.craxiom.networksurvey.R.string.ca_multi_bandwidths
        }
        CarrierAggregation.Evidence.NONE -> 0
    }
    val summary = if (res == 0) CarrierAggregation.englishSummary(snapshot) else localize(res, n, mhz)
    return CarrierAggregationViewState(chips, summary)
}

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
}
