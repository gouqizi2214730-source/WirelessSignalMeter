package com.craxiom.networksurvey.ui.cellular

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.craxiom.networksurvey.fragments.model.CellularViewModel
import com.craxiom.networksurvey.ui.main.appbar.FragmentTopBarScroll
import com.craxiom.networksurvey.ui.main.appbar.fragmentTopBarScroll
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Java-friendly bridge for installing the Compose cellular details UI into the fragment's
 * [ComposeView]. Mirrors ui/bluetooth/BluetoothNetworksCompose so the Java fragment keeps owning
 * the Android plumbing (service binding, SIM paging, info dialogs) while the UI is fully Compose.
 */
object CellularDetailsCompose {

    @JvmStatic
    fun setContent(
        composeView: ComposeView,
        viewModel: CellularViewModel,
        chartViewModel: CellularChartViewModel,
        onCellularInfoClick: Runnable,
        onTechnologyInfoClick: Runnable,
        onNrInfoClick: Runnable,
        topBarScroll: FragmentTopBarScroll?,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NsTheme {
                CellularDetailsScreen(
                    viewModel = viewModel,
                    chartViewModel = chartViewModel,
                    onCellularInfoClick = { onCellularInfoClick.run() },
                    onTechnologyInfoClick = { onTechnologyInfoClick.run() },
                    onNrInfoClick = { onNrInfoClick.run() },
                    modifier = Modifier.fragmentTopBarScroll(topBarScroll),
                )
            }
        }
    }
}
