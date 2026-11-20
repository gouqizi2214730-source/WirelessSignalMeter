package com.craxiom.networksurvey.ui.rootdiag

import com.craxiom.networksurvey.model.NrCarrier
import com.craxiom.networksurvey.model.NrCarrier.CaState
import com.craxiom.networksurvey.model.NrCarrier.Identity
import com.craxiom.networksurvey.model.NrCarrier.Role
import com.craxiom.networksurvey.model.NrCarrier.Signal
import org.json.JSONObject

/** Live-only state. Constructed anew for each collector run, never fed cached records. */
internal class RootNrCarriers {
    private data class Configured(val carrier: NrCarrier, val dlArfcn: Int) {
        fun matches(identity: Identity): Boolean =
            identity.isValid && identity.pci() == carrier.identity().pci() &&
                (identity.arfcn() == carrier.identity().arfcn() || identity.arfcn() == dlArfcn)
    }
    private data class Configuration(
        val rows: List<Configured>, val received: Long, val since: Long,
        val confirmed: Boolean,
    ) {
        fun matches(identity: Identity) = rows.first().matches(identity)
    }
    private data class Serving(val identity: Identity, val cid: Long, val observed: Long)
    private data class Sample(val value: Signal, val time: Long, val observed: Long)
    private data class CarrierState(val dl: CaState, val ul: CaState)
    private data class CaSnapshot(val rows: Map<Int, CarrierState>, val time: Long, val observed: Long)

    private var serving: Serving? = null
    private var config: Configuration? = null
    private var caState: CaSnapshot? = null
    private var boundary = 0L
    private val scheduled = mutableMapOf<Int, Long>()
    private val powers = mutableMapOf<Int, Sample>()
    private val ratios = mutableMapOf<Int, Sample>()

    private fun resetSamples() {
        caState = null
        scheduled.clear()
        powers.clear()
        ratios.clear()
    }

    private fun clear(time: Long) {
        config = null
        resetSamples()
        boundary = maxOf(boundary, time)
    }

    fun accept(data: JSONObject, now: Long) {
        val time = data.optLong("t")
        val rat = data.optString("rat")
        when (data.optString("type")) {
            "cell_change" -> if (rat == "NR" || rat == "LTE") {
                val target = Identity(data.optInt("to_pci", -1), data.optInt("to_arfcn", -1))
                // The target may be the DL frequency of the same primary or a config already received.
                if (rat != "NR" || config?.matches(target) != true) clear(time)
            }
            "event" -> if ((rat == "NR" || rat == "LTE") && data.optString("event") in resetEvents) clear(time)
            "rrc" -> if ((rat == "NR" || rat == "LTE") &&
                data.optString("display_msg").ifBlank { data.optString("msg") } in resetMessages) clear(time)
            "serving" -> if (rat == "NR") {
                val identity = data.identity("arfcn")
                val age = data.optLong("seen_age_ms", -1)
                if (!identity.isValid || age !in 0..SERVING_MS) return
                val cid = data.optLong("cid")
                val previous = serving
                if (previous != null && now - previous.observed > CONFIG_HOLD_MS && config?.confirmed == true)
                    clear(time)
                val sameCell = previous != null && (previous.identity == identity ||
                    (config?.matches(previous.identity) == true && config?.matches(identity) == true))
                val cidChanged = sameCell && previous != null && cid > 0 && previous.cid > 0 && cid != previous.cid
                if (previous != null && (!sameCell || cidChanged)) {
                    // A config for the new cell can arrive before its first serving heartbeat.
                    if (config?.matches(identity) != true || cidChanged) clear(time)
                }
                serving = Serving(identity, cid.takeIf { it > 0 }
                    ?: previous?.takeIf { sameCell }?.cid ?: 0,
                    maxOf(now - age, previous?.takeIf { sameCell && !cidChanged }?.observed ?: 0L))
                config = config?.let {
                    if (!it.confirmed && it.matches(identity) && now - it.received in 0..PENDING_CONFIG_MS)
                        it.copy(confirmed = true)
                    else it
                }
            }
            "nr_cfg" -> {
                if (time <= boundary || time < (config?.since ?: 0L)) return
                if (!data.verified("B825")) return
                if (data.optString("state") != "connected") {
                    if (data.optString("state") == "idle") clear(time)
                    return
                }
                val entries = data.optJSONArray("cells")
                val count = data.optInt("ncells")
                val rows = if (entries != null && count in 1..16 && count == entries.length()) {
                    (0 until count).mapNotNull { i ->
                        val row = entries.optJSONObject(i) ?: return@mapNotNull null
                        val cc = row.optInt("cc", -1)
                        val band = row.optInt("band")
                        val id = row.identity("ssb_arfcn")
                        if (cc !in 0..15 || !id.isValid || band in sulBands) return@mapNotNull null
                        Configured(NrCarrier(id, if (cc == 0) Role.PRIMARY else Role.SECONDARY, cc,
                            if (band in 1..1024) "n$band" else "",
                            row.optInt("bw_mhz").takeIf { it in 1..400 } ?: 0, false),
                            row.optInt("dl_arfcn", -1))
                    }.sortedBy { it.carrier.index }
                } else emptyList()
                val primary = rows.firstOrNull()?.carrier?.takeIf { it.index() == 0 }?.identity()
                if (primary == null || primary != data.identity("ssb_arfcn") ||
                    rows.map { it.carrier.index() }.toSet().size != rows.size ||
                    rows.map { it.carrier.identity() }.toSet().size != rows.size) {
                    clear(time)
                    return
                }
                val previous = config
                val currentServing = serving
                val same = previous?.rows == rows &&
                    (previous.confirmed || now - previous.received in 0..PENDING_CONFIG_MS)
                if (!same) resetSamples()
                val anchorAge = currentServing?.let { now - it.observed } ?: -1L
                config = Configuration(rows, now, previous?.since?.takeIf { same } ?: time,
                    currentServing != null && rows.first().matches(currentServing.identity) &&
                        (anchorAge in 0..SERVING_MS ||
                            (same && previous?.confirmed == true && anchorAge in 0..CONFIG_HOLD_MS)))
            }
            "nr_meas", "nr_snr" -> {
                val current = config ?: return
                if (time < current.since || time <= boundary || data.optInt("guess") != 0 ||
                    data.optInt("tentative") != 0 || data.optInt("truncated") != 0) return
                val identity = data.identity("arfcn")
                if (data.optString("type") == "nr_meas") {
                    if (data.optString("src") != "B97F" || data.optInt("serving", 1) != 1) return
                    // Only use the layer's serving PCI, never a neighbor entry or a layer ordinal as a CC index.
                    val cc = current.rows.singleOrNull { it.carrier.identity() == identity }?.carrier?.index() ?: return
                    if (time < (powers[cc]?.time ?: 0L)) return
                    val rsrp = data.optDouble("rsrp").takeUnless { it == -156.0 } ?: Double.NaN
                    val rsrq = data.optDouble("rsrq").takeUnless { it == -43.0 } ?: Double.NaN
                    powers[cc] = Sample(Signal.ss(rsrp, rsrq, Double.NaN), time, now)
                } else {
                    if (data.optString("rs") != "SSB" || data.optString("src") !in ssSinrSources) return
                    val cc = data.optInt("carrier", -1)
                    val row = current.rows.firstOrNull { it.carrier.index() == cc } ?: return
                    if (data.optInt("pci", -1) != row.carrier.identity().pci() ||
                        time < (ratios[cc]?.time ?: 0L)) return
                    if (cc == 0) {
                        if (!row.matches(identity)) return
                    } else {
                        val power = powers[cc] ?: return
                        if (data.optString("src") != "B8D8" || data.optString("ver") != "3.2" ||
                            data.has("arfcn") || now - power.observed !in 0..SIGNAL_JOIN_MS ||
                            time - power.time !in 0..SIGNAL_JOIN_MS) return
                    }
                    ratios[cc] = Sample(Signal.ss(Double.NaN, Double.NaN, data.optDouble("sinr")), time, now)
                }
            }
            "nr_dl_sched" -> {
                val current = config ?: return
                val window = data.optLong("dt_ms", -1)
                if (!data.verified("B887") || window !in 1..SCHEDULE_MS ||
                    !current.matches(data.identity("arfcn")) ||
                    time - window < current.since || time - window <= boundary) return
                val entries = data.optJSONArray("cc") ?: return
                val cc0 = (0 until entries.length()).any { i ->
                    val row = entries.optJSONObject(i)
                    row != null && row.optInt("cc", -1) == 0 && row.optLong("tb") > 0 &&
                        current.matches(row.identity("arfcn"))
                }
                val recentAnchor = serving?.let {
                    current.matches(it.identity) && now - it.observed in 0..CONFIG_HOLD_MS
                } == true
                if (cc0 && ((current.confirmed && recentAnchor) ||
                        now - current.received in 0..PENDING_CONFIG_MS)) {
                    val primary = serving
                    val identity = data.identity("arfcn")
                    serving = Serving(identity, primary?.takeIf { current.matches(it.identity) }?.cid ?: 0, now)
                    config = current.copy(confirmed = true)
                }
                val primary = serving ?: return
                if (config?.confirmed != true || !current.matches(primary.identity) ||
                    now - primary.observed !in 0..SERVING_MS) return
                for (i in 0 until entries.length()) {
                    val row = entries.optJSONObject(i) ?: continue
                    val cc = row.optInt("cc", -1)
                    val carrier = current.rows.firstOrNull { it.carrier.index() == cc } ?: continue
                    if (row.optLong("tb") > 0 && carrier.matches(row.identity("arfcn"))) {
                        scheduled[cc] = now
                    }
                }
            }
        }
    }

    /** A valid B9A7 packet supplements B825; it cannot create a serving cell or replace its config. */
    fun acceptCaState(data: JSONObject, now: Long): String {
        if (!data.verified("B9A7")) return "unverified"
        val current = config ?: return "no_configuration"
        val time = data.optLong("t")
        val received = data.optLong("received_time_ms")
        if (time <= boundary || time < current.since || time < (caState?.time ?: 0L) ||
            (received > 0 && received - time !in 0..MAX_PACKET_DELAY_MS)) return "stale"
        val rows = data.optJSONArray("cc") ?: return "invalid"
        if (rows.length() !in 0..16) return "invalid"
        val matched = mutableMapOf<Int, CarrierState>()
        val seen = mutableSetOf<Int>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: return "invalid"
            val cc = row.optInt("cc", -1)
            val dl = row.optInt("dl", -1)
            val ul = row.optInt("ul", -1)
            if (cc !in 0..15 || !seen.add(cc) || dl !in 0..3 || ul !in 0..3) return "invalid"
            if (row.optInt("band") in sulBands) continue
            val id = row.identity("ssb_arfcn")
            val configured = current.rows.firstOrNull { it.carrier.index() == cc && it.matches(id) }
            if (configured == null) {
                // Ignore unidentifiable placeholders, but never mix two active carrier groups.
                if ((id.isValid && cc == 0) || (cc != 0 && (dl in 1..2 || ul in 1..2)))
                    return "identity_mismatch"
                continue
            }
            val band = row.optInt("band")
            if (band in 1..1024 && configured.carrier.band().isNotEmpty() &&
                configured.carrier.band() != "n$band") return "identity_mismatch"
            matched[cc] = CarrierState(decodeState(dl), decodeState(ul))
        }
        if (matched.isEmpty() && rows.length() != 0) return "no_match"
        caState = CaSnapshot(matched, time, now)
        return if (matched.isEmpty()) "empty" else "matched"
    }

    fun snapshot(now: Long): List<NrCarrier> {
        val primary = serving ?: return emptyList()
        val current = config ?: return emptyList()
        val age = now - primary.observed
        if (!current.confirmed || !current.matches(primary.identity) ||
            age !in 0..CONFIG_HOLD_MS) return emptyList()
        if (age > SERVING_MS) return current.rows.map { it.carrier.asLastObservation() }
        val states = caState?.takeIf { now - it.observed in 0..CA_STATE_MS }?.rows.orEmpty()
        return current.rows.map { (row, _) ->
            val downlink = scheduled[row.index()]?.let { now - it in 0..SCHEDULE_MS } == true
            val power = powers[row.index()]?.takeIf { now - it.observed in 0..SIGNAL_MS }?.value
            val ratio = ratios[row.index()]?.takeIf { now - it.observed in 0..SIGNAL_MS }?.value
            val state = states[row.index()]
            NrCarrier(row.identity(), row.role(), row.index(), row.band(), row.bandwidthMhz(), downlink,
                Signal(power?.rsrp(), power?.rsrq(), ratio?.sinr()), 0, false,
                state?.dl ?: CaState.UNKNOWN, state?.ul ?: CaState.UNKNOWN)
        }
    }

    private fun JSONObject.identity(channel: String) = Identity(optInt("pci", -1), optInt(channel, -1))
    private fun JSONObject.verified(source: String) =
        optString("src") == source && optInt("tentative", -1) == 0 && optInt("guess") == 0

    private companion object {
        const val PENDING_CONFIG_MS = 15_000L
        const val SERVING_MS = 10_000L
        const val CONFIG_HOLD_MS = 30_000L
        const val CA_STATE_MS = 15_000L
        const val MAX_PACKET_DELAY_MS = 5_000L
        const val SCHEDULE_MS = 4_000L
        const val SIGNAL_MS = 5_000L
        const val SIGNAL_JOIN_MS = 2_000L
        val ssSinrSources = setOf("B8D8", "B8DD")
        val sulBands = setOf(80, 81, 82, 83, 84, 86, 89, 95, 97, 98, 99)
        fun decodeState(value: Int) = when (value) {
            0 -> CaState.DECONFIGURED
            1 -> CaState.CONFIGURED
            2 -> CaState.ACTIVATED
            else -> CaState.UNKNOWN
        }
        val resetEvents = setOf("rrc_release", "rrc_setup", "rrc_reestablish", "scg_failure", "inter_rat_mobility")
        val resetMessages = setOf("rrcRelease", "rrcConnectionRelease", "rrcSetup", "rrcConnectionSetup",
            "rrcReestablishmentRequest", "rrcConnectionReestablishmentRequest",
            "scgFailureInformation", "scgFailureInformationEUTRA", "scgFailureInformationNR-r15")
    }
}
