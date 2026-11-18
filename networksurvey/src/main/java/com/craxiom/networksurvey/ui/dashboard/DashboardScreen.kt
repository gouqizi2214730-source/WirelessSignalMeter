package com.craxiom.networksurvey.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.main.CdrHelpDialog
import com.craxiom.networksurvey.ui.main.FileLoggingHelpDialog
import com.craxiom.networksurvey.ui.main.MissionIdHelpDialog
import com.craxiom.networksurvey.ui.main.PhoneStateHelpDialog
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import timber.log.Timber

/**
 * The main Dashboard screen composable. A fully Compose-native implementation that
 * manages service binding, composes all cards in a scrollable column, and handles
 * dialog state.
 */
@Composable
fun DashboardScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel(),
    onCellularClick: () -> Unit = {},
    onWifiClick: () -> Unit = {},
    onBluetoothClick: () -> Unit = {},
    onGnssClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect state from separate flows
    val batteryState by dashboardViewModel.batteryState.collectAsStateWithLifecycle()
    val locationState by dashboardViewModel.locationState.collectAsStateWithLifecycle()
    val loggingState by dashboardViewModel.loggingState.collectAsStateWithLifecycle()
    val missionIdState by dashboardViewModel.missionIdState.collectAsStateWithLifecycle()

    // Dialog visibility states
    var showPhoneStateHelpDialog by remember { mutableStateOf(false) }
    var showCdrHelpDialog by remember { mutableStateOf(false) }
    var showFileLoggingHelpDialog by remember { mutableStateOf(false) }
    var showMissionIdHelpDialog by remember { mutableStateOf(false) }
    var showBluetoothPermissionDialog by remember { mutableStateOf(false) }
    var showCdrRequiredPermissionDialog by remember { mutableStateOf(false) }
    var showCdrOptionalPermissionDialog by remember { mutableStateOf(false) }

    // Service binding via lifecycle
    ServiceBindingEffect(
        dashboardViewModel = dashboardViewModel,
    )

    // Initialize location on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.initializeLocationState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle one-shot events
    LaunchedEffect(Unit) {
        dashboardViewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is DashboardEvent.ShowSnackbar -> {
                    // No-op (previously navigated to MQTT settings)
                }

                is DashboardEvent.ShowBatteryOptimizationDialog -> {
                    sharedViewModel.triggerBatteryOptimizationDialog()
                }

                is DashboardEvent.ShowBluetoothPermissionRationale -> {
                    showBluetoothPermissionDialog = true
                }

                is DashboardEvent.ShowCdrPermissionRationale -> {
                    if (event.isRequired) {
                        showCdrRequiredPermissionDialog = true
                    } else {
                        showCdrOptionalPermissionDialog = true
                    }
                }
            }
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 120.dp), // 预留底部 KernelSU 液态玻璃悬浮底栏间距，防止内容被遮挡
    ) {
        // KernelSU 风格标志性主状态与统计卡片
        MainHeroStatusCard(
            loggingState = loggingState,
            locationState = locationState,
            onCellularClick = onCellularClick,
            onWifiClick = onWifiClick,
            onBluetoothClick = onBluetoothClick,
            onGnssClick = onGnssClick,
        )

        // Battery card (animated visibility handled internally)
        BatteryCard(state = batteryState)

        // Location card
        LocationCard(
            state = locationState,
            onNavigateToSettings = { sharedViewModel.triggerNavigationToSettings() },
        )

        // Mission ID card (animated visibility handled internally)
        MissionIdCard(
            state = missionIdState,
            onHelpClick = { showMissionIdHelpDialog = true },
        )

        // Logging controls card
        LoggingControlsCard(
            state = loggingState,
            onCellularToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleCellularLogging(enable)
            },
            onPhoneStateToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.togglePhoneStateLogging(enable)
            },
            onWifiToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleWifiLogging(enable)
            },
            onBluetoothToggle = { enable ->
                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val activity = context as? NetworkSurveyActivity
                    if (activity != null) {
                        val missing = NetworkSurveyActivity.BLUETOOTH_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (missing) {
                            showBluetoothPermissionDialog = true
                            return@LoggingControlsCard
                        }
                    }
                }
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleBluetoothLogging(enable)
            },
            onGnssToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleGnssLogging(enable)
            },
            onCdrToggle = { enable ->
                if (enable) {
                    val cdrRequiredMissing =
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    if (cdrRequiredMissing) {
                        showCdrRequiredPermissionDialog = true
                        return@LoggingControlsCard
                    }
                    val cdrOptionalMissing =
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    if (cdrOptionalMissing) {
                        showCdrOptionalPermissionDialog = true
                        return@LoggingControlsCard
                    }
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleCdrLogging(enable)
            },
            onPhoneStateHelpClick = { showPhoneStateHelpDialog = true },
            onCdrHelpClick = { showCdrHelpDialog = true },
            onFileHelpClick = { showFileLoggingHelpDialog = true },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialogs
    if (showPhoneStateHelpDialog) {
        PhoneStateHelpDialog(onDismissRequest = { showPhoneStateHelpDialog = false })
    }
    if (showCdrHelpDialog) {
        CdrHelpDialog(onDismissRequest = { showCdrHelpDialog = false })
    }
    if (showFileLoggingHelpDialog) {
        FileLoggingHelpDialog(onDismissRequest = { showFileLoggingHelpDialog = false })
    }
    if (showMissionIdHelpDialog) {
        MissionIdHelpDialog(onDismissRequest = { showMissionIdHelpDialog = false })
    }
    if (showBluetoothPermissionDialog) {
        BluetoothPermissionRationaleDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        NetworkSurveyActivity.BLUETOOTH_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onDismiss = { showBluetoothPermissionDialog = false },
        )
    }
    if (showCdrRequiredPermissionDialog) {
        CdrRequiredPermissionDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_REQUIRED_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onDismiss = { showCdrRequiredPermissionDialog = false },
        )
    }
    if (showCdrOptionalPermissionDialog) {
        CdrOptionalPermissionDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_OPTIONAL_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onIgnore = {
                dashboardViewModel.toggleCdrLogging(true)
            },
            onDismiss = { showCdrOptionalPermissionDialog = false },
        )
    }
}

/**
 * Manages service binding/unbinding tied to the lifecycle.
 * Binds in ON_RESUME, unbinds in ON_PAUSE, matching the original Fragment behavior.
 */
@Composable
private fun ServiceBindingEffect(
    dashboardViewModel: DashboardViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as NetworkSurveyService.SurveyServiceBinder
                val service = serviceBinder.service as NetworkSurveyService
                dashboardViewModel.onServiceConnected(service)
                Timber.d("Dashboard connected to NetworkSurveyService")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.d("Dashboard disconnected from NetworkSurveyService")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val appContext = context.applicationContext
                    val startIntent =
                        Intent(appContext, NetworkSurveyService::class.java)
                    appContext.startService(startIntent)

                    val bindIntent =
                        Intent(appContext, NetworkSurveyService::class.java)
                    appContext.bindService(
                        bindIntent,
                        serviceConnection,
                        Context.BIND_ABOVE_CLIENT,
                    )
                    Timber.i("NetworkSurveyService bound in DashboardScreen")
                }

                Lifecycle.Event.ON_PAUSE -> {
                    dashboardViewModel.service?.let {
                        dashboardViewModel.onServiceDisconnecting(it)
                    }
                    try {
                        context.applicationContext.unbindService(serviceConnection)
                        Timber.i("NetworkSurveyService unbound in DashboardScreen")
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Could not unbind service")
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dashboardViewModel.service?.let {
                dashboardViewModel.onServiceDisconnecting(it)
            }
            try {
                context.applicationContext.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Already unbound
            }
        }
    }
}