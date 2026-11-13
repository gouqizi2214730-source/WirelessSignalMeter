package com.craxiom.networksurvey.ui.cellular.baidumap

import android.content.res.Resources
import androidx.compose.runtime.withFrameNanos
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.model.MapPointRecord
import com.craxiom.networksurvey.util.CoordConverter
import com.craxiom.networksurvey.util.SignalGrade
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class BaiduMapArea(val mapView: TextureMapView) {
    val baiduMap: BaiduMap get() = mapView.map

    private data class SectorStyle(val color: Int, val selected: Boolean, val labeled: Boolean, val radius: Int)
    private data class SectorMarker(val marker: Marker, val style: SectorStyle)
    private val sectors = HashMap<Long, SectorMarker>()
    private val markerParams = HashMap<Marker, EngineeringParam>()

    fun paramOf(marker: Marker): EngineeringParam? = markerParams[marker]

    // 动态层
    private var linkOutline: Polyline? = null
    private var linkPolyline: Polyline? = null
    private var linkMarker: Marker? = null
    private var locMarker: Marker? = null

    private val pointMarkers = HashMap<Long, Marker>()
    private var pointMetric: String? = null

    companion object {
        const val SECTOR_ZOOM = 11f
        const val CLOSE_ZOOM = 17f
        private const val LABEL_ZOOM = 17f
        const val MAP_MIN_ZOOM = 4f

        private val sectorCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
            // 缓存上限 16MB：标签位图尺寸较大（含文字区域），按字节数限制而非张数。
            // 超限时 LruCache 自动淘汰最旧条目，被淘汰位图不回收（可能仍被 Marker 引用）。
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount
            }
        }
        // 非标签模式共享位图：同一运营商颜色 + 选中状态 → 同一张扇区位图（方位角只影响朝向指示线，
        // 而方位角通过 Marker 旋转实现，不再绘制在 Bitmap 内，故可共享）
        private val sectorCacheNoLabel = LruCache<String, Bitmap>(80)
        private val dotCache = LruCache<Long, Bitmap>(64)
        private var locIconBitmap: Bitmap? = null
        private val density: Float get() = Resources.getSystem().displayMetrics.density

        // ==================== 扇区配色方案 ====================
        // 运营商品牌色（替代旧 PCI mod 3 红绿蓝，更专业、更有辨识度）
        // 移动 #0066FF → 联通 #E60012 → 电信 #1FA974 → 广电 #7B4FE0 → 未知 #78909C
        const val COLOR_CMCC = 0xFF0066FF.toInt()   // 中国移动
        const val COLOR_CUCC = 0xFFE60012.toInt()   // 中国联通
        const val COLOR_CTCC = 0xFF1FA974.toInt()   // 中国电信
        const val COLOR_CBN = 0xFF7B4FE0.toInt()    // 中国广电
        const val COLOR_UNKNOWN = 0xFF78909C.toInt() // 未知运营商
        const val COLOR_NET_5G = 0xFF2E9E5B.toInt()   // 制式回退色：5G 绿
        const val COLOR_NET_4G = 0xFF1A73E8.toInt()   // 制式回退色：4G 蓝

        /** 根据 MCC+MNC 返回运营商品牌色 */
        fun operatorColor(mcc: Int, mnc: Int, netType: String?): Int {
            // 修复：CSV 未提供 MCC/MNC 列（mcc==0）时，不能判为未知运营商而统一灰色，
            // 回退为按网络制式着色（5G 绿 / 4G 蓝），与旧版 netTypeColor 行为一致。
            if (mcc == 0) return netTypeColor(netType)
            if (mcc != 460) return COLOR_UNKNOWN
            return when (mnc) {
                0, 2, 7, 8 -> COLOR_CMCC   // 中国移动
                1, 6, 9 -> COLOR_CUCC       // 中国联通
                3, 5, 11 -> COLOR_CTCC      // 中国电信
                15 -> COLOR_CBN             // 中国广电
                else -> COLOR_UNKNOWN
            }
        }

        /** 制式回退配色（MCC/MNC 缺失时）：5G 绿 / 4G 蓝 / 其他灰 */
        fun netTypeColor(netType: String?): Int = when {
            is5G(netType) -> COLOR_NET_5G
            is4G(netType) -> COLOR_NET_4G
            else -> COLOR_UNKNOWN
        }

        /** 根据工参的 netType 判断制式：5G / 4G / 其他 */
        fun netTypeLabel(netType: String?): String {
            if (netType == null) return "未知"
            val lower = netType.lowercase()
            return when {
                lower.contains("nr") || lower.contains("5g") -> "5G"
                lower.contains("lte") || lower.contains("4g") -> "4G"
                lower.contains("umts") || lower.contains("3g") -> "3G"
                lower.contains("gsm") || lower.contains("2g") -> "2G"
                else -> "未知"
            }
        }

        /** 判断是否 5G */
        fun is5G(netType: String?): Boolean {
            val lower = netType?.lowercase() ?: return false
            return lower.contains("nr") || lower.contains("5g")
        }

        /** 判断是否 4G */
        fun is4G(netType: String?): Boolean {
            val lower = netType?.lowercase() ?: return false
            return lower.contains("lte") || lower.contains("4g")
        }

        /** RSRP/SINR → 等级色 */
        fun gradeColor(value: Int, sinr: Boolean): Int {
            val level = if (sinr) SignalGrade.fromSinr(value) else SignalGrade.fromRsrp(value)
            return Color.parseColor(SignalGrade.hexOf(level))
        }

        fun isValid(lat: Double, lon: Double): Boolean =
            !lat.isNaN() && !lon.isNaN() && !lat.isInfinite() && !lon.isInfinite() &&
                (lat != 0.0 || lon != 0.0) &&
                lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun toBd09LatLng(lat: Double, lon: Double): LatLng {
        val bd = CoordConverter.wgs84ToBd09(lat, lon)
        return LatLng(bd[0], bd[1])
    }

    internal data class Viewport(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
        val zoom: Float,
    ) {
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)
        fun contains(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon

        fun distanceFromCenter(lat: Double, lon: Double): Double =
            kotlin.math.abs((lat - (minLat + maxLat) / 2) / latSpan) +
                kotlin.math.abs((lon - (minLon + maxLon) / 2) / lonSpan)
    }

    internal fun viewport(status: MapStatus?): Viewport? {
        val bound = status?.bound ?: return null
        val sw = CoordConverter.bd09ToWgs84(bound.southwest.latitude, bound.southwest.longitude)
        val ne = CoordConverter.bd09ToWgs84(bound.northeast.latitude, bound.northeast.longitude)
        return Viewport(min(sw[0], ne[0]), min(sw[1], ne[1]), max(sw[0], ne[0]), max(sw[1], ne[1]), status.zoom)
    }

    internal suspend fun renderSectors(
        params: List<EngineeringParam>,
        viewport: Viewport,
        selectedId: Long?,
        servingId: Long?,
        showLabels: Boolean,
    ) {
        val keep = params.mapTo(HashSet()) { it.id }
        val iterator = sectors.iterator()
        while (iterator.hasNext()) {
            val (id, entry) = iterator.next()
            if (id !in keep) {
                entry.marker.remove()
                markerParams.remove(entry.marker)
                iterator.remove()
            }
        }

        val zoom = viewport.zoom.toInt()
        val radius = (16f * 1.19f.pow((zoom - 15).toFloat())).toInt().coerceIn(8, 46)
        val changed = params.mapNotNull { param ->
            val selected = param.id == selectedId || param.id == servingId
            val labeled = showLabels && (selected || viewport.zoom >= LABEL_ZOOM)
            val style = SectorStyle(operatorColor(param.mcc, param.mnc, param.netType), selected, labeled, radius)
            (param to style).takeIf { sectors[param.id]?.style != style }
        }
        for (batch in changed.chunked(if (showLabels && viewport.zoom >= LABEL_ZOOM) 14 else 48)) {
            withFrameNanos { }
            val pending = ArrayList<Triple<EngineeringParam, SectorStyle, MarkerOptions>>()
            val bitmaps = withContext(Dispatchers.Default) {
                batch.map { (param, style) ->
                    makeSectorBitmap(style.color, style.selected, style.labeled, param.displayName, param.azimuth, style.radius)
                }
            }
            for ((index, entry) in batch.withIndex()) {
                val (param, style) = entry
                val icon = BitmapDescriptorFactory.fromBitmap(bitmaps[index])
                val angle = if (style.labeled || !param.azimuth.isFinite()) 0f
                else (((param.azimuth % 360.0) + 360.0) % 360.0).toFloat()
                val z = if (style.selected) 3 else 1
                val previous = sectors[param.id]
                if (previous != null) {
                    previous.marker.icon = icon
                    previous.marker.setRotate(angle)
                    previous.marker.zIndex = z
                    sectors[param.id] = SectorMarker(previous.marker, style)
                } else {
                    val option = MarkerOptions().position(toBd09LatLng(param.lat, param.lon))
                        .icon(icon).anchor(0.5f, 0.5f).rotate(angle).zIndex(z)
                    pending.add(Triple(param, style, option))
                }
            }
            if (pending.isEmpty()) continue
            val added = baiduMap.addOverlays(pending.map { it.third })
            pending.forEachIndexed { index, (param, style, _) ->
                val marker = added.getOrNull(index) as? Marker ?: return@forEachIndexed
                sectors[param.id] = SectorMarker(marker, style)
                markerParams[marker] = param
            }
        }
    }

    internal fun renderPoints(points: List<MapPointRecord>, metric: String) {
        val keep = points.mapTo(HashSet()) { it.id }
        val iterator = pointMarkers.iterator()
        while (iterator.hasNext()) {
            val (id, marker) = iterator.next()
            if (id !in keep || pointMetric != metric) {
                marker.remove()
                iterator.remove()
            }
        }

        pointMetric = metric
        val sinr = metric == "SINR"
        val options = points.filter { it.id !in pointMarkers }.mapNotNull { point ->
            val value = if (sinr) point.sinr else point.rsrp
            if (sinr && value !in -40..50 || !sinr && value !in -160..-20) return@mapNotNull null
            val position = toBd09LatLng(point.latitude, point.longitude)
            point.id to MarkerOptions().position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(dotBitmap(gradeColor(value, sinr), (5 * density).toInt())))
                .anchor(0.5f, 0.5f).zIndex(4)
        }
        for (batch in options.chunked(48)) {
            val added = baiduMap.addOverlays(batch.map { it.second })
            batch.forEachIndexed { index, (id, _) ->
                (added.getOrNull(index) as? Marker)?.let { pointMarkers[id] = it }
            }
        }
    }

    /* ============ 动态层：连线 + 位置 ============ */

    fun updateLink(
        selected: EngineeringParam?,
        curLat: Double,
        curLon: Double,
        show: Boolean
    ) {
        val bmap = baiduMap
        try {
            val showLink = show && selected != null && isValid(selected.lat, selected.lon) &&
                isValid(curLat, curLon)
            if (!showLink) {
                linkOutline?.let { if (!it.isRemoved) it.remove() }
                linkOutline = null
                linkPolyline?.let { if (!it.isRemoved) it.remove() }
                linkPolyline = null
                linkMarker?.let { if (!it.isRemoved) it.remove() }
                linkMarker = null
                return
            }
            val sel = selected
            val target = toBd09LatLng(sel.lat, sel.lon)
            val pts = listOf(LatLng(curLat, curLon), target)
            if (linkPolyline == null || linkPolyline!!.isRemoved) {
                val color = Color.rgb(0, 91, 211)
                linkOutline = bmap.addOverlay(
                    PolylineOptions().points(pts).color(Color.WHITE).width((6 * density).toInt()).zIndex(5)
                ) as Polyline
                linkPolyline = bmap.addOverlay(
                    PolylineOptions().points(pts).color(color).width((3.5f * density).toInt()).zIndex(6)
                ) as Polyline
                val bmp = dotBitmap(color, (5 * density).toInt())
                linkMarker = bmap.addOverlay(
                    MarkerOptions()
                        .position(target)
                        .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                        .anchor(0.5f, 0.5f)
                        .zIndex(7)
                ) as Marker
            } else {
                linkOutline?.setPoints(pts)
                linkPolyline?.setPoints(pts)
                linkMarker?.setPosition(target)
            }
        } catch (e: Throwable) {
        }
    }

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

    private fun makeSectorBitmap(
        color: Int,
        selected: Boolean,
        showLabel: Boolean,
        name: String,
        azimuth: Double,
        radius: Int,
    ): Bitmap {
        // 非标签模式：颜色+选中状态即可共享位图（方位角不画进 Bitmap，由 marker.setRotate 实现）
        if (!showLabel) {
            val shareKey = "$color|$selected|$radius"
            sectorCacheNoLabel.get(shareKey)?.let { return it }
            // 非标签模式固定 az=0 绘制，朝向由 marker.setRotate(azimuth) 控制
            val bmp = drawSectorBitmap(color, selected, showLabel, name, 0.0, radius)
            sectorCacheNoLabel.put(shareKey, bmp)
            return bmp
        }

        // 标签模式：继续用完整 key 缓存（含 name/azimuth）
        val az = if (azimuth.isFinite()) (((azimuth % 360.0) + 360.0) % 360.0).toFloat() else 0f
        val cacheKey = "$color|$selected|$name|$az|$radius"
        sectorCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        val bmp = drawSectorBitmap(color, selected, showLabel, name, azimuth, radius)
        sectorCache.put(cacheKey, bmp)
        return bmp
    }

    // 扇区中心与地图锚点对齐，标签沿扇区方位角偏移。
    private fun drawSectorBitmap(
        color: Int,
        selected: Boolean,
        showLabel: Boolean,
        name: String,
        azimuth: Double,
        radius: Int,
    ): Bitmap {
        val az = if (azimuth.isFinite()) (((azimuth % 360.0) + 360.0) % 360.0).toFloat() else 0f

        val radiusDp = if (selected) radius * 1.16f else radius.toFloat()
        val radiusPx = radiusDp * density
        val marginPx = 4f * density
        val gapPx = 6f * density

        // 先计算标签文字布局（文字块尺寸决定画布大小与偏移量）
        var textW = 0f
        var textH = 0f
        var textOffset = 0f   // 圆心 → 文字块中心距离（沿方位角方向）
        var tp: TextPaint? = null
        var layout: StaticLayout? = null
        if (name.isNotEmpty() && showLabel) {
            tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                isFakeBoldText = true
                this.color = color
                textAlign = Paint.Align.CENTER
                textSize = (if (selected) 20f else 18f) * density
            }
            val labelWidthDp = if (radius >= 26) 174f else 142f
            val maxTextW = (labelWidthDp * density).toInt().coerceAtLeast(1)
            layout = try {
                StaticLayout.Builder.obtain(name, 0, name.length, tp, maxTextW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } catch (e: Throwable) {
                null
            }
            if (layout != null) {
                textW = (0 until layout.lineCount).maxOfOrNull(layout::getLineWidth) ?: 0f
                textH = layout.height.toFloat()
            } else {
                textW = tp.measureText(name)
                textH = tp.descent() - tp.ascent()
            }
            // 文字块内缘紧贴扇区外沿（圆心 + 6dp 间隙）
            val radians = Math.toRadians(az.toDouble())
            textOffset = radiusPx + gapPx +
                (kotlin.math.abs(Math.sin(radians)) * textW + kotlin.math.abs(Math.cos(radians)) * textH).toFloat() / 2f
        }
        // 文字块中心相对圆心的偏移（Canvas 坐标系 y 向下，方位 0°=北=上，故 dy = -cos）
        val azRad = Math.toRadians(az.toDouble())
        val textDX = (textOffset * Math.sin(azRad)).toFloat()
        val textDY = (-textOffset * Math.cos(azRad)).toFloat()

        // 对称画布：半宽/半高取“扇区圆外沿”与“圆心到文字块最远角”的双向最大值，
        // 保证圆心恒在位图正中心（锚点恒 0.5/0.5），任何朝向都不裁剪
        val rExt = radiusPx + marginPx
        val halfW = max(rExt, kotlin.math.abs(textDX) + textW / 2f + marginPx)
        val halfH = max(rExt, kotlin.math.abs(textDY) + textH / 2f + marginPx)
        val w = (2f * halfW).toInt().coerceIn(1, 1024)
        val h = (2f * halfH).toInt().coerceIn(1, 1024)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f

        // 扇区弧形：azimuth 中心 ±30°（60° 张角）
        // Android Canvas drawArc 的 0° = 东（3 点钟），地理方位角 0° = 北（12 点钟），差 90°
        val canvasStart = az - 30f - 90f
        val rectF = RectF(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val fillAlpha = if (selected) 0xA6 else 0x54
        paint.style = Paint.Style.FILL
        paint.color = (fillAlpha shl 24) or (color and 0x00FFFFFF)
        canvas.drawArc(rectF, canvasStart, 60f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (selected) 2f else 1.25f) * density
        paint.color = color
        canvas.drawArc(rectF, canvasStart, 60f, true, paint)

        val dotR = (if (selected) 4f else 2.5f) * density
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx, cy, dotR, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, dotR * 0.4f, paint)

        // 白色描边可让小区名称在卫星底图和浅色底图上保持可读。
        if (name.isNotEmpty() && showLabel && tp != null) {
            if (layout != null) {
                canvas.save()
                canvas.translate(cx + textDX, cy + textDY - textH / 2f)
                tp.style = Paint.Style.STROKE
                tp.strokeWidth = 2.5f * density
                tp.color = Color.WHITE
                drawLayoutCentered(canvas, layout, tp, 0f)
                tp.style = Paint.Style.FILL
                tp.strokeWidth = 0f
                tp.color = Color.rgb(22, 28, 38)
                drawLayoutCentered(canvas, layout, tp, 0f)
                canvas.restore()
            } else {
                // 极少数 StaticLayout 失败兜底：单行绘制，同样双 pass
                val baseline = (cy + textDY) - (tp.ascent() + tp.descent()) / 2f
                tp.style = Paint.Style.STROKE
                tp.strokeWidth = 2.5f * density
                tp.color = Color.WHITE
                canvas.drawText(name, cx + textDX, baseline, tp)
                tp.style = Paint.Style.FILL
                tp.strokeWidth = 0f
                tp.color = Color.rgb(22, 28, 38)
                canvas.drawText(name, cx + textDX, baseline, tp)
            }
        }

        return bmp
    }

    private fun drawLayoutCentered(canvas: Canvas, layout: StaticLayout, tp: TextPaint, cx: Float) {
        tp.textAlign = Paint.Align.LEFT
        val count = layout.lineCount
        for (i in 0 until count) {
            val lineWidth = layout.getLineWidth(i)
            val start = layout.getLineStart(i)
            val ellipsis = layout.getEllipsisCount(i)
            val text = if (ellipsis == 0) {
                layout.text.subSequence(start, layout.getLineEnd(i)).toString()
            } else {
                layout.text.subSequence(start, start + layout.getEllipsisStart(i)).toString() + "…"
            }
            canvas.drawText(text, cx - minOf(lineWidth, layout.width.toFloat()) / 2f,
                layout.getLineBaseline(i).toFloat(), tp)
        }
        tp.textAlign = Paint.Align.CENTER
    }

    private fun dotBitmap(color: Int, size: Int): Bitmap {
        val key = (color.toLong() shl 20) or size.toLong()
        dotCache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(size.toFloat(), size.toFloat(), size.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(size.toFloat(), size.toFloat(), size * 0.8f, paint)
        dotCache.put(key, bmp)
        return bmp
    }

    private fun locIcon(): Bitmap {
        locIconBitmap?.let { if (!it.isRecycled) return it }
        val bmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(46, 0, 102, 255)
        canvas.drawCircle(30f, 30f, 30f, paint)
        paint.color = Color.rgb(0, 102, 255)
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
