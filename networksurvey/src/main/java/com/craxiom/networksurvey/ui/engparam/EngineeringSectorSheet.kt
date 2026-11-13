package com.craxiom.networksurvey.ui.engparam

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.domain.CsvParsingEngine.Col
import com.craxiom.networksurvey.domain.ImportFieldSpec
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineeringSectorSheet(
    param: EngineeringParam,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    nearbyPosition: Pair<Int, Int>? = null,
    onPreviousSector: () -> Unit = {},
    onNextSector: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val scroll = rememberScrollState()
        LaunchedEffect(param.id) { scroll.scrollTo(0) }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("扇区详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (nearbyPosition != null) {
                val (position, total) = nearbyPosition
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onPreviousSector) { Text("上一个扇区") }
                    Text("$position / $total", style = MaterialTheme.typography.labelMedium)
                    FilledTonalButton(onClick = onNextSector) { Text("下一个扇区") }
                }
            }
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = DashboardCardDefaults.shape,
                color = DashboardCardDefaults.cardContainerColor(),
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    ImportFieldSpec.FIELDS.forEachIndexed { index, field ->
                        if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Text(param.fieldValue(field.col).ifBlank { "—" }, Modifier.weight(1.85f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除工参", color = colors.error) }
                }
                FilledTonalButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

private fun EngineeringParam.fieldValue(col: Int): String = when (col) {
    Col.NETTYPE -> netType.orEmpty()
    Col.SITE -> siteName.orEmpty()
    Col.CELL -> cellName.orEmpty()
    Col.GNB_ID -> gnbId.toString()
    Col.SECTOR_ID -> sectorId.toString()
    Col.PCI -> pci.toString()
    Col.EARFCN -> earfcn.toString()
    Col.LON -> String.format(Locale.ROOT, "%.6f", lon)
    Col.LAT -> String.format(Locale.ROOT, "%.6f", lat)
    Col.AZIMUTH -> "${azimuth}°"
    Col.TILT -> "${tilt}°"
    else -> ""
}
