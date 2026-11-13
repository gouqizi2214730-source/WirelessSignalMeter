package com.craxiom.networksurvey.ui.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.bluetooth.AddressType
import com.craxiom.messaging.bluetooth.SupportedTechnologies
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.BluetoothMessageConstants
import com.craxiom.networksurvey.ui.SignalChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.manufacturer.BluetoothManufacturerCard
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight
import com.craxiom.networksurvey.util.ColorUtils

/**
 * A Compose screen that shows the details of a single Bluetooth device. The main purpose for this
 * screen is to display the RSSI chart for the selected BT device so that the RSSI can be viewed
 * over time.
 */
@Composable
internal fun BluetoothDetailsScreen(
    viewModel: BluetoothDetailsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val rssi by viewModel.rssiFlow.collectAsStateWithLifecycle()
    val scanRate by viewModel.scanRate.collectAsStateWithLifecycle()
    val colorId = ColorUtils.getColorForSignalStrength(rssi)
    val colorResource = Color(context.getColor(colorId))

    Scaffold(
        topBar = {
            TitleBar(
                "Bluetooth Device Details"
            ) { onNavigateBack() }
        },
    ) { insetPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(padding),
            verticalArrangement = Arrangement.spacedBy(padding),
            modifier = Modifier.padding(insetPadding)
        ) {
            chartItems(viewModel, colorResource, rssi, scanRate, onNavigateToSettings)
        }
    }
}

private fun LazyListScope.chartItems(
    viewModel: BluetoothDetailsViewModel,
    signalStrengthColor: Color,
    rssi: Float,
    scanRate: Int,
    onNavigateToSettings: () -> Unit
) {
    item {
        SelectionContainer {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = padding / 2)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = padding / 2),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.bluetoothData.sourceAddress,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(LocalContext.current.getColor(R.color.colorAccent))
                                )
                            )
                            Text(
                                text = "源地址",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (rssi == UNKNOWN_RSSI) "未知" else "${rssi.toInt()} dBm",
                                style = MaterialTheme.typography.titleMedium.copy(color = signalStrengthColor)
                            )
                            Text(
                                text = "信号强度",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    viewModel.bluetoothData.otaDeviceName.takeIf { it.isNotEmpty() }?.let {
                        LabeledRow("设备名称：", it)
                    }

                    LabeledRow(
                        label = "支持技术：",
                        value = BluetoothMessageConstants.getSupportedTechString(
                            viewModel.bluetoothData.supportedTechnologies
                        )
                    )

                    viewModel.bluetoothData.deviceClass.takeIf { it.isNotEmpty() }?.let {
                        LabeledRow("设备类别：", it)
                    }

                    val formattedAddressType =
                        formatAddressType(viewModel.bluetoothData.addressType)
                    LabeledRow("地址类型：", formattedAddressType)

                    val uuids = viewModel.bluetoothData.serviceUuidsList
                    if (uuids.isNotEmpty()) {
                        LabeledRow("服务 UUID：", uuids.joinToString(", "))
                    }

                    viewModel.bluetoothData.companyId.takeIf { it.isNotEmpty() }?.let {
                        LabeledRow("公司 ID：", it)
                    }
                }
            }
        }
    }

    item {
        BluetoothManufacturerCard(
            mac = viewModel.bluetoothData.sourceAddress,
            serviceUuids = viewModel.bluetoothData.serviceUuidsList,
            companyId = viewModel.bluetoothData.companyId
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = padding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "扫描速率：",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "$scanRate seconds",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                ScanRateInfoButton()
                OpenSettingsButton(onNavigateToSettings)
            }
        }
    }

    cardItem {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "信号强度（最近 2 分钟）",
                style = MaterialTheme.typography.titleMedium
            )
            SignalChart(viewModel)
        }
    }
}

private fun LazyListScope.cardItem(content: @Composable () -> Unit) {
    item {
        Card(colors = CardDefaults.defaultColors()) {
            Box(Modifier.padding(padding)) {
                content()
            }
        }
    }
}

@Composable
fun ScanRateInfoButton() {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            Icons.Default.Info,
            contentDescription = "关于蓝牙扫描速率",
        )
    }

    // Info Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("蓝牙扫描速率说明") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "扫描蓝牙设备的频率（以秒计）。较小的值会降低电池续航，但较大值会使信号强度图过期。23 秒是支持的最小蓝牙扫描间隔，因为一次完整扫描需要这么长时间。"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false }
                ) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
fun OpenSettingsButton(onNavigateToSettings: () -> Unit) {

    IconButton(onClick = {
        onNavigateToSettings()
    }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "设置按钮",
        )
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = padding)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(2f)
        )
    }
}

fun formatAddressType(addressType: AddressType): String {
    return addressType.name.lowercase().replaceFirstChar { it.uppercase() }
}

private val padding = 16.dp

@PreviewDayNight()
@Composable
fun BluetoothDetailsScreenPreview() {
    val mockRecord = BluetoothRecordData.newBuilder()
        .setSourceAddress("00:11:22:33:44:55")
        .setOtaDeviceName("Mock Device")
        .setSupportedTechnologies(SupportedTechnologies.DUAL)
        .setDeviceClass("41C")
        .setAddressType(AddressType.PUBLIC)
        .addServiceUuids("0000fe07-0000-1000-8000-00805f9b34fb")
        .build()

    // Create a real instance of the ViewModel
    val viewModel = BluetoothDetailsViewModel()

    // Inject the fake BluetoothRecordData (since it's lateinit and internal)
    viewModel.apply {
        val dataField = BluetoothDetailsViewModel::class.java.getDeclaredField("bluetoothData")
        dataField.isAccessible = true
        dataField.set(this, mockRecord)

        addNewRssi(-58f)
    }

    NsPreview {
        BluetoothDetailsScreen(
            viewModel = viewModel,
            onNavigateBack = {},
            onNavigateToSettings = {})
    }
}