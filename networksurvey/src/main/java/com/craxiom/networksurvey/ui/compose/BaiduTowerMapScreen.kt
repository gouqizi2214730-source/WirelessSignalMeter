package com.craxiom.networksurvey.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.craxiom.networksurvey.db.EngineeringParamDbHelper
import com.craxiom.networksurvey.model.EngineeringParam

/**
 * 无线无忧·基站地图页（百度地图 7.6.4）
 *
 * - TextureMapView 生命周期通过 LifecycleEventObserver 接管（onResume/onPause/onDestroy），
 *   避免 SDK 内部回调失效或泄漏导致闪退
 * - 工参扇区：本地 SQLite 工参（WGS-84）→ BD-09 投放，按制式着色（BaiduMapArea 渲染）
 * - 定位：原生 LocationManager（NETWORK + GPS），精度 >150m 不跳转，支持跟随
 * - 控制条：跟随定位 / 标签开关 / 工参图层开关
 */
@Composable
fun BaiduTowerMapScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current

    val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasFine) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val params = remember {
        runCatching {
            EngineeringParamDbHelper.getInstance(context).getAll()
        }.getOrElse { emptyList() }
    }

    // 地图 View 与渲染引擎（Composable 局部持有，避免全局引用跨实例串扰）
    var mapView by remember { mutableStateOf<TextureMapView?>(null) }
    var area by remember { mutableStateOf<BaiduMapArea?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var curLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) } // BD-09
    var follow by remember { mutableStateOf(true) }
    var showLabel by remember { mutableStateOf(true) }
    var showTowers by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<EngineeringParam?>(null) }

    // 地图生命周期接管（百度 SDK 必须收到 onResume/onPause/onDestroy）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val mv = mapView
        val observer = LifecycleEventObserver { _, event ->
            val m = mv ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { m.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { m.onPause() }
                Lifecycle.Event.ON_DESTROY -> {
                    runCatching { m.map.clear() }
                    runCatching { m.onDestroy() }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                runCatching {
                    TextureMapView(ctx).also { mv ->
                        mapView = mv
                        mv.map.apply {
                            mapType = BaiduMap.MAP_TYPE_NORMAL
                            setOnMapLoadedCallback {
                                runCatching {
                                    mapReady = true
                                    area = BaiduMapArea(mv)
                                }
                            }
                            setOnMarkerClickListener { marker ->
                                runCatching {
                                    area?.markerIdOf(marker)?.let { id ->
                                        if (id != -1L) {
                                            selected = params.firstOrNull { it.id == id }
                                        }
                                    }
                                }
                                true
                            }
                        }
                    }
                }.getOrNull() ?: android.widget.FrameLayout(ctx) // SDK 异常时兜底空容器，避免闪退
            },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "基站地图",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // 选中小区信息卡片
        selected?.let { p ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(p.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "制式 ${p.netType ?: "--"}  |  频段 ${p.band ?: "--"}  |  PCI ${p.pci}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "经度 %.5f  纬度 %.5f".format(p.lon, p.lat),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 底部控制条
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MapCtrlButton(icon = { Icon(Icons.Default.Place, null, tint = if (follow) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }, active = follow) {
                follow = true
                curLoc?.let { (lat, lon) ->
                    runCatching {
                        area?.baiduMap?.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(LatLng(lat, lon), 17f))
                    }
                }
            }
            MapCtrlButton(icon = { Icon(Icons.Default.Info, null, tint = if (showLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }, active = showLabel) {
                showLabel = !showLabel
                renderTowers(area, params, showTowers, showLabel, selected?.id ?: -1L)
            }
            MapCtrlButton(icon = { Icon(Icons.Default.Menu, null, tint = if (showTowers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }, active = showTowers) {
                showTowers = !showTowers
                renderTowers(area, params, showTowers, showLabel, selected?.id ?: -1L)
            }
        }
    }

    // 定位（原生 LocationManager）
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val acc = location.accuracy
                if (acc > 150f && curLoc != null) return
                curLoc = location.latitude to location.longitude
                val areaNow = area ?: return
                runCatching {
                    areaNow.updateLocation(location.latitude, location.longitude, true, 0f)
                    if (follow && mapReady) {
                        areaNow.baiduMap.animateMapStatus(
                            MapStatusUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17f)
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.forEach { provider ->
            runCatching { lm.requestLocationUpdates(provider, 2000L, 0f, listener) }
        }
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(listener::onLocationChanged) }
        runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(listener::onLocationChanged) }
        onDispose {
            runCatching { lm.removeUpdates(listener) }
        }
    }

    // 工参渲染（地图就绪后）
    LaunchedEffect(mapReady, showTowers, showLabel) {
        if (mapReady) {
            renderTowers(area, params, showTowers, showLabel, selected?.id ?: -1L)
        }
    }
}

private fun renderTowers(
    area: BaiduMapArea?,
    params: List<EngineeringParam>,
    show: Boolean,
    showLabel: Boolean,
    selectedId: Long
) {
    val a = area ?: return
    try {
        if (show) {
            a.renderStatic(params, selectedId, -1L, 0, showLabel)
        } else {
            a.renderStatic(emptyList(), selectedId, -1L, 0, showLabel)
        }
    } catch (t: Throwable) {
        // 地图未就绪/被回收时静默
    }
}

@Composable
private fun MapCtrlButton(
    icon: @Composable () -> Unit,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        icon()
    }
}
