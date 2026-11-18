package com.craxiom.networksurvey.ui.activesurvey

import android.app.Application
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.listeners.IMissionIdListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.NsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.baidu.mapapi.model.LatLng
import timber.log.Timber

/**
 * ViewModel for the Active Survey screen that manages survey status monitoring
 */
class SurveyMonitorViewModel(
    private val application: Application
) : AndroidViewModel(application),
    ILoggingChangeListener, LocationListener, ICellularSurveyRecordListener, IMissionIdListener {

    private val _surveyState = MutableStateFlow(ActiveSurveyState())
    val surveyState: StateFlow<ActiveSurveyState> = _surveyState.asStateFlow()

    private val _servingCellInfo = MutableStateFlow<ServingCellInfo?>(null)
    val servingCellInfo: StateFlow<ServingCellInfo?> = _servingCellInfo.asStateFlow()

    private var networkSurveyService: NetworkSurveyService? = null
    private val database = SurveyDatabase.getInstance(application)

    // Track points for the current session
    private val currentTrackPoints = mutableListOf<LatLng>()
    private val currentTrackTimestamps = mutableListOf<Long>()
    private var currentSessionId: String = ""

    // Refresh interval for statistics - increased to reduce main thread load
    private val STATS_REFRESH_INTERVAL_MS = 10000L  // 10 seconds

    init {
        // Start periodic updates
        startPeriodicUpdates()
    }

    /**
     * Sets the NetworkSurveyService reference
     */
    fun setNetworkSurveyService(service: NetworkSurveyService?) {
        // Unregister from old service
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.unregisterCellularSurveyRecordListener(this)
        networkSurveyService?.unregisterMissionIdListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)

        networkSurveyService = service

        // Register with new service
        service?.let {
            it.registerLoggingChangeListener(this)
            it.registerCellularSurveyRecordListener(this)
            it.registerMissionIdListener(this)
            it.primaryLocationListener?.registerListener(this)

            // Get initial states
            updateSurveyStates()
        }
    }


    /**
     * LocationListener implementation - Called when location is updated
     */
    override fun onLocationChanged(location: Location) {
        // Only track if any survey is active
        if (_surveyState.value.isAnyActive) {
            currentTrackPoints.add(LatLng(location.latitude, location.longitude))
            currentTrackTimestamps.add(System.currentTimeMillis())

            // Update the current track
            _surveyState.update { state ->
                state.copy(
                    currentTrack = SurveyTrack(
                        points = currentTrackPoints.toList(),
                        timestamps = currentTrackTimestamps.toList(),
                        sessionId = currentSessionId
                    )
                )
            }

            Timber.d("Added location to track. Total points: ${currentTrackPoints.size}")
        }
    }

    /**
     * LocationListener implementation - Called when provider status changes
     */
    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Not needed for our use case
    }

    /**
     * LocationListener implementation - Called when provider is enabled
     */
    override fun onProviderEnabled(provider: String) {
        Timber.d("Location provider enabled: $provider")
    }

    /**
     * LocationListener implementation - Called when provider is disabled
     */
    override fun onProviderDisabled(provider: String) {
        Timber.d("Location provider disabled: $provider")
    }

    /**
     * Starts a new tracking session
     */
    private fun startNewTrackingSession() {
        currentSessionId = System.currentTimeMillis().toString()
        currentTrackPoints.clear()
        currentTrackTimestamps.clear()
    }

    /**
     * ILoggingChangeListener implementation
     */
    override fun onLoggingChanged() {
        updateSurveyStates()
    }

    /**
     * IMissionIdListener implementation. Update immediately so the Mission ID row reflects a roll
     * the instant it happens, independent of the periodic refresh.
     */
    override fun onMissionIdChanged(missionId: String?, missionSessionActive: Boolean) {
        _surveyState.update { state ->
            state.copy(missionId = if (missionSessionActive) (missionId ?: "") else "")
        }
    }

    /**
     * Updates all survey states based on current service state
     */
    private fun updateSurveyStates() {
        val service = networkSurveyService ?: return

        viewModelScope.launch {
            // Check if any logging is enabled
            val fileLoggingActive = service.isCellularLoggingEnabled ||
                    service.isWifiLoggingEnabled ||
                    service.isBluetoothLoggingEnabled ||
                    service.isGnssLoggingEnabled

            val isAnyActive = fileLoggingActive

            // Start new tracking session if surveys just became active
            if (isAnyActive && !_surveyState.value.isAnyActive) {
                startNewTrackingSession()
            }

            _surveyState.update { state ->
                state.copy(
                    isAnyActive = isAnyActive,
                    lastUpdateTime = System.currentTimeMillis(),
                    totalRecordCount = getSurveySessionRecordCount(),
                    isFileLoggingActive = fileLoggingActive,
                    missionId = if (service.isMissionSessionActive) (service.rolledMissionId
                        ?: "") else ""
                )
            }
        }
    }


    /**
     * Starts periodic updates for statistics
     */
    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (true) {
                if (_surveyState.value.isAnyActive) {
                    updateSurveyStates()
                }
                delay(STATS_REFRESH_INTERVAL_MS)
            }
        }
    }


    /**
     * Get the survey session start time from the service
     * @return Start time in milliseconds since epoch, or null if no session
     */
    fun getSurveySessionStartTime(): Long? {
        return networkSurveyService?.surveySessionStartTime
    }

    /**
     * Get the survey session record count from the service
     * @return Number of records processed in the current session
     */
    fun getSurveySessionRecordCount(): Int {
        return networkSurveyService?.surveySessionRecordCount ?: 0
    }

    /**
     * ICellularSurveyRecordListener implementation - Called when new cellular records are received
     */
    override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
        Timber.d("onCellularBatch called with ${cellularGroup.size} records")

        // Find the serving cell in the batch
        cellularGroup.forEach { cellularRecord ->
            if (CellularUtils.isServingCell(cellularRecord.cellularRecord)) {
                // Extract signal value for logging
                val signalValue = when (cellularRecord.cellularProtocol) {
                    CellularProtocol.LTE -> {
                        val lte = cellularRecord.cellularRecord as LteRecord
                        lte.data?.rsrp?.value
                    }

                    CellularProtocol.NR -> {
                        val nr = cellularRecord.cellularRecord as NrRecord
                        nr.data?.ssRsrp?.value
                    }

                    CellularProtocol.GSM -> {
                        val gsm = cellularRecord.cellularRecord as GsmRecord
                        gsm.data?.signalStrength?.value
                    }

                    CellularProtocol.UMTS -> {
                        val umts = cellularRecord.cellularRecord as UmtsRecord
                        umts.data?.rscp?.value
                    }

                    else -> null
                }

                _servingCellInfo.value =
                    ServingCellInfo(cellularRecord, subscriptionId, System.currentTimeMillis())
                Timber.d("Updated serving cell: ${cellularRecord.cellularProtocol}, Signal: $signalValue")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.unregisterCellularSurveyRecordListener(this)
        networkSurveyService?.unregisterMissionIdListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }

}