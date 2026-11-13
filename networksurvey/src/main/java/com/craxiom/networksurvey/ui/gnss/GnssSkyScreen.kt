package com.craxiom.networksurvey.ui.gnss

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.ui.component.statustag.StatusTag
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults

/**
 * GNSS（卫星天空图）页面，KernelSU 3.2.5 视觉语言。
 *
 * 卫星星座画布 [GnssSkyView]、C/N0 渐变指示条、图例均为上游 Network Survey 的精细自绘 View，
 * 风险高、收益低，故通过 [AndroidView] 原样保留；本 Composable 只在外层套上 KernelSU 24dp 大圆角卡：
 * 顶部 Hero 状态卡（锁定状态 + 可见/用于定位卫星数）。
 */
@Composable
fun GnssSkyScreen(
    viewModel: SignalInfoViewModel,
    legacyView: () -> View?,
) {
    val statuses by viewModel.filteredStatuses.observeAsState(emptyList())
    val fixState by viewModel.fixState.observeAsState(FixState.NotAcquired)
    val hasFix = fixState is FixState.Acquired

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DashboardCardDefaults.margin)
    ) {
        Spacer(Modifier.height(8.dp))
        GnssHeroCard(hasFix = hasFix, statuses = statuses)
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { legacyView() ?: View(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun GnssHeroCard(hasFix: Boolean, statuses: List<SatelliteStatus>) {
    val used = statuses.count { it.usedInFix }
    val inView = statuses.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_gnss),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "卫星定位",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "可见 $inView · 用于定位 $used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusTag(
                label = if (hasFix) "已锁定" else "未锁定",
                backgroundColor = if (hasFix) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasFix) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
