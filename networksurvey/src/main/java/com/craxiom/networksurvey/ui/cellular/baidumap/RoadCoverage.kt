package com.craxiom.networksurvey.ui.cellular.baidumap

import com.craxiom.networksurvey.model.MapPointRecord
import com.craxiom.networksurvey.model.SurveyEvent
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val ROAD_SAMPLE_LIMIT = 50_000
internal const val WEAK_RSRP_DBM = -110.0
private const val SESSION_GAP_MS = 120_000L
private const val PATH_GAP_MS = 30_000L

internal data class RoadSample(
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val rsrp: Double?,
    val sinr: Double?,
    val rsrq: Double?,
    val rat: String?,
    val pci: Int?,
    val arfcn: Int?,
    val plmn: String?,
    val simSlot: Int?,
    val cellId: Long? = null,
    val baseStationId: Int? = null,
    val tac: Int? = null,
    val taskId: String? = null,
    val missionId: String? = null,
    val subscriptionId: Int? = null,
    val elapsedMs: Long? = null,
    val clockId: String? = null,
    val locationAgeMs: Long? = null,
    val locationAccuracyM: Double? = null,
    val locationProvider: String? = null,
) {
    val sourceKey: String get() = subscriptionId?.let { "sub$it" } ?: simSlot?.let { "sim$it" } ?: plmn.orEmpty()
}

internal data class RoadTrack(val samples: List<RoadSample>) {
    val startTime get() = samples.first().time
    val endTime get() = samples.last().time
    val simSlot get() = samples.first().simSlot
    val plmn get() = samples.lastOrNull { it.plmn != null }?.plmn
    val taskId get() = samples.first().taskId
}

internal data class RoadSection(val firstIndex: Int, val lastIndex: Int, val averageRsrp: Double)

internal data class RoadReport(
    val samplesWithRsrp: Int,
    val weakSamples: Int,
    val averageRsrp: Double?,
    val distanceMeters: Double,
    val weakSections: List<RoadSection>,
)

internal fun MapPointRecord.toRoadSample(): RoadSample? {
    if (!validRoadCoordinate(latitude, longitude) || timestamp <= 0) return null
    return RoadSample(
        timestamp, latitude, longitude,
        rsrp.toDouble().inRange(-160.0, -20.0),
        sinr.toDouble().inRange(-40.0, 50.0),
        rsrq.toDouble().inRange(-45.0, 10.0),
        roadRat(netType), pci.takeIf { it in 0..1007 }, earfcn.takeIf { it in 0..3_279_165 },
        normalizePlmn(plmnDisplay), simSlot.takeIf { it >= 0 },
        cellId.takeIf { it > 0 }, gnbId.takeIf { it > 0 }, tac.takeIf { it > 0 },
        taskId, missionId, subscriptionId.takeIf { it >= 0 }, elapsedRealtimeMs.takeIf { it > 0 },
        clockId, locationAgeMs.takeIf { it >= 0 }, locationAccuracyM.toDouble().takeIf { it >= 0 }, locationProvider,
    )
}

internal fun roadTracks(samples: List<RoadSample>): List<RoadTrack> = buildList {
    for (source in samples.groupBy { it.taskId to it.sourceKey }.values) {
        val ordered = source.sortedBy(RoadSample::time)
        var start = 0
        for (i in 1 until ordered.size) {
            if (ordered[i].time - ordered[i - 1].time > SESSION_GAP_MS ||
                ordered[i].clockId != ordered[i - 1].clockId) {
                add(RoadTrack(ordered.subList(start, i)))
                start = i
            }
        }
        add(RoadTrack(ordered.subList(start, ordered.size)))
    }
}.sortedByDescending(RoadTrack::endTime)

internal fun roadDistance(a: RoadSample, b: RoadSample): Double? {
    val delta = b.time - a.time
    if (delta !in 0..PATH_GAP_MS) return null
    val rad = Math.PI / 180
    val lat = (b.latitude - a.latitude) * rad
    val lon = (b.longitude - a.longitude) * rad
    val h = sin(lat / 2) * sin(lat / 2) +
        cos(a.latitude * rad) * cos(b.latitude * rad) * sin(lon / 2) * sin(lon / 2)
    val distance = 12_742_000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    // Allow ordinary GPS drift, while skipping jumps that cannot represent this route.
    return distance.takeIf { it <= 30 + delta / 1000.0 * 140 }
}

internal data class RoadEventLink(val event: SurveyEvent, val trackIndex: Int?, val sampleIndex: Int?,
                                  val devicePositionOnly: Boolean = false)

/** Refuse ambiguous modem/SIM ownership and stale fixes instead of putting events on the wrong road. */
internal fun linkRoadEvents(tracks: List<RoadTrack>, events: List<SurveyEvent>): List<RoadEventLink> {
    data class Located(val track: Int, val index: Int, val sample: RoadSample, val stamp: Long)
    // Index each clock domain once. Matching a long route must not scan 50,000 points per event.
    val groups = tracks.flatMapIndexed { trackIndex, track ->
        track.samples.mapIndexedNotNull { index, point ->
            point.taskId ?: return@mapIndexedNotNull null
            val stamp = if (!point.clockId.isNullOrBlank()) point.elapsedMs ?: return@mapIndexedNotNull null else point.time
            Located(trackIndex, index, point, stamp)
        }
    }.groupBy { it.sample.taskId to it.sample.clockId.orEmpty() }.mapValues { (_, rows) -> rows.sortedBy { it.stamp } }
    return events.map { event ->
        val deviceOnly = event.source == "speedtest"
        val rows = groups[event.taskId to event.clockId] ?: emptyList()
        val legacy = groups[event.taskId to ""].orEmpty()
        val candidates = buildList {
            for (domain in listOf(rows, legacy).distinct()) {
                if (domain.isEmpty()) continue
                val stamp = if (domain.first().sample.clockId.isNullOrBlank()) event.time else event.elapsedMs
                var lo = 0
                var hi = domain.size
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (domain[mid].stamp < stamp - 10_000) lo = mid + 1 else hi = mid
                }
                for (i in lo until domain.size) {
                    val row = domain[i]
                    if (row.stamp > stamp + 10_000) break
                    val p = row.sample
                    val ownership = when {
                        deviceOnly -> true
                        event.subscriptionId != null -> p.subscriptionId == event.subscriptionId
                        event.simSlot != null -> p.simSlot == event.simSlot
                        else -> event.rat != null && event.pci != null && event.arfcn != null &&
                            p.rat == event.rat && p.pci == event.pci && p.arfcn == event.arfcn
                    }
                    if (ownership && (event.rat == null || p.rat == event.rat) &&
                        (event.pci == null || p.pci == event.pci) && (event.arfcn == null || p.arfcn == event.arfcn) &&
                        (p.locationAgeMs == null || p.locationAgeMs in 0..10_000) &&
                        (p.locationAccuracyM == null || p.locationAccuracyM in 0.0..100.0))
                        add(row to kotlin.math.abs(row.stamp - stamp))
                }
            }
        }
        val sources = candidates.map { it.first.sample.sourceKey }.distinct()
        val nearest = candidates.minByOrNull { it.second }?.first
        val consistentDevicePosition = deviceOnly && nearest != null && candidates.all { (row, _) ->
            val a = nearest.sample
            val b = row.sample
            // Dual SIMs may share a device fix; divergent locations are not interchangeable.
            roadDistance(a.copy(time = 1), b.copy(time = 1))?.let {
                it <= 30
            } == true
        }
        val match = if (sources.size == 1 || consistentDevicePosition) nearest else null
        RoadEventLink(event, match?.track, match?.index, deviceOnly && match != null)
    }
}

internal fun analyzeRoad(samples: List<RoadSample>): RoadReport {
    val sections = mutableListOf<RoadSection>()
    var start = -1
    var sum = 0.0
    var weak = 0
    var measured = 0
    var distance = 0.0
    fun closeSection(end: Int) {
        if (start >= 0 && end - start >= 2 && samples[end].time - samples[start].time >= 5_000) {
            val average = (start..end).mapNotNull { samples[it].rsrp }.average()
            sections.add(RoadSection(start, end, average))
        }
        start = -1
    }
    for ((index, sample) in samples.withIndex()) {
        if (index > 0) {
            val traveled = roadDistance(samples[index - 1], sample)
            if (traveled == null) closeSection(index - 1) else distance += traveled
        }
        val rsrp = sample.rsrp
        if (rsrp != null) {
            measured++
            sum += rsrp
        }
        if (rsrp != null && rsrp < WEAK_RSRP_DBM) {
            weak++
            if (start < 0) start = index
        } else {
            closeSection(index - 1)
        }
    }
    closeSection(samples.lastIndex)
    return RoadReport(measured, weak, if (measured > 0) sum / measured else null, distance, sections)
}

internal class RoadCsvFormatException : IOException()

internal fun readRoadCsv(input: Reader): List<RoadSample> {
    val reader = input.buffered()
    reader.mark(1)
    if (reader.read() != 0xFEFF) reader.reset()
    val format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
        .setIgnoreSurroundingSpaces(true).setMaxRows(ROAD_SAMPLE_LIMIT.toLong() + 1).get()
    return format.parse(reader).use { csv ->
        val headers = csv.headerNames.map(::normalizeHeader)
        fun field(vararg names: String) = names.firstNotNullOfOrNull { name ->
            headers.indexOf(normalizeHeader(name)).takeIf { it >= 0 }
        }
        val latitude = field("latitude", "lat", "纬度", "BIN_LAT") ?: throw RoadCsvFormatException()
        val longitude = field("longitude", "lon", "lng", "经度", "BIN_LON") ?: throw RoadCsvFormatException()
        val time = field("timestamp_ms", "timestamp", "deviceTime", "Time Stamp", "时间", "操作时间", "time")
            ?: throw RoadCsvFormatException()
        val nrRsrp = field("ssRsrp", "NR_RSRP")
        val rsrp = field("RSRP(dBm)", "LTE_RSRP", "Serving RSRP", "rsrp_dbm", "rsrp")
        val sinr = field("SINR(dB)", "sinr_db", "ssSinr", "LTE_SINR", "NR_SINR", "sinr", "snr", "rssnr")
        val rsrq = field("RSRQ(dB)", "rsrq_db", "ssRsrq", "LTE_RSRQ", "NR_RSRQ", "rsrq")
        if (nrRsrp == null && rsrp == null && sinr == null && rsrq == null) throw RoadCsvFormatException()
        val rat = field("net_type", "网络制式", "RAT", "network_type")
        val slot = field("sim_slot", "slot")
        val serving = field("servingCell", "isServing", "is_registered")
        val pci = field("pci", "NR_PCI", "Cell_PCI")
        val channel = field("arfcn", "earfcn", "nrarfcn", "narfcn", "SSB_ARFCN", "频点")
        val plmn = field("plmn")
        val mcc = field("mcc")
        val mnc = field("mnc")
        val cellId = field("cell_id", "ECI", "NCI", "小区ID")
        val stationId = field("base_station_id", "gNB ID", "eNB ID", "基站ID")
        val tac = field("tac")
        val task = field("task_id")
        val mission = field("mission_id")
        val subscription = field("subscription_id")
        val elapsed = field("elapsed_ms")
        val clock = field("clock_id")
        val locationAge = field("location_age_ms")
        val accuracy = field("location_accuracy_m")
        val provider = field("location_provider")
        val nonServing = setOf("false", "0", "no")
        buildList {
            for ((i, row) in csv.withIndex()) {
                if (i == ROAD_SAMPLE_LIMIT) throw RoadCsvFormatException()
                fun value(index: Int?): String? =
                    index?.takeIf { it < row.size() }?.let { row.get(it).trim().takeIf(String::isNotEmpty) }
                if (value(serving)?.lowercase(Locale.ROOT) in nonServing) continue
                val lat = value(latitude)?.toDoubleOrNull() ?: continue
                val lon = value(longitude)?.toDoubleOrNull() ?: continue
                if (!validRoadCoordinate(lat, lon)) continue
                val stamp = parseRoadTime(value(time)) ?: continue
                val ssRsrp = value(nrRsrp)?.toDoubleOrNull()?.inRange(-160.0, -20.0)
                val power = ssRsrp ?: value(rsrp)?.toDoubleOrNull()?.inRange(-160.0, -20.0)
                val network = roadRat(value(rat)) ?: when {
                    ssRsrp != null -> "NR"
                    channel != null && headers[channel] == "earfcn" -> "LTE"
                    else -> null
                }
                val operator = normalizePlmn(value(plmn)) ?: value(mcc)?.let { a ->
                    value(mnc)?.let { b -> normalizePlmn("$a-$b") }
                }
                add(RoadSample(
                    stamp, lat, lon, power,
                    value(sinr)?.toDoubleOrNull()?.inRange(-40.0, 50.0),
                    value(rsrq)?.toDoubleOrNull()?.inRange(-45.0, 10.0),
                    network, value(pci)?.toIntOrNull()?.takeIf { it in 0..1007 },
                    value(channel)?.toIntOrNull()?.takeIf { it in 0..3_279_165 },
                    operator, value(slot)?.toIntOrNull()?.takeIf { it >= 0 },
                    value(cellId)?.toLongOrNull()?.takeIf { it in 1..68_719_476_735L },
                    value(stationId)?.toIntOrNull()?.takeIf { it in 1..67_108_863 },
                    value(tac)?.toIntOrNull()?.takeIf { it in 1..16_777_215 },
                    value(task), value(mission), value(subscription)?.toIntOrNull()?.takeIf { it >= 0 },
                    value(elapsed)?.toLongOrNull()?.takeIf { it >= 0 }, value(clock),
                    value(locationAge)?.toLongOrNull()?.takeIf { it >= 0 },
                    value(accuracy)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }, value(provider),
                ))
            }
        }
    }
}

internal fun writeRoadCsv(out: Writer, samples: List<RoadSample>) {
    out.write(0xFEFF)
    val columns = arrayOf("timestamp_ms", "time", "longitude", "latitude", "rsrp_dbm", "sinr_db",
        "rsrq_db", "net_type", "pci", "arfcn", "plmn", "sim_slot", "cell_id", "base_station_id", "tac", "weak_coverage",
        "task_id", "mission_id", "subscription_id", "elapsed_ms", "clock_id", "location_age_ms", "location_accuracy_m", "location_provider")
    CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(*columns).get()).use { csv ->
        for (point in samples) {
            csv.printRecord(point.time, Instant.ofEpochMilli(point.time), point.longitude, point.latitude,
                point.rsrp, point.sinr, point.rsrq, point.rat, point.pci, point.arfcn, point.plmn,
                point.simSlot, point.cellId, point.baseStationId, point.tac, point.rsrp?.let { it < WEAK_RSRP_DBM },
                point.taskId, point.missionId, point.subscriptionId, point.elapsedMs, point.clockId,
                point.locationAgeMs, point.locationAccuracyM, point.locationProvider)
        }
    }
}

private val headerPunctuation = Regex("[^\\p{L}\\p{N}]")
private val plmnPattern = Regex("([0-9]{3})[-/]?([0-9]{1,3})")
private fun normalizeHeader(text: String) = text.trim().lowercase(Locale.ROOT).replace(headerPunctuation, "")
private fun normalizePlmn(value: String?): String? {
    val parts = value?.let(plmnPattern::matchEntire)?.groupValues ?: return null
    return "${parts[1]}-${parts[2].padStart(2, '0')}"
}

private fun parseRoadTime(value: String?): Long? {
    if (value == null) return null
    value.toLongOrNull()?.let {
        return when {
            it in 1_000_000_000L..9_999_999_999L -> it * 1000
            it in 1_000_000_000_000L..9_999_999_999_999L -> it
            else -> null
        }
    }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
}

private fun validRoadCoordinate(lat: Double, lon: Double) =
    lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)

private fun Double.inRange(min: Double, max: Double) = takeIf { it in min..max }

private fun roadRat(value: String?) = when (value?.uppercase(Locale.ROOT)) {
    "NR", "5G", "NR SA", "NR NSA", "5G SA", "5G NSA" -> "NR"
    "LTE", "4G", "LTE-A", "LTE_CA" -> "LTE"
    else -> null
}
