package com.craxiom.networksurvey.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.component.material.ExpressiveSwitch
import com.craxiom.networksurvey.ui.component.material.SegmentedColumn
import com.craxiom.networksurvey.ui.component.material.SegmentedRow

/**
 * KernelSU 3.2.5 风格的分段列表开关行（Segmented Switch Item）：
 * 六个测量通道开关以连体分段列表呈现，配合 M3 Expressive 开关，视觉统一精致。
 */

/**
 * 单个测量通道开关的分段行内容（置于 [SegmentedColumn] 中使用）。
 */
@Composable
private fun LoggingSegmentedToggleRow(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null,
    onHelpClick: (() -> Unit)? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val iconBgColor = if (enabled) {
        primaryColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val iconTintColor = if (enabled) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    SegmentedRow(
        headline = label,
        supporting = subtitle,
        onClick = { onToggle(!enabled) },
        leading = {
            // 圆形微徽标容器（保留原有 KernelSU 经典设计语言）
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTintColor,
                )
            }
        },
        trailing = {
            if (onHelpClick != null) {
                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_help),
                        contentDescription = "$label help",
                        modifier = Modifier.size(16.dp),
                        tint = DashboardCardDefaults.textSecondary(),
                    )
                }
            }
            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
    )
}

/**
 * The full logging control content for the local file logging card. Renders all 6 toggle rows
 * (Cellular, Phone State, Wi-Fi, Bluetooth, GNSS, CDR) as a KernelSU-style segmented list.
 */
@Composable
fun LoggingControlContent(
    cellularEnabled: Boolean,
    phoneStateEnabled: Boolean,
    phoneStateAutoStarted: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    gnssEnabled: Boolean,
    cdrEnabled: Boolean,
    onCellularToggle: (Boolean) -> Unit,
    onPhoneStateToggle: (Boolean) -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onBluetoothToggle: (Boolean) -> Unit,
    onGnssToggle: (Boolean) -> Unit,
    onCdrToggle: (Boolean) -> Unit,
    onPhoneStateHelpClick: () -> Unit,
    onCdrHelpClick: () -> Unit,
) {
    val rows = mutableListOf<@Composable () -> Unit>()

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_cellular,
            label = stringResource(R.string.cellular_title),
            enabled = cellularEnabled,
            onToggle = onCellularToggle,
        )
    }

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_phone_state,
            label = stringResource(R.string.phone_state_title),
            enabled = phoneStateEnabled,
            onToggle = onPhoneStateToggle,
            subtitle = if (phoneStateAutoStarted) stringResource(R.string.phone_state_logging_status_auto) else null,
            onHelpClick = onPhoneStateHelpClick,
        )
    }

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_wifi,
            label = stringResource(R.string.wifi_title),
            enabled = wifiEnabled,
            onToggle = onWifiToggle,
        )
    }

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_bluetooth,
            label = stringResource(R.string.bluetooth_title),
            enabled = bluetoothEnabled,
            onToggle = onBluetoothToggle,
        )
    }

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_gnss,
            label = stringResource(R.string.gnss_title),
            enabled = gnssEnabled,
            onToggle = onGnssToggle,
        )
    }

    rows += {
        LoggingSegmentedToggleRow(
            icon = R.drawable.ic_cdr,
            label = stringResource(R.string.cdr_title),
            enabled = cdrEnabled,
            onToggle = onCdrToggle,
            onHelpClick = onCdrHelpClick,
        )
    }

    SegmentedColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        content = rows,
    )
}
