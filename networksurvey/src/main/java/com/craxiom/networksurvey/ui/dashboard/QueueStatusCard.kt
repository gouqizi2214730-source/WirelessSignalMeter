package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val WarningOrange = Color(0xFFE65100)
private val WarningOrangeBg = Color(0xFFFFF3E0)

/**
 * KernelSU 风格队列积压/降级警告卡片
 */
@Composable
fun QueueStatusCard(
    state: QueueUiState,
    onAdjustLimit: () -> Unit,
    onDisableLimit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
            colors = CardDefaults.defaultColors(
                color = WarningOrangeBg,
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
                            painter = painterResource(id = R.drawable.ic_cloud_off),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = WarningOrange,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (state.isDropping) {
                            stringResource(R.string.mqtt_drop_mode_title)
                        } else {
                            stringResource(R.string.queue_paused_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WarningOrange,
                    )
                }

                Text(
                    text = if (state.isDropping) {
                        stringResource(R.string.mqtt_drop_mode_subtitle)
                    } else {
                        stringResource(R.string.queue_paused_subtitle)
                    },
                    color = WarningOrange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 10.dp),
                )

                Text(
                    text = when {
                        state.isDropping && state.isUnderMdmControl ->
                            stringResource(R.string.mqtt_drop_mode_mdm_description)
                        state.isDropping ->
                            stringResource(R.string.mqtt_drop_mode_description)
                        state.isUnderMdmControl ->
                            stringResource(R.string.queue_paused_mdm_description)
                        else ->
                            stringResource(R.string.queue_paused_description)
                    },
                    color = WarningOrange.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onAdjustLimit,
                    ) {
                        Text(
                            text = if (state.isUnderMdmControl) {
                                stringResource(R.string.queue_paused_view_settings)
                            } else {
                                stringResource(R.string.queue_paused_adjust_limit)
                            },
                        )
                    }

                    if (!state.isUnderMdmControl) {
                        TextButton(
                            onClick = onDisableLimit,
                        ) {
                            Text(
                                text = stringResource(R.string.queue_paused_disable_limit),
                                color = WarningOrange,
                            )
                        }
                    }
                }
            }
        }
    }
}
