package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.component.material.ExpressiveSwitch
import com.craxiom.networksurvey.ui.component.material.SegmentedColumn

private val MqttDisconnected = Color(0xFFA21919)
private val MqttConnecting = Color(0xFFFF6D00)
private val MqttConnected = Color(0xFF00912F)
private val StreamActive = Color(0xFF03A9F4)
private val StreamInactive = Color(0xFFA2A2A2)

/**
 * KernelSU 风格现代 MQTT 连接卡片
 */
@Composable
fun MqttStatusCard(
    state: MqttUiState,
    onToggleMqtt: (Boolean) -> Unit,
    onNavigateToMqttSettings: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnectedOrConnecting = state.connectionState == ConnectionState.CONNECTED
            || state.connectionState == ConnectionState.CONNECTING

    val statusColor by animateColorAsState(
        targetValue = when (state.connectionState) {
            ConnectionState.CONNECTED -> MqttConnected
            ConnectionState.CONNECTING -> MqttConnecting
            else -> MqttDisconnected
        },
        animationSpec = tween(300),
        label = "mqttStatusColor",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
        colors = CardDefaults.defaultColors(
            color = DashboardCardDefaults.cardContainerColor(),
        ),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_cloud_connection,
            title = stringResource(R.string.card_title_mqtt_details),
            onHelpClick = onHelpClick,
            action = {
                IconButton(
                    onClick = onNavigateToMqttSettings,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.mqtt_connection_settings),
                        modifier = Modifier.size(19.dp),
                        tint = DashboardCardDefaults.textSecondary(),
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // MQTT connection status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.connection_icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = statusColor,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.mqtt_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.mqtt_connecting)
                            else -> stringResource(R.string.mqtt_off)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = DashboardCardDefaults.textPrimary(),
                    )
                    Text(
                        text = if (isConnectedOrConnecting) "云端遥测传输中" else "MQTT 推流已关闭",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DashboardCardDefaults.textSecondary(),
                    )
                }

                if (!state.isMqttToggleHiddenByMdm) {
                    ExpressiveSwitch(
                        checked = isConnectedOrConnecting,
                        onCheckedChange = onToggleMqtt,
                    )
                }
            }

            // Streaming indicators - only visible when connected or connecting
            if (isConnectedOrConnecting) {
                Column(
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.what_is_configured_to_stream),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = DashboardCardDefaults.textSecondary(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val streamRows = mutableListOf<@Composable () -> Unit>()

                    streamRows += { MqttStreamItem(stringResource(R.string.cellular_title), state.cellularStreamEnabled) }
                    streamRows += { MqttStreamItem(stringResource(R.string.phone_state_title), state.phoneStateStreamEnabled) }
                    streamRows += { MqttStreamItem(stringResource(R.string.wifi_title), state.wifiStreamEnabled) }
                    streamRows += { MqttStreamItem(stringResource(R.string.bluetooth_title), state.bluetoothStreamEnabled) }
                    streamRows += { MqttStreamItem(stringResource(R.string.gnss_title), state.gnssStreamEnabled) }
                    streamRows += { MqttStreamItem(stringResource(R.string.device_status_label), state.deviceStatusStreamEnabled) }

                    SegmentedColumn(content = streamRows)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * A single MQTT stream indicator showing the protocol icon, label, and on/off status.
 * Placed inside a [SegmentedColumn]; the container provides the segmented card background.
 */
@Composable
private fun MqttStreamItem(
    label: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (enabled) StreamActive else StreamInactive,
                    shape = CircleShape,
                ),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = DashboardCardDefaults.textSecondary(),
            modifier = Modifier.weight(1f),
        )

        Text(
            text = if (enabled) stringResource(R.string.status_on) else stringResource(R.string.status_disabled),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal
            ),
            color = if (enabled) StreamActive else StreamInactive,
        )
    }
}
