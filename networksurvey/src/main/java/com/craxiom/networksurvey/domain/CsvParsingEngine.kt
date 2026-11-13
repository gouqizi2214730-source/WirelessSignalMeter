package com.craxiom.networksurvey.domain

import java.io.InputStream
import com.craxiom.networksurvey.model.EngineeringParam
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * CSV 工参解析引擎（领域层）。
 *
 * 从 [CsvParamParser] 抽取的纯 Kotlin 逻辑，不依赖任何 Android 类，可在 JVM 上直接单元测试。
 * 职责边界：
 *  - 字节读取与编码探测（UTF-8 BOM / UTF-16LE/BE / 回退 GBK）
 *  - 分隔符探测（, ; Tab 中文逗号）
 *  - 表头 + 首条非空数据行示例抽取
 *  - 按「用户列映射」逐行构造 [EngineeringParam]，并校验必填字段
 *
 * 对外行为与旧 [CsvParamParser] 完全一致（字段 enum/常量、跳过逻辑、isValid 规则），
 * 仅内部实现更干净、可单测、异常可传播。
 *
 * @see com.craxiom.networksurvey.util.CsvParamParser 兼容的对外入口
 */
object CsvParsingEngine {

    /** 工参字段列索引（与旧 CsvParamParser 的 COL_* 常量严格对齐）。 */
    object Col {
        const val CITY = 0
        const val SITE = 1
        const val CELL = 2
        const val CI = 3
        const val GNB_ID = 4
        const val SECTOR_ID = 5
        const val LAC = 6
        const val TAC = 7
        const val MCC = 8
        const val MNC = 9
        const val EARFCN = 10
        const val PCI = 11
        const val LON = 12
        const val LAT = 13
        const val AZIMUTH = 14
        const val TILT = 15
        const val BAND = 16
        const val NETTYPE = 17
        const val SITETYPE = 18
        const val REMARK = 19
    }

    /** 需要被调用方引用的字段总数（供 UI 计算列下拉）。 */
    const val FIELD_COUNT = 20

    data class ParseResult(
        val params: List<EngineeringParam> = emptyList(),
        val totalLines: Int = 0,
        val skippedLines: Int = 0,
        val message: String = ""
    )

    data class Preview(
        val headers: List<String> = emptyList(),
        val samples: List<String> = emptyList()
    )

    /**
     * 读取全部字节。
     */
    private fun readAllBytes(inputStream: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = inputStream.read(buf)
            if (n < 0) break
            if (n > 0) baos.write(buf, 0, n)
        }
        return baos.toByteArray()
    }

    /** 编码探测：BOM 优先，否则 UTF-8（内容含替换字符 \uFFFD 时回退 GBK）。 */
    private fun detectEncoding(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) return "UTF-8"
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xFE) return "UTF-16LE"
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) return "UTF-16BE"
        return "UTF-8"
    }

    /** 取表头行（首个换行前），按需回退 GBK。 */
    private fun resolveHeaderLine(bytes: ByteArray): Pair<String, String> {
        var encoding = detectEncoding(bytes)
        var content = String(bytes, Charset.forName(encoding))
        var nl = content.indexOfAny(charArrayOf('\n', '\r'))
        var headerLine = if (nl >= 0) content.substring(0, nl).removePrefix("\uFEFF").trim() else content.removePrefix("\uFEFF").trim()
        if (content.contains("\uFFFD")) {
            try {
                content = String(bytes, Charset.forName("GBK"))
                nl = content.indexOfAny(charArrayOf('\n', '\r'))
                headerLine = if (nl >= 0) content.substring(0, nl).removePrefix("\uFEFF").trim() else content.removePrefix("\uFEFF").trim()
                encoding = "GBK"
            } catch (_: Exception) {
                // 保持 UTF-8 原值
            }
        }
        return encoding to headerLine
    }

    /** 分隔符探测。 */
    private fun detectDelimiter(headerLine: String): Char {
        var comma = 0
        var semicolon = 0
        var tab = 0
        var cnComma = 0
        for (c in headerLine) {
            when (c) {
                ',' -> comma++
                ';' -> semicolon++
                '\t' -> tab++
                '，' -> cnComma++
            }
        }
        val max = maxOf(comma, semicolon, tab, cnComma)
        if (max == 0) return ','
        if (max == semicolon) return ';'
        if (max == tab) return '\t'
        if (max == cnComma) return '，'
        return ','
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index++]
            when {
                char == '"' && quoted && line.getOrNull(index) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    fields.add(field.toString())
                    field.clear()
                }
                else -> field.append(char)
            }
        }
        if (quoted) return emptyList()
        fields.add(field.toString())
        return fields
    }

    /** 按行切分（兼容 \r?\n）。 */
    private fun splitLines(text: String): List<String> =
        text.lines()

    /**
     * 读取表头 + 每列首条非空数据行的示例值，供「手动选列」对话框展示。
     */
    fun readPreview(inputStream: InputStream): Preview {
        val bytes = readAllBytes(inputStream)
        if (bytes.isEmpty()) return Preview()
        val (encoding, headerLine) = resolveHeaderLine(bytes)
        if (headerLine.isEmpty()) return Preview()
        val delimiter = detectDelimiter(headerLine)
        val headers = splitLine(headerLine, delimiter).map { it.trim() }
        val hdrList = ArrayList<String>(headers)
        val content = String(bytes, Charset.forName(encoding))
        val nl = content.indexOfAny(charArrayOf('\n', '\r'))
        if (nl < 0) return Preview(headers = hdrList)
        val rest = content.substring(nl + 1)
        var dataLine: String? = null
        for (line in splitLines(rest)) {
            if (line.isNotBlank()) {
                dataLine = line
                break
            }
        }
        val sampleList = ArrayList<String>()
        if (dataLine != null) {
            val dataCells = splitLine(dataLine, delimiter)
            for (i in hdrList.indices) {
                sampleList.add(if (i < dataCells.size) dataCells[i].trim() else "")
            }
        }
        return Preview(headers = hdrList, samples = sampleList)
    }

    /**
     * 按表头关键词自动匹配列映射（辅助，仅供快速参考）。
     * 返回 工参列索引 -> CSV 列索引。
     */
    fun autoMap(headers: List<String>): Map<Int, Int> {
        val map = linkedMapOf<Int, Int>()
        for (c in headers.indices) {
            val h = normalize(headers[c])
            val col = matchColumn(h)
            if (col >= 0 && !map.containsKey(col)) map[col] = c
        }
        return map
    }

    /**
     * 用户显式列映射解析：columnMap = 工参列索引 -> CSV 列索引。
     */
    @JvmOverloads
    fun parse(inputStream: InputStream, columnMap: Map<Int, Int>, requireAllFields: Boolean = false): ParseResult =
        parseBytes(readAllBytes(inputStream), columnMap, requireAllFields)

    private fun parseBytes(bytes: ByteArray, columnMap: Map<Int, Int>?, requireAllFields: Boolean): ParseResult {
        if (bytes.isEmpty()) return ParseResult(message = "文件为空")
        val (encoding, headerLine) = resolveHeaderLine(bytes)
        if (headerLine.isEmpty()) return ParseResult(message = "文件为空")
        val delimiter = detectDelimiter(headerLine)
        val headers = splitLine(headerLine, delimiter)
        val fullContent = String(bytes, Charset.forName(encoding))
        val lines = splitLines(fullContent)
        // 第 0 行是表头，跳过；从第 1 行开始解析数据
        val dataStart = if (lines.isNotEmpty()) 1 else 0
        val idx = IntArray(FIELD_COUNT) { -1 }
        val mapping = columnMap ?: autoMap(headers)
        for ((paramCol, csvCol) in mapping) {
            if (paramCol in 0 until FIELD_COUNT && csvCol in headers.indices) idx[paramCol] = csvCol
        }
        if (requireAllFields) {
            val required = ImportFieldSpec.FIELDS.map { idx[it.col] }
            val missing = ImportFieldSpec.missingRequired(mapping.filterValues { it in headers.indices })
            if (missing.isNotEmpty()) return ParseResult(message = "缺少必填字段 " + missing.joinToString("、"))
            if (required.distinct().size != required.size) return ParseResult(message = "每个字段需要对应不同的 CSV 列")
        }
        val out = ArrayList<EngineeringParam>()
        var total = 0
        var skipped = 0
        for (i in dataStart until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            total++
            val fields = splitLine(line, delimiter)
            if (requireAllFields) {
                val param = parseRequiredFields(fields, idx)
                if (param != null) out.add(param) else skipped++
                continue
            }
            val p = EngineeringParam()
            for (fc in 0 until FIELD_COUNT) {
                val csvIdx = idx[fc]
                if (csvIdx < 0 || csvIdx >= fields.size) continue
                val v = fields[csvIdx].trim()
                applyField(p, fc, v)
            }
            // 补齐「完整小区标识」与「基站ID+小区ID」互为缺口的一侧，
            // 必须在 isValidParam 之前：若 CSV 只给了完整 ECI/NCI，需先反推出基站ID 才不会被判为无效行。
            normalizeCellIdentity(p)
            if (isValidParam(p)) out.add(p) else skipped++
        }
        return ParseResult(
            params = out,
            totalLines = total,
            skippedLines = skipped,
            message = if (out.isEmpty()) "没有可导入的记录，已跳过 $skipped 行。请检查必填字段、制式和数值" else "成功解析 ${out.size} 条工参"
        )
    }

    /**
     * 自动匹配解析：先按表头关键词 autoMap，再按映射解析。
     * 等价旧 CsvParamParser.parse(InputStream) 的「自动匹配」语义。
     */
    fun parseAuto(inputStream: InputStream): ParseResult = parseBytes(readAllBytes(inputStream), null, false)

    private fun parseRequiredFields(fields: List<String>, indices: IntArray): EngineeringParam? {
        fun value(col: Int) = fields.getOrNull(indices[col])?.trim().orEmpty()
        fun whole(col: Int) = value(col).toIntOrNull() ?: value(col).toDoubleOrNull()?.let {
            it.toInt().takeIf { integer -> integer.toDouble() == it }
        }
        val net = value(Col.NETTYPE).uppercase(java.util.Locale.ROOT)
        val network = when {
            net.contains("NR") || net.contains("5G") -> "NR"
            net.contains("LTE") || net.contains("4G") -> "LTE"
            else -> return null
        }
        val station = value(Col.SITE).takeIf { it.isNotEmpty() } ?: return null
        val cell = value(Col.CELL).takeIf { it.isNotEmpty() } ?: return null
        val divisor = if (network == "NR") 4096 else 256
        val baseId = whole(Col.GNB_ID)?.takeIf { it > 0 } ?: return null
        val localId = whole(Col.SECTOR_ID)?.takeIf { it in 0 until divisor } ?: return null
        val pci = whole(Col.PCI)?.takeIf { it >= 0 } ?: return null
        val channel = whole(Col.EARFCN)?.takeIf { it >= 0 } ?: return null
        val longitude = value(Col.LON).toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
        val latitude = value(Col.LAT).toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
        if (longitude == 0.0 && latitude == 0.0) return null
        val bearing = value(Col.AZIMUTH).toDoubleOrNull()?.takeIf { it in 0.0..360.0 } ?: return null
        val downtilt = value(Col.TILT).toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return EngineeringParam().apply {
            netType = network
            siteName = station
            cellName = cell
            gnbId = baseId
            sectorId = localId
            ci = baseId.toLong() * divisor + localId
            this.pci = pci
            earfcn = channel
            lon = longitude
            lat = latitude
            azimuth = bearing
            tilt = downtilt
        }
    }

    /** 关键词匹配：表头 -> 工参列索引（负值=未匹配）。 */
    private fun matchColumn(h: String): Int {
        if (h.isEmpty()) return -1
        return when {
            containsAny(h, "城市", "city") -> Col.CITY
            containsAny(h, "基站id", "基站标识", "gnb", "enb", "nodeid") -> Col.GNB_ID
            containsAny(h, "站名", "站点", "site", "基站名") -> Col.SITE
            containsAny(h, "pci") -> Col.PCI
            containsAny(h, "lac") -> Col.LAC
            containsAny(h, "tac") -> Col.TAC
            containsAny(h, "mcc") -> Col.MCC
            containsAny(h, "mnc") -> Col.MNC
            containsAny(h, "cgi") -> Col.CI
            h in setOf("ci", "eci", "nci") || containsAny(h, "完整小区标识", "eci/nci") -> Col.CI
            // 现网工参表里的「小区ID / 小区标识 / CellId」指的是站内小区编号（即扇区号），
            // 并非完整 ECI/NCI。完整值需按制式合成：
            //   4G LTE：ECI = 基站ID × 256  + 小区ID
            //   5G NR ：NCI = 基站ID × 4096 + 小区ID
            // 若误当作完整 CI 存入，会导致 sector_id 恒为 0、ci 存成 1/2/3 这类小区编号，
            // 无论按哪种方式查询都永远匹配不上。
            containsAny(h, "小区id", "cellid", "小区标识", "扇区号", "sectorid", "sector") -> Col.SECTOR_ID
            containsAny(h, "小区名", "cell", "小区") -> Col.CELL
            containsAny(h, "频点", "earfcn", "arfcn", "频点号") -> Col.EARFCN
            containsAny(h, "经度", "lon", "lng", "long") -> Col.LON
            containsAny(h, "纬度", "lat") -> Col.LAT
            containsAny(h, "方位角", "azimuth") -> Col.AZIMUTH
            containsAny(h, "下倾角", "tilt") -> Col.TILT
            containsAny(h, "频段", "band") -> Col.BAND
            containsAny(h, "网络", "制式", "nettype") -> Col.NETTYPE
            containsAny(h, "站型") -> Col.SITETYPE
            containsAny(h, "备注", "remark", "note") -> Col.REMARK
            else -> -1
        }
    }

    private fun applyField(p: EngineeringParam, col: Int, v: String) {
        when (col) {
            Col.CITY -> p.city = v
            Col.SITE -> p.siteName = v
            Col.CELL -> p.cellName = v
            Col.CI -> p.ci = parseLongSafe(v)
            Col.GNB_ID -> p.gnbId = parseIntSafe(v)
            Col.SECTOR_ID -> p.sectorId = parseIntSafe(v)
            Col.LAC -> p.lac = parseIntSafe(v)
            Col.TAC -> p.tac = parseIntSafe(v)
            Col.MCC -> p.mcc = parseIntSafe(v)
            Col.MNC -> p.mnc = parseIntSafe(v)
            Col.EARFCN -> p.earfcn = parseIntSafe(v)
            Col.PCI -> p.pci = parseIntSafe(v)
            Col.LON -> p.lon = parseDoubleSafe(v)
            Col.LAT -> p.lat = parseDoubleSafe(v)
            Col.AZIMUTH -> p.azimuth = parseDoubleSafe(v)
            Col.TILT -> p.tilt = parseDoubleSafe(v)
            Col.BAND -> p.band = v
            Col.NETTYPE -> p.netType = v
            Col.SITETYPE -> p.siteType = v
            Col.REMARK -> p.remark = v
        }
    }

    /** 必填校验：小区名 / 经纬度非0 / 频点非0 / 基站ID非0。 */
    private fun isValidParam(p: EngineeringParam): Boolean {
        if (p.cellName == null || p.cellName.trim().isEmpty()) return false
        if (p.lat == 0.0 || p.lon == 0.0) return false
        if (p.earfcn == 0) return false
        return p.gnbId != 0
    }

    private fun normalize(s: String?): String = (s ?: "").trim().lowercase()

    private fun containsAny(s: String, vararg keys: String): Boolean {
        for (k in keys) if (s.contains(k)) return true
        return false
    }

    private fun parseIntSafe(v: String?): Int {
        if (v.isNullOrEmpty()) return 0
        val sb = StringBuilder()
        var started = false
        for (c in v) {
            if (c.isDigit()) {
                sb.append(c)
                started = true
            } else if (started) {
                break
            }
        }
        return if (sb.isNotEmpty()) sb.toString().toIntOrNull() ?: 0 else 0
    }

    /**
     * 小区标识规范化：在「完整小区标识（ECI/NCI）」与「基站ID + 小区ID」之间互推补齐。
     *
     * 现网工参表的换算约定：
     *   4G LTE：ECI = 基站ID × 256  + 小区ID
     *   5G NR ：NCI = 基站ID × 4096 + 小区ID
     *
     * 导入的 CSV 通常只提供其中一种形式，这里补齐另一种，
     * 使 matchByCi(完整值) 与 matchByGnbIdSectorId(基站ID, 小区ID) 两条查询路径都能命中。
     */
    private fun normalizeCellIdentity(p: EngineeringParam) {
        val mult = if (isNrNetType(p.netType)) 4096L else 256L
        if (p.ci != 0L && (p.gnbId == 0 || p.sectorId == 0)) {
            // 只有完整值：反推基站ID 与小区ID
            if (p.gnbId == 0) p.gnbId = (p.ci / mult).toInt()
            if (p.sectorId == 0) p.sectorId = (p.ci % mult).toInt()
        } else if (p.ci == 0L && p.gnbId != 0) {
            // 只有基站ID（+小区ID）：合成完整值
            p.ci = p.gnbId.toLong() * mult + p.sectorId.toLong()
        }
        // 修复：表头「CI」若存的是站内小区编号（值小于进制 mult，如 1/2/3）而非
        // 完整 ECI/NCI，则 matchByCi 永远查不到。完整值 = 基站ID×mult+小区ID
        // 必然 ≥ mult，故 ci < mult 可断定为误入的扇区号，按约定补合成完整值。
        if (p.ci in 1 until mult && p.gnbId != 0) {
            p.ci = p.gnbId.toLong() * mult + p.sectorId.toLong()
        }
    }

    /** 依据制式列判断是否为 5G（NR）。无法判定时按 4G 的 256 处理。 */
    private fun isNrNetType(netType: String?): Boolean {
        val n = normalize(netType)
        if (n.isEmpty()) return false
        return n.contains("nr") || n.contains("5g")
    }

    /** 与 parseIntSafe 同规则，但按 long 解析：ECI/NCI 可能超出 int 范围。 */
    private fun parseLongSafe(v: String?): Long {
        if (v.isNullOrEmpty()) return 0L
        val sb = StringBuilder()
        var started = false
        for (c in v) {
            if (c.isDigit()) {
                sb.append(c)
                started = true
            } else if (started) {
                break
            }
        }
        return if (sb.isNotEmpty()) sb.toString().toLongOrNull() ?: 0L else 0L
    }

    private fun parseDoubleSafe(v: String?): Double {
        if (v.isNullOrEmpty()) return 0.0
        return v.trim().toDoubleOrNull() ?: 0.0
    }
}
