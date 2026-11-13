package com.craxiom.networksurvey.ui.cellular.baidumap

import androidx.compose.runtime.withFrameNanos
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.location.Location
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.util.TypedValue
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class BaiduMapArea(val mapView: TextureMapView) {
    val baiduMap: BaiduMap get() = mapView.map

    private data class SectorStyle(
        val color: Int,
        val selected: Boolean,
        val radius: Int,
    )
    private data class SectorMarker(val marker: Marker, val style: SectorStyle)
    private data class LabelKey(val name: String, val selected: Boolean)
    private data class SectorLabel(
        val key: LabelKey,
        val latitude: Double,
        val longitude: Double,
        val xOffset: Int,
        val yOffset: Int,
    ) {
        fun position() = LatLng(latitude, longitude)
    }
    private data class LabelMarker(val marker: Marker, val label: SectorLabel)
    private data class SectorIconKey(val style: SectorStyle, val bearing: Float)
    private val labelBitmaps = object : LruCache<LabelKey, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: LabelKey, value: Bitmap) = value.byteCount
    }
    private val density = mapView.resources.displayMetrics.density
    private val metrics = mapView.resources.displayMetrics
    private val labelTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, metrics)
    private val sectorIcons = object : LruCache<SectorIconKey, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: SectorIconKey, value: Bitmap) = value.byteCount
    }
    private val pointIcons = HashMap<Int, BitmapDescriptor>()
    private val sectors = HashMap<Long, SectorMarker>()
    private val sectorLabels = HashMap<Long, LabelMarker>()
    private val markerParams = HashMap<Marker, EngineeringParam>()

    fun paramOf(marker: Marker): EngineeringParam? = markerParams[marker]

    // 动态层
    private var linkOutline: Polyline? = null
    private var linkPolyline: Polyline? = null
    private var linkMarker: Marker? = null
    private var locMarker: Marker? = null
    private var lastLinkPoints: List<LatLng>? = null

    private val pointMarkers = HashMap<Long, Marker>()
    private var pointMetric: String? = null

    companion object {
        const val SECTOR_ZOOM = 11f
        const val CLOSE_ZOOM = 17f
        private const val LABEL_ZOOM = 17f
        private const val SAME_SITE_DISTANCE_METERS = 10f
        private const val SITE_LATITUDE_BUCKET = 0.0001
        const val MAP_MIN_ZOOM = 4f

        private val dotCache = LruCache<Long, Bitmap>(64)
        private var locIconBitmap: Bitmap? = null

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
        for (batch in sectors.keys.filter { it !in keep }.chunked(48)) {
            withFrameNanos { }
            for (id in batch) {
                val entry = sectors.remove(id) ?: continue
                entry.marker.remove()
                markerParams.remove(entry.marker)
            }
        }

        val zoom = viewport.zoom.toInt()
        val radius = (16f * 1.19f.pow((zoom - 15).toFloat())).toInt().coerceIn(8, 46)
        val changed = params.mapNotNull { param ->
            val selected = param.id == selectedId || param.id == servingId
            val style = SectorStyle(operatorColor(param.mcc, param.mnc, param.netType), selected, radius)
            (param to style).takeIf { sectors[param.id]?.style != style }
        }
        for (batch in changed.chunked(40)) {
            val bitmaps = withContext(Dispatchers.Default) {
                batch.map { (param, style) -> makeSectorIcon(style, param.azimuth) }
            }
            withFrameNanos { }
            val pending = ArrayList<Triple<EngineeringParam, SectorStyle, MarkerOptions>>()
            for ((index, entry) in batch.withIndex()) {
                val (param, style) = entry
                val bitmap = bitmaps[index]
                val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
                val z = if (style.selected) 3 else 1
                val previous = sectors[param.id]
                if (previous != null) {
                    previous.marker.icon = icon
                    previous.marker.zIndex = z
                    sectors[param.id] = SectorMarker(previous.marker, style)
                } else {
                    val option = MarkerOptions().position(toBd09LatLng(param.lat, param.lon))
                        .icon(icon).anchor(0.5f, 0.5f).zIndex(z)
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

        val showAllLabels = viewport.zoom >= LABEL_ZOOM
        val labels = if (showLabels && (showAllLabels || selectedId != null || servingId != null)) {
            withContext(Dispatchers.Default) {
                layoutSectorLabels(params, radius, showAllLabels, selectedId, servingId)
            }
        } else emptyList()
        renderSectorLabels(labels)
    }

    private fun layoutSectorLabels(
        params: List<EngineeringParam>,
        radius: Int,
        showAll: Boolean,
        selectedId: Long?,
        servingId: Long?,
    ): List<Pair<EngineeringParam, SectorLabel>> {
        val distance = FloatArray(1)
        val ordered = params.sortedWith(compareBy<EngineeringParam> { it.lat }.thenBy { it.lon }.thenBy { it.id })
        val groups = ordered.groupBy { bearingOf(it.azimuth) }
        val lineOverlap = ceil(3f * density).toInt() // Only the transparent bitmap padding can overlap.
        val clearance = (radius * 1.16f + 3f) * density

        return buildList {
            for ((bearing, sectorsInDirection) in groups) {
                val sitesByLatitude = mutableMapOf<Int, MutableList<MutableList<EngineeringParam>>>()
                // Match nearby coordinates within this direction, without projecting into the viewport.
                for (param in sectorsInDirection) {
                    val bucket = floor(param.lat / SITE_LATITUDE_BUCKET).toInt()
                    val current = sitesByLatitude.getOrPut(bucket) { mutableListOf() }
                    fun matches(site: List<EngineeringParam>): Boolean {
                        val anchor = site.first()
                        Location.distanceBetween(anchor.lat, anchor.lon, param.lat, param.lon, distance)
                        return distance[0] <= SAME_SITE_DISTANCE_METERS
                    }
                    val site = sitesByLatitude[bucket - 1]?.firstOrNull(::matches) ?: current.firstOrNull(::matches)
                    if (site == null) current.add(mutableListOf(param)) else site.add(param)
                }

                val radians = Math.toRadians(bearing.toDouble())
                val east = sin(radians).toFloat()
                val south = -cos(radians).toFloat()
                for (sites in sitesByLatitude.values) {
                    for (site in sites) {
                        val names = site.sortedWith(compareBy<EngineeringParam> { it.sectorId }.thenBy { it.id })
                            .mapNotNull { param ->
                                val selected = param.id == selectedId || param.id == servingId
                                val name = param.displayName
                                if ((showAll || selected) && name.isNotBlank()) param to LabelKey(name, selected) else null
                            }
                        if (names.isEmpty()) continue
                        val bitmaps = names.map { (_, key) -> labelBitmap(key) }
                        val width = bitmaps.maxOf { it.width }
                        val height = bitmaps.sumOf { it.height } - lineOverlap * (bitmaps.size - 1)
                        val offset = clearance + abs(east) * width / 2 + abs(south) * height / 2
                        val xOffset = (east * offset).roundToInt()
                        var yOffset = (south * offset - height / 2f).roundToInt()
                        val anchor = site.first()
                        val position = toBd09LatLng(anchor.lat, anchor.lon)
                        names.forEachIndexed { index, (param, key) ->
                            add(param to SectorLabel(key, position.latitude, position.longitude, xOffset, yOffset))
                            yOffset += bitmaps[index].height - lineOverlap
                        }
                    }
                }
            }
        }
    }

    private suspend fun renderSectorLabels(labels: List<Pair<EngineeringParam, SectorLabel>>) {
        val keep = labels.mapTo(HashSet()) { it.first.id }
        for (batch in sectorLabels.keys.filter { it !in keep }.chunked(48)) {
            withFrameNanos { }
            for (id in batch) {
                val entry = sectorLabels.remove(id) ?: continue
                entry.marker.remove()
                markerParams.remove(entry.marker)
            }
        }
        val changed = labels.filter { (param, label) -> sectorLabels[param.id]?.label != label }
        for (batch in changed.chunked(24)) {
            val needsIcon = batch.map { (param, label) -> sectorLabels[param.id]?.label?.key != label.key }
            val bitmaps = withContext(Dispatchers.Default) {
                batch.mapIndexed { index, (_, label) -> if (needsIcon[index]) labelBitmap(label.key) else null }
            }
            withFrameNanos { }
            val pending = ArrayList<Triple<EngineeringParam, SectorLabel, MarkerOptions>>()
            batch.forEachIndexed { index, (param, label) ->
                val previous = sectorLabels[param.id]
                val icon = bitmaps[index]?.let(BitmapDescriptorFactory::fromBitmap)
                val z = if (label.key.selected) 3 else 2
                if (previous != null) {
                    if (icon != null) previous.marker.icon = icon
                    if (previous.label.latitude != label.latitude || previous.label.longitude != label.longitude) {
                        previous.marker.position = label.position()
                    }
                    if (previous.label.xOffset != label.xOffset) previous.marker.xOffset = label.xOffset
                    if (previous.label.yOffset != label.yOffset) previous.marker.yOffset = label.yOffset
                    if (previous.label.key.selected != label.key.selected) previous.marker.zIndex = z
                    sectorLabels[param.id] = LabelMarker(previous.marker, label)
                } else if (icon != null) {
                    val option = MarkerOptions().position(label.position()).icon(icon)
                        .anchor(0.5f, 0f).xOffset(label.xOffset).yOffset(label.yOffset).zIndex(z)
                    pending.add(Triple(param, label, option))
                }
            }
            if (pending.isEmpty()) continue
            val added = baiduMap.addOverlays(pending.map { it.third })
            pending.forEachIndexed { index, (param, label, _) ->
                val marker = added.getOrNull(index) as? Marker ?: return@forEachIndexed
                sectorLabels[param.id] = LabelMarker(marker, label)
                markerParams[marker] = param
            }
        }
    }

    internal suspend fun renderPoints(points: List<MapPointRecord>, metric: String) {
        val keep = points.mapTo(HashSet()) { it.id }
        val reset = pointMetric != metric
        for (batch in pointMarkers.keys.filter { reset || it !in keep }.chunked(48)) {
            withFrameNanos { }
            batch.forEach { pointMarkers.remove(it)?.remove() }
        }
        pointMetric = metric
        val sinr = metric == "SINR"
        val additions = points.filter { it.id !in pointMarkers }
        for (batch in additions.chunked(48)) {
            val positions = withContext(Dispatchers.Default) {
                batch.map { toBd09LatLng(it.latitude, it.longitude) }
            }
            withFrameNanos { }
            val options = batch.mapIndexed { index, point ->
                val color = gradeColor(if (sinr) point.sinr else point.rsrp, sinr)
                val icon = pointIcons.getOrPut(color) {
                    BitmapDescriptorFactory.fromBitmap(dotBitmap(color, (5 * density).toInt()))
                }
                MarkerOptions().position(positions[index]).icon(icon).anchor(0.5f, 0.5f).zIndex(4)
            }
            val added = baiduMap.addOverlays(options)
            batch.forEachIndexed { index, point ->
                (added.getOrNull(index) as? Marker)?.let { pointMarkers[point.id] = it }
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
                lastLinkPoints = null
                linkPolyline?.let { if (!it.isRemoved) it.remove() }
                linkPolyline = null
                linkMarker?.let { if (!it.isRemoved) it.remove() }
                linkMarker = null
                return
            }
            val sel = selected
            val target = toBd09LatLng(sel.lat, sel.lon)
            val pts = listOf(LatLng(curLat, curLon), target)
            if (pts == lastLinkPoints) return
            if (linkPolyline == null || linkPolyline!!.isRemoved) {
                val color = Color.rgb(0, 91, 211)
                linkOutline = bmap.addOverlay(
                    PolylineOptions().points(pts).color(Color.WHITE).width((2.7f * density).toInt().coerceAtLeast(2)).zIndex(5)
                ) as Polyline
                linkPolyline = bmap.addOverlay(
                    PolylineOptions().points(pts).color(color).width((1.5f * density).toInt().coerceAtLeast(1)).zIndex(6)
                ) as Polyline
                val bmp = dotBitmap(color, (3.5f * density).toInt())
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
            lastLinkPoints = pts
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

    private fun labelBitmap(key: LabelKey): Bitmap {
        labelBitmaps.get(key)?.let { return it }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (key.selected) Color.rgb(16, 65, 135) else Color.rgb(22, 28, 38)
            textSize = labelTextSize
        }
        val width = (148f * density).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(key.name, 0, key.name.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val textWidth = (0 until layout.lineCount).maxOfOrNull(layout::getLineWidth) ?: 0f
        val padding = ceil(4f * density).toInt()
        val bitmapWidth = ceil(min(textWidth, width.toFloat())).toInt() + 2 * padding
        val bitmapHeight = layout.height + 2 * padding
        val glyphs = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val textCanvas = Canvas(glyphs)
        textCanvas.translate((bitmapWidth - width) / 2f, padding.toFloat())
        layout.draw(textCanvas)

        // Build the halo from the rendered glyphs so CJK fallback fonts get the same outline.
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        val outlineRadius = 2f * density
        for (direction in 0 until 8) {
            val angle = direction * Math.PI / 4
            canvas.drawBitmap(
                glyphs,
                cos(angle).toFloat() * outlineRadius,
                sin(angle).toFloat() * outlineRadius,
                outline,
            )
        }
        canvas.drawBitmap(glyphs, 0f, 0f, null)
        glyphs.recycle()
        labelBitmaps.put(key, bitmap)
        return bitmap
    }

    private fun bearingOf(azimuth: Double): Float =
        if (azimuth.isFinite()) ((azimuth % 360.0 + 360.0) % 360.0).toFloat() else 0f

    private fun makeSectorIcon(style: SectorStyle, azimuth: Double): Bitmap {
        val key = SectorIconKey(style, bearingOf(azimuth))
        return sectorIcons.get(key) ?: drawSectorIcon(key).also { sectorIcons.put(key, it) }
    }

    private fun drawSectorIcon(key: SectorIconKey): Bitmap {
        val (style, az) = key
        val (color, selected, radius) = style
        val radiusPx = (if (selected) radius * 1.16f else radius.toFloat()) * density
        val center = ceil(radiusPx + 5f * density)
        val size = (2 * center).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = center
        val cy = center

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

        return bmp
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
