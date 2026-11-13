package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.util.ThemePref
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

private val themeModes = listOf(ThemePref.MODE_SYSTEM to "跟随系统", ThemePref.MODE_LIGHT to "浅色", ThemePref.MODE_DARK to "深色")
private val themeSeeds = listOf(
    "默认" to Color(0xFF0F6681), "蓝色" to Color(0xFF1565C0), "绿色" to Color(0xFF2E7D32),
    "紫色" to Color(0xFF7B1FA2), "橙色" to Color(0xFFE65100), "红色" to Color(0xFFC62828),
    "玫红" to Color(0xFFAD3259), "青色" to Color(0xFF00696D),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val version = ThemePref.currentThemeVersion
    val mode = remember(version, context) { ThemePref.get(context) }
    val dynamicAvailable = ThemePref.isDynamicColorSupported()
    val dynamic = dynamicAvailable && remember(version, context) { ThemePref.isMonet(context) }
    val seed = remember(version, context) { ThemePref.getSeed(context) }
    val amoled = remember(version, context) { ThemePref.isAmoled(context) }
    val dark = mode == ThemePref.MODE_DARK || (mode == ThemePref.MODE_SYSTEM && isSystemInDarkTheme())
    val colors = MaterialTheme.colorScheme
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemePreviewCard(dark = dark, amoled = dark && amoled)
        ThemePanel("显示模式") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = {
                            if (mode != value) {
                                ThemePref.set(context, value)
                                ThemePref.apply(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, themeModes.size),
                    ) { Text(label) }
                }
            }
            if (mode != ThemePref.MODE_LIGHT) {
                ThemeSwitch("纯黑背景", "深色模式下生效", amoled) {
                    ThemePref.setAmoled(context, it)
                    ThemePref.apply(context)
                }
            }
        }
        ThemePanel("主题色") {
            if (dynamicAvailable) {
                ThemeSwitch("跟随系统壁纸", "使用 Android 动态取色", dynamic) {
                    prefs.edit().putBoolean(ThemePref.KEY_MONET, it).apply()
                    ThemePref.apply(context)
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
            Text(
                if (dynamic) "点选色板即可改用自定义主题色" else "当前配色 · ${themeSeeds.getOrNull(seed)?.first ?: "默认"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                themeSeeds.chunked(4).forEachIndexed { row, swatches ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        swatches.forEachIndexed { column, (name, color) ->
                            val index = row * 4 + column
                            val selected = !dynamic && index == seed
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .selectable(selected = selected, role = Role.RadioButton) {
                                        if (dynamic) prefs.edit().putBoolean(ThemePref.KEY_MONET, false).apply()
                                        ThemePref.setSeed(context, index)
                                        ThemePref.apply(context)
                                    }
                                    .semantics { contentDescription = name },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(color)
                                        .border(1.dp, colors.outlineVariant, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(19.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        ThemePanel("其他外观") {
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起选项" else "展开选项") }
            if (advanced) {
                if (!dynamic && seed != ThemePref.SEED_DEFAULT) {
                    ThemeMenu("配色风格", ThemePref.getPaletteStyle(context), PaletteStyle.entries.map { it.name }) {
                        ThemePref.setPaletteStyle(context, it)
                        ThemePref.apply(context)
                    }
                    ThemeMenu("配色算法", ThemePref.getColorSpec(context), ColorSpec.SpecVersion.entries.map { it.name }) {
                        ThemePref.setColorSpec(context, it)
                        ThemePref.apply(context)
                    }
                }
                ThemeSwitch("Miuix 进度条", "用于信号参数的进度条", ThemePref.isMiuixProgressBar(context)) {
                    ThemePref.setProgressBarStyle(context, if (it) ThemePref.PROGRESS_BAR_MIUIX else ThemePref.PROGRESS_BAR_MATERIAL3)
                }
            }
        }
    }
}

@Composable
private fun ThemePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = DashboardCardDefaults.shape, color = DashboardCardDefaults.cardContainerColor()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ThemeSwitch(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun ThemeMenu(title: String, current: String, items: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(current) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { expanded = false; onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(dark: Boolean, amoled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = DashboardCardDefaults.shape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("主题预览", style = MaterialTheme.typography.titleMedium)
                Text(if (amoled) "纯黑" else if (dark) "深色" else "浅色", style = MaterialTheme.typography.labelLarge)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = colors.background, contentColor = colors.onBackground) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("无线无忧", style = MaterialTheme.typography.titleSmall)
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.SignalCellularAlt, null, Modifier.size(18.dp))
                            Text("信号趋势", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("−86 dBm", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                                Icon(Icons.Rounded.SignalCellularAlt, null, Modifier.padding(horizontal = 14.dp, vertical = 4.dp).size(16.dp))
                            }
                            Icon(Icons.Rounded.Build, null, Modifier.size(16.dp))
                            Icon(Icons.Rounded.Settings, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
