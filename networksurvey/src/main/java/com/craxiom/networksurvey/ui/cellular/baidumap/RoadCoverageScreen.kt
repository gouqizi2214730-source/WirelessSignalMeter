package com.craxiom.networksurvey.ui.cellular.baidumap

import android.graphics.Color as MapColor
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baidu.mapapi.map.Dot
import com.baidu.mapapi.map.DotOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.db.MapPointDbHelper
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.util.CoordConverter
import com.craxiom.networksurvey.util.SignalGrade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun RoadCoverageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val points = remember(context) { MapPointDbHelper.getInstance(context) }
    var tracks by remember { mutableStateOf<List<RoadTrack>>(emptyList()) }
    var selectedTrack by remember { mutableIntStateOf(0) }
    var selectedSample by remember { mutableIntStateOf(0) }
    var focusSample by remember { mutableIntStateOf(-1) }
    var chooseTrack by remember { mutableStateOf(false) }
    var showWeakSections by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var sinr by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    var limited by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(MapTrack.isEnabled(context)) }
    val track = tracks.getOrNull(selectedTrack)
    val sample = track?.samples?.getOrNull(selectedSample)
    var report by remember(track) { mutableStateOf<RoadReport?>(null) }
    LaunchedEffect(track) {
        report = track?.let { withContext(Dispatchers.Default) { analyzeRoad(it.samples) } }
    }

    fun replaceTracks(next: List<RoadTrack>, fromFile: Boolean, isLimited: Boolean) {
        playing = false
        selectedTrack = 0
        selectedSample = 0
        focusSample = -1
        imported = fromFile
        limited = isLimited
        tracks = next
    }
    fun launchIo(error: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }
    fun loadSaved() = launchIo(R.string.road_read_failed) {
        val (next, isLimited) = withContext(Dispatchers.IO) {
            val recent = points.getRecentForReplay(ROAD_SAMPLE_LIMIT + 1)
            roadTracks(recent.take(ROAD_SAMPLE_LIMIT).asReversed().mapNotNull { it.toRoadSample() }) to
                (recent.size > ROAD_SAMPLE_LIMIT)
        }
        replaceTracks(next, fromFile = false, isLimited = isLimited)
    }
    LaunchedEffect(points) { loadSaved() }

    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchIo(R.string.road_import_failed) {
            val next = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException()
                roadTracks(input.bufferedReader(Charsets.UTF_8).use(::readRoadCsv))
            }
            if (next.isEmpty()) throw RoadCsvFormatException()
            replaceTracks(next, fromFile = true, isLimited = false)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && track != null) launchIo(R.string.road_export_failed) {
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri) ?: throw IOException()
                output.bufferedWriter(Charsets.UTF_8).use { writeRoadCsv(it, track.samples) }
            }
            Toast.makeText(context, R.string.road_exported, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(playing, track) {
        if (!playing || track == null) return@LaunchedEffect
        val last = track.samples.lastIndex
        val initial = track.samples[selectedSample].time
        val started = SystemClock.elapsedRealtime()
        while (selectedSample < last) {
            val playTime = initial + (SystemClock.elapsedRealtime() - started) * 10
            var lo = selectedSample
            var hi = last
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (track.samples[mid].time <= playTime) lo = mid else hi = mid - 1
            }
            selectedSample = lo
            delay(80)
        }
        playing = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TitleBar(stringResource(R.string.tool_road_coverage_title), compact = true, onBackClick = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = !busy, onClick = {
                    importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }) { Text(stringResource(R.string.road_import)) }
                TextButton(enabled = !busy, onClick = ::loadSaved) { Text(stringResource(R.string.road_reload)) }
                TextButton(enabled = !busy && track != null, onClick = {
                    export.launch("wireless-route-${track?.startTime}.csv")
                }) { Text(stringResource(R.string.road_export)) }
            }
            if (track == null) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator()
                    else Text(stringResource(R.string.road_empty), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.weight(1f), onClick = { chooseTrack = true }) {
                        Text(stringResource(R.string.road_track_picker, selectedTrack + 1, tracks.size))
                    }
                    FilterChip(!sinr, { sinr = false }, label = { Text("RSRP") })
                    FilterChip(sinr, { sinr = true }, label = { Text("SINR") })
                }
                if (limited) Text(stringResource(R.string.road_recent_limit),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                RoadMap(track, selectedSample, focusSample, sinr, Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = track.samples.size > 1, onClick = {
                            if (!playing && selectedSample == track.samples.lastIndex) selectedSample = 0
                            focusSample = -1
                            playing = !playing
                        }) { Text(stringResource(if (playing) R.string.road_pause else R.string.road_play)) }
                        Slider(
                            value = selectedSample.toFloat(),
                            onValueChange = {
                                playing = false
                                focusSample = -1
                                selectedSample = it.roundToInt().coerceIn(track.samples.indices)
                            },
                            valueRange = 0f..track.samples.lastIndex.coerceAtLeast(1).toFloat(),
                            enabled = track.samples.size > 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    sample?.let {
                        val metric = if (sinr) it.sinr else it.rsrp
                        val suffix = if (sinr) "dB" else "dBm"
                        val information = listOfNotNull(
                            roadDate.format(Instant.ofEpochMilli(it.time)),
                            it.rat,
                            it.pci?.let { pci -> "PCI $pci" },
                            metric?.let { level -> "${formatRoadValue(level)} $suffix" } ?: "—",
                        ).joinToString(" · ")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(9.dp).background(Color(roadColor(it, sinr)), CircleShape))
                            Text(information, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    report?.let { result ->
                        val percent = if (result.samplesWithRsrp == 0) "—" else
                            "${formatRoadValue(result.weakSamples * 100.0 / result.samplesWithRsrp)}%"
                        val average = result.averageRsrp?.let { "${formatRoadValue(it)} dBm" } ?: "—"
                        Text(stringResource(R.string.road_summary, track.samples.size, formatRoadValue(result.distanceMeters / 1000)),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.road_weak_summary, average, percent),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = result.weakSections.isNotEmpty(), onClick = { showWeakSections = true }) {
                            Text(stringResource(R.string.road_weak_sections, result.weakSections.size))
                        }
                    }
                }
            }
            if (!imported) TextButton(
                enabled = !busy,
                onClick = {
                    tracking = !tracking
                    MapTrack.setEnabled(context, tracking)
                    if (!tracking) loadSaved()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(if (tracking) R.string.road_stop_recording else R.string.road_start_recording))
            }
        }
    }
    if (chooseTrack) AlertDialog(
        onDismissRequest = { chooseTrack = false },
        title = { Text(stringResource(R.string.road_choose_track)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(tracks) { index, item ->
                    val sim = item.simSlot?.let { stringResource(R.string.road_sim, it + 1) }
                    val time = roadDate.format(Instant.ofEpochMilli(item.startTime))
                    val end = roadClock.format(Instant.ofEpochMilli(item.endTime))
                    TextButton(onClick = {
                        playing = false
                        selectedSample = 0
                        focusSample = -1
                        selectedTrack = index
                        chooseTrack = false
                    }) {
                        Text(listOfNotNull("$time – $end", sim, item.plmn).joinToString(" · "))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { chooseTrack = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    if (showWeakSections && track != null) AlertDialog(
        onDismissRequest = { showWeakSections = false },
        title = { Text(stringResource(R.string.road_weak_sections, report?.weakSections?.size ?: 0)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(report?.weakSections.orEmpty()) { _, section ->
                    val start = roadClock.format(Instant.ofEpochMilli(track.samples[section.firstIndex].time))
                    val end = roadClock.format(Instant.ofEpochMilli(track.samples[section.lastIndex].time))
                    TextButton(onClick = {
                        playing = false
                        selectedSample = section.firstIndex
                        focusSample = section.firstIndex
                        showWeakSections = false
                    }) { Text("$start – $end · ${formatRoadValue(section.averageRsrp)} dBm") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showWeakSections = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private data class RoadLine(val points: List<LatLng>, val colors: List<Int>)

@Composable
private fun RoadMap(track: RoadTrack, index: Int, focus: Int, sinr: Boolean, modifier: Modifier) {
    var view by remember { mutableStateOf<TextureMapView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var dot by remember { mutableStateOf<Dot?>(null) }
    BaiduMapView(
        onCreate = { created ->
            view = created
            created.showZoomControls(false)
            created.map.setOnMapLoadedCallback { if (view === created) ready = true }
        },
        onRelease = { released ->
            if (view === released) {
                dot = null
                ready = false
                view = null
            }
        },
        modifier = modifier,
    )
    LaunchedEffect(view, ready, track, sinr) {
        val map = view?.map ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val lines = withContext(Dispatchers.Default) { roadLines(track.samples, sinr) }
        dot = null
        map.clear()
        for (line in lines) {
            if (line.points.size >= 2) {
                map.addOverlay(PolylineOptions().points(line.points).colorsValues(line.colors.drop(1))
                    .width(12).zIndex(3))
            } else if (lines.size <= 1024) {
                map.addOverlay(DotOptions().center(line.points.first()).radius(7).color(line.colors.first()).zIndex(3))
            }
        }
        dot = map.addOverlay(DotOptions().center(roadCoordinate(track.samples.first()))
            .radius(9).color(MapColor.WHITE).zIndex(5)) as? Dot
    }
    LaunchedEffect(view, ready, track) {
        val map = view?.map ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val coordinates = withContext(Dispatchers.Default) { track.samples.map(::roadCoordinate) }
        val update = if (coordinates.all { it == coordinates.first() }) {
            MapStatusUpdateFactory.newLatLngZoom(coordinates.first(), 16f)
        } else {
            val bounds = LatLngBounds.Builder()
            coordinates.forEach { bounds.include(it) }
            MapStatusUpdateFactory.newLatLngBounds(bounds.build())
        }
        map.setMapStatus(update)
    }
    LaunchedEffect(dot, track, index) {
        val sample = track.samples.getOrNull(index) ?: return@LaunchedEffect
        dot?.center = roadCoordinate(sample)
    }
    LaunchedEffect(view, ready, track, focus) {
        val sample = track.samples.getOrNull(focus) ?: return@LaunchedEffect
        if (ready) view?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(roadCoordinate(sample), 16f))
    }
}

private fun roadLines(samples: List<RoadSample>, sinr: Boolean): List<RoadLine> = buildList {
    var points = mutableListOf<LatLng>()
    var colors = mutableListOf<Int>()
    fun addLine() {
        if (points.isNotEmpty()) add(RoadLine(points, colors))
        points = mutableListOf()
        colors = mutableListOf()
    }
    for ((i, sample) in samples.withIndex()) {
        val point = roadCoordinate(sample)
        val color = roadColor(sample, sinr)
        if (i > 0 && roadDistance(samples[i - 1], sample) == null) addLine()
        if (points.lastOrNull() == point) {
            colors[colors.lastIndex] = color
            continue
        }
        points.add(point)
        colors.add(color)
        if (points.size == 4096) {
            addLine()
            points.add(point)
            colors.add(color)
        }
    }
    addLine()
}

private fun roadCoordinate(sample: RoadSample): LatLng {
    val bd = CoordConverter.wgs84ToBd09(sample.latitude, sample.longitude)
    return LatLng(bd[0], bd[1])
}

private fun roadColor(sample: RoadSample, sinr: Boolean): Int {
    val value = (if (sinr) sample.sinr else sample.rsrp) ?: return MapColor.GRAY
    val level = if (sinr) SignalGrade.fromSinr(floor(value).toInt()) else SignalGrade.fromRsrp(floor(value).toInt())
    return MapColor.parseColor(SignalGrade.hexOf(level))
}

private val roadClock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val roadDate = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
private fun formatRoadValue(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
