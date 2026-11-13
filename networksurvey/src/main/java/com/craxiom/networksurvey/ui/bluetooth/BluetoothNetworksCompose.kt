package com.craxiom.networksurvey.ui.bluetooth

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.networksurvey.fragments.model.BluetoothViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Java-friendly bridge for installing the Compose Bluetooth list UI into the fragment's
 * [ComposeView]. Mirrors ui/wifi/WifiNetworksCompose so the Java fragment keeps owning the
 * Android plumbing (service binding, permissions, sort dialog) while the UI is Compose.
 */
object BluetoothNetworksCompose {

    @JvmStatic
    fun setContent(
        composeView: ComposeView,
        viewModel: BluetoothViewModel,
        onDeviceSelected: BluetoothDeviceSelected,
        onTogglePause: Runnable,
        onSortClick: Runnable,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NsTheme {
                BluetoothListScreen(
                    viewModel = remember { viewModel },
                    onDeviceSelected = { record -> onDeviceSelected.invoke(record) },
                    onTogglePause = { onTogglePause.run() },
                    onSortClick = { onSortClick.run() },
                )
            }
        }
    }

    /** Functional interface compatible with Java method references (e.g. `this::showSortByDialog`). */
    fun interface BluetoothDeviceSelected {
        fun invoke(record: BluetoothRecord)
    }
}
