package com.craxiom.networksurvey.ui.cellular.baidumap

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.craxiom.networksurvey.db.EngineeringParamDbHelper
import com.craxiom.networksurvey.ui.engparam.EngineeringSectorSheet
import com.craxiom.networksurvey.db.MapPointDbHelper
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CoordConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaiduTowerMapScreen(
    onBackButtonPressed: () -> Unit,
    servingCellInfo: ServingCellInfo? = null,
    subscriptionId: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(context) { EngineeringParamDbHelper.getInstance(context.applicationContext) }
    val pointsDb = remember(context) { MapPointDbHelper.getInstance(context.applicationContext) }
    val updates = rememberMapServingCell(servingCellInfo, subscriptionId)
    val servingStation = remember(updates) { updates?.servingCell?.toStationInfo() }

    var area by remember { mutableStateOf<BaiduMapArea?>(null) }
    var ready by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf<BaiduMapArea.Viewport?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var showLabels by rememberSaveable { mutableStateOf(true) }
    var showPoints by rememberSaveable { mutableStateOf(true) }
    var showLink by rememberSaveable { mutableStateOf(true) }
    var netFilter by rememberSaveable { mutableStateOf("全部") }
    var metric by rememberSaveable { mutableStateOf("RSRP") }
    var servingParam by remember { mutableStateOf<EngineeringParam?>(null) }
    var selected by remember { mutableStateOf<EngineeringParam?>(null) }
    var detail by remember { mutableStateOf<EngineeringParam?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var cameraPositioned by remember { mutableStateOf(false) }
    val trackRevision by MapTrack.revision.collectAsStateWithLifecycle()
    var autoTrack by remember { mutableStateOf(MapTrack.isEnabled(context)) }
    var pointCount by remember { mutableStateOf(0) }
    var visibleSectors by remember { mutableStateOf<Int?>(null) }
    var renderedSectors by remember { mutableStateOf(0) }
    var paramsCount by remember { mutableStateOf<Int?>(null) }
    var paramsLoaded by remember { mutableStateOf(false) }
    var pointActionBusy by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(mapLocationPermissions(context)) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = mapLocationPermissions(context)
        if (permissions) {
            follow = true
        } else {
            Toast.makeText(context, "可继续查看地图。定位和打点需要位置权限", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(db) {
        paramsCount = withContext(Dispatchers.IO) { runCatching { db.count }.getOrNull() }
        paramsLoaded = true
    }

    LaunchedEffect(pointsDb, trackRevision) {
        val count = withContext(Dispatchers.IO) { runCatching { pointsDb.count() }.getOrNull() }
        if (count != null) pointCount = count
    }

    LaunchedEffect(
        servingStation?.networkType, servingStation?.cellId, servingStation?.mcc, servingStation?.mnc,
        servingStation?.pci, servingStation?.earfcn, paramsCount,
    ) {
        servingParam = null
        val station = servingStation ?: return@LaunchedEffect
        servingParam = withContext(Dispatchers.IO) {
            runCatching { db.matchServingIdentity(station) }.getOrNull()
        }
        if (servingParam == null && station.pci >= 0 && station.earfcn >= 0) {
            snapshotFlow { location }.filterNotNull().collectLatest { fix ->
                val nearby = withContext(Dispatchers.IO) {
                    runCatching { db.matchNearbyServing(fix.latitude, fix.longitude, station) }.getOrNull()
                }
                servingParam = nearby
            }
        }
    }

    val map = area
    val currentViewport = viewport
    LaunchedEffect(map, ready, currentViewport, netFilter, selected?.id, servingParam?.id, showLabels, paramsCount) {
        if (map == null || !ready || currentViewport == null) return@LaunchedEffect
        delay(100L)
        val important = setOfNotNull(selected?.id, servingParam?.id)
        val (visible, displayed) = withContext(Dispatchers.IO) {
            val candidates = if (currentViewport.zoom >= BaiduMapArea.SECTOR_ZOOM && (paramsCount ?: 0) > 0) {
                runCatching {
                    with(currentViewport) {
                        db.getByRange(
                            minLat - latSpan * 0.12, minLon - lonSpan * 0.12,
                            maxLat + latSpan * 0.12, maxLon + lonSpan * 0.12,
                        )
                    }.filter { param ->
                        BaiduMapArea.isValid(param.lat, param.lon) &&
                            (netFilter == "全部" || BaiduMapArea.netTypeLabel(param.netType) == netFilter)
                    }.sortedWith(
                        compareBy<EngineeringParam> { it.id !in important }
                            .thenBy { !currentViewport.contains(it.lat, it.lon) }
                            .thenBy { currentViewport.distanceFromCenter(it.lat, it.lon) },
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val limit = if (currentViewport.zoom < BaiduMapArea.CLOSE_ZOOM) 800 else Int.MAX_VALUE
            candidates.count { currentViewport.contains(it.lat, it.lon) } to candidates.take(limit)
        }
        map.renderSectors(displayed, currentViewport, selected?.id, servingParam?.id, showLabels)
        renderedSectors = displayed.count { currentViewport.contains(it.lat, it.lon) }
        visibleSectors = visible
    }

    LaunchedEffect(map, ready, currentViewport, trackRevision, metric, netFilter, showPoints) {
        if (map == null || !ready || currentViewport == null) return@LaunchedEffect
        val visible = withContext(Dispatchers.IO) {
            if (!showPoints) emptyList() else runCatching {
                with(currentViewport) {
                    pointsDb.getByRange(
                        minLat - latSpan * 0.12, minLon - lonSpan * 0.12,
                        maxLat + latSpan * 0.12, maxLon + lonSpan * 0.12,
                        netFilter, metric == "SINR", 600,
                    )
                }
            }.getOrDefault(emptyList())
        }
        map.renderPoints(visible, metric)
    }

    LifecycleResumeEffect(permissions) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { incoming ->
            val previous = location
            if (previous == null || incoming.elapsedRealtimeNanos > previous.elapsedRealtimeNanos &&
                (!previous.hasAccuracy() || incoming.accuracy <= previous.accuracy * 2 ||
                    incoming.elapsedRealtimeNanos - previous.elapsedRealtimeNanos > 15_000_000_000L)
            ) {
                location = incoming
            }
        }
        if (permissions && manager != null) {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)) {
                runCatching {
                    manager.getLastKnownLocation(provider)?.takeIf { it.isRecent() }?.let(listener::onLocationChanged)
                    manager.requestLocationUpdates(provider, 2_000L, 3f, listener)
                }
            }
        } else {
            location = null
        }
        onPauseOrDispose { manager?.removeUpdates(listener) }
    }

    val coordinate = remember(location) {
        location?.let {
            val bd = CoordConverter.wgs84ToBd09(it.latitude, it.longitude)
            LatLng(bd[0], bd[1])
        }
    }
    LaunchedEffect(map, ready, coordinate, servingParam, follow, showLink) {
        if (map == null || !ready) return@LaunchedEffect
        val current = coordinate
        if (current != null) {
            map.updateLocation(current.latitude, current.longitude, true, 0f)
            map.updateLink(servingParam, current.latitude, current.longitude, showLink)
            if (follow) {
                if (!cameraPositioned) {
                    cameraPositioned = true
                    map.baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(current, 16f))
                } else {
                    map.baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(current))
                }
            }
        } else {
            map.updateLocation(0.0, 0.0, false, 0f)
            map.updateLink(null, 0.0, 0.0, false)
            val serving = servingParam
            if (!cameraPositioned && follow && serving != null && BaiduMapArea.isValid(serving.lat, serving.lon)) {
                val bd = CoordConverter.wgs84ToBd09(serving.lat, serving.lon)
                map.baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(LatLng(bd[0], bd[1]), 16f))
                cameraPositioned = true
            }
        }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(map, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    map?.mapView?.onPause()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LifecycleResumeEffect(context) {
        permissions = mapLocationPermissions(context)
        onPauseOrDispose {}
    }

    fun locate() {
        if (!permissions) {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager?.isLocationEnabled != true) {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        follow = true
        if (coordinate == null) {
            Toast.makeText(context, "正在等待定位", Toast.LENGTH_SHORT).show()
        } else {
            map?.baiduMap?.animateMapStatus(MapStatusUpdateFactory.newLatLng(coordinate))
        }
    }

    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        AndroidView(
            factory = { ctx ->
                TextureMapView(ctx).apply {
                    showZoomControls(false)
                    val renderer = BaiduMapArea(this)
                    val baidu = renderer.baiduMap
                    baidu.setBuildingsEnabled(false)
                    baidu.setIndoorEnable(false)
                    baidu.setMaxAndMinZoomLevel(21f, BaiduMapArea.MAP_MIN_ZOOM)
                    baidu.setOnMapLoadedCallback {
                        ready = true
                        viewport = renderer.viewport(baidu.mapStatus)
                    }
                    baidu.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
                        override fun onMapStatusChangeStart(status: MapStatus?) = Unit
                        override fun onMapStatusChangeStart(status: MapStatus?, reason: Int) {
                            if (reason == BaiduMap.OnMapStatusChangeListener.REASON_GESTURE) {
                                follow = false
                                cameraPositioned = true
                            }
                        }
                        override fun onMapStatusChange(status: MapStatus?) = Unit
                        override fun onMapStatusChangeFinish(status: MapStatus?) {
                            if (ready) viewport = renderer.viewport(status)
                        }
                    })
                    baidu.setOnMarkerClickListener { marker ->
                        val param = renderer.paramOf(marker) ?: return@setOnMarkerClickListener false
                        selected = param
                        detail = param
                        true
                    }
                    area = renderer
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onDestroy() },
        )

        if (!ready) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp)
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp, color = colors.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackButtonPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                        Text("基站地图", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "搜索工参")
                        }
                        IconButton(onClick = { showLayers = true }) {
                            Icon(Icons.Default.Layers, "图层与打点设置")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("全部", "4G", "5G").forEach { filter ->
                        FilterChip(
                            selected = netFilter == filter,
                            onClick = {
                                netFilter = filter
                                selected = selected?.takeIf {
                                    filter == "全部" || BaiduMapArea.netTypeLabel(it.netType) == filter
                                }
                            },
                            label = { Text(filter) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                        )
                    }
                }
                val status = when {
                    paramsLoaded && paramsCount == null -> "无法读取工参"
                    paramsCount == 0 -> "未导入工参，可在设置中导入 CSV"
                    currentViewport != null && currentViewport.zoom < BaiduMapArea.SECTOR_ZOOM -> "放大地图查看扇区"
                    visibleSectors == 0 -> "当前视野无匹配扇区"
                    visibleSectors != null && renderedSectors < (visibleSectors ?: 0) ->
                        "显示 $renderedSectors / $visibleSectors 个扇区 · 放大查看"
                    visibleSectors != null -> "视野内 $visibleSectors 个扇区"
                    else -> null
                }
                if (status != null) {
                    Surface(color = colors.surface, shape = CircleShape) {
                        Text(status, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    shadowElevation = 3.dp,
                    color = if (follow && coordinate != null) colors.primaryContainer else colors.surface,
                ) {
                    IconButton(onClick = ::locate) {
                        Icon(Icons.Default.MyLocation, if (follow) "回到当前位置" else "定位并跟随")
                    }
                }
                Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 3.dp, color = colors.surface) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 15.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    servingParam?.displayName ?: "当前服务小区",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val linkStatus = when {
                                    !permissions -> "等待定位权限"
                                    coordinate == null -> "等待定位"
                                    servingStation == null -> "等待服务小区"
                                    !showLink -> "连线已关闭"
                                    servingParam == null -> "未匹配服务小区工参"
                                    else -> "已匹配服务小区"
                                }
                                Text(
                                    "$linkStatus · $pointCount 个打点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            val trackingLabel = when {
                                !autoTrack -> "打点已暂停"
                                !permissions -> "授予定位权限"
                                coordinate == null || servingStation == null -> "等待打点"
                                else -> "自动打点"
                            }
                            FilterChip(
                                selected = autoTrack && permissions,
                                onClick = {
                                    if (autoTrack && !permissions) {
                                        requestLocation.launch(arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ))
                                    } else {
                                        autoTrack = !autoTrack
                                        MapTrack.setEnabled(context, autoTrack)
                                        if (autoTrack && !permissions) {
                                            requestLocation.launch(arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ))
                                        }
                                    }
                                },
                                label = { Text(trackingLabel) },
                            )
                        }
                        servingStation?.let { station ->
                            FlowRow(
                                Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(BaiduMapArea.netTypeLabel(station.networkType), color = colors.primary, style = MaterialTheme.typography.labelMedium)
                                val metrics = listOf(
                                    "RSRP" to station.rsrp.takeIf { it in -160..-20 }?.let { "$it dBm" },
                                    "RSRQ" to station.rsrq.takeIf { it in -45..10 }?.let { "$it dB" },
                                    "SINR" to station.sinr.takeIf { it in -40..50 }?.let { "$it dB" },
                                    "PCI" to station.pci.takeIf { it >= 0 }?.toString(),
                                    "频点" to station.earfcn.takeIf { it >= 0 }?.toString(),
                                )
                                for ((label, value) in metrics) {
                                    Text("$label ${value ?: "—"}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        TowerSearchSheet(
            onDismiss = { showSearch = false },
            onResult = { param ->
                showSearch = false
                selected = param
                detail = param
                if (BaiduMapArea.isValid(param.lat, param.lon)) {
                    follow = false
                    cameraPositioned = true
                    if (netFilter != "全部" && netFilter != BaiduMapArea.netTypeLabel(param.netType)) netFilter = "全部"
                    val bd = CoordConverter.wgs84ToBd09(param.lat, param.lon)
                    map?.baiduMap?.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(LatLng(bd[0], bd[1]), 18f))
                }
            },
            db = db,
        )
    }

    detail?.let { EngineeringSectorSheet(it, onDismiss = { detail = null }) }

    if (showLayers) {
        MapLayerSheet(
            showLabels, showPoints, showLink, metric, pointCount, pointActionBusy,
            onToggleLabels = { showLabels = !showLabels },
            onTogglePoints = { showPoints = !showPoints },
            onToggleLink = { showLink = !showLink },
            onMetric = { metric = it },
            onDismiss = { showLayers = false },
            onClear = {
                showLayers = false
                confirmClear = true
            },
            onExport = {
                pointActionBusy = true
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { pointsDb.exportCsv(context.cacheDir) }.getOrNull()
                    }
                    val shared = path != null && runCatching {
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享打点数据"))
                    }.isSuccess
                    if (!shared) Toast.makeText(context, "无法导出打点", Toast.LENGTH_SHORT).show()
                    pointActionBusy = false
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空打点？") },
            text = { Text("将删除已保存的 ${pointCount} 个打点。") },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    pointActionBusy = true
                    scope.launch {
                        val deleted = runCatching { MapTrack.clear(context) }.isSuccess
                        if (deleted) pointCount = 0
                        else Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show()
                        pointActionBusy = false
                    }
                }) { Text("清空", color = colors.error) }
            },
        )
    }
}

private fun mapLocationPermissions(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun Location.isRecent(): Boolean =
    SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos in 0..30_000_000_000L

@Composable
private fun rememberMapServingCell(initial: ServingCellInfo?, subscription: Int?): ServingCellInfo? {
    val context = LocalContext.current.applicationContext
    val updates = remember(initial, subscription) { MutableStateFlow(initial?.takeIf { subscription == null || it.subscriptionId == subscription }) }
    LifecycleResumeEffect(context, updates) {
        var service: NetworkSurveyService? = null
        val listener = object : ICellularSurveyRecordListener {
            override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
                val serving = cellularGroup.mapServingCell()
                if (subscription != null && subscriptionId != subscription &&
                    subscriptionId != com.craxiom.networksurvey.services.controller.CellularController.DEFAULT_SUBSCRIPTION_ID
                ) return
                if (serving != null) updates.value = ServingCellInfo(serving, subscriptionId)
                else if (updates.value?.subscriptionId == subscriptionId) updates.value = null
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? NetworkSurveyService.SurveyServiceBinder)?.service as? NetworkSurveyService
                service?.registerCellularSurveyRecordListener(listener)
                service?.runSingleCellularScan()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service?.unregisterCellularSurveyRecordListener(listener)
                service = null
            }
        }
        val bound = context.bindService(Intent(context, NetworkSurveyService::class.java), connection, Context.BIND_AUTO_CREATE)
        onPauseOrDispose {
            service?.unregisterCellularSurveyRecordListener(listener)
            if (bound) context.unbindService(connection)
        }
    }
    return updates.collectAsStateWithLifecycle().value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLayerSheet(
    labels: Boolean,
    points: Boolean,
    link: Boolean,
    metric: String,
    pointCount: Int,
    busy: Boolean,
    onToggleLabels: () -> Unit,
    onTogglePoints: () -> Unit,
    onToggleLink: () -> Unit,
    onMetric: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("地图图层", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = labels, onClick = onToggleLabels, label = { Text("扇区名称") })
                FilterChip(selected = points, onClick = onTogglePoints, label = { Text("打点") })
                FilterChip(selected = link, onClick = onToggleLink, label = { Text("服务小区连线") })
            }
            Text("扇区颜色", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "移动" to BaiduMapArea.COLOR_CMCC, "联通" to BaiduMapArea.COLOR_CUCC,
                    "电信" to BaiduMapArea.COLOR_CTCC, "广电" to BaiduMapArea.COLOR_CBN,
                ).forEach { (name, color) -> MapLegendEntry(name, color) }
            }
            Text("打点指标", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RSRP", "SINR").forEach { item ->
                    FilterChip(selected = metric == item, onClick = { onMetric(item) }, label = { Text(item) })
                }
            }
            val sinr = metric == "SINR"
            val grades = if (sinr) listOf(30, 20, 12, 5, -5) else listOf(-70, -90, -100, -110, -125)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                grades.forEach { grade -> MapLegendEntry("$grade", BaiduMapArea.gradeColor(grade, sinr)) }
                Text(if (sinr) "dB" else "dBm", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onExport, enabled = !busy && pointCount > 0) { Text("导出 CSV") }
                TextButton(onClick = onClear, enabled = !busy && pointCount > 0) { Text("清空打点") }
            }
        }
    }
}

@Composable
private fun MapLegendEntry(label: String, color: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(color), CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TowerSearchSheet(
    onDismiss: () -> Unit,
    onResult: (EngineeringParam) -> Unit,
    db: EngineeringParamDbHelper,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<EngineeringParam>()) }
    var completedQuery by remember { mutableStateOf<String?>(null) }
    val term = query.trim()
    LaunchedEffect(term, db) {
        if (term.isEmpty()) {
            results = emptyList()
            completedQuery = null
        } else {
            delay(220)
            results = withContext(Dispatchers.IO) { runCatching { db.search(term, 50) }.getOrDefault(emptyList()) }
            completedQuery = term
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text("搜索工参", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                label = { Text("小区名、站名、CI、PCI 或频点") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "清除搜索") }
                    }
                },
            )
            when {
                term.isEmpty() -> Unit
                completedQuery != term -> Text("正在搜索…", Modifier.padding(12.dp))
                results.isEmpty() -> Text("未找到匹配工参", Modifier.padding(12.dp))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { param ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onResult(param) }.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(param.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "PCI ${param.pci} · ${BaiduMapArea.netTypeLabel(param.netType)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ==================== 工参详情弹窗 ==================== */
