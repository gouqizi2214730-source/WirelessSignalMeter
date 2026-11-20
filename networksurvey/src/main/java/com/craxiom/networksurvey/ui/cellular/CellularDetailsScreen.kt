package com.craxiom.networksurvey.ui.cellular

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalCellularAlt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults as MiuixProgressIndicatorDefaults
import androidx.compose.material3.LinearProgressIndicator as M3LinearProgressIndicator
import com.craxiom.networksurvey.util.ThemePref
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.AndroidNrCarriers
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.model.StationInfo
import com.craxiom.networksurvey.db.EngineeringParamDbHelper
import androidx.compose.runtime.produceState
import com.craxiom.networksurvey.fragments.model.CellularViewModel
import com.craxiom.networksurvey.fragments.model.CarrierAggregationViewState
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState
import com.craxiom.networksurvey.ui.component.statustag.StatusTag
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.ui.dashboard.DashboardCardHeader
import com.craxiom.networksurvey.ui.main.FloatingNavigationClearance
import com.craxiom.networksurvey.ui.rootdiag.DiagField
import com.craxiom.networksurvey.ui.rootdiag.NeighborMeasurements
import com.craxiom.networksurvey.ui.rootdiag.SIGNAL_UNAVAILABLE
import com.craxiom.networksurvey.ui.rootdiag.ServingParameterFields
import com.craxiom.networksurvey.ui.rootdiag.SignalParameterCard
import com.craxiom.networksurvey.ui.rootdiag.SignalParameterRow
import com.craxiom.networksurvey.ui.rootdiag.servingCellIdFields
import com.craxiom.networksurvey.ui.rootdiag.signalDisplayValue
import com.craxiom.networksurvey.util.ColorUtils
import com.craxiom.networksurvey.util.CellularUtils.UplinkReference
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CellularDetailsScreen(
    viewModel: CellularViewModel,
    chartViewModel: CellularChartViewModel,
    onNrInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 监听主题/偏好版本号：进度条风格等偏好切换时立即重组刷新，无需等待导航
    @Suppress("UNUSED_EXPRESSION")
    ThemePref.currentThemeVersion

    val carrier by viewModel.getCarrier().observeAsState("")
    val heroText by viewModel.getHeroText().observeAsState("")
    val voicePill by viewModel.getVoicePillValue().observeAsState(null)
    val dataPill by viewModel.getDataPillValue().observeAsState(null)
    val brandingPill by viewModel.getBrandingPillValue().observeAsState(null)
    val carrierAggregation by viewModel.getCarrierAggregation().observeAsState(null)
    val airplaneMode by viewModel.getAirplaneModeActive().observeAsState(false)

    val protocol by viewModel.getServingCellProtocol().observeAsState(CellularProtocol.NONE)
    val mcc by viewModel.getMcc().observeAsState("")
    val mnc by viewModel.getMnc().observeAsState("")
    val registeredPlmn by viewModel.getRegisteredPlmn().observeAsState("")
    val cellPlmns by viewModel.getCellPlmns().observeAsState("")
    val areaCode by viewModel.getAreaCode().observeAsState("")
    val cellId by viewModel.getCellId().observeAsState(null)
    val channelNumber by viewModel.getChannelNumber().observeAsState("")
    val frequency by viewModel.getFrequency().observeAsState("")
    val uplink by viewModel.uplinkReference.observeAsState()
    val band by viewModel.getBand().observeAsState("")
    val lteBand by viewModel.getLteBand().observeAsState("")
    val pci by viewModel.getPci().observeAsState("")
    val bandwidth by viewModel.getBandwidth().observeAsState("")
    val ta by viewModel.getTa().observeAsState("")
    val cqi by viewModel.getCqi().observeAsState("")

    val signalOne by viewModel.getSignalOne().observeAsState(null)
    val signalTwo by viewModel.getSignalTwo().observeAsState(null)
    val signalThree by viewModel.getSignalThree().observeAsState(null)

    val nrSecondary by viewModel.getNrSecondaryCell().observeAsState(null)
    val reportedNrCarriers by viewModel.nrCarriers.observeAsState(emptyList())
    val nrCarrierClock by produceState(0L, reportedNrCarriers) {
        if (reportedNrCarriers.size >= 2) {
            do {
                value = SystemClock.elapsedRealtime()
                if (AndroidNrCarriers.displayAt(reportedNrCarriers, value).size < 2) break
                delay(1_000)
            } while (true)
        }
    }
    val nrCarriers = AndroidNrCarriers.displayAt(reportedNrCarriers,
        maxOf(nrCarrierClock, SystemClock.elapsedRealtime()))
    val nrServing by viewModel.nrServingCell.observeAsState()
    val nrLastSeen by viewModel.getNrLastSeenText().observeAsState("")

    val umtsNeighbors by viewModel.getUmtsNeighbors().observeAsState(null)
    val gsmNeighbors by viewModel.getGsmNeighbors().observeAsState(null)

    val isLive = protocol != CellularProtocol.NONE

    val matchedCell by produceState<EngineeringParam?>(null, cellId, protocol, mcc, mnc, pci, channelNumber) {
        value = null
        val id = cellId
        if (id != null && id > 0 && (protocol == CellularProtocol.NR || protocol == CellularProtocol.LTE)) {
            val station = StationInfo().apply {
                isServing = true
                this.cellId = id
                networkType = if (protocol == CellularProtocol.NR) "NR" else "LTE"
                this.pci = pci.substringBefore(' ').trim().toIntOrNull() ?: -1
                earfcn = channelNumber.toIntOrNull() ?: -1
                val divisor = if (protocol == CellularProtocol.NR) 4096 else 256
                gnbId = (id / divisor).toInt()
                sectorId = (id % divisor).toInt()
                this.mcc = mcc.toIntOrNull() ?: 0
                this.mnc = mnc.toIntOrNull() ?: 0
            }
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val database = EngineeringParamDbHelper.getInstance(context.applicationContext)
                    database.matchServingIdentity(station)
                }.getOrNull()
            }
        }
    }

    val matchedCellName = matchedCell?.let { it.cellName.orEmpty().ifBlank { it.siteName.orEmpty() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp)
    ) {
        if (airplaneMode) {
            WarningCard(text = "已开启飞行模式，无法获取蜂窝网络信息")
        }

        SignalSectionCard {
            ServingCellHeader(
                carrier = carrier,
                cellName = matchedCellName.orEmpty(),
                networkDescription = heroText,
                voicePill = voicePill,
                dataPill = dataPill,
                brandingPill = brandingPill,
                carrierAggregation = carrierAggregation,
            )

            if (!isLive && !airplaneMode) {
                Text(
                    text = stringResource(R.string.waiting_for_cellular_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isLive) return@Column
        if (protocol != CellularProtocol.NR || nrCarriers.count { !it.lastObservation() } < 2) {
            ServingSignalMetrics(protocol, signalOne, signalTwo, signalThree)
        }
        if (protocol == CellularProtocol.NR || protocol == CellularProtocol.LTE) {
            NrCarrierSection(nrCarriers, fromDiag = false)
        }
        nrSecondary?.takeIf { protocol == CellularProtocol.LTE && !it.idle() &&
            nrCarriers.count { carrier -> !carrier.lastObservation() } < 2 }?.let { secondary ->
            NrSecondaryCard(secondary, nrLastSeen, onNrInfoClick)
        }
        val fields = cellIdentityFields(
            protocol, registeredPlmn, cellPlmns, areaCode, cellId, channelNumber, frequency,
            band, lteBand, pci, bandwidth, ta, cqi, matchedCell, uplink,
        )
        val nrBandwidthRow: (@Composable () -> Unit)? = if (protocol == CellularProtocol.NR) {
            val cell = NrBandwidthCell.from(nrServing)?.takeIf {
                it.nci == cellId && it.channel.toString() == channelNumber
            }
            val reported = bandwidth.takeIf { it.isNotBlank() }
            val saved = if (reported == null) rememberSavedNrBandwidth(cell) else null
            if (reported != null || (cell != null && saved != null)) {
                {
                    SignalParameterRow(stringResource(R.string.diag_bandwidth_label)) {
                        if (reported != null) Text("$reported MHz", style = MaterialTheme.typography.bodySmall)
                        else if (cell != null && saved != null) SavedNrBandwidthValue(cell, saved)
                    }
                }
            } else null
        } else null
        SignalParameterCard(stringResource(R.string.serving_cell_parameters), fields.primary, nrBandwidthRow)

        // 邻区按制式展示
        if (protocol == CellularProtocol.NR || protocol == CellularProtocol.LTE) {
            NeighborMeasurements(protocol.name, null, emptyList(), viewModel, isSignalTab = false)
        }
        val umtsN = umtsNeighbors
        if (protocol == CellularProtocol.UMTS && !umtsN.isNullOrEmpty()) {
            NeighborSectionCard(title = "UMTS 邻区", neighbors = umtsN.map {
                "UARFCN ${it.uarfcn}  ·  PSC ${it.psc}  ·  RSCP ${it.rscp} dBm"
            })
        }
        val gsmN = gsmNeighbors
        if (protocol == CellularProtocol.GSM && !gsmN.isNullOrEmpty()) {
            NeighborSectionCard(title = "GSM 邻区", neighbors = gsmN.map {
                "ARFCN ${it.arfcn}  ·  BSIC ${it.bsic}  ·  RSSI ${it.rssi} dBm"
            })
        }

        SignalSectionCard(contentPadding = 0.dp) { CellularChartComponent(viewModel = chartViewModel) }
        SignalParameterCard(stringResource(R.string.diag_cell_details), fields.secondary)

        // 底部留白：为悬浮底栏腾出空间，避免内容被遮挡
        val navigationInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        Spacer(Modifier.height(FloatingNavigationClearance + navigationInset))
    }
}

@Composable
internal fun SignalSectionCard(contentPadding: Dp = 16.dp, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
        shape = DashboardCardDefaults.shape,
        color = DashboardCardDefaults.cardContainerColor(),
        border = BorderStroke(1.dp, DashboardCardDefaults.outlineVariant()),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
internal fun ServingCellHeader(
    carrier: String,
    cellName: String,
    networkDescription: String,
    voicePill: String?,
    dataPill: String?,
    brandingPill: String?,
    carrierAggregation: CarrierAggregationViewState?,
) {
    val colors = MaterialTheme.colorScheme
    val carrierIcon = when {
        carrier.contains("电信") || carrier.contains("Telecom", ignoreCase = true) -> R.drawable.ic_carrier_telecom
        carrier.contains("移动") || carrier.contains("China Mobile", ignoreCase = true) -> R.drawable.ic_carrier_mobile
        carrier.contains("联通") || carrier.contains("Unicom", ignoreCase = true) -> R.drawable.ic_carrier_unicom
        carrier.contains("广电") || carrier.contains("CBN", ignoreCase = true) -> R.drawable.ic_carrier_broadcast
        else -> null
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                if (carrierIcon != null) {
                    Icon(painterResource(carrierIcon), null, Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.SignalCellularAlt, null, Modifier.size(24.dp))
                }
            }
        }

        Column(Modifier.weight(if (cellName.isBlank()) 1f else 0.85f)) {
            Text(
                text = carrier.ifBlank { stringResource(R.string.cellular_title) },
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            if (networkDescription.isNotBlank()) {
                Text(networkDescription, style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
        }

        if (cellName.isNotBlank()) {
            Text(cellName, Modifier.weight(1.4f), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 1.sp))
        }

    }

    val noBranding = stringResource(R.string.branding_none)
    val pills = (listOfNotNull(
        voicePill,
        dataPill?.takeUnless { it == networkDescription.substringBefore(" · ") },
        brandingPill?.takeUnless { it == noBranding },
    ) + carrierAggregation?.chipLabels.orEmpty()).filter { it.isNotBlank() }.distinct()
    if (pills.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pills.forEach { label ->
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh,
                    contentColor = colors.onSurfaceVariant,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    carrierAggregation?.let {
        Text(it.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private data class SignalMetric(
    val label: String,
    val value: Number?,
    val minValue: Int,
    val maxNormalizedValue: Int,
    val unit: String,
) {
    val valueText: String get() {
        if (value == null) return SIGNAL_UNAVAILABLE
        val reading = if (value is Double) String.format(Locale.ROOT, "%.1f", value) else value.toString()
        return "$reading $unit"
    }
}

@Composable
internal fun ServingSignalMetrics(
    protocol: CellularProtocol,
    signalOne: Number?,
    signalTwo: Number?,
    signalThree: Number?,
) {
    val oneLabel = when (protocol) {
        CellularProtocol.GSM, CellularProtocol.UMTS -> "RSSI"
        CellularProtocol.LTE -> "RSRP"
        CellularProtocol.NR -> "SS-RSRP"
        else -> stringResource(R.string.serving_signal_strength)
    }
    val twoLabel = when (protocol) {
        CellularProtocol.UMTS -> "RSCP"
        CellularProtocol.LTE -> "RSRQ"
        CellularProtocol.NR -> "SS-RSRQ"
        else -> null
    }
    val threeLabel = when (protocol) {
        CellularProtocol.LTE -> "SINR"
        CellularProtocol.NR -> "SS-SINR"
        else -> null
    }
    val two = signalTwo?.takeUnless { protocol == CellularProtocol.UMTS && (it.toInt() == -120 || it.toInt() == -24) }
    val twoUnit = if (protocol == CellularProtocol.UMTS) "dBm" else "dB"
    val metrics = listOfNotNull(
        SignalMetric(oneLabel, signalOne, protocol.getMinSignalOne(), protocol.getMaxNormalizedSignalOne(), "dBm"),
        twoLabel?.let { SignalMetric(it, two, protocol.getMinSignalTwo(), protocol.getMaxNormalizedSignalTwo(), twoUnit) },
        threeLabel?.let {
            SignalMetric(it, signalThree, protocol.getMinSignalThree(), protocol.getMaxNormalizedSignalThree(), "dB")
        },
    )
    val barColors = rememberSignalBarColors(protocol)

    SignalSectionCard(contentPadding = 12.dp) {
        Text(
            text = stringResource(R.string.serving_signal_strength),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (metrics.size > 1 && maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.15f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEachIndexed { index, metric ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = DashboardCardDefaults.smallShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    metric.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    metric.valueText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                val palette = barColors.getOrNull(index)
                                if (palette != null) ColoredSignalBar(metric.value, palette, Modifier.fillMaxWidth())
                                else MetricProgress(metric.value?.toInt(), metric.minValue, metric.maxNormalizedValue,
                                    Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEachIndexed { index, metric ->
                        SignalBarRow(metric.label, metric.value, metric.minValue, metric.maxNormalizedValue,
                            barColors.getOrNull(index)) {
                            metric.valueText
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalBarRow(
    label: String,
    value: Number?,
    minValue: Int,
    maxNormalizedValue: Int,
    palette: SignalBarColors? = null,
    valueText: (Number) -> String,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyMedium
    val bar: @Composable (Modifier) -> Unit = { modifier ->
        if (palette != null) ColoredSignalBar(value, palette, modifier)
        else MetricProgress(value?.toInt(), minValue, maxNormalizedValue, modifier)
    }
    val reading = value?.let(valueText) ?: SIGNAL_UNAVAILABLE

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp || LocalDensity.current.fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = textStyle, color = colors.onSurfaceVariant)
                    Text(reading, style = textStyle, color = colors.onSurface)
                }
                bar(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.width(74.dp), style = textStyle, color = colors.onSurfaceVariant)
                bar(Modifier.weight(1f))
                Text(
                    text = reading,
                    modifier = Modifier.width(74.dp),
                    style = textStyle,
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun MetricProgress(value: Int?, minValue: Int, maxNormalizedValue: Int, modifier: Modifier) {
    if (value == null || maxNormalizedValue <= 0) {
        Surface(modifier.height(6.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
    } else {
        SignalProgressBar(value, minValue, maxNormalizedValue, modifier)
    }
}

@Composable
private fun SignalProgressBar(value: Int, minValue: Int, maxNormalizedValue: Int, modifier: Modifier) {
    val normalizedValue = (value.toLong() - minValue).coerceIn(0L, maxNormalizedValue.toLong()).toInt()
    val progress = (normalizedValue.toFloat() / maxNormalizedValue).coerceAtLeast(0.05f)
    val foreground = Color(ColorUtils.getSignalColorForValue(normalizedValue, maxNormalizedValue))
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val barModifier = modifier.height(6.dp)

    if (ThemePref.isMiuixProgressBar(LocalContext.current)) {
        MiuixLinearProgressIndicator(
            modifier = barModifier,
            progress = progress,
            colors = MiuixProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = foreground,
                backgroundColor = track,
            ),
        )
    } else {
        M3LinearProgressIndicator(
            progress = { progress },
            modifier = barModifier,
            color = foreground,
            trackColor = track,
        )
    }
}

@Composable
private fun cellIdentityFields(
    protocol: CellularProtocol,
    registeredPlmn: String,
    cellPlmns: String,
    areaCode: String,
    cellId: Long?,
    channelNumber: String,
    frequency: String,
    band: String,
    lteBand: String,
    pci: String,
    bandwidth: String,
    ta: String,
    cqi: String,
    matchedCell: EngineeringParam?,
    uplink: UplinkReference?,
): ServingParameterFields {
    val areaLabel = if (protocol == CellularProtocol.UMTS || protocol == CellularProtocol.GSM) "LAC" else "TAC"
    val channelLabel = when (protocol) {
        CellularProtocol.GSM -> "ARFCN"
        CellularProtocol.UMTS -> "UARFCN"
        CellularProtocol.LTE, CellularProtocol.NR -> stringResource(R.string.cell_downlink_channel)
        else -> "频点"
    }
    val pciLabel = when (protocol) {
        CellularProtocol.GSM -> "BSIC"
        CellularProtocol.UMTS -> "PSC"
        else -> "PCI"
    }

    val rawCi = cellId?.takeIf { it > 0 }
    val cellIdLabel = when (protocol) {
        CellularProtocol.NR -> "NCI"
        CellularProtocol.LTE -> "ECI"
        else -> "CID"
    }
    val site = matchedCell?.takeIf {
        it.lat in -90.0..90.0 && it.lon in -180.0..180.0 && (it.lat != 0.0 || it.lon != 0.0)
    }
    val hasLteOrNr = protocol == CellularProtocol.LTE || protocol == CellularProtocol.NR
    fun field(label: String, value: String?) = DiagField(label, value.signalDisplayValue())
    val rows = buildList {
        add(field(pciLabel, pci.substringBefore(' ')))
        if (hasLteOrNr) add(field(stringResource(R.string.diag_band), if (protocol == CellularProtocol.NR) band else lteBand))
        addAll(servingCellIdFields(protocol, rawCi))
        add(field(channelLabel, channelNumber))
        if (hasLteOrNr) {
            add(field(stringResource(R.string.diag_registered_plmn), registeredPlmn))
            add(field(stringResource(R.string.diag_cell_plmns), cellPlmns))
        } else add(field("PLMN", cellPlmns))
        add(field(areaLabel, areaCode))
        add(field(cellIdLabel, rawCi?.toString()))
        if (hasLteOrNr) {
            val dlFrequencyLabel = if (protocol == CellularProtocol.NR) R.string.cell_downlink_reference_frequency
                else R.string.cell_downlink_frequency
            add(field(stringResource(dlFrequencyLabel), frequency))
            if (protocol == CellularProtocol.LTE)
                add(field(stringResource(R.string.diag_bandwidth_label), bandwidth.takeIf(String::isNotBlank)?.let { "$it MHz" }))
        }
    }
    val detailRows = when (protocol) {
        CellularProtocol.LTE -> listOf("TA" to ta, "CQI" to cqi)
        CellularProtocol.NR, CellularProtocol.GSM -> listOf("TA" to ta)
        else -> emptyList()
    } + if (hasLteOrNr) listOf(
        stringResource(R.string.diag_duplex_mode) to uplink?.duplex(),
        stringResource(R.string.cell_uplink_reference_channel) to uplink?.arfcn()?.takeIf { it >= 0 }?.toString(),
        stringResource(R.string.cell_uplink_reference_frequency) to uplink?.mhz()?.takeIf { it > 0 }?.let {
            val format = if (protocol == CellularProtocol.NR) "%.3f MHz" else "%.1f MHz"
            String.format(Locale.ROOT, format, it)
        },
        stringResource(R.string.cell_site_longitude) to site?.let { String.format(Locale.ROOT, "%.5f", it.lon) },
        stringResource(R.string.cell_site_latitude) to site?.let { String.format(Locale.ROOT, "%.5f", it.lat) },
    ) else emptyList()
    return ServingParameterFields(rows, detailRows.map { (label, value) -> field(label, value) })
}

@Composable
internal fun CellField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** NR 辅卡：NR 数据腿指标 + 空闲徽标 + 最近出现。 */
@Composable
private fun NrSecondaryCard(
    state: NrSecondaryCellViewState,
    lastSeenText: String,
    onInfoClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_cell_tower,
            title = "NR 辅载波",
            onHelpClick = onInfoClick
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.idle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusTag(
                        label = "空闲",
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastSeenText.isNotBlank()) {
                        Text(
                            text = lastSeenText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val fields = listOfNotNull(
                "频段" to (state.band().takeIf { it.isNotBlank() } ?: ""),
                "频率" to (state.frequency().takeIf { it.isNotBlank() } ?: ""),
                "PCI" to (state.pci().takeIf { it.isNotBlank() } ?: ""),
                "NARFCN" to (state.narfcn().takeIf { it.isNotBlank() } ?: ""),
            )
            if (fields.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    fields.forEachIndexed { idx, (l, v) ->
                        CellField(l, v, modifier = Modifier.weight(1f))
                        if (idx < fields.lastIndex) Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            state.bandwidth().takeIf(String::isNotBlank)?.let {
                CellField(stringResource(R.string.diag_bandwidth_label), "$it MHz")
            }

            val colors = rememberSignalBarColors(CellularProtocol.NR)
            val readings = listOf(state.ssRsrp(), state.ssRsrq(), state.ssSinr())
            SignalBarMetric.forProtocol(CellularProtocol.NR).forEachIndexed { index, metric ->
                SignalBarRow(metric.title.removePrefix("NR "), readings[index], 0, 0, colors[index]) { "$it ${metric.unit}" }
            }
        }
    }
}

/** 邻区卡片：单制式一组，逐行列出邻区（KernelSU 风格分行卡片）。 */
@Composable
private fun NeighborSectionCard(title: String, neighbors: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
    ) {
        DashboardCardHeader(icon = R.drawable.ic_cell_tower, title = title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            neighbors.forEachIndexed { index, line ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
