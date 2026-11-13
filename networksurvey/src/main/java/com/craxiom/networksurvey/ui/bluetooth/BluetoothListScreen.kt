package com.craxiom.networksurvey.ui.bluetooth

import android.util.SparseArray
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.networksurvey.constants.BluetoothMessageConstants
import com.craxiom.networksurvey.data.BluetoothCompanyNameProvider
import com.craxiom.networksurvey.fragments.model.BluetoothViewModel
import com.craxiom.networksurvey.ui.bluetooth.model.BluetoothListBridge
import com.craxiom.networksurvey.util.ColorUtils

/**
 * 蓝牙扫描结果列表（Compose 版，KernelSU 3.2.5 视觉语言）。
 *
 * Java 侧的 [BluetoothViewModel] 用 SortedSet 保存记录并通过
 * [BluetoothViewModel.bumpListVersion] 递增版本号；本页观察该版本号后
 * 经 [BluetoothListBridge] 重新取快照渲染。
 */
@Composable
fun BluetoothListScreen(
    viewModel: BluetoothViewModel,
    onDeviceSelected: (BluetoothRecord) -> Unit,
    onTogglePause: () -> Unit,
    onSortClick: () -> Unit,
) {
    val context = LocalContext.current
    val companyNameResolver = BluetoothCompanyNameProvider.getInstance(context)

    // 版本号变化驱动列表重组；其余 LiveData 直接观察
    val version by viewModel.getListVersion().observeAsState()
    val devices = BluetoothListBridge.snapshot(viewModel, version)
    val deviceCount by viewModel.getDevicesInScan().observeAsState(0)
    val scanStatusId by viewModel.getScanStatusId().observeAsState(0)
    val paused by viewModel.areUpdatesPaused().observeAsState(false)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 顶部状态条作为列表首项，跟随滑动自然滚出/滑入
        item(key = "status_strip") {
            BluetoothStatusStrip(
                deviceCount = deviceCount ?: devices.size,
                scanStatusText = if (scanStatusId != null && scanStatusId != 0) {
                    runCatching { context.getString(scanStatusId!!) }.getOrDefault("")
                } else "",
                isPaused = paused ?: false,
                onSortClick = onSortClick,
                onTogglePause = onTogglePause,
            )
        }

        if (devices.isEmpty()) {
            item(key = "empty_state") {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                ) {
                    BluetoothEmptyState()
                }
            }
        } else {
            items(items = devices, key = { it.data.sourceAddress }) { record ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    BluetoothDeviceCard(
                        record = record,
                        companyName = companyNameResolver.resolveCompanyName(
                            record.data.serviceUuidsList,
                            record.data.companyId,
                        ),
                        isAirTag = isAirTag(viewModel.getManufacturerData(record.data.sourceAddress)),
                        onClick = { onDeviceSelected(record) },
                    )
                }
            }
        }
    }
}

/** 顶部状态条：设备数量 + 扫描状态 + 排序/暂停操作。 */
@Composable
private fun BluetoothStatusStrip(
    deviceCount: Int,
    scanStatusText: String,
    isPaused: Boolean,
    onSortClick: () -> Unit,
    onTogglePause: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "扫描到 $deviceCount 个设备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (scanStatusText.isNotBlank()) {
                    Text(
                        text = scanStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "排序",
                modifier = Modifier
                    .clickable(onClick = onSortClick)
                    .padding(end = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = if (isPaused) "继续" else "暂停",
                modifier = Modifier.clickable(onClick = onTogglePause),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 单个蓝牙设备卡片：源地址 + 信号强度 + 厂商/AirTag/设备名/支持技术。 */
@Composable
private fun BluetoothDeviceCard(
    record: BluetoothRecord,
    companyName: String?,
    isAirTag: Boolean,
    onClick: () -> Unit,
) {
    val data = record.data
    val signalStrength = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else null

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.sourceAddress,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (signalStrength != null) {
                    Text(
                        text = "$signalStrength dBm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = signalColor(signalStrength),
                    )
                }
            }

            val details = buildList {
                if (!companyName.isNullOrBlank()) add(companyName)
                if (isAirTag) add("AirTag")
                if (data.otaDeviceName.isNotBlank()) add("名称：${data.otaDeviceName}")
                val tech = BluetoothMessageConstants.getSupportedTechString(data.supportedTechnologies)
                if (tech.isNotBlank()) add(tech)
            }

            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString("  ·  "),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BluetoothEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "尚未扫描到蓝牙设备",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "请确认蓝牙已开启并已授予扫描权限",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** AirTag 判定：Apple 厂商 ID(0x004C) 且首字节为 0x12(离线发现) 或 0x07。 */
private fun isAirTag(manufacturerData: SparseArray<ByteArray>?): Boolean {
    if (manufacturerData == null) return false
    val bytes = manufacturerData.get(0x004C) ?: return false
    if (bytes.isEmpty()) return false
    return bytes[0] == 0x12.toByte() || bytes[0] == 0x07.toByte()
}

@Composable
private fun signalColor(signalStrength: Int): Color {
    val context = LocalContext.current
    return Color(context.getColor(ColorUtils.getColorForSignalStrength(signalStrength.toFloat())))
}
