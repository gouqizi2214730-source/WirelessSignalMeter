package com.craxiom.networksurvey.ui.compose

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.OverlayOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.model.MapPointRecord
import com.craxiom.networksurvey.util.CoordConverter
import com.craxiom.networksurvey.util.SignalGrade
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 百度地图渲染引擎（恢复版，v3.90 反编译 BaiduMapAreaKt 转写）。
 * 铁律：
 * - 工参为 WGS-84 → 投放前 wgs84ToBd09；打点/位置为 BD-09 → 直接投放（SDK setCoordType(BD09LL)）
 * - 静态层仅状态变化才 clear+全量 render；打点走独立增量层；位置变化走 setPoints/setPosition
 * - MAX_OVERLAY=800 视口裁剪、LABEL_ZOOM_THRESHOLD=17f 标签阈值
 * - makeSectorBitmap 画布固定两档尺寸 + LruCache(384)；dotBitmap LRU 256
 */
class BaiduMapArea(val mapView: TextureMapView) {

    val baiduMap: BaiduMap get() = mapView.map

    // 静态层（工参扇区）
    private val staticMarkers = ArrayList<Marker>()
    private val markerIdMap = HashMap<Marker, Long>()

    /** 通过 Marker 反查工参 id（点击选中用） */
    fun markerIdOf(marker: Marker): Long = markerIdMap[marker] ?: -1L
    // 打点增量层
    private val pointMarkers = ArrayList<Marker>()
    private var pointKey = ""
    private var pointCount = 0
    // 动态层
    private var linkPolyline: Polyline? = null
    private var linkMarker: Marker? = null
    private var locMarker: Marker? = null

    companion object {
        const val MAX_OVERLAY = 800
        const val LABEL_ZOOM_THRESHOLD = 17f

        private val sectorCache = LruCache<String, Bitmap>(384)
        private val dotCache = HashMap<Long, Bitmap>()
        private var locIconBitmap: Bitmap? = null
        private val density: Float get() = Resources.getSystem().displayMetrics.density

        // 制式色：5G 绿 / 4G 蓝 / 其他灰（对应原 y10.O(netType) → 色）
        fun netTypeColor(netType: String?): Int = when (netType) {
            "5G" -> Color.rgb(46, 158, 91)
            "4G" -> Color.rgb(26, 115, 232)
            else -> Color.rgb(158, 158, 158)
        }

        /** RSRP/SINR → 等级色（复用 SignalGrade 色标） */
        fun gradeColor(value: Int, sinr: Boolean): Int {
            val level = if (sinr) SignalGrade.fromSinr(value) else SignalGrade.fromRsrp(value)
            return Color.parseColor(SignalGrade.hexOf(level))
        }

        fun isValid(lat: Double, lon: Double): Boolean =
            !lat.isNaN() && !lon.isNaN() && !lat.isInfinite() && !lon.isInfinite() &&
                (lat != 0.0 || lon != 0.0) &&
                lat in -90.0..90.0 && lon in -180.0..180.0
    }

    /** WGS-84 工参 → BD-09 LatLng（投放） */
    fun toBd09LatLng(lat: Double, lon: Double): LatLng {
        val bd = CoordConverter.wgs84ToBd09(lat, lon)
        return LatLng(bd[0], bd[1])
    }

    /* ============ 静态层：工参扇区 ============ */

    fun renderStatic(
        params: List<EngineeringParam>,
        selectedId: Long,
        targetId: Long,
        colorIdx: Int,
        showLabel: Boolean
    ) {
        val bmap = baiduMap
        try {
            staticMarkers.forEach { runCatching { it.remove() } }
            staticMarkers.clear()
            markerIdMap.clear()
            if (params.isEmpty()) return

            val status = bmap.mapStatus
            val bound = status?.bound
            val zoom = status?.zoom ?: 0f
            val labelOn = showLabel && zoom >= LABEL_ZOOM_THRESHOLD

            val visible = ArrayList<EngineeringParam>(params.size)
            for (p in params) {
                if (p.lat == 0.0 && p.lon == 0.0) continue
                if (!isValid(p.lat, p.lon)) continue
                // 视口裁剪（转 BD-09 后判界）
                if (bound != null) {
                    val bd = CoordConverter.wgs84ToBd09(p.lat, p.lon)
                    val latOk = bd[0] >= min(bound.southwest.latitude, bound.northeast.latitude) - 0.05 &&
                        bd[0] <= max(bound.southwest.latitude, bound.northeast.latitude) + 0.05
                    val lonOk = bd[1] >= min(bound.southwest.longitude, bound.northeast.longitude) - 0.05 &&
                        bd[1] <= max(bound.southwest.longitude, bound.northeast.longitude) + 0.05
                    if (!latOk || !lonOk) continue
                }
                visible.add(p)
                if (visible.size >= MAX_OVERLAY) break
            }

            val overlays = ArrayList<OverlayOptions>(visible.size)
            for (p in visible) {
                val sel = p.id == selectedId
                val target = p.id == targetId
                val color = if (colorIdx == 0) netTypeColor(p.netType) else colorIdx
                val bmp = makeSectorBitmap(color, sel || target, labelOn, p.displayName, p.azimuth)
                val opts = MarkerOptions()
                    .position(toBd09LatLng(p.lat, p.lon))
                    .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (sel || target) 2 else 1)
                overlays.add(opts)
            }
            val added = bmap.addOverlays(overlays)
            added.forEachIndexed { i, o ->
                if (o is Marker && i < visible.size) {
                    staticMarkers.add(o)
                    markerIdMap[o] = visible[i].id
                }
            }
        } catch (e: Throwable) {
            // 地图未就绪/被回收时静默失败
        }
    }

    /* ============ 打点增量层 ============ */

    /**
     * 增量渲染：key(metric|filter) 或数量回退时清空重渲；否则只渲染新增段。
     * 打点坐标库内为 BD-09，直接投放。
     */
    fun renderPoints(points: List<MapPointRecord>, metric: String, filter: String) {
        val bmap = baiduMap
        try {
            val key = "$metric|$filter"
            if (key != pointKey || points.size < pointCount) {
                pointMarkers.forEach { runCatching { it.remove() } }
                pointMarkers.clear()
                pointCount = 0
                pointKey = key
            }
            if (points.isEmpty() && pointCount == 0) return

            val isSinr = metric == "SINR"
            val filter5g = filter == "5G"
            var i = pointCount
            while (i < points.size) {
                val p = points[i]
                try {
                    if (!isValid(p.latitude, p.longitude)) { i++; continue }
                    val matched = if (filter5g) p.is5G else true
                    val value = if (isSinr) p.sinr else p.rsrp
                    val color = if (matched) gradeColor(value, isSinr) else Color.rgb(158, 158, 158)
                    val size = if (matched) 20 else 12
                    val opts = MarkerOptions()
                        .position(LatLng(p.latitude, p.longitude))
                        .icon(BitmapDescriptorFactory.fromBitmap(dotBitmap(color, size)))
                        .anchor(0.5f, 0.5f)
                        .zIndex(4)
                    if (matched && value != 0) opts.title("${value}dBm")
                    val m = bmap.addOverlay(opts)
                    if (m != null) {
                        pointMarkers.add(m as Marker)
                        pointCount++
                    }
                } catch (e: Throwable) {
                }
                i++
            }
        } catch (e: Throwable) {
        }
    }

    /* ============ 动态层：连线 + 位置 ============ */

    /** 当前位置 → 选中小区 连线（Polyline setPoints 复用，不重复 add） */
    fun updateLink(
        selected: EngineeringParam?,
        curLat: Double,
        curLon: Double,
        show: Boolean,
        colorIdx: Int
    ) {
        val bmap = baiduMap
        try {
            val showLink = show && selected != null && isValid(selected.lat, selected.lon) &&
                isValid(curLat, curLon)
            if (!showLink) {
                linkPolyline?.let { if (!it.isRemoved) it.remove() }
                linkPolyline = null
                linkMarker?.let { if (!it.isRemoved) it.remove() }
                linkMarker = null
                return
            }
            val sel = selected!!
            val pts = listOf(LatLng(curLat, curLon), toBd09LatLng(sel.lat, sel.lon))
            if (linkPolyline == null || linkPolyline!!.isRemoved) {
                val color = if (colorIdx == 0) Color.argb(217, 46, 158, 91)
                else (colorIdx and 0xFFFFFF) or 0xD8000000.toInt()
                linkPolyline = bmap.addOverlay(
                    PolylineOptions().points(pts).color(color).width(4).zIndex(5)
                ) as Polyline
                val bmp = dotBitmap(if (colorIdx == 0) Color.rgb(46, 158, 91) else colorIdx, 16)
                linkMarker = bmap.addOverlay(
                    MarkerOptions()
                        .position(toBd09LatLng(sel.lat, sel.lon))
                        .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                        .anchor(0.5f, 0.5f)
                        .zIndex(7)
                ) as Marker
            } else {
                linkPolyline?.setPoints(pts)
                linkMarker?.setPosition(toBd09LatLng(sel.lat, sel.lon))
            }
        } catch (e: Throwable) {
        }
    }

    /** 当前位置 Marker（自定义蓝色定位图标，setPosition 复用） */
    fun updateLocation(lat: Double, lon: Double, show: Boolean, rotate: Float) {
        val bmap = baiduMap
        try {
            if (!show || !isValid(lat, lon)) {
                locMarker?.let { if (!it.isRemoved) it.remove() }
                locMarker = null
                return
            }
            if (locMarker == null || locMarker!!.isRemoved) {
                locMarker = bmap.addOverlay(
                    MarkerOptions()
                        .position(LatLng(lat, lon))
                        .icon(BitmapDescriptorFactory.fromBitmap(locIcon()))
                        .anchor(0.5f, 0.5f)
                        .zIndex(999)
                ) as Marker
            } else {
                locMarker?.setPosition(LatLng(lat, lon))
            }
            locMarker?.setRotate(rotate)
        } catch (e: Throwable) {
        }
    }

    /* ============ Bitmap 工厂 ============ */

    /** 扇区 Bitmap：60° 扇形（azimuth 为中心）+ 半透明填充 + 中心圆点 + 名字描边文字。画布固定两档尺寸 */
    fun makeSectorBitmap(
        color: Int,
        selected: Boolean,
        showLabel: Boolean,
        name: String,
        azimuth: Double
    ): Bitmap {
        val az = (((azimuth % 360.0) + 360.0) % 360.0).toFloat()
        val cacheKey = "$color|$selected|$showLabel|$name|$az"
        sectorCache.get(cacheKey)?.let { if (!it.isRecycled) return it }

        val sizeDp = if (selected) 18f else 16f
        val radiusPx = sizeDp * density
        val total = if (name.isNotEmpty()) 56f else 40f
        val w = (total * density + 2 * radiusPx).toInt().coerceIn(1, 1024)
        val h = (total * density + 2 * radiusPx).toInt().coerceIn(1, 1024)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f

        // 扇区弧形：azimuth 中心 ±30°
        val rectF = RectF(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val fillColor = if (selected) (0x8F shl 24) or color else (0x38 shl 24) or color
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawArc(rectF, az - 30f, 60f, true, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = color
        canvas.drawArc(rectF, az - 30f, 60f, true, paint)

        // 中心圆点 + 白芯
        val dotR = (if (selected) 6f else 5f) * density
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx, cy, dotR, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 2f * density, paint)

        // 名字（白描边 + 彩色实心，字号自适应，长名 StaticLayout 3 行省略）
        if (name.isNotEmpty()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                isFakeBoldText = true
                this.color = color
                textAlign = Paint.Align.CENTER
                textSize = (if (selected) 13f else 12f) * density
            }
            val maxTextW = w - 2f * radiusPx - 4f * density
            var tries = 0
            while (tp.measureText(name) > maxTextW && tries < 8) {
                tp.textSize -= 1f * density
                tries++
            }
            val baseline = cy - (tp.descent() + tp.ascent()) / 2f
            val layout: StaticLayout? = try {
                StaticLayout.Builder.obtain(name, 0, name.length, tp, maxTextW.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(3)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } catch (e: Throwable) {
                null
            }
            if (layout != null) {
                val textH = layout.height.toFloat()
                canvas.save()
                canvas.translate(0f, cy - textH / 2f)
                // 白描边 4 向
                tp.style = Paint.Style.STROKE
                tp.strokeWidth = 2f * density
                tp.color = Color.WHITE
                for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) {
                    canvas.save()
                    canvas.translate(dx * density, dy * density)
                    drawLayoutCentered(canvas, layout, tp, cx)
                    canvas.restore()
                }
                tp.style = Paint.Style.FILL
                tp.color = color
                drawLayoutCentered(canvas, layout, tp, cx)
                canvas.restore()
            } else {
                tp.style = Paint.Style.STROKE
                tp.strokeWidth = 2f * density
                tp.color = Color.WHITE
                canvas.drawText(name, cx, baseline, tp)
                tp.style = Paint.Style.FILL
                tp.color = color
                canvas.drawText(name, cx, baseline, tp)
            }
        }
        sectorCache.put(cacheKey, bmp)
        return bmp
    }

    private fun drawLayoutCentered(canvas: Canvas, layout: StaticLayout, tp: TextPaint, cx: Float) {
        tp.textAlign = Paint.Align.LEFT
        val count = layout.lineCount
        for (i in 0 until count) {
            val lineWidth = layout.getLineWidth(i)
            canvas.drawText(
                layout.text.subSequence(layout.getLineStart(i), layout.getLineEnd(i)).toString(),
                cx - lineWidth / 2f,
                layout.getLineBaseline(i).toFloat(),
                tp
            )
        }
        tp.textAlign = Paint.Align.CENTER
    }

    /** 圆点 Marker Bitmap（LRU 256，2*size 画布 + 白描边） */
    fun dotBitmap(color: Int, size: Int): Bitmap {
        val key = (color.toLong() shl 20) or size.toLong()
        dotCache[key]?.let { if (!it.isRecycled) return it }
        val bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(size.toFloat(), size.toFloat(), size.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(size.toFloat(), size.toFloat(), size * 0.8f, paint)
        if (dotCache.size > 256) dotCache.clear()
        dotCache[key] = bmp
        return bmp
    }

    /** 当前位置图标：绿半透明外圈 + 蓝实心圆 + 白描边 + 白色三角箭头 */
    fun locIcon(): Bitmap {
        locIconBitmap?.let { if (!it.isRecycled) return it }
        val bmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(46, 46, 158, 91)
        canvas.drawCircle(30f, 30f, 30f, paint)
        paint.color = Color.rgb(30, 142, 230)
        canvas.drawCircle(30f, 30f, 25f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.WHITE
        canvas.drawCircle(30f, 30f, 25f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val path = Path().apply {
            moveTo(30f, 16f)
            lineTo(25f, 34f)
            lineTo(35f, 34f)
            close()
        }
        canvas.drawPath(path, paint)
        locIconBitmap = bmp
        return bmp
    }
}
