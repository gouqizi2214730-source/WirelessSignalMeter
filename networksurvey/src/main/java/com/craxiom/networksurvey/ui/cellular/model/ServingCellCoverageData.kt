package com.craxiom.networksurvey.ui.cellular.model

import com.baidu.mapapi.model.LatLng

/**
 * Data class representing coverage circle for a serving cell tower.
 */
data class ServingCellCoverageData(
    val subscriptionId: Int,
    val center: LatLng,
    val radiusMeters: Int
)