package com.craxiom.networksurvey.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 中国地图坐标系转换（WGS-84 ↔ GCJ-02 ↔ BD-09）。
 * 工参和定位使用 WGS-84；投放到百度地图前需调用 wgs84ToBd09；导出时保留 BD-09 主列，并用 bd09ToWgs84 还原。
 */
object CoordConverter {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private val X_PI = PI * 3000.0 / 180.0

    /** WGS-84 → GCJ-02，返回 [gcjLat, gcjLon] */
    fun wgs84ToGcj02(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (A * (1 - EE) / (magic * sqrtMagic) * PI)
        dLon = dLon * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return doubleArrayOf(lat + dLat, lon + dLon)
    }

    /** GCJ-02 → WGS-84，返回 [wgsLat, wgsLon] */
    fun gcj02ToWgs84(lat: Double, lon: Double): DoubleArray {
        val g = wgs84ToGcj02(lat, lon)
        return doubleArrayOf(lat * 2 - g[0], lon * 2 - g[1])
    }

    /** GCJ-02 → BD-09，返回 [bdLat, bdLon] */
    fun gcj02ToBd09(lat: Double, lon: Double): DoubleArray {
        val x = lon
        val y = lat
        val z = sqrt(x * x + y * y) + 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) + 0.000003 * cos(x * X_PI)
        val bdLon = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return doubleArrayOf(bdLat, bdLon)
    }

    /** BD-09 → GCJ-02，返回 [gcjLat, gcjLon] */
    fun bd09ToGcj02(lat: Double, lon: Double): DoubleArray {
        val x = lon - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        val gcjLon = z * cos(theta)
        val gcjLat = z * sin(theta)
        return doubleArrayOf(gcjLat, gcjLon)
    }

    /** WGS-84 → BD-09，返回 [bdLat, bdLon]（百度地图投放用） */
    fun wgs84ToBd09(lat: Double, lon: Double): DoubleArray {
        val g = wgs84ToGcj02(lat, lon)
        return gcj02ToBd09(g[0], g[1])
    }

    /** BD-09 → WGS-84，返回 [wgsLat, wgsLon]（导出用） */
    fun bd09ToWgs84(lat: Double, lon: Double): DoubleArray {
        val g = bd09ToGcj02(lat, lon)
        return gcj02ToWgs84(g[0], g[1])
    }

    private fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
