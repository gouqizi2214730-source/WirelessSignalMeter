package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * One plottable point on a Watchlist map: a sighting (colored by signal) or a watched network
 * (colored by its legend color).
 *
 * @property id stable identifier echoed back by [WatchlistMap]'s click callback
 * @property colorHex the fill color as a "#RRGGBB" string
 */
data class WatchlistMapPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val colorHex: String,
)

/** A network row on the history map sheet: the watched entry's id, name, located-sighting count, and color. */
data class WatchlistMapLegendEntry(
    val entryId: Long,
    val name: String,
    val count: Int,
    val colorHex: String,
)

/**
 * The "#RRGGBB" color for an RSSI, derived from the shared signal ramp so map pins match the meters
 * and dBm text elsewhere. Usable off the main thread (no Compose state read).
 */
internal fun signalColorHex(rssi: Int): String =
    "#%06X".format(0xFFFFFF and rssi.toWifiSignalCategory().color.toArgb())
