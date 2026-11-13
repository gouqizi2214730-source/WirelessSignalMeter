package com.craxiom.networksurvey.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.util.LocationStatusHelper.LocationState
import com.craxiom.networksurvey.util.MeasurementFormatter
import kotlinx.coroutines.delay
import java.text.DecimalFormat

private val LocationGreen = Color(0xFF2E7D32)
private val LocationGreenBg = Color(0xFFE8F5E9)
private val LocationYellow = Color(0xFFF57F17)
private val LocationYellowBg = Color(0xFFFFFDE7)
private val LocationRed = Color(0xFFC62828)
private val LocationRedBg = Color(0xFFFFEBEE)

private val locationFormat = DecimalFormat("###.#####")

/**
 * KernelSU 风格现代圆角定位卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationCard(
    state: LocationUiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    LaunchedEffect(state.state) {
        showHint = false
        if (state.state == LocationState.SEARCHING) {
            delay(15_000L)
            showHint = true
        }
    }

    val context = LocalContext.current

    val (iconColor, iconBgColor) = when (state.state) {
        LocationState.FIX -> Pair(LocationGreen, LocationGreenBg)
        LocationState.SEARCHING -> Pair(LocationYellow, LocationYellowBg)
        else -> Pair(LocationRed, LocationRedBg)
    }

    val statusText = when (state.state) {
        LocationState.FIX -> stringResource(R.string.location_status_fixed)
        LocationState.SEARCHING -> stringResource(R.string.location_status_searching)
        LocationState.GPS_DISABLED -> stringResource(R.string.location_status_gps_disabled)
        LocationState.NO_GPS -> stringResource(R.string.location_status_no_gps)
        LocationState.MISSING_PERMISSION -> stringResource(R.string.location_status_missing_permission)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 5.dp),
        colors = CardDefaults.defaultColors(
            color = DashboardCardDefaults.cardContainerColor(),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (state.hasLocation) {
                            expanded = !expanded
                        }
                    },
                    onLongClick = {
                        if (state.hasLocation) {
                            copyTextToClipboard(
                                context,
                                context.getString(R.string.location_clip_label),
                                getDetailsText(context, state),
                                context.getString(R.string.location_copied),
                            )
                        }
                    },
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆形定位图标胶囊容器
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconColor,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = DashboardCardDefaults.textPrimary(),
                    )
                    if (state.state == LocationState.FIX) {
                        Text(
                            text = stringResource(
                                R.string.location_status_accuracy,
                                MeasurementFormatter.formatAccuracy(context, state.accuracy),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DashboardCardDefaults.textSecondary(),
                        )
                    }
                }

                // 复制/展开状态提示胶囊
                if (state.hasLocation) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (expanded) "收起" else "详情",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && state.hasLocation,
                enter = expandVertically(tween(200)),
                exit = shrinkVertically(tween(200)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = getDetailsText(context, state),
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp
                        ),
                        color = DashboardCardDefaults.textPrimary(),
                    )
                }
            }

            AnimatedVisibility(
                visible = showHint && state.state == LocationState.SEARCHING,
                enter = expandVertically() + fadeIn(),
            ) {
                Text(
                    text = AnnotatedString.fromHtml(
                        stringResource(R.string.location_hint_open_settings)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .combinedClickable(onClick = onNavigateToSettings),
                )
            }
        }
    }
}

private fun getDetailsText(context: Context, state: LocationUiState): String {
    val latLon =
        "${locationFormat.format(state.latitude)}, ${locationFormat.format(state.longitude)}"
    val altitude = MeasurementFormatter.formatAltitude(context, state.altitude)
    return context.getString(R.string.location_details_format, latLon, altitude)
}
