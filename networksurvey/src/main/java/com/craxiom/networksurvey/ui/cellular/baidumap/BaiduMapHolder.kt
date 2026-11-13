package com.craxiom.networksurvey.ui.cellular.baidumap

import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.TextureMapView

/**
 * 百度地图 View 实例与图层状态持有器。
 *
 * 持有 TextureMapView 引用和运行时视图状态，供 Composable 在重组时存取。
 */
class BaiduMapHolder {
    var mapView: TextureMapView? = null
    var baiduMap: BaiduMap? = null
    var zoomLevel: Float = 17f
    var isFollow: Boolean = true
    var showLabel: Boolean = true
    var centerLat: Double = 0.0
    var centerLng: Double = 0.0
    var curLat: Double = 0.0
    var curLng: Double = 0.0
    var curAcc: Float = 0f
    var linkPolyline: Polyline? = null
    var linkMarker: Marker? = null
    var sectorMarkers: HashMap<Long, Marker> = HashMap()
    var isRecording: Boolean = false
    var selectedId: Long = -1L
}
