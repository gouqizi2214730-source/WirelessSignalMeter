package com.craxiom.networksurvey.ui.activesurvey.model

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.model.SurveyTypes
import com.baidu.mapapi.model.LatLng

/**
 * Represents the current status of a survey type
 */
data class SurveyStatus(
    val type: SurveyTypes,
    val isActive: Boolean,
    val recordCount: Long = 0,
    val errorMessage: String? = null,
    val protocols: Set<String> = emptySet(),
    val fileInfo: FileLoggingInfo? = null
)

/**
 * Information about file logging status
 */
data class FileLoggingInfo(
    val csvEnabled: Boolean,
    val csvFileSize: Long = 0,
    val csvRecordCount: Long = 0,
    val geoPackageEnabled: Boolean,
    val geoPackageFileSize: Long = 0,
    val geoPackageRecordCount: Long = 0,
    val activeProtocols: Set<String> = emptySet()
)

/**
 * Represents a GPS track for survey visualization
 */
data class SurveyTrack(
    val points: List<LatLng>,
    val timestamps: List<Long>,
    val sessionId: String,
    val color: Color = Color.Blue
)

/**
 * Overall survey monitoring state
 */
data class ActiveSurveyState(
    val fileLoggingStatus: SurveyStatus? = null,
    val currentTrack: SurveyTrack? = null,
    val isAnyActive: Boolean = false,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val totalRecordCount: Int = 0,
    val isFileLoggingActive: Boolean = false,
    val missionId: String = ""
) {
    /**
     * Get list of active survey types for display
     */
    val activeSurveyTypes: List<String>
        get() = buildList {
            if (isFileLoggingActive) add("File")
        }

    /**
     * Check if any file logging survey is active
     */
    val hasNonUploadSurvey: Boolean
        get() = isFileLoggingActive
}

/**
 * Supported wireless protocols
 */
enum class WirelessProtocol(val displayName: String) {
    GSM("GSM"),
    CDMA("CDMA"),
    UMTS("UMTS"),
    LTE("LTE"),
    NR("5G NR"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    GNSS("GNSS")
}