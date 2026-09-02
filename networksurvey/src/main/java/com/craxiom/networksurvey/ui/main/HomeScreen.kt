package com.craxiom.networksurvey.ui.main

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.ContainerBluetoothFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerCellularFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerGnssFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiFragmentBinding
import com.craxiom.networksurvey.fragments.BluetoothFragment
import com.craxiom.networksurvey.fragments.MainCellularFragment
import com.craxiom.networksurvey.fragments.MainGnssFragment
import com.craxiom.networksurvey.fragments.WifiNetworksFragment
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.util.LibUIUtils
import com.craxiom.networksurvey.ui.dashboard.DashboardScreen
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBar
import com.craxiom.networksurvey.ui.kernelsu.FloatingBottomBarItem
import com.craxiom.networksurvey.ui.main.appbar.AppBar
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.wifi.components.WifiFilterSheet
import com.craxiom.networksurvey.ui.wifi.model.WifiListUiState
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel
import com.craxiom.networksurvey.update.UpdateChecker
import com.craxiom.networksurvey.update.UpdateDialog
import com.craxiom.networksurvey.update.UpdateDownloader
import com.craxiom.networksurvey.util.PreferenceUtils
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel
) {
    var bottomNavSelectedItem by rememberSaveable { mutableIntStateOf(0) }
    var bubbleProgress by remember { mutableFloatStateOf(0f) }
    val bottomNavController: NavHostController = rememberNavController()
    var currentScreen by remember { mutableStateOf<MainScreens>(MainScreens.Cellular) }
    var currentGnssScreen by remember { mutableStateOf(GnssScreen.GNSS_DETAILS) }
    var showGnssFilterDialog by remember { mutableStateOf(false) }
    var showGnssSortDialog by remember { mutableStateOf(false) }
    var showWifiFilterSheet by remember { mutableStateOf(false) }

    // KernelSU 液态玻璃：内容层挂载 Backdrop 供悬浮底栏毛玻璃采样
    val surfaceColor = MaterialTheme.colorScheme.surface
    val tabsBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // 更新检测：每次进入主界面自动检查一次（服务端 versionCode > 本地则弹窗）
    val context = LocalContext.current
    val pendingUpdate by UpdateChecker.pendingUpdate
    LaunchedEffect(Unit) {
        UpdateChecker.check(context, force = true)
    }
    if (pendingUpdate != null) {
        UpdateDialog(
            version = pendingUpdate!!,
            onDismiss = { UpdateChecker.pendingUpdate.value = null },
            onDownload = {
                UpdateDownloader.download(context, pendingUpdate!!.apkUrl)
                UpdateChecker.pendingUpdate.value = null
            },
            onIgnore = {
                UpdateChecker.ignoreVersion(context, pendingUpdate!!.versionCode)
                UpdateChecker.pendingUpdate.value = null
            }
        )
    }

    // Shares the activity-scoped Wi-Fi list view model with WifiNetworksFragment so the app bar
    // funnel action and filter sheet operate on the same display filter as the list itself.
    val activity = LocalContext.current.findComponentActivity()
    val wifiListViewModel: WifiListViewModel? =
        if (activity != null) viewModel(viewModelStoreOwner = activity) else null
    // Only collect Wi-Fi list state while the Wi-Fi tab is showing so scan-rate updates don't
    // recompose the app bar on the other tabs.
    val wifiListUiState: WifiListUiState? =
        if (currentScreen == MainScreens.Wifi && wifiListViewModel != null) {
            wifiListViewModel.uiState.collectAsStateWithLifecycle().value
        } else {
            null
        }

    bottomNavController.addOnDestinationChangedListener { _, destination, _ ->
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, item ->
            // Only needed to update the selected item when hitting back from a bottom tab (which
            // takes you to the dashboard tab)
            if (destination.route == item.route) {
                bottomNavSelectedItem = index
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 内容层（挂载 Backdrop 供底栏毛玻璃采样）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(tabsBackdrop)
        ) {
            Scaffold(
                topBar = {
                    AppBar(
                        drawerState = drawerState,
                        title = getAppBarTitle(currentScreen),
                        appBarActions = getAppBarActions(
                            currentScreen,
                            currentGnssScreen,
                            mainNavController,
                            showGnssFilterDialog = { showGnssFilterDialog = it },
                            showGnssSortDialog = { showGnssSortDialog = true },
                            wifiFilterActive = wifiListUiState?.filter?.isActive == true,
                            showWifiFilterSheet = { showWifiFilterSheet = true })
                    )
                },
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) { padding ->
                NavHost(
                    bottomNavController,
                    startDestination = MainScreens.Cellular.route,
                    modifier = Modifier.padding(paddingValues = padding)
                ) {
                    composable(MainScreens.Cellular.route) {
                        currentScreen = MainScreens.Cellular
                        CellularFragmentInCompose()
                    }
                    composable(MainScreens.Wifi.route) {
                        currentScreen = MainScreens.Wifi
                        WifiFragmentInCompose()
                    }
                    composable(MainScreens.Bluetooth.route) {
                        currentScreen = MainScreens.Bluetooth
                        BluetoothFragmentInCompose()
                    }
                    composable(MainScreens.Gnss.route) {
                        currentScreen = MainScreens.Gnss
                        GnssFragmentInCompose(onGnssScreenChange = { newScreen ->
                            currentGnssScreen = newScreen
                        })
                    }
                }
            }
        }

        // KernelSU 风格液态玻璃悬浮底栏（悬浮于内容之上，毛玻璃采样 tabsBackdrop）
        FloatingBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedIndex = { bottomNavSelectedItem },
            onSelected = { index ->
                bottomNavSelectedItem = index
                val navigationItem = BottomNavItem().bottomNavigationItems()[index]
                bottomNavController.navigate(navigationItem.route) {
                    popUpTo(bottomNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    // Avoid multiple copies of the same destination when
                    // reselecting the same item
                    launchSingleTop = true
                    // Restore state when reselecting a previously selected item
                    restoreState = true
                }
            },
            backdrop = tabsBackdrop,
            tabsCount = BottomNavItem().bottomNavigationItems().size,
            isBlurEnabled = true,
            accentColor = MaterialTheme.colorScheme.primary,
            surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
            onBubbleProgress = { bubbleProgress = it }
        ) {
            BottomNavItem().bottomNavigationItems().forEachIndexed { index, navigationItem ->
                val label = stringResource(id = navigationItem.labelRes)
                val dist = abs(bubbleProgress - index.toFloat()).coerceIn(0f, 1f)
                val tabAccent = lerp(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    dist
                )
                FloatingBottomBarItem(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        bottomNavSelectedItem = index
                        bottomNavController.navigate(navigationItem.route) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = navigationItem.icon),
                        contentDescription = label,
                        tint = tabAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = label,
                        color = tabAccent,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showGnssFilterDialog) {
        ShowSatsFilterDialog(
            onDismissRequest = { showGnssFilterDialog = false },
            onSave = { showGnssFilterDialog = false }
        )
    }

    if (showGnssSortDialog) {
        GnssSortByDialog(
            onDismissRequest = { showGnssSortDialog = false },
            onSave = { showGnssFilterDialog = false }
        )
    }

    // The funnel action only exists on the Wi-Fi tab, so the collected state is always
    // available while the sheet can be open.
    if (showWifiFilterSheet && wifiListViewModel != null && wifiListUiState != null) {
        WifiFilterSheet(
            filter = wifiListUiState.filter,
            visibleApCount = wifiListUiState.visibleApCount,
            totalApCount = wifiListUiState.apCount,
            onQueryChanged = wifiListViewModel::setFilterQuery,
            onBandToggled = wifiListViewModel::toggleFilterBand,
            onClear = wifiListViewModel::clearFilter,
            onManageExclusionList = {
                showWifiFilterSheet = false
                mainNavController.navigate(NavOption.SsidExclusionList.name)
            },
            onDismiss = { showWifiFilterSheet = false },
        )
    }
}

/**
 * Walks the context wrapper chain to find the hosting [ComponentActivity]. Needed to scope
 * view models to the activity from inside the composition.
 */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

/**
 * Upper bound applied to the system fontScale when rendering bottom-nav labels. Keeps the labels
 * legible without letting them grow large enough to wrap onto a second line.
 */
private const val LABEL_MAX_FONT_SCALE = 1.15f

/**
 * System fontScale at and above which the bottom-nav labels are hidden entirely (icons only). This
 * is intentionally past Android's "Largest" preset so only extreme accessibility settings hit the
 * icons-only state.
 */
private const val LABEL_HIDE_FONT_SCALE_THRESHOLD = 1.5f

@Composable
fun BottomNavigationBar(
    navController: NavController,
    onBottomNavigationItemSelected: (Int) -> Unit,
    bottomNavSelectedItem: Int
) {
    val currentDensity = LocalDensity.current
    val systemFontScale = currentDensity.fontScale
    val cappedFontScale = systemFontScale.coerceAtMost(LABEL_MAX_FONT_SCALE)
    val showLabels = systemFontScale < LABEL_HIDE_FONT_SCALE_THRESHOLD
    val cappedDensity = remember(currentDensity, cappedFontScale) {
        Density(currentDensity.density, cappedFontScale)
    }

    NavigationBar {
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, navigationItem ->
            val label = stringResource(id = navigationItem.labelRes)
            NavigationBarItem(
                selected = index == bottomNavSelectedItem,
                label = if (showLabels) {
                    {
                        CompositionLocalProvider(LocalDensity provides cappedDensity) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else null,
                icon = {
                    Icon(
                        painter = painterResource(id = navigationItem.icon),
                        contentDescription = label
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = {
                    onBottomNavigationItemSelected(index)
                    navController.navigate(navigationItem.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

/**
 * Returns the title resource ID that corresponds to the current screen
 */
fun getAppBarTitle(currentScreen: MainScreens): Int {
    return when (currentScreen) {
        MainScreens.Dashboard -> R.string.nav_dashboard
        MainScreens.Cellular -> R.string.cellular_title
        MainScreens.Wifi -> R.string.wifi_title
        MainScreens.Bluetooth -> R.string.bluetooth_title
        MainScreens.Gnss -> R.string.gnss_title
    }
}

@Composable
fun getAppBarActions(
    currentScreen: MainScreens,
    currentGnssScreen: GnssScreen,
    navController: NavController,
    showGnssFilterDialog: (Boolean) -> Unit,
    showGnssSortDialog: (Boolean) -> Unit,
    wifiFilterActive: Boolean = false,
    showWifiFilterSheet: (Boolean) -> Unit = {}
): List<AppBarAction> {
    return when (currentScreen) {
        MainScreens.Dashboard -> listOf(
            AppBarAction(
                icon = R.drawable.ic_survey_monitor,
                description = R.string.survey_monitor,
                onClick = {
                    navController.navigate(NavDrawerOption.SurveyMonitor.name)
                }
            ),
            AppBarAction(
                icon = R.drawable.ic_ns_analytics,
                description = R.string.ns_analytics,
                onClick = {
                    navController.navigate(NavDrawerOption.NsAnalyticsConnection.name)
                }
            )
        )

        MainScreens.Cellular -> listOf(
            AppBarAction(
                icon = R.drawable.ic_survey_monitor,
                description = R.string.survey_monitor,
                onClick = {
                    navController.navigate(NavDrawerOption.SurveyMonitor.name)
                }
            ),
            AppBarAction(
                icon = android.R.drawable.ic_dialog_map,
                description = R.string.open_tower_map,
                onClick = {
                    navController.navigate(NavOption.TowerMap.name)
                }
            )
        )

        MainScreens.Wifi -> listOf(
            AppBarAction(
                icon = R.drawable.ic_spectrum_chart,
                description = R.string.open_wifi_spectrum,
                onClick = {
                    navController.navigate(NavOption.WifiSpectrum.name)
                }
            ),
            AppBarAction(
                icon = R.drawable.ic_filter,
                // Announce the active-filter state to screen readers; the badge dot alone
                // is purely visual.
                description = if (wifiFilterActive) {
                    R.string.wifi_filter_content_description_active
                } else {
                    R.string.wifi_filter_content_description
                },
                onClick = {
                    showWifiFilterSheet(true)
                },
                active = wifiFilterActive
            )
        )

        MainScreens.Gnss -> {
            return when (currentGnssScreen) {
                GnssScreen.GNSS_DETAILS -> listOf(
                    AppBarAction(
                        icon = R.drawable.ic_sort,
                        description = R.string.menu_option_sort_by,
                        onClick = { showGnssSortDialog(true) }
                    ),
                    AppBarAction(
                        icon = R.drawable.ic_filter,
                        description = R.string.menu_option_filter_content_description,
                        onClick = { showGnssFilterDialog(true) }
                    )
                )

                GnssScreen.GNSS_SKY_VIEW -> listOf(
                    AppBarAction(
                        icon = R.drawable.ic_filter,
                        description = R.string.menu_option_filter_content_description,
                        onClick = { showGnssFilterDialog(true) }
                    ))
            }
        }

        else -> emptyList()
    }
}

@Composable
fun ShowSatsFilterDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val gnssTypes = GnssType.entries.toTypedArray()
    val len = gnssTypes.size

    // Retrieve the current filter from SharedPreferences
    val filter = PreferenceUtils.gnssFilter(context, Application.getPrefs())

    val items = Array(len) { index ->
        LibUIUtils.getGnssDisplayName(context, gnssTypes[index])
    }
    val checks = BooleanArray(len) { index ->
        filter.contains(gnssTypes[index])
    }

    // Display the GnssFilterDialog with the prepared items and initial checks
    GnssFilterDialog(
        initialItems = items,
        initialChecks = checks,
        onDismissRequest = onDismissRequest,
        onSave = onSave
    )
}

sealed class MainScreens(val route: String) {
    data object Dashboard : MainScreens("dashboard_route")
    data object Cellular : MainScreens("cellular_route")
    data object Wifi : MainScreens("wifi_route")
    data object Bluetooth : MainScreens("bluetooth_route")
    data object Gnss : MainScreens("gnss_route")
}

enum class GnssScreen {
    GNSS_DETAILS,
    GNSS_SKY_VIEW
}

data class BottomNavItem(
    @StringRes val labelRes: Int = R.string.nav_dashboard,
    @DrawableRes val icon: Int = R.drawable.ic_dashboard,
    val route: String = ""
) {
    fun bottomNavigationItems(): List<BottomNavItem> {
        return listOf(
            BottomNavItem(
                labelRes = R.string.cellular_title,
                icon = R.drawable.ic_cellular,
                route = MainScreens.Cellular.route
            ),
            BottomNavItem(
                labelRes = R.string.wifi_title,
                icon = R.drawable.ic_wifi,
                route = MainScreens.Wifi.route
            ),
            BottomNavItem(
                labelRes = R.string.bluetooth_title,
                icon = R.drawable.ic_bluetooth,
                route = MainScreens.Bluetooth.route
            ),
            BottomNavItem(
                labelRes = R.string.gnss_title,
                icon = R.drawable.ic_gnss,
                route = MainScreens.Gnss.route
            ),
        )
    }
}

@Composable
fun CellularFragmentInCompose() {
    AndroidViewBinding(ContainerCellularFragmentBinding::inflate) {
        cellularFragmentContainerView.getFragment<MainCellularFragment>()
    }
}

@Composable
fun WifiFragmentInCompose() {
    AndroidViewBinding(ContainerWifiFragmentBinding::inflate) {
        wifiFragmentContainerView.getFragment<WifiNetworksFragment>()
    }
}

@Composable
fun BluetoothFragmentInCompose() {
    AndroidViewBinding(ContainerBluetoothFragmentBinding::inflate) {
        bluetoothFragmentContainerView.getFragment<BluetoothFragment>()
    }
}

@Composable
fun GnssFragmentInCompose(onGnssScreenChange: (GnssScreen) -> Unit) {
    var fragment: MainGnssFragment? = null

    val lifecycleOwner = LocalLifecycleOwner.current
    val tabChangeObserver = rememberUpdatedState(newValue = { position: Int ->
        val newScreen = if (position == 0) GnssScreen.GNSS_DETAILS else GnssScreen.GNSS_SKY_VIEW
        onGnssScreenChange(newScreen)
    })

    DisposableEffect(lifecycleOwner) {
        val observer = Observer<Int> { position ->
            tabChangeObserver.value(position)
        }
        fragment?.tabChangeLiveData?.observe(lifecycleOwner, observer)
        onDispose {
            fragment?.tabChangeLiveData?.removeObserver(observer)
        }
    }

    AndroidViewBinding(ContainerGnssFragmentBinding::inflate) {
        fragment = gnssFragmentContainerView.getFragment<MainGnssFragment>()
    }
}
