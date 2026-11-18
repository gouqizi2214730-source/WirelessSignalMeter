package com.craxiom.networksurvey.ui.activesurvey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.cellular.baidumap.BaiduTowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.util.copyMissionIdToClipboard

/**
 * Main screen for monitoring active surveys with status and map views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMonitorScreen(
    viewModel: SurveyMonitorViewModel = viewModel(),
    onBackPressed: () -> Unit,
    onNavigateToTowerMapSettings: () -> Unit
) {
    val surveyState by viewModel.surveyState.collectAsStateWithLifecycle()
    val servingCellInfo by viewModel.servingCellInfo.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }

    // Handle service connection
    ServiceConnectionHandler(viewModel)

    // Keep screen always on when viewing Survey Monitor
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Scaffold(
        topBar = {
            ActiveSurveyTopBar(
                onBackPressed = onBackPressed
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("状�?) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("地图") }
                )
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize()) {
                // Status tab with animated visibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedTab == 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SurveyStatusTab(
                        surveyState = surveyState,
                        servingCellInfo = servingCellInfo,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Map tab with animated visibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedTab == 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SurveyMapTab(
                        surveyState = surveyState,
                        servingCellInfo = servingCellInfo,
                        onNavigateToTowerMapSettings = onNavigateToTowerMapSettings
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSurveyTopBar(
    onBackPressed: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "测站监控",
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        }
    )
}

@Composable
private fun SurveyStatusTab(
    surveyState: ActiveSurveyState,
    servingCellInfo: ServingCellInfo?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section - Status and Statistics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Status Indicator and Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                CompactSurveyStatusIndicator(
                    isActive = surveyState.isAnyActive
                )

                Text(
                    text = if (surveyState.isAnyActive) "测量进行�? else "测量已停�?,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (surveyState.isAnyActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Statistics at the top
            if (surveyState.isAnyActive) {
                SurveyStatistics(
                    surveyState = surveyState,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        // Spacer to push bottom content down
        Spacer(modifier = Modifier.weight(1f))

        // Bottom Section - Serving Cell (only show when survey is active)
        if (surveyState.isAnyActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Serving Cell Card with enhanced styling
                ServingCellCard(
                    servingCellInfo = servingCellInfo,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun SurveyMapTab(
    surveyState: ActiveSurveyState,
    servingCellInfo: ServingCellInfo?,
    onNavigateToTowerMapSettings: () -> Unit
) {
    // 使用百度地图基站地图页（已替代旧 MapLibre TowerMapScreen�?    BaiduTowerMapScreen(
        onBackButtonPressed = null,
        servingCellInfo = servingCellInfo
    )
}

// Data class for particles
private data class Particle(
    val initialY: Float,
    val x: Float,
    val size: Float,
    val velocity: Float
)

/**
 * A full width row that shows the current Mission ID with a copy button. The Mission ID identifies
 * the survey session and is what the user uses to find this survey's data.
 */
@Composable
private fun MissionIdRow(
    missionId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val copyMissionId = { copyMissionIdToClipboard(context, missionId) }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.mission_id_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { copyMissionId() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_content_copy),
                        contentDescription = stringResource(R.string.mission_id_copy_content_description),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = missionId,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SurveyStatistics(
    surveyState: ActiveSurveyState,
    modifier: Modifier = Modifier
) {
    // Pass viewModel to access session data
    val viewModel: SurveyMonitorViewModel = viewModel()

    // Calculate elapsed time from session start
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(surveyState.isAnyActive) {
        if (surveyState.isAnyActive) {
            while (surveyState.isAnyActive) {
                val sessionStartTime = viewModel.getSurveySessionStartTime()
                if (sessionStartTime != null) {
                    elapsedSeconds =
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                }
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    // Get total records from session with animation
    val totalRecords = viewModel.getSurveySessionRecordCount()
    val activeSurveyTypes = surveyState.activeSurveyTypes

    // Animated values for smooth transitions
    val animatedTotalRecords by animateIntAsState(
        targetValue = totalRecords,
        animationSpec = tween(300),
        label = "total_records"
    )

    // State for help dialogs
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with help icon
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.survey_statistics_header),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = "Help",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Mission ID row (shown only while a mission relevant survey is active)
        if (surveyState.missionId.isNotEmpty()) {
            MissionIdRow(missionId = surveyState.missionId)
        }

        // Statistics Cards Grid - show Time Elapsed and Records side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elapsed Time Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.defaultColors(
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "已用时长",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = formatElapsedTime(elapsedSeconds),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        },
                        label = "elapsed_time"
                    ) { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Records Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.defaultColors(
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.records_captured_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format("%,d", animatedTotalRecords),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Active survey types indicator
        if (activeSurveyTypes.isNotEmpty()) {
            Text(
                text = "进行�? ${activeSurveyTypes.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(text = stringResource(R.string.help_dialog_title))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(text = stringResource(R.string.help_dialog_total_count))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.help_dialog_got_it))
                }
            }
        )
    }
}

private fun formatElapsedTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

@Composable
private fun CompactSurveyStatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")

    // Pulsing animation for active state
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Ripple scale animation
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple"
    )

    // Color animation
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "color"
    )

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing rings when active
        if (isActive) {
            // First ripple
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(
                        scaleX = rippleScale,
                        scaleY = rippleScale
                    )
            ) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension / 2,
                    alpha = pulseAlpha * 0.2f
                )
            }

            // Second ripple
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(
                        scaleX = rippleScale * 0.8f,
                        scaleY = rippleScale * 0.8f
                    )
            ) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension / 2,
                    alpha = pulseAlpha * 0.3f
                )
            }
        }

        // Inner dot with glow
        Canvas(
            modifier = Modifier.size(14.dp)
        ) {
            // Glow effect
            drawCircle(
                color = indicatorColor,
                radius = size.minDimension / 2 + 2.dp.toPx(),
                alpha = 0.3f
            )

            // Core dot
            drawCircle(
                color = indicatorColor,
                radius = size.minDimension / 2
            )
        }
    }
}

@Composable
private fun ServingCellCard(
    servingCellInfo: ServingCellInfo?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cell_tower),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "主服务小�?,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (servingCellInfo?.servingCell != null) {
                val wrapper = servingCellInfo.servingCell
                val protocol = wrapper.cellularProtocol
                val record = wrapper.cellularRecord

                // Extract common fields based on protocol
                val cellInfo = when (protocol) {
                    com.craxiom.networksurvey.model.CellularProtocol.LTE -> {
                        val lte = record as com.craxiom.messaging.LteRecord
                        val data = lte.data
                        listOf(
                            "LTE", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.tac?.value ?: 0, data.eci?.value?.toLong() ?: 0L,
                            data.rsrp?.value?.toInt()?.toString() ?: "---",
                            if (data.hasPlmn()) data.plmn.value else null
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.NR -> {
                        val nr = record as com.craxiom.messaging.NrRecord
                        val data = nr.data
                        listOf(
                            "5G NR", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.tac?.value ?: 0, data.nci?.value ?: 0L,
                            data.ssRsrp?.value?.toInt()?.toString() ?: "---",
                            if (data.hasPlmn()) data.plmn.value else null
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.GSM -> {
                        val gsm = record as com.craxiom.messaging.GsmRecord
                        val data = gsm.data
                        listOf(
                            "GSM", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.ci?.value?.toLong() ?: 0L,
                            data.signalStrength?.value?.toInt()?.toString() ?: "---",
                            if (data.hasPlmn()) data.plmn.value else null
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.UMTS -> {
                        val umts = record as com.craxiom.messaging.UmtsRecord
                        val data = umts.data
                        listOf(
                            "UMTS", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.cid?.value?.toLong() ?: 0L,
                            data.rscp?.value?.toInt()?.toString() ?: "---",
                            if (data.hasPlmn()) data.plmn.value else null
                        )
                    }

                    else -> listOf("Unknown", 0, 0, 0, 0L, "---", null)
                }

                val technology = cellInfo[0] as String
                val mcc = (cellInfo[1] as Number).toInt()
                val mnc = (cellInfo[2] as Number).toInt()
                val plmnString = cellInfo.getOrNull(6) as? String
                val area = (cellInfo[3] as Number).toInt()
                val cellId = (cellInfo[4] as Number).toLong()
                val signalStrength = cellInfo[5] as String

                // Technology and Signal Row with enhanced styling
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "技术制�?,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Technology badge
                                    Card(
                                        colors = CardDefaults.defaultColors(
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                    ) {
                                        Text(
                                            text = technology,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when (technology) {
                                                "5G NR", "LTE" -> MaterialTheme.colorScheme.onPrimary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "信号强度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Column(horizontalAlignment = Alignment.Start) {
                                // Signal strength bars visualization
                                val signalValue = signalStrength.toIntOrNull() ?: -999
                                val signalBars = when {
                                    signalValue > -70 -> 4 // Excellent
                                    signalValue > -85 -> 3 // Good
                                    signalValue > -100 -> 2 // Fair
                                    signalValue > -110 -> 1 // Poor
                                    else -> 0 // No signal
                                }
                                val signalColor = when (signalBars) {
                                    4 -> Color(0xFF4CAF50) // Green
                                    3 -> Color(0xFF8BC34A) // Light Green
                                    2 -> Color(0xFFFFC107) // Yellow
                                    1 -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFFF44336) // Red
                                }

                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

                                    for (i in 1..4) {
                                        val barHeight = (i * 5 + 2).dp
                                        val isActive = i <= signalBars
                                        val barAlpha by animateFloatAsState(
                                            targetValue = if (isActive) 1f else 0.3f,
                                            animationSpec = tween(200),
                                            label = "bar_alpha_$i"
                                        )

                                        Canvas(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(barHeight)
                                        ) {
                                            drawRoundRect(
                                                color = if (isActive) signalColor else inactiveColor,
                                                alpha = barAlpha,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                    2.dp.toPx()
                                                ),
                                                size = androidx.compose.ui.geometry.Size(
                                                    size.width,
                                                    size.height
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "$signalStrength dBm",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Network and Area Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "运营商网�?,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = plmnString ?: "$mcc-$mnc",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (protocol == com.craxiom.networksurvey.model.CellularProtocol.GSM ||
                                protocol == com.craxiom.networksurvey.model.CellularProtocol.UMTS
                            ) "区域�?LAC" else "区域�?TAC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = area.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Cell ID with enhanced styling
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "小区编号 CELL ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = cellId.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Loading state
                ServingCellCardSkeleton()
            }
        }
    }
}

@Composable
private fun ServingCellCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column {
        // Technology and Signal Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "技术制�?,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.6f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "信号强度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.7f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Network and Area Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "运营商网�?,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.5f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "区域�?,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.4f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Cell ID
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "小区编号 CELL ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth(0.8f)
                    .background(shimmerColor, RoundedCornerShape(4.dp))
            )
        }
    }
}