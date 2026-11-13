package com.craxiom.networksurvey.ui.main

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.ContainerGnssFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiFragmentBinding
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.util.LibUIUtils
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.wifi.components.WifiFilterSheet
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel
import com.craxiom.networksurvey.util.PreferenceUtils
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
internal fun WifiToolScreen(navController: NavHostController) {
    val activity = LocalContext.current.findComponentActivity()
    val wifiViewModel: WifiListViewModel? = activity?.let { viewModel(viewModelStoreOwner = it) }
    val state = wifiViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var showFilter by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            val filterActive = state?.filter?.isActive == true
            TitleBar(
                title = stringResource(R.string.tool_wifi_title),
                appBarActions = buildList {
                    add(
                        AppBarAction(
                            icon = R.drawable.ic_spectrum_chart,
                            description = R.string.open_wifi_spectrum,
                            onClick = {
                                navController.navigate(NavOption.WifiSpectrum.name) { launchSingleTop = true }
                            },
                        )
                    )
                    if (state != null) {
                        add(
                            AppBarAction(
                                icon = R.drawable.ic_filter,
                                description = if (filterActive) R.string.wifi_filter_content_description_active
                                else R.string.wifi_filter_content_description,
                                active = filterActive,
                                onClick = { showFilter = true },
                            )
                        )
                    }
                },
                onBackClick = { navController.navigateUp() },
            )
        },
        popupHost = {},
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidViewBinding(ContainerWifiFragmentBinding::inflate, Modifier.fillMaxSize())
        }
    }

    if (showFilter && wifiViewModel != null && state != null) {
        WifiFilterSheet(
            filter = state.filter,
            visibleApCount = state.visibleApCount,
            totalApCount = state.apCount,
            onQueryChanged = wifiViewModel::setFilterQuery,
            onBandToggled = wifiViewModel::toggleFilterBand,
            onClear = wifiViewModel::clearFilter,
            onManageExclusionList = {
                showFilter = false
                navController.navigate(NavOption.SsidExclusionList.name)
            },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
internal fun GnssToolScreen(onBack: () -> Unit) {
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TitleBar(
                title = stringResource(R.string.tool_gnss_title),
                appBarActions = listOf(
                    AppBarAction(
                        icon = R.drawable.ic_sort,
                        description = R.string.menu_option_sort_by,
                        onClick = { showSort = true },
                    ),
                    AppBarAction(
                        icon = R.drawable.ic_filter,
                        description = R.string.menu_option_filter_content_description,
                        onClick = { showFilter = true },
                    ),
                ),
                onBackClick = onBack,
            )
        },
        popupHost = {},
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidViewBinding(ContainerGnssFragmentBinding::inflate, Modifier.fillMaxSize())
        }
    }

    if (showFilter) {
        val context = LocalContext.current
        val types = GnssType.entries
        val filter = PreferenceUtils.gnssFilter(context, Application.getPrefs())
        GnssFilterDialog(
            initialItems = types.map { LibUIUtils.getGnssDisplayName(context, it) }.toTypedArray(),
            initialChecks = BooleanArray(types.size) { filter.contains(types[it]) },
            onDismissRequest = { showFilter = false },
            onSave = { showFilter = false },
        )
    }

    if (showSort) {
        GnssSortByDialog(
            onDismissRequest = { showSort = false },
            onSave = { showSort = false },
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
