package com.craxiom.networksurvey.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.ContainerCellularFragmentBinding
import com.craxiom.networksurvey.fragments.MainCellularFragment
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.component.PagerSpringSpec
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBar
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBarItem
import com.craxiom.networksurvey.ui.settings.SettingsScreen
import com.craxiom.networksurvey.update.UpdateChecker
import com.craxiom.networksurvey.update.UpdateDialog
import com.craxiom.networksurvey.update.UpdateDownloader
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class HomeTab(@StringRes val title: Int, val icon: ImageVector) {
    Signal(R.string.home_signal, Icons.Rounded.SignalCellularAlt),
    Tools(R.string.home_tools, Icons.Rounded.Build),
    Settings(R.string.settings, Icons.Rounded.Settings),
}

internal val FloatingNavigationClearance = 80.dp

@Composable
fun HomeScreen(
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel,
) {
    val pagerState = rememberPagerState { HomeTab.entries.size }
    val pagerScope = rememberCoroutineScope()
    var bubbleProgress by remember { mutableFloatStateOf(pagerState.currentPage.toFloat()) }

    val colors = MaterialTheme.colorScheme
    val background = colors.background
    val bottomPadding = FloatingNavigationClearance +
        WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val openMap = {
        mainNavController.currentBackStackEntry?.savedStateHandle?.set(
            ServingCellInfo.SUBSCRIPTION_KEY,
            sharedViewModel.latestServingCellInfo?.subscriptionId,
        )
        mainNavController.navigate(NavOption.TowerMap.name) { launchSingleTop = true }
    }

    val context = LocalContext.current
    val pendingUpdate by UpdateChecker.pendingUpdate
    LaunchedEffect(Unit) {
        UpdateChecker.check(context, force = false)
    }
    pendingUpdate?.let { update ->
        UpdateDialog(
            version = update,
            onDismiss = { UpdateChecker.pendingUpdate.value = null },
            onDownload = {
                UpdateDownloader.download(context, update.apkUrl)
                UpdateChecker.pendingUpdate.value = null
            },
            onIgnore = {
                UpdateChecker.ignoreVersion(context, update.versionCode)
                UpdateChecker.pendingUpdate.value = null
            },
        )
    }

    Box(Modifier.fillMaxSize().background(background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                ),
            // Keep the Android fragment container mounted while switching between home tabs.
            beyondViewportPageCount = HomeTab.entries.lastIndex,
            overscrollEffect = null,
        ) { page ->
            when (HomeTab.entries[page]) {
                HomeTab.Signal -> SignalTab(onOpenMap = openMap)
                HomeTab.Tools -> ToolsScreen(
                    bottomPadding = bottomPadding,
                    onOpenMap = openMap,
                    onOpenGnss = {
                        mainNavController.navigate(NavOption.Gnss.name) { launchSingleTop = true }
                    },
                    onOpenSpeedTest = {
                        mainNavController.navigate(NavDrawerOption.SpeedTest.name) { launchSingleTop = true }
                    },
                    onOpenWifi = {
                        mainNavController.navigate(NavOption.Wifi.name) { launchSingleTop = true }
                    },
                )
                HomeTab.Settings -> SettingsScreen(
                    onNavigateToSsidExclusion = { mainNavController.navigate(NavOption.SsidExclusionList.name) },
                    onNavigateToAcknowledgments = { mainNavController.navigate(NavOption.Acknowledgments.name) },
                    onNavigateToEngParams = { mainNavController.navigate(NavDrawerOption.EngineeringParams.name) },
                    onNavigateToTheme = { mainNavController.navigate(NavOption.ColorPalette.name) },
                    bottomContentPadding = bottomPadding,
                    header = { HomePageTitle(R.string.settings) },
                )
            }
        }

        FloatingBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(bottom = 8.dp),
            selectedIndex = pagerState.targetPage,
            onSelected = { index ->
                pagerScope.launch { pagerState.animateScrollToPage(index, animationSpec = PagerSpringSpec) }
            },
            tabsCount = HomeTab.entries.size,
            isBlurEnabled = false,
            accentColor = colors.primary,
            surfaceContainer = colors.surfaceContainer,
            onBubbleProgress = { bubbleProgress = it },
        ) { activateTab ->
            HomeTab.entries.forEachIndexed { index, tab ->
                val distance = abs(bubbleProgress - index).coerceIn(0f, 1f)
                val tint = lerp(colors.primary, colors.onSurfaceVariant, distance)
                FloatingBottomBarItem(
                    onClick = { activateTab(index) },
                    isSelected = pagerState.currentPage == index,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(tab.title),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomePageTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun SignalTab(onOpenMap: () -> Unit) {
    var fragment by remember { mutableStateOf<MainCellularFragment?>(null) }
    var simCount by remember { mutableIntStateOf(0) }
    var simLabel by remember { mutableStateOf("") }

    DisposableEffect(fragment) {
        val cellular = fragment
        cellular?.setSimSwitchCallback { _, count, label ->
            simCount = count
            simLabel = label
        }
        simCount = cellular?.simCount ?: 0
        simLabel = cellular?.currentSimLabel.orEmpty()
        onDispose { cellular?.setSimSwitchCallback(null) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = simLabel.takeIf { simCount > 0 } ?: stringResource(R.string.home_signal)
            if (simCount > 1) {
                val switchSim = stringResource(R.string.sim_switch)
                Surface(
                    onClick = { fragment?.switchToNextSim() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                        .widthIn(max = 260.dp)
                        .semantics { contentDescription = "$label, $switchSim" },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 260.dp),
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onOpenMap) {
                Icon(Icons.Rounded.Map, contentDescription = stringResource(R.string.open_tower_map))
            }
        }

        AndroidViewBinding(
            factory = ContainerCellularFragmentBinding::inflate,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            fragment = cellularFragmentContainerView.getFragment<MainCellularFragment>()
        }
    }
}
