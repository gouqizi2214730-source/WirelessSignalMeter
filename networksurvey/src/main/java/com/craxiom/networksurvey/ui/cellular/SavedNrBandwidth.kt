package com.craxiom.networksurvey.ui.cellular

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.craxiom.messaging.NrRecordData
import com.craxiom.networksurvey.R

internal data class NrBandwidthCell(val plmn: String, val nci: Long, val pci: Int, val channel: Int) {
    val preferenceKey: String get() = "$plmn:$nci:$pci:$channel"

    companion object {
        fun from(data: NrRecordData?): NrBandwidthCell? {
            if (data == null || !data.hasPlmn() || !data.hasNci() || !data.hasPci() || !data.hasNarfcn()) return null
            val plmn = data.plmn.value.replace("-", "")
            val nci = data.nci.value
            val pci = data.pci.value
            val channel = data.narfcn.value
            if (plmn.length !in 5..6 || plmn.any { it !in '0'..'9' } ||
                nci !in 1L..0xF_FFFF_FFFFL || pci !in 0..1007 || channel !in 1..3_279_165
            ) return null
            return NrBandwidthCell(plmn, nci, pci, channel)
        }
    }
}

private const val MAX_HISTORY_AGE_MS = 7 * 24 * 60 * 60 * 1_000L
private const val HISTORY_REFRESH_MS = 60 * 60 * 1_000L
private const val MAX_HISTORY_ENTRIES = 128

private fun Context.nrBandwidthPreferences() = getSharedPreferences("nr_cell_bandwidth", Context.MODE_PRIVATE)
private fun NrBandwidthCell.measuredKey() = "measured:$preferenceKey"
private fun NrBandwidthCell.manualKey() = "manual:$preferenceKey"

private fun Int.validBandwidth() = takeIf { it in 5..2000 }

private data class MeasuredBandwidth(val mhz: Int, val recordedAt: Long) {
    fun isRecent(now: Long) = now - recordedAt in 0..MAX_HISTORY_AGE_MS
}

private fun String.toMeasurement(): MeasuredBandwidth? {
    val mhz = substringBefore(':').toIntOrNull()?.validBandwidth() ?: return null
    val time = substringAfter(':').toLongOrNull() ?: return null
    return MeasuredBandwidth(mhz, time)
}

private fun SharedPreferences.measuredBandwidth(cell: NrBandwidthCell) =
    getString(cell.measuredKey(), null)?.toMeasurement()

internal fun saveMeasuredNrBandwidth(context: Context, cell: NrBandwidthCell, mhz: Double) {
    val value = mhz.toInt().validBandwidth()?.takeIf { it.toDouble() == mhz } ?: return
    val prefs = context.nrBandwidthPreferences()
    val previous = prefs.measuredBandwidth(cell)
    val now = System.currentTimeMillis()
    if (previous?.mhz == value && now - previous.recordedAt in 0..HISTORY_REFRESH_MS) return
    val entryKey = cell.measuredKey()
    val editor = prefs.edit()
    prefs.all.mapNotNull { (key, raw) ->
        if (!key.startsWith("measured:") || key == entryKey) return@mapNotNull null
        val measurement = (raw as? String)?.toMeasurement()
        if (measurement == null || !measurement.isRecent(now)) {
            editor.remove(key)
            return@mapNotNull null
        }
        key to measurement.recordedAt
    }.sortedByDescending { it.second }.drop(MAX_HISTORY_ENTRIES - 1).forEach { editor.remove(it.first) }
    editor.putString(entryKey, "$value:$now").apply()
}

internal data class SavedNrBandwidth(val mhz: Int, val manual: Boolean)

@Composable
internal fun rememberSavedNrBandwidth(cell: NrBandwidthCell?): SavedNrBandwidth? {
    if (cell == null) return null
    val context = LocalContext.current
    val prefs = remember(context) { context.nrBandwidthPreferences() }
    var manual by remember(prefs, cell) { mutableStateOf(prefs.getInt(cell.manualKey(), 0).validBandwidth()) }
    var measured by remember(prefs, cell) { mutableStateOf(prefs.measuredBandwidth(cell)) }
    DisposableEffect(prefs, cell) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                cell.manualKey() -> manual = prefs.getInt(cell.manualKey(), 0).validBandwidth()
                cell.measuredKey() -> measured = prefs.measuredBandwidth(cell)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        manual = prefs.getInt(cell.manualKey(), 0).validBandwidth()
        measured = prefs.measuredBandwidth(cell)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return manual?.let { SavedNrBandwidth(it, manual = true) }
        ?: measured?.takeIf { it.isRecent(System.currentTimeMillis()) }?.let {
            SavedNrBandwidth(it.mhz, manual = false)
        }
}

@Composable
internal fun SavedNrBandwidthValue(cell: NrBandwidthCell, saved: SavedNrBandwidth) {
    val context = LocalContext.current
    var editing by remember(cell) { mutableStateOf(false) }
    val labelId = if (saved.manual) R.string.nr_bandwidth_manual_value else R.string.nr_bandwidth_history_value
    val label = stringResource(labelId, saved.mhz)
    TextButton(onClick = { editing = true }) { Text(label, style = MaterialTheme.typography.bodySmall) }

    if (editing) {
        var input by remember(cell) { mutableStateOf(saved.mhz.toString()) }
        val value = input.trim().toIntOrNull()?.validBandwidth()
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.nr_bandwidth_edit_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.nr_bandwidth_mhz)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.nr_bandwidth_edit_help)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value != null || (input.isBlank() && saved.manual),
                    onClick = {
                        val editor = context.nrBandwidthPreferences().edit()
                        if (value == null) editor.remove(cell.manualKey()) else editor.putInt(cell.manualKey(), value)
                        editor.apply()
                        editing = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
