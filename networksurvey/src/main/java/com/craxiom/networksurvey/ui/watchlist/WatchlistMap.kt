package com.craxiom.networksurvey.ui.watchlist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.OverlayOptions
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.craxiom.networksurvey.util.CoordConverter

/**
 * 百度地图版 Watchlist 地图：在地图上绘制彩色圆点并报告点击。
 *
 * 坐标转换：输入为 WGS-84，投放前通过 CoordConverter.wgs84ToBd09 转为 BD-09。
 */
@Composable
fun WatchlistMap(
    points: List<WatchlistMapPoint>,
    selectedId: String?,
    onPointClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    paddingInsets: PaddingValues = PaddingValues(0.dp),
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    attributionEnabled: Boolean = false,
) {
    var baiduMap by remember { mutableStateOf<BaiduMap?>(null) }
    var mapView by remember { mutableStateOf<TextureMapView?>(null) }
    var fitted by remember { mutableStateOf(false) }
    val markerMap = remember { mutableMapOf<String, Marker>() }

    // Lifecycle management for TextureMapView
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapView?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapView?.onPause() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView?.onDestroy() }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                TextureMapView(ctx).apply {
                    mapView = this
                    baiduMap = this.map
                    this.map.setOnMarkerClickListener { marker ->
                        // Find which point this marker corresponds to
                        val entry = markerMap.entries.firstOrNull { it.value === marker }
                        if (entry != null) {
                            onPointClick(entry.key)
                            true
                        } else {
                            false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Update markers when points or selection change
        LaunchedEffect(points, selectedId, baiduMap) {
            val bm = baiduMap ?: return@LaunchedEffect
            // Clear old markers
            markerMap.values.forEach { it.remove() }
            markerMap.clear()

            for (point in points) {
                val bd09 = CoordConverter.wgs84ToBd09(point.latitude, point.longitude)
                val color = try {
                    android.graphics.Color.parseColor(point.colorHex)
                } catch (_: Exception) {
                    android.graphics.Color.BLUE
                }
                val isSelected = point.id == selectedId
                val radius = if (isSelected) 28f else 18f
                val bmp = createCircleBitmap(color, radius)
                val bd = BitmapDescriptorFactory.fromBitmap(bmp)
                val options: OverlayOptions = MarkerOptions()
                    .position(LatLng(bd09[0], bd09[1]))
                    .icon(bd)
                    .zIndex(if (isSelected) 10 else 1)
                val marker = bm.addOverlay(options) as? Marker
                if (marker != null) {
                    markerMap[point.id] = marker
                }
            }
        }

        // Fit camera to all points the first time
        LaunchedEffect(baiduMap, points) {
            val bm = baiduMap ?: return@LaunchedEffect
            if (points.isNotEmpty() && !fitted) {
                if (points.size == 1) {
                    val only = points.first()
                    val bd09 = CoordConverter.wgs84ToBd09(only.latitude, only.longitude)
                    bm.animateMapStatus(
                        MapStatusUpdateFactory.newLatLngZoom(LatLng(bd09[0], bd09[1]), 16f)
                    )
                } else {
                    val builder = LatLngBounds.Builder()
                    points.forEach { p ->
                        val bd09 = CoordConverter.wgs84ToBd09(p.latitude, p.longitude)
                        builder.include(LatLng(bd09[0], bd09[1]))
                    }
                    bm.animateMapStatus(
                        MapStatusUpdateFactory.newLatLngBounds(builder.build(), 120, 120, 120, 120)
                    )
                }
                fitted = true
            }
        }

        // Recenter on selected point
        LaunchedEffect(selectedId, topInset, bottomInset) {
            val bm = baiduMap ?: return@LaunchedEffect
            val target = selectedId?.let { id -> points.firstOrNull { it.id == id } }
                ?: return@LaunchedEffect
            val bd09 = CoordConverter.wgs84ToBd09(target.latitude, target.longitude)
            bm.animateMapStatus(
                MapStatusUpdateFactory.newLatLng(LatLng(bd09[0], bd09[1]))
            )
        }
    }
}

/** Create a circular bitmap for map markers. */
private fun createCircleBitmap(color: Int, radius: Float): Bitmap {
    val size = (radius * 2 + 6f).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)
    // White stroke for visibility
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)
    return bmp
}
