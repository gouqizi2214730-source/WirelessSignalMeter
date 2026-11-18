package com.craxiom.networksurvey.ui.dashboard

import com.craxiom.networksurvey.util.LocationStatusHelper.LocationState

/**
 * UI state for the battery management card.
 */
data class BatteryUiState(
    val visible: Boolean = false,
    val isPaused: Boolean = false,
    val batteryLevel: Int = -1,
    val batteryThreshold: Int = 0,
)

/**
 * UI state for the location status display.
 */
data class LocationUiState(
    val state: LocationState = LocationState.SEARCHING,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val hasLocation: Boolean = false,
)

/**
 * UI state for the logging controls card.
 */
data class LoggingUiState(
    val cellularEnabled: Boolean = false,
    val phoneStateEnabled: Boolean = false,
    val phoneStateAutoStarted: Boolean = false,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val gnssEnabled: Boolean = false,
    val cdrEnabled: Boolean = false,
)

/**
 * UI state for the Mission ID card.
 *
 * @param visible True once a real survey has started during this app session. Stays true after a
 *                survey stops so the user can still copy the most recent Mission ID.
 * @param active True while a relevant survey is currently running.
 * @param missionId The current or most recent Mission ID.
 */
data class MissionIdUiState(
    val visible: Boolean = false,
    val active: Boolean = false,
    val missionId: String = "",
)

/**
 * One-shot events emitted by the DashboardViewModel.
 */
sealed class DashboardEvent {
    data class ShowToast(val message: String) : DashboardEvent()
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    ) : DashboardEvent()

    data object ShowBatteryOptimizationDialog : DashboardEvent()
    data object ShowBluetoothPermissionRationale : DashboardEvent()
    data class ShowCdrPermissionRationale(val isRequired: Boolean) : DashboardEvent()
}