package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R

private const val ANDROID_BATTERY_ICON = android.R.drawable.ic_lock_idle_low_battery

private val BatteryWarningBackground = Color(0xFFFFF8E1)
private val BatteryPausedBackground = Color(0xFFFFEBEE)
private val BatteryWarningText = Color(0xFFE65100)
private val BatteryPausedText = Color(0xFFC62828)
private val BatteryIconWarning = Color(0xFFFF6F00)
private val BatteryIconPaused = Color(0xFFD32F2F)

/**
 * KernelSU 风格低电量管理警告卡片
 */
@Composable
fun BatteryCard(
    state: BatteryUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val backgroundColor = if (state.isPaused) BatteryPausedBackground else BatteryWarningBackground
        val textColor = if (state.isPaused) BatteryPausedText else BatteryWarningText
        val iconColor = if (state.isPaused) BatteryIconPaused else BatteryIconWarning

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
            colors = CardDefaults.defaultColors(
                color = backgroundColor,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = ANDROID_BATTERY_ICON),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconColor,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (state.isPaused) {
                            stringResource(R.string.battery_paused_title)
                        } else {
                            stringResource(R.string.battery_warning_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = textColor,
                    )
                }

                Text(
                    text = if (state.isPaused) {
                        stringResource(
                            R.string.battery_status_paused_with_level,
                            state.batteryLevel,
                            state.batteryThreshold,
                        )
                    } else {
                        stringResource(
                            R.string.battery_warning_message,
                            state.batteryLevel,
                        )
                    },
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 10.dp),
                )

                Text(
                    text = if (state.isPaused) {
                        stringResource(
                            R.string.battery_management_paused_description,
                            state.batteryThreshold,
                        )
                    } else {
                        stringResource(
                            R.string.battery_pause_active_description,
                            state.batteryThreshold,
                        )
                    },
                    color = textColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
