package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.SignalBarColors
import com.craxiom.networksurvey.ui.cellular.SignalBarMetric
import com.craxiom.networksurvey.ui.cellular.SignalBarPreferences
import java.util.Locale

@Composable
internal fun SignalBarColorSettings(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var metric by remember { mutableStateOf(SignalBarMetric.LTE_RSRP) }
    var drafts by remember(prefs) { mutableStateOf(SignalBarMetric.entries.associateWith {
        SignalBarPreferences.load(prefs, it).toDraft()
    }) }
    var expanded by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Int?>(null) }
    val draft = drafts.getValue(metric)
    val bounds = draft.bounds.mapNotNull(String::toIntOrNull)
    val valid = SignalBarPreferences.validBounds(bounds)
    val update: (ColorDraft) -> Unit = { drafts = drafts + (metric to it) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.signal_bar_colors)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { expanded = true }) { Text("${metric.title} · ${metric.unit} ▾") }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        SignalBarMetric.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                metric = option
                                expanded = false
                            })
                        }
                    }
                }
                for (index in draft.colors.indices) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        for (boundary in index..index + 1) {
                            if (boundary > index) Text("–")
                            OutlinedTextField(
                                value = draft.bounds[boundary],
                                onValueChange = { input ->
                                    if (input.length <= 4 && input.all { it == '-' || it.isDigit() }) {
                                        update(draft.copy(bounds = draft.bounds.toMutableList().apply { set(boundary, input) }))
                                    }
                                },
                                label = { Text(stringResource(if (boundary == index) R.string.signal_range_min else R.string.signal_range_max)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val description = stringResource(R.string.signal_range_color, index + 1)
                        Box(Modifier.size(42.dp, 28.dp).clip(MaterialTheme.shapes.small)
                            .background(Color(draft.colors[index])).semantics { contentDescription = description }
                            .clickable(role = Role.Button) { picker = index })
                    }
                }
                if (!valid) Text(stringResource(R.string.signal_range_invalid), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { update(SignalBarPreferences.defaults(metric).toDraft()) }) {
                        Text(stringResource(R.string.signal_color_reset))
                    }
                    TextButton(onClick = {
                        drafts = SignalBarMetric.entries.associateWith { SignalBarPreferences.defaults(it).toDraft() }
                    }) { Text(stringResource(R.string.signal_color_reset_all)) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = drafts.values.all { SignalBarPreferences.validBounds(it.bounds.mapNotNull(String::toIntOrNull)) },
                onClick = {
                    SignalBarPreferences.save(prefs, drafts.mapValues { (_, value) ->
                        SignalBarColors(value.bounds.map(String::toInt), value.colors)
                    })
                    onClose()
                },
            ) { Text(stringResource(R.string.signal_color_save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
    picker?.let { index ->
        SignalSwatchPicker(draft.colors[index], onClose = { picker = null }, onColor = { color ->
            update(draft.copy(colors = draft.colors.toMutableList().apply { set(index, color) }))
            picker = null
        })
    }
}

private data class ColorDraft(val bounds: List<String>, val colors: List<Int>)
private fun SignalBarColors.toDraft() = ColorDraft(bounds.map(Int::toString), colors)

@Composable
private fun SignalSwatchPicker(color: Int, onClose: () -> Unit, onColor: (Int) -> Unit) {
    var hex by remember(color) { mutableStateOf(String.format(Locale.ROOT, "%06X", color and 0xFFFFFF)) }
    val selected = hex.toIntOrNull(16)?.takeIf { hex.length == 6 }?.or(0xFF000000.toInt())
    val swatches = SignalBarPreferences.defaults(SignalBarMetric.LTE_RSRP).colors
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.signal_bar_pick_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    swatches.forEach { swatch ->
                        val rgb = String.format(Locale.ROOT, "%06X", swatch and 0xFFFFFF)
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(swatch))
                            .semantics { contentDescription = "#$rgb" }.clickable(role = Role.Button) { hex = rgb })
                    }
                }
                OutlinedTextField(hex, { hex = it.filter { character -> character.digitToIntOrNull(16) != null }.take(6) },
                    label = { Text("#RRGGBB") }, singleLine = true)
                Box(Modifier.fillMaxWidth().height(18.dp).clip(CircleShape)
                    .background(selected?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onColor) }, enabled = selected != null) {
                Text(stringResource(R.string.signal_color_save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
