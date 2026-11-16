package com.craxiom.networksurvey.ui.cellular

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.model.CellularProtocol

internal enum class SignalBarMetric(val title: String, val unit: String, val bounds: List<Int>) {
    NR_RSRP("NR SS-RSRP", "dBm", listOf(-156, -115, -105, -95, -85, -75, -31)),
    NR_RSRQ("NR SS-RSRQ", "dB", listOf(-43, -20, -16, -13, -10, -7, 20)),
    NR_SINR("NR SS-SINR", "dB", listOf(-23, -5, 0, 5, 10, 20, 40)),
    LTE_RSRP("LTE RSRP", "dBm", listOf(-140, -110, -100, -90, -80, -70, -40)),
    LTE_RSRQ("LTE RSRQ", "dB", listOf(-20, -17, -15, -12, -10, -7, -3)),
    LTE_SINR("LTE SINR", "dB", listOf(-23, -5, 0, 5, 10, 20, 40));

    companion object {
        fun forProtocol(protocol: CellularProtocol) = when (protocol) {
            CellularProtocol.NR -> listOf(NR_RSRP, NR_RSRQ, NR_SINR)
            CellularProtocol.LTE -> listOf(LTE_RSRP, LTE_RSRQ, LTE_SINR)
            else -> emptyList()
        }
    }
}

internal data class SignalBarColors(val bounds: List<Int>, val colors: List<Int>) {
    fun fraction(value: Double) = ((value - bounds.first()) / (bounds.last() - bounds.first())).toFloat().coerceIn(0.04f, 1f)
    fun color(value: Double): Color = Color(colors[bounds.drop(1).indexOfFirst { value < it }.takeIf { it >= 0 } ?: colors.lastIndex])
}

internal object SignalBarPreferences {
    private const val PREFIX = "cellular_signal_colors_"
    private val colors = listOf(0xFFF44732, 0xFFF5784A, 0xFFAA8500, 0xFF008FD1, 0xFF269B53, 0xFF006B39).map(Long::toInt)

    fun defaults(metric: SignalBarMetric) = SignalBarColors(metric.bounds, colors)

    fun load(preferences: SharedPreferences, metric: SignalBarMetric): SignalBarColors {
        val stored = preferences.getString(PREFIX + metric.name, null)?.split('|') ?: return defaults(metric)
        if (stored.size != 2) return defaults(metric)
        val bounds = stored[0].split(',').mapNotNull(String::toIntOrNull)
        val palette = stored[1].split(',').mapNotNull(String::toIntOrNull)
        return if (validBounds(bounds) && palette.size == 6) SignalBarColors(bounds, palette) else defaults(metric)
    }

    fun validBounds(bounds: List<Int>) = bounds.size == 7 &&
        bounds.all { it in -200..100 } && bounds.zipWithNext().all { (lower, upper) -> lower < upper }

    fun save(preferences: SharedPreferences, values: Map<SignalBarMetric, SignalBarColors>) {
        val edit = preferences.edit()
        for ((metric, colors) in values) {
            edit.putString(PREFIX + metric.name, colors.bounds.joinToString(",") + "|" + colors.colors.joinToString(","))
        }
        edit.apply()
    }

    fun isColorKey(key: String?) = key == null || key.startsWith(PREFIX)
}

@Composable
internal fun rememberSignalBarColors(protocol: CellularProtocol): List<SignalBarColors> {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val metrics = remember(protocol) { SignalBarMetric.forProtocol(protocol) }
    if (metrics.isEmpty()) return emptyList()
    val colors = remember(preferences, metrics) { mutableStateOf(metrics.map { SignalBarPreferences.load(preferences, it) }) }
    DisposableEffect(preferences, metrics) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (SignalBarPreferences.isColorKey(key)) colors.value = metrics.map { SignalBarPreferences.load(prefs, it) }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return colors.value
}

@Composable
internal fun ColoredSignalBar(value: Number?, colors: SignalBarColors, modifier: Modifier = Modifier) {
    Box(modifier.height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val reading = value?.toDouble()?.takeIf { it.isFinite() }
        if (reading != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(colors.fraction(reading)).background(colors.color(reading), CircleShape))
        }
    }
}
