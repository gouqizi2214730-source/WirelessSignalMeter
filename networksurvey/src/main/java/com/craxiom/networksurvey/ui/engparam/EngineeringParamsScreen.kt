package com.craxiom.networksurvey.ui.engparam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.domain.CsvParsingEngine
import com.craxiom.networksurvey.domain.ImportFieldSpec
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.ui.cellular.baidumap.BaiduMapArea
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EngineeringParamsScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val vm: EngineeringParamViewModel = viewModel(factory = EngineeringParamViewModelFactory(context))
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.load() }

    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<EngineeringParam?>(null) }
    val search = query.trim()
    val params = state.params
    val filtered by produceState(emptyList<EngineeringParam>(), params, search) {
        value = if (search.isEmpty()) params else withContext(Dispatchers.Default) {
            params.filter {
                it.cellName.orEmpty().contains(search, ignoreCase = true) ||
                    it.siteName.orEmpty().contains(search, ignoreCase = true) ||
                    search == it.ci.toString() || search == it.gnbId.toString() ||
                    search == it.pci.toString() || search == it.earfcn.toString()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) vm.onFileSelected(it)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val templateExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("无法打开保存位置")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(ImportFieldSpec.csvTemplate()) }
                }.isSuccess
            }
            snackbar.showSnackbar(if (saved) "已导出工参模板" else "模板导出失败")
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            vm.clearMessage()
        }
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = { TitleBar(stringResource(R.string.eng_param_title), onBackClick = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("summary") {
                Surface(
                    shape = DashboardCardDefaults.shape,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("本地工参", style = MaterialTheme.typography.titleMedium)
                        Text("${params.size} 个小区", style = MaterialTheme.typography.headlineMedium)
                        Text("导入含 11 个必填字段的 4G / 5G CSV", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { importer.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                                enabled = !state.importing,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.importing) "正在处理" else "导入 CSV")
                            }
                            FilledTonalButton(
                                onClick = { templateExporter.launch("基站工参模板.csv") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("导出模板")
                            }
                        }
                        if (params.isNotEmpty()) {
                            TextButton(
                                onClick = vm::onRequestClear,
                                enabled = params.isNotEmpty() && !state.importing,
                            ) { Text("清空工参", color = if (params.isNotEmpty() && !state.importing) colors.error else Color.Unspecified) }
                        }
                        if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (params.isNotEmpty() || query.isNotEmpty()) {
                item("search") {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(),
                        label = { Text("搜索小区、站名、ID、PCI 或频点") },
                        singleLine = true, shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (query.isEmpty()) null else {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "清空搜索") } }
                        },
                    )
                }
                item("results") {
                    Text(
                        if (search.isEmpty()) "小区列表" else "匹配 ${filtered.size} 个小区",
                        Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item("empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (search.isEmpty()) "导入 CSV 后可在这里查看小区" else "没有匹配的小区",
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { param ->
                Surface(
                    onClick = { selected = param },
                    shape = RoundedCornerShape(18.dp),
                    color = DashboardCardDefaults.cardContainerColor(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = RoundedCornerShape(11.dp), color = colors.secondaryContainer) {
                            Text(BaiduMapArea.netTypeLabel(param.netType), Modifier.padding(9.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(param.cellName.orEmpty().ifBlank { param.siteName.orEmpty().ifBlank { "小区 ${param.sectorId}" } },
                                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("基站 ${param.gnbId}  ·  PCI ${param.pci}  ·  频点 ${param.earfcn}",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }

    selected?.let { param ->
        EngineeringSectorSheet(
            param,
            onDismiss = { selected = null },
            onDelete = {
                selected = null
                vm.onRequestDelete(param.id)
            },
        )
    }
    if (state.showMappingDialog) {
        ColumnMappingDialog(state.headers, state.samples, vm::onConfirmMapping, vm::onDismissMapping)
    }
    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = vm::onDismissClear,
            title = { Text("清空全部工参？") },
            text = { Text("将删除已导入的 ${params.size} 个小区。") },
            dismissButton = { TextButton(onClick = vm::onDismissClear) { Text("取消") } },
            confirmButton = { TextButton(onClick = vm::onConfirmClear) { Text("清空", color = colors.error) } },
        )
    }
    if (state.pendingDeleteId >= 0) {
        val name = params.firstOrNull { it.id == state.pendingDeleteId }?.cellName.orEmpty()
        AlertDialog(
            onDismissRequest = vm::onDismissDelete,
            title = { Text("删除这条工参？") },
            text = { Text(name.ifBlank { "将从本地工参中删除该小区。" }) },
            dismissButton = { TextButton(onClick = vm::onDismissDelete) { Text("取消") } },
            confirmButton = { TextButton(onClick = vm::onConfirmDelete) { Text("删除", color = colors.error) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingDialog(
    headers: List<String>,
    samples: List<String>,
    onConfirm: (Map<Int, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = ImportFieldSpec.FIELDS
    var selection by remember(headers) { mutableStateOf(CsvParsingEngine.autoMap(headers).filterKeys { col -> fields.any { it.col == col } }) }
    val missing = ImportFieldSpec.missingRequired(selection)
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("核对 CSV 列", style = MaterialTheme.typography.headlineSmall)
            Text("${fields.size - missing.size} / ${fields.size} 项已匹配 · 点字段可更换对应列",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(fields, key = { it.col }) { field ->
                    var open by remember { mutableStateOf(false) }
                    val selected = selection[field.col]
                    Box {
                        Surface(onClick = { open = true }, shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerHigh) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium)
                                val header = selected?.let(headers::get)
                                val sample = selected?.let(samples::getOrNull).orEmpty()
                                Text(if (header == null) "选择列" else if (sample.isBlank()) header else "$header · $sample",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (selected == null) colors.primary else colors.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            headers.forEachIndexed { index, header ->
                                val owner = fields.firstOrNull { selection[it.col] == index && it.col != field.col }
                                DropdownMenuItem(
                                    text = { Text("${index + 1}. ${header.ifBlank { "未命名列" }}" + (owner?.let { " · ${it.label}" } ?: "")) },
                                    onClick = {
                                        selection = selection.filterValues { it != index } + (field.col to index)
                                        open = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = { onConfirm(selection) }, enabled = missing.isEmpty()) { Text("导入工参") }
            }
        }
    }
}
