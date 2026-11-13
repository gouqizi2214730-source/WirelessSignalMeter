package com.craxiom.networksurvey.ui.gnss

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.SbasType
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.gpstest.util.LibUIUtils
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatAccuracy
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatAltitude
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatAltitudeMsl
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatBearing
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatDoP
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatHvDOP
import com.craxiom.networksurvey.gpstest.util.FormatUtils.formatSpeed
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.model.DilutionOfPrecision
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun GnssOverviewScreen(
    viewModel: SignalInfoViewModel,
    hasPermission: Boolean,
    locationEnabled: Boolean,
    onEnableLocation: () -> Unit,
) {
    val statuses by viewModel.filteredStatuses.observeAsState(emptyList())
    val gnss by viewModel.filteredGnssStatuses.observeAsState(emptyList())
    val sbas by viewModel.filteredSbasStatuses.observeAsState(emptyList())
    val unfiltered by viewModel.allStatuses.observeAsState(emptyList())
    val fix by viewModel.fixState.observeAsState(FixState.NotAcquired)
    val visible = hasPermission && locationEnabled
    val current = if (visible) statuses else emptyList()
    val satellites = remember(current) {
        current.groupBy { it.gnssType to it.svid }.values.map { signals ->
            signals.maxBy { it.cn0DbHz } to signals.any { it.usedInFix }
        }
    }
    val used = satellites.count { it.second }
    val signals = remember(gnss, sbas) { (gnss + sbas).distinctBy { it.identity() } }
    var showLocation by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("overview") {
                Surface(
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    shape = DashboardCardDefaults.shape,
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = when {
                                !hasPermission -> "需要精确定位权限"
                                !locationEnabled -> "系统定位已关闭"
                                fix == FixState.Acquired -> "卫星定位已锁定"
                                else -> "正在搜寻卫星"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (visible) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GnssCount("可见卫星", satellites.size, Modifier.weight(1f))
                                GnssCount("参与定位", used, Modifier.weight(1f))
                            }
                        } else {
                            TextButton(onClick = onEnableLocation) {
                                Text(if (hasPermission) "打开定位设置" else "授予定位权限")
                            }
                        }
                    }
                }
            }

            if (visible) {
                item("sky") { SkyPlot(satellites) }
                item("location") {
                    Surface(
                        onClick = { showLocation = !showLocation },
                        shape = RoundedCornerShape(18.dp),
                        color = DashboardCardDefaults.cardContainerColor(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("位置与精度", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Icon(
                                if (showLocation) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (showLocation) "收起位置详情" else "展开位置详情",
                            )
                        }
                    }
                }
                if (showLocation) item("location-details") { LocationDetails(viewModel, fix) }
                item("satellite-heading") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("卫星信号", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (unfiltered.size > current.size &&
                            PreferenceUtils.gnssFilter(Application.get(), Application.getPrefs()).isNotEmpty()
                        ) {
                            TextButton(onClick = {
                                PreferenceUtils.clearGnssFilter(Application.get(), Application.getPrefs())
                            }) { Text("清除星座筛选") }
                        }
                    }
                }
                if (signals.isEmpty()) {
                    item("no-satellites") {
                        Text(
                            "等待可用卫星信号",
                            Modifier.padding(start = 4.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                items(signals, key = { it.identity() }) { signal -> SatelliteReading(signal) }
            }
        }
    }
}

@Composable
private fun GnssCount(label: String, count: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$count", style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SkyPlot(satellites: List<Pair<SatelliteStatus, Boolean>>) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val context = LocalContext.current
    val constellations = remember(satellites) { satellites.map { it.first.gnssType }.distinct().sorted() }
    val visibleFlags = remember(satellites) { satellites.mapNotNull { it.first.skyFlag() }.distinct() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val flags = remember(resources, visibleFlags) {
        visibleFlags.associateWith { ImageBitmap.imageResource(resources, it) }
    }
    val drawOrder = remember(satellites) { satellites.sortedWith(compareBy({ it.second }, { it.first.cn0DbHz })) }

    Surface(color = DashboardCardDefaults.cardContainerColor(), shape = DashboardCardDefaults.shape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("天空图", style = MaterialTheme.typography.titleSmall)
                Text("高亮边框 · 参与定位", style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f)) {
                    val labelStyle = MaterialTheme.typography.labelSmall
                    Text("北", Modifier.align(Alignment.TopCenter), style = labelStyle)
                    Text("东", Modifier.align(Alignment.CenterEnd), style = labelStyle)
                    Text("南", Modifier.align(Alignment.BottomCenter), style = labelStyle)
                    Text("西", Modifier.align(Alignment.CenterStart), style = labelStyle)
                    Canvas(
                        Modifier.fillMaxSize().padding(20.dp).semantics {
                            contentDescription = "卫星天空图，外圈为地平线，高亮边框标记参与定位的卫星"
                        },
                    ) {
                        val radius = size.minDimension / 2 - 6.dp.toPx()
                        drawCircle(colors.surfaceContainer, radius)
                        val line = colors.outlineVariant
                        for (ring in 1..3) {
                            drawCircle(line, radius * ring / 3, style = Stroke(1.dp.toPx()))
                        }
                        drawLine(line, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
                        drawLine(line, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))

                        drawOrder.forEach { (satellite, used) ->
                            val elevation = satellite.elevationDegrees
                            val azimuth = satellite.azimuthDegrees
                            if (elevation.isFinite() && azimuth.isFinite() && elevation in 0f..90f) {
                                val distance = radius * (1f - elevation / 90f)
                                val angle = Math.toRadians(azimuth.toDouble())
                                val position = center + Offset(
                                    (sin(angle) * distance).toFloat(),
                                    (-cos(angle) * distance).toFloat(),
                                )
                                val tint = constellationColor(satellite.gnssType, light)
                                val flag = flags[satellite.skyFlag()]
                                if (flag == null) {
                                    drawCircle(tint, 4.dp.toPx(), position)
                                } else {
                                    val width = (if (used) 21.dp else 17.dp).toPx()
                                    val height = width * flag.height / flag.width
                                    val topLeft = position - Offset(width / 2f, height / 2f)
                                    drawRect(colors.surface, topLeft - Offset(1f, 1f),
                                        androidx.compose.ui.geometry.Size(width + 2f, height + 2f))
                                    drawImage(flag,
                                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                                        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                                        alpha = if (used) 1f else 0.82f)
                                    drawRect(if (used) colors.primary else colors.outline,
                                        topLeft, androidx.compose.ui.geometry.Size(width, height),
                                        style = Stroke((if (used) 1.5.dp else 0.75.dp).toPx()))
                                }
                            }
                        }
                    }
                }
            }
            if (constellations.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    constellations.forEach { type ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = constellationFlag(type)
                            if (icon != null) Image(painterResource(icon), null, Modifier.size(width = 17.dp, height = 13.dp))
                            Text(LibUIUtils.getGnssDisplayName(context, type), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun constellationFlag(type: GnssType): Int? = when (type) {
    GnssType.BEIDOU -> R.drawable.ic_flag_china
    GnssType.NAVSTAR -> R.drawable.ic_flag_usa
    GnssType.GLONASS -> R.drawable.ic_flag_russia
    GnssType.GALILEO -> R.drawable.ic_flag_european_union
    GnssType.QZSS -> R.drawable.ic_flag_japan
    GnssType.IRNSS -> R.drawable.ic_flag_india
    GnssType.SBAS -> R.drawable.ic_flag_icao
    GnssType.UNKNOWN -> null
}

private fun SatelliteStatus.skyFlag(): Int? =
    if (gnssType != GnssType.SBAS) constellationFlag(gnssType)
    else when (sbasType) {
        SbasType.WAAS -> R.drawable.ic_flag_usa
        SbasType.EGNOS -> R.drawable.ic_flag_european_union
        SbasType.GAGAN -> R.drawable.ic_flag_india
        SbasType.MSAS -> R.drawable.ic_flag_japan
        SbasType.SDCM -> R.drawable.ic_flag_russia
        SbasType.SNAS -> R.drawable.ic_flag_china
        SbasType.SOUTHPAN -> R.drawable.ic_flag_southpan
        else -> R.drawable.ic_flag_icao
    }

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun LocationDetails(viewModel: SignalInfoViewModel, fix: FixState) {
    val location by viewModel.location.observeAsState(Location("default"))
    val ttff by viewModel.ttff.observeAsState("")
    val altitude by viewModel.altitudeMsl.observeAsState(Double.NaN)
    val dop by viewModel.dop.observeAsState(DilutionOfPrecision(Double.NaN, Double.NaN, Double.NaN))
    val context = LocalContext.current
    val hasPosition = location.time > 0L
    val lat = if (hasPosition) "%.6f°".format(location.latitude) else ""
    val lon = if (hasPosition) "%.6f°".format(location.longitude) else ""
    val data = listOf(
        "纬度" to lat,
        "经度" to lon,
        "定位精度" to if (hasPosition && location.hasAccuracy()) formatAccuracy(context, location) else "",
        "海拔" to formatAltitudeMsl(context, altitude).ifBlank { formatAltitude(context, location) },
        "速度" to formatSpeed(context, location),
        "航向" to formatBearing(context, location),
        "首次定位" to ttff,
        "PDOP" to formatDoP(context, dop),
        "HDOP / VDOP" to formatHvDOP(context, dop),
    ).filter { it.second.isNotBlank() }

    Surface(color = DashboardCardDefaults.cardContainerColor(), shape = DashboardCardDefaults.shape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.isEmpty()) {
                Text("等待定位数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                data.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                if (hasPosition && fix != FixState.Acquired) {
                    Text("上次定位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasPosition) {
                    TextButton(onClick = { NsUtils.copyToClipboard("${location.latitude}, ${location.longitude}") }) {
                        Text("复制坐标")
                    }
                }
            }
        }
    }
}

@Composable
private fun SatelliteReading(signal: SatelliteStatus) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(signal.gnssType, signal.svid, signal.carrierFrequencyHz) { mutableStateOf(false) }
    val strength = signal.cn0DbHz.takeIf { it.isFinite() && it > 0f }
    val constellation = LibUIUtils.getGnssDisplayName(context, signal.gnssType)
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = DashboardCardDefaults.cardContainerColor(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$constellation · ${signal.svid}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (signal.usedInFix) "参与定位" else "可见",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (signal.usedInFix) colors.primary else colors.onSurfaceVariant,
                    )
                }
                Text(strength?.let { "${it.roundToInt()} dB-Hz" } ?: "—", style = MaterialTheme.typography.labelLarge)
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起信号详情" else "展开信号详情",
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            if (expanded) {
                val details = buildList {
                    if (signal.elevationDegrees.isFinite()) add("仰角 ${signal.elevationDegrees.roundToInt()}°")
                    if (signal.azimuthDegrees.isFinite()) add("方位角 ${signal.azimuthDegrees.roundToInt()}°")
                    if (signal.hasCarrierFrequency) add("载频 %.2f MHz".format(signal.carrierFrequencyHz / 1e6))
                    if (signal.hasAgc) add("AGC %.1f dB".format(signal.agcDb))
                    if (signal.hasAlmanac) add("有历书")
                    if (signal.hasEphemeris) add("有星历")
                }
                Text(details.joinToString("  ·  ").ifEmpty { "暂无更多信号参数" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun SatelliteStatus.identity(): String = "$gnssType/$sbasType/$svid/$carrierFrequencyHz"

private fun constellationColor(type: GnssType, light: Boolean): Color = when (type) {
    GnssType.NAVSTAR -> if (light) Color(0xFF185BB0) else Color(0xFF9EC7FF)
    GnssType.BEIDOU -> if (light) Color(0xFFA43737) else Color(0xFFFFAAAA)
    GnssType.GALILEO -> if (light) Color(0xFF00735C) else Color(0xFF7ADCC1)
    GnssType.GLONASS -> if (light) Color(0xFF68439C) else Color(0xFFD0B6FF)
    GnssType.QZSS -> if (light) Color(0xFF8B5400) else Color(0xFFFFCA80)
    else -> if (light) Color(0xFF50616E) else Color(0xFFBDC9D6)
}
