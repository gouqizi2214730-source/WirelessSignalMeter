package com.craxiom.networksurvey.ui.settings

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.common.dialogs.NsSingleChoiceDialog
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults

@Composable
fun SettingsScreen(
    onNavigateToSsidExclusion: () -> Unit = {},
    onNavigateToAcknowledgments: () -> Unit = {},
    onNavigateToEngParams: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    bottomContentPadding: Dp = 24.dp,
    header: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val ouiKey = NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED
    var ouiEnabled by remember(preferences) { mutableStateOf(preferences.getBoolean(ouiKey, true)) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { header() }
            item {
                SettingsGroup("常用") {
                    SettingsLink(Icons.Rounded.Palette, "外观", "深色模式、动态取色与主题色", onNavigateToTheme)
                    SettingsDivider()
                    SettingsLink(Icons.Rounded.FileUpload, "基站工参", "导入和管理 CSV 工参", onNavigateToEngParams)
                }
            }
            item {
                SettingsGroup("扫描") {
                    ScanIntervalItem(
                        preferences,
                        NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
                        "蜂窝扫描间隔",
                        NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS,
                        Icons.Rounded.SignalCellularAlt,
                    )
                    SettingsDivider()
                    ScanIntervalItem(
                        preferences,
                        NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                        "Wi-Fi 扫描间隔",
                        NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
                        Icons.Rounded.Wifi,
                    )
                }
            }
            item {
                SettingsGroup("Wi-Fi") {
                    SettingsLink(Icons.Rounded.Wifi, "SSID 排除列表", null, onNavigateToSsidExclusion)
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Search,
                        title = "在线查询设备厂商",
                        subtitle = "根据 Wi-Fi 设备的 MAC 地址查询",
                        modifier = Modifier.toggleable(
                            value = ouiEnabled,
                            role = Role.Switch,
                            onValueChange = {
                                preferences.edit().putBoolean(ouiKey, it).apply()
                                ouiEnabled = it
                            },
                        ),
                        trailing = { Switch(checked = ouiEnabled, onCheckedChange = null) },
                    )
                }
            }
            item {
                SettingsGroup("关于") {
                    SettingsLink(
                        Icons.Rounded.Info, "开源致谢", "无线无忧 ${BuildConfig.VERSION_NAME}", onNavigateToAcknowledgments,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = DashboardCardDefaults.shape,
            color = DashboardCardDefaults.cardContainerColor(),
            border = BorderStroke(1.dp, DashboardCardDefaults.outlineVariant()),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(start = 68.dp, end = 16.dp),
        color = DashboardCardDefaults.outlineVariant(),
    )
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    SettingsRow(icon, title, subtitle, Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun ScanIntervalItem(
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Int,
    icon: ImageVector,
) {
    var value by remember(preferences, key) {
        mutableStateOf(preferences.getString(key, null)?.toIntOrNull() ?: defaultValue)
    }
    var showOptions by remember { mutableStateOf(false) }
    SettingsLink(icon, title, "$value 秒") { showOptions = true }
    if (showOptions) {
        val options = (listOf(5, 10, 15, 30, 60, 120, 300) + value).distinct().sorted()
        NsSingleChoiceDialog(
            title = title,
            options = options.map { "$it 秒" },
            selectedIndex = options.indexOf(value),
            onConfirm = { index ->
                value = options[index]
                preferences.edit().putString(key, value.toString()).apply()
                showOptions = false
            },
            onDismiss = { showOptions = false },
        )
    }
}