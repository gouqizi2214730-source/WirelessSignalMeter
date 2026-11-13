package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyMissionIdToClipboard
import kotlinx.coroutines.delay

/**
 * KernelSU 风格 Mission ID 质感卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MissionIdCard(
    state: MissionIdUiState,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var showNewBadge by remember { mutableStateOf(false) }
    var previousMissionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.missionId) {
        val previous = previousMissionId
        previousMissionId = state.missionId
        if (state.missionId.isNotEmpty() && previous != null && previous != state.missionId) {
            showNewBadge = true
            delay(NEW_BADGE_DURATION_MS)
            showNewBadge = false
        }
    }

    val copyMissionId = { copyMissionIdToClipboard(context, state.missionId) }

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
                color = DashboardCardDefaults.cardContainerColor(),
            ),
        ) {
            DashboardCardHeader(
                icon = R.drawable.ic_survey_monitor,
                title = stringResource(R.string.mission_id_card_title),
                onHelpClick = onHelpClick,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                    .combinedClickable(
                        onClick = { copyMissionId() },
                        onLongClick = { copyMissionId() }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.missionId,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            fontFamily = FontFamily.Monospace,
                            color = DashboardCardDefaults.textPrimary(),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.active) {
                                    stringResource(R.string.mission_id_caption_active)
                                } else {
                                    stringResource(R.string.mission_id_caption_recent)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = DashboardCardDefaults.textSecondary(),
                            )
                            if (showNewBadge) {
                                Spacer(modifier = Modifier.width(8.dp))
                                NewBadge()
                            }
                        }
                    }

                    IconButton(
                        onClick = { copyMissionId() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_content_copy),
                            contentDescription = stringResource(R.string.mission_id_copy_content_description),
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NewBadge() {
    Text(
        text = stringResource(R.string.mission_id_new_badge),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private const val NEW_BADGE_DURATION_MS = 3000L
