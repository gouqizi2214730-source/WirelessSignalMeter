package com.craxiom.networksurvey.ui.rootdiag

import org.json.JSONArray
import org.json.JSONObject

internal enum class NrMeasurementReference(val label: String, val channelKey: String, val cellKey: String, val beamKey: String) {
    Ssb("SSB", "ssb_arfcn", "resultsSSB-Cell", "ssb_beam_sinr_index"),
    CsiRs("CSI-RS", "csi_arfcn", "resultsCSI-RS-Cell", "csi_beam_sinr_index"),
}

internal data class RrcNeighborSinr(
    val channel: Int, val pci: Int, val index: Int, val reference: NrMeasurementReference? = null, val beam: Boolean = false,
)

internal fun JSONObject.rrcNeighborSinr(carrier: JSONObject): List<RrcNeighborSinr> = buildList {
    when (carrier.optString("rat")) {
        "LTE" -> {
            val channel = carrier.optInt("earfcn", -1)
            if (channel !in 0..262_143) return@buildList
            optJSONArray("measResultListEUTRA").forEachObject { cell ->
                val pci = cell.optInt("physCellId", -1)
                val index = cell.optJSONObject("measResult")?.optInt("sinr_index", -1) ?: -1
                if (pci in 0..503 && index in 0..127) add(RrcNeighborSinr(channel, pci, index))
            }
        }
        "NR" -> optJSONArray("measResultListNR").forEachObject { cell ->
            for (reference in NrMeasurementReference.entries) {
                cell.nrNeighborSinr(carrier.optInt(reference.channelKey, -1), reference)?.let(::add)
            }
        }
    }
}

private fun JSONObject.nrNeighborSinr(channel: Int, reference: NrMeasurementReference): RrcNeighborSinr? {
    val pci = optInt("physCellId", -1)
    if (pci !in 0..1007 || channel !in 1..3_279_165) return null
    val cellIndex = optJSONObject("cellResults")?.optJSONObject(reference.cellKey)?.optInt("sinr_index", -1) ?: -1
    val beam = cellIndex !in 0..127
    val index = if (beam) optInt(reference.beamKey, -1) else cellIndex
    return if (index in 0..127) RrcNeighborSinr(channel, pci, index, reference, beam) else null
}

internal fun JSONObject.nrServingObjectNeighborSinr(servingPci: Int, ssbChannel: Int): RrcNeighborSinr? {
    val best = optJSONObject("measResultBestNeighCell") ?: return null
    if (servingPci !in 0..1007 || best.optInt("servCellId", -1) != 0 || best.optInt("physCellId", -1) == servingPci) return null
    // The best neighbour belongs to this serving cell's measurement object, not to the triggering measId.
    val reference = NrMeasurementReference.Ssb
    optJSONArray("measResultServingMOList").forEachObject { cell ->
        if (cell.optInt("servCellId", -1) == 0 && cell.optInt("physCellId", -1) == servingPci &&
            cell.optJSONObject("cellResults")?.has(reference.cellKey) == true
        ) return best.nrNeighborSinr(ssbChannel, reference)
    }
    return null
}

/** The measurement IDs of an RRC connection refer to objects that can be updated independently. */
internal class RrcMeasurementConfig {
    private data class Link(val objectId: Int, val reportId: Int)
    private data class Carrier(val rat: String, val ssb: Int?, val csi: Int?, val earfcn: Int?) {
        fun toJson() = JSONObject().put("rat", rat).apply {
            ssb?.let { put("ssb_arfcn", it) }
            csi?.let { put("csi_arfcn", it) }
            earfcn?.let { put("earfcn", it) }
        }
    }

    private val carriers = mutableMapOf<Int, Carrier>()
    private val links = mutableMapOf<Int, Link>()

    fun clear() {
        carriers.clear()
        links.clear()
    }

    fun update(config: JSONObject): Set<Int> {
        val removedObjects = config.ids("remove_objects")
        val removedReports = config.ids("remove_reports")
        val affected = config.ids("remove_ids").toMutableSet()
        for ((id, link) in links) {
            if (link.objectId in removedObjects || link.reportId in removedReports) affected += id
        }
        removedObjects.forEach(carriers::remove)
        affected.forEach(links::remove)
        config.optJSONArray("objects").forEachObject { data ->
            val id = data.id("id") ?: return@forEachObject
            val rat = data.optString("rat")
            val next = when (rat) {
                "NR" -> Carrier(rat, data.channel("ssb_arfcn", 3_279_165), data.channel("csi_arfcn", 3_279_165), null)
                "LTE" -> Carrier(rat, null, null, data.channel("earfcn", 262_143))
                else -> null
            }
            if (carriers[id] != next) {
                for ((measId, link) in links) if (link.objectId == id) affected += measId
            }
            if (next != null) carriers[id] = next else carriers.remove(id)
        }
        config.optJSONArray("links").forEachObject { data ->
            val id = data.id("id") ?: return@forEachObject
            val objectId = data.id("object_id")
            val reportId = data.id("report_id")
            if (objectId == null || reportId == null) {
                affected += id
                links.remove(id)
            } else {
                val next = Link(objectId, reportId)
                if (links.put(id, next) != next) affected += id
            }
        }
        return affected
    }

    fun carrierFor(measId: Int): JSONObject? = links[measId]?.let { carriers[it.objectId]?.toJson() }

    private fun JSONObject.id(key: String) = optInt(key, -1).takeIf { it in 1..64 }

    private fun JSONObject.channel(key: String, max: Int) = optInt(key, -1).takeIf { it in 0..max }

    private fun JSONObject.ids(key: String): Set<Int> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) array.optInt(index, -1).takeIf { it in 1..64 }?.let(::add)
        }
    }

}

private inline fun JSONArray?.forEachObject(visit: (JSONObject) -> Unit) {
    if (this == null) return
    for (index in 0 until length()) optJSONObject(index)?.let(visit)
}
