package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.component.material.TonalCard
import com.craxiom.networksurvey.ui.component.statustag.StatusTag

/**
 * KernelSU 3.2.5 首页标志性顶级主状态大卡片（StatusCard & Metric Tonal Cards）：
 * 包含运行状态总览、动态模式药丸（StatusTag）、以及 2x2 Tonal 统计指标微磁贴。
 */
@Composable
fun MainHeroStatusCard(
    loggingState: LoggingUiState,
    locationState: LocationUiState,
    onCellularClick: () -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onGnssClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggingActive = loggingState.cellularEnabled || loggingState.wifiEnabled ||
            loggingState.bluetoothEnabled || loggingState.gnssEnabled || loggingState.cdrEnabled
    val hasGpsFix = locationState.hasLocation

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. KernelSU 风格的核心主状态大卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (isLoggingActive) {
                    primaryContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大号圆形状态徽标容器
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoggingActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (isLoggingActive) {
                            painterResource(id = R.drawable.ic_survey_monitor)
                        } else {
                            painterResource(id = R.drawable.ic_dashboard)
                        },
                        contentDescription = null,
                        tint = if (isLoggingActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLoggingActive) "无线信号测绘中" else "无线无忧 · 就绪",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = if (isLoggingActive) onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isLoggingActive) {
                            StatusTag(
                                label = "记录中",
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            StatusTag(
                                label = "空闲",
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (hasGpsFix) {
                            "GNSS 卫星已定位 (${locationState.latitude.toString().take(7)}, ${locationState.longitude.toString().take(7)})"
                        } else {
                            "GNSS 搜星就绪 · 各制式测量模组准备完毕"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = if (isLoggingActive) onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. KernelSU 经典 2x2 TonalCard 统计与快捷进入磁贴
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 蜂窝网络磁贴
            TonalCard(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = onCellularClick
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "蜂窝基站",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusTag(
                            label = if (loggingState.cellularEnabled) "已开" else "待机",
                            backgroundColor = if (loggingState.cellularEnabled) Color(0xFF43A047).copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            contentColor = if (loggingState.cellularEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "蜂窝测量模组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Wi-Fi 磁贴
            TonalCard(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = onWifiClick
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wi-Fi 热点",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusTag(
                            label = if (loggingState.wifiEnabled) "已开" else "待机",
                            backgroundColor = if (loggingState.wifiEnabled) Color(0xFF1E88E5).copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            contentColor = if (loggingState.wifiEnabled) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wi-Fi 热点扫描",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 蓝牙扫描磁贴
            TonalCard(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = onBluetoothClick
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "蓝牙设备",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusTag(
                            label = if (loggingState.bluetoothEnabled) "已开" else "待机",
                            backgroundColor = if (loggingState.bluetoothEnabled) Color(0xFF8E24AA).copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            contentColor = if (loggingState.bluetoothEnabled) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "蓝牙设备扫描",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // GNSS 卫星磁贴
            TonalCard(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = onGnssClick
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GNSS 卫星",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusTag(
                            label = if (hasGpsFix) "已定位" else "搜星中",
                            backgroundColor = if (hasGpsFix) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFF57F17).copy(alpha = 0.2f),
                            contentColor = if (hasGpsFix) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (hasGpsFix) "GNSS 已定位" else "GNSS 搜星中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
