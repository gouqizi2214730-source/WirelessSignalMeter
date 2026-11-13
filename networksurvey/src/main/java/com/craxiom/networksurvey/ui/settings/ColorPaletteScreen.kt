package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBar
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBarItem
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.util.ThemePref
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private val displayModes = listOf(ThemePref.MODE_SYSTEM, ThemePref.MODE_LIGHT, ThemePref.MODE_DARK)
private val themeSeeds = listOf(
    "默认" to Color(0xFF0F6681), "蓝色" to Color(0xFF1565C0), "绿色" to Color(0xFF2E7D32),
    "紫色" to Color(0xFF7B1FA2), "橙色" to Color(0xFFE65100), "红色" to Color(0xFFC62828),
    "玫红" to Color(0xFFAD3259), "青色" to Color(0xFF00696D),
)

@Composable
fun ColorPaletteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val version = ThemePref.currentThemeVersion
    val mode = remember(version, context) { ThemePref.get(context) }
    val dynamicSupported = ThemePref.isDynamicColorSupported()
    val dynamic = dynamicSupported && remember(version, context) { ThemePref.isMonet(context) }
    val seed = remember(version, context) { ThemePref.getSeed(context) }
    val amoled = remember(version, context) { ThemePref.isAmoled(context) }
    val dark = mode == ThemePref.MODE_DARK || (mode == ThemePref.MODE_SYSTEM && isSystemInDarkTheme())
    val glassSupported = remember { isRuntimeShaderSupported() }
    val glass = glassSupported && remember(version, context) { ThemePref.isLiquidGlassEnabled(context) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TitleBar("主题", compact = true, onBackClick = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = DashboardCardDefaults.margin, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("preview") { ThemePreview(glass, dark, dark && amoled) }
                item("mode") {
                    ThemeGroup("显示模式") {
                        TabRow(
                            tabs = listOf("跟随系统", "浅色", "深色"),
                            selectedTabIndex = displayModes.indexOf(mode).coerceAtLeast(0),
                            onTabSelected = { index ->
                                val next = displayModes[index]
                                if (mode != next) {
                                    ThemePref.set(context, next)
                                    ThemePref.apply(context)
                                }
                            },
                        )
                        if (mode != ThemePref.MODE_LIGHT) {
                            ThemeToggle(Icons.Rounded.DarkMode, "纯黑背景", "深色模式下生效", amoled) {
                                ThemePref.setAmoled(context, it)
                                ThemePref.apply(context)
                            }
                        }
                    }
                }
                item("palette") {
                    ThemeGroup("主题配色") {
                        if (dynamicSupported) {
                            ThemeToggle(Icons.Rounded.Wallpaper, "跟随系统壁纸", "使用系统动态取色", dynamic) {
                                prefs.edit().putBoolean(ThemePref.KEY_MONET, it).apply()
                                ThemePref.apply(context)
                            }
                            ThemeDivider()
                        }
                        Text(
                            if (dynamic) "自定义主题色" else "主题色 · ${themeSeeds.getOrNull(seed)?.first ?: "默认"}",
                            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        )
                        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                            themeSeeds.chunked(4).forEachIndexed { row, swatches ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    swatches.forEachIndexed { column, (label, color) ->
                                        val index = row * 4 + column
                                        val selected = !dynamic && index == seed
                                        Box(
                                            Modifier.size(48.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton) {
                                                if (dynamic) prefs.edit().putBoolean(ThemePref.KEY_MONET, false).apply()
                                                ThemePref.setSeed(context, index)
                                                ThemePref.apply(context)
                                            }.semantics { contentDescription = label },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.size(31.dp).clip(CircleShape).background(color)
                                                    .border(1.dp, colors.outlineVariant, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!dynamic && seed != ThemePref.SEED_DEFAULT) {
                            ThemeDivider()
                            ThemeMenu("配色风格", ThemePref.getPaletteStyle(context), PaletteStyle.entries.map { it.name }) {
                                ThemePref.setPaletteStyle(context, it)
                                ThemePref.apply(context)
                            }
                            ThemeDivider()
                            ThemeMenu("配色算法", ThemePref.getColorSpec(context), ColorSpec.SpecVersion.entries.map { it.name }) {
                                ThemePref.setColorSpec(context, it)
                                ThemePref.apply(context)
                            }
                        }
                    }
                }
                item("effects") {
                    ThemeGroup("界面效果") {
                        ThemeToggle(
                            Icons.Rounded.BlurOn,
                            "液态玻璃",
                            if (glassSupported) "悬浮底栏随页面内容折射" else "当前设备不支持玻璃折射",
                            glass,
                            enabled = glassSupported,
                        ) { ThemePref.setLiquidGlassEnabled(context, it) }
                        ThemeDivider()
                        ThemeToggle(Icons.Rounded.Tune, "Miuix 进度条", "用于信号参数", ThemePref.isMiuixProgressBar(context)) {
                            ThemePref.setProgressBarStyle(context,
                                if (it) ThemePref.PROGRESS_BAR_MIUIX else ThemePref.PROGRESS_BAR_MATERIAL3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = DashboardCardDefaults.shape,
            color = DashboardCardDefaults.cardContainerColor(),
            border = BorderStroke(1.dp, DashboardCardDefaults.outlineVariant()),
        ) {
            Column(Modifier.padding(5.dp), content = content)
        }
    }
}

@Composable
private fun ThemeDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = DashboardCardDefaults.outlineVariant())
}

@Composable
private fun ThemeToggle(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(DashboardCardDefaults.smallShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (enabled) colors.primary else colors.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ThemeMenu(title: String, current: String, items: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(current) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = {
                        expanded = false
                        onSelect(item)
                    })
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(glass: Boolean, dark: Boolean, amoled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val backdrop = if (glass) rememberLayerBackdrop() else null
    var page by rememberSaveable { mutableIntStateOf(0) }
    val icons = listOf(Icons.Rounded.SignalCellularAlt, Icons.Rounded.Build, Icons.Rounded.Settings)
    val labels = listOf(R.string.home_signal, R.string.home_tools, R.string.settings)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardCardDefaults.shape,
        color = DashboardCardDefaults.cardContainerColor(),
        border = BorderStroke(1.dp, DashboardCardDefaults.outlineVariant()),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("主题预览", style = MaterialTheme.typography.titleSmall)
                Text(if (amoled) "纯黑" else if (dark) "深色" else "浅色",
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(165.dp).clip(DashboardCardDefaults.smallShape)) {
                Column(
                    Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                        .background(Brush.linearGradient(listOf(colors.background, colors.secondaryContainer, colors.primaryContainer)))
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(stringResource(labels[page]), style = MaterialTheme.typography.titleMedium)
                    Surface(shape = DashboardCardDefaults.smallShape, color = colors.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icons[page], null, Modifier.size(17.dp), tint = colors.primary)
                            Text(when (page) { 1 -> "基站地图"; 2 -> "外观"; else -> "蜂窝网络" },
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (page == 0) Text("−86 dBm", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                }
                FloatingBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp),
                    selectedIndex = page, onSelected = { page = it }, backdrop = backdrop, isBlurEnabled = glass,
                    accentColor = colors.primary, surfaceContainer = colors.surfaceContainer,
                ) { select ->
                    icons.forEachIndexed { index, icon ->
                        val tint = if (index == page) colors.primary else colors.onSurfaceVariant
                        FloatingBottomBarItem(onClick = { select(index) }, isSelected = index == page) {
                            Icon(icon, null, Modifier.size(17.dp), tint = tint)
                            Text(stringResource(labels[index]), style = MaterialTheme.typography.labelSmall, color = tint)
                        }
                    }
                }
            }
        }
    }
}
