package com.craxiom.networksurvey.ui.engparam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.db.EngineeringParamDbHelper
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.util.CsvParamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import java.io.InputStream

/**
 * 工参管理页：导入 CSV 工参、查看列表、清空。
 * 数据层来自恢复工程移植（EngineeringParamDbHelper + CsvParamParser，本地 SQLite）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EngineeringParamsScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val db = remember { EngineeringParamDbHelper.getInstance(context) }

    var params by remember { mutableStateOf(db.getAll()) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // 导入协程：OpenDocument 返回 uri 后触发
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingImportUri = uri
    }

    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        pendingImportUri = null
        importing = true
        try {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val r = CsvParamParser.parse(stream)
                    if (r.params.isNotEmpty()) db.insertAll(r.params)
                    r
                }
            }
            if (result != null) {
                val successMsg = context.getString(R.string.eng_param_import_success, result.params.size)
                val msg = if (result.skippedLines > 0) {
                    "$successMsg；" + context.getString(R.string.eng_param_import_skipped, result.skippedLines)
                } else {
                    successMsg
                }
                toastMsg = msg
            } else {
                toastMsg = context.getString(R.string.eng_param_import_failed, "无法读取文件")
            }
        } catch (e: Exception) {
            toastMsg = context.getString(R.string.eng_param_import_failed, e.message ?: "解析错误")
        } finally {
            importing = false
            reloadTick++
        }
    }

    LaunchedEffect(reloadTick) {
        params = db.getAll()
    }

    // Toast 简单展示
    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            toastMsg = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.eng_param_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateUp) { Text("←") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                    enabled = !importing
                ) {
                    Text(if (importing) "导入中…" else stringResource(R.string.eng_param_import))
                }
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.eng_param_clear))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.eng_param_count, params.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            toastMsg?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            if (params.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.eng_param_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(params, key = { it.id }) { p ->
                        EngineeringParamRow(p)
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.eng_param_clear)) },
            text = { Text(stringResource(R.string.eng_param_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    db.clearAll()
                    showClearConfirm = false
                    reloadTick++
                    toastMsg = context.getString(R.string.eng_param_import_success, 0)
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EngineeringParamRow(p: EngineeringParam) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = p.cellName.ifBlank { p.siteName.ifBlank { "-" } },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = p.netType.ifBlank { p.band.ifBlank { "" } },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParamField(stringResource(R.string.eng_param_ci), p.ci.toString())
                ParamField(stringResource(R.string.eng_param_earfcn), p.earfcn.toString())
                ParamField(stringResource(R.string.eng_param_pci), p.pci.toString())
                if (p.lat != 0.0 || p.lon != 0.0) {
                    ParamField(
                        "位置",
                        "%.5f,%.5f".format(p.lat, p.lon)
                    )
                }
            }
        }
    }
}

@Composable
private fun ParamField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
