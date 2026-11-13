package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.SurveyTypes

@Composable
private fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

/**
 * KernelSU 风格众包上传与扫描控制卡片
 */
@Composable
fun UploadScanningCard(
    state: UploadUiState,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onUpload: () -> Unit,
    onCancelUpload: () -> Unit,
    onNavigateToUploadSettings: () -> Unit,
    onHelpClick: () -> Unit,
    shouldStartCellular: Boolean,
    shouldStartWifi: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
        colors = CardDefaults.defaultColors(
            color = DashboardCardDefaults.cardContainerColor(),
        ),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_upload_24,
            title = stringResource(R.string.card_title_upload),
            onHelpClick = onHelpClick,
            action = {
                IconButton(
                    onClick = onNavigateToUploadSettings,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_upload_settings),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = DashboardCardDefaults.textSecondary(),
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.upload_scanning_description_text),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = DashboardCardDefaults.textSecondary(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 扫描状态控制区（KernelSU 风格状态框）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (state.scanningActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.scanningActive) {
                                stringResource(R.string.scanning_active)
                            } else {
                                stringResource(R.string.scanning_inactive)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (state.scanningActive) MaterialTheme.colorScheme.primary else DashboardCardDefaults.textPrimary(),
                        )
                    }

                    if (state.scanningActive && state.activeSurveys.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (SurveyTypes.CELLULAR in state.activeSurveys) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cellular),
                                    contentDescription = stringResource(R.string.cellular_title),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (SurveyTypes.WIFI in state.activeSurveys) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_wifi),
                                    contentDescription = stringResource(R.string.wifi_title),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = state.scanningActive,
                    label = "scanningButton",
                ) { isActive ->
                    if (isActive) {
                        Button(
                            onClick = onStopScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = stringResource(R.string.stop_scanning))
                        }
                    } else {
                        Button(
                            onClick = onStartScanning,
                        ) {
                            Text(text = stringResource(R.string.start_scanning))
                        }
                    }
                }
            }

            // Upload progress
            when (val progress = state.uploadProgress) {
                is UploadProgressState.InProgress -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    UploadProgressSection(
                        progress = progress,
                        onCancel = onCancelUpload,
                    )
                }

                is UploadProgressState.Finished -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    UploadResultsSection(result = progress)
                }

                is UploadProgressState.Hidden -> { /* no-op */ }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = dividerColor(), thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Queue counts section
            UploadQueueSection(
                cellularCount = state.cellularQueueCount,
                wifiCount = state.wifiQueueCount,
                autoUploadEnabled = state.autoUploadEnabled,
                uploadButtonEnabled = state.uploadButtonEnabled,
                shouldStartCellular = shouldStartCellular,
                shouldStartWifi = shouldStartWifi,
                onUpload = onUpload,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UploadProgressSection(
    progress: UploadProgressState.InProgress,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.upload_progress),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { progress.progress.toFloat() / progress.maxProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.upload_percentage, progress.progress),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        }

        if (progress.statusMessage.isNotEmpty()) {
            Text(
                text = progress.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = DashboardCardDefaults.textSecondary(),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cancel),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.cancel), fontSize = 13.sp)
        }
    }
}

@Composable
private fun UploadResultsSection(result: UploadProgressState.Finished) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.upload_result),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (result.cancelled) {
            Text(
                text = stringResource(R.string.uploader_canceled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row {
                Text(
                    text = stringResource(R.string.opencellid_upload),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.ocidResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (result.ocidResultMessage.isNotEmpty()) {
                Text(
                    text = result.ocidResultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.textSecondary(),
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                Text(
                    text = stringResource(R.string.beacondb_upload),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.beaconDbResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (result.beaconDbResultMessage.isNotEmpty()) {
                Text(
                    text = result.beaconDbResultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.textSecondary(),
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun UploadQueueSection(
    cellularCount: Int,
    wifiCount: Int,
    autoUploadEnabled: Boolean,
    uploadButtonEnabled: Boolean,
    shouldStartCellular: Boolean,
    shouldStartWifi: Boolean,
    onUpload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.upload_queue_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            if (autoUploadEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auto_upload_status_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.textSecondary(),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cellular),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = DashboardCardDefaults.textSecondary(),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            cellularCount == 0 && !shouldStartCellular ->
                                stringResource(R.string.cellular_upload_queue_count_disabled)
                            cellularCount >= 0 ->
                                stringResource(R.string.cellular_upload_queue_count, cellularCount)
                            else -> stringResource(R.string.cellular_upload_queue_count_initial)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = DashboardCardDefaults.textSecondary(),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            wifiCount == 0 && !shouldStartWifi ->
                                stringResource(R.string.wifi_upload_queue_count_disabled)
                            wifiCount >= 0 ->
                                stringResource(R.string.wifi_upload_queue_count, wifiCount)
                            else -> stringResource(R.string.wifi_upload_queue_count_initial)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = onUpload,
                enabled = uploadButtonEnabled,
            ) {
                Text(
                    text = stringResource(R.string.upload_survey_records),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
