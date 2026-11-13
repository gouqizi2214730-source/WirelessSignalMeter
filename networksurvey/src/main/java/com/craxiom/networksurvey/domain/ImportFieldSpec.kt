package com.craxiom.networksurvey.domain

/**
 * 工参字段规格（领域层常量）。
 *
 * 定义一个工参需要哪些字段、哪些为必填、其「工参列索引」和显示名。
 * 由 UI 的列映射对话框与导入逻辑共用，保证「字段定义」只有一份真源。
 *
 * [col] 即工参列索引（与 [CsvParsingEngine.Col] 对齐）。[required] 表示该字段必填，
 * 未映射必填字段时导入应提示用户。
 *
 * @see com.craxiom.networksurvey.domain.CsvParsingEngine 字段索引常量
 * @see com.craxiom.networksurvey.ui.engparam 列映射对话框以此为唯一真源
 */
object ImportFieldSpec {

    data class FieldDef(val col: Int, val label: String, val required: Boolean)

    /** 导入所需的全部工参字段（顺序即列映射对话框展示顺序）。 */
    val FIELDS: List<FieldDef> = listOf(
        FieldDef(CsvParsingEngine.Col.NETTYPE, "网络制式", true),
        FieldDef(CsvParsingEngine.Col.SITE, "基站名称", true),
        FieldDef(CsvParsingEngine.Col.CELL, "小区名称", true),
        FieldDef(CsvParsingEngine.Col.GNB_ID, "基站ID", true),
        FieldDef(CsvParsingEngine.Col.SECTOR_ID, "小区ID", true),
        FieldDef(CsvParsingEngine.Col.PCI, "PCI", true),
        FieldDef(CsvParsingEngine.Col.EARFCN, "频点", true),
        FieldDef(CsvParsingEngine.Col.LON, "经度", true),
        FieldDef(CsvParsingEngine.Col.LAT, "纬度", true),
        FieldDef(CsvParsingEngine.Col.AZIMUTH, "方位角", true),
        FieldDef(CsvParsingEngine.Col.TILT, "下倾角", true)
    )

    /** 根据字段定义构建「工参列索引 -> 显示名」的映射，供 UI 使用。 */
    fun labelOf(col: Int): String = FIELDS.firstOrNull { it.col == col }?.label ?: ""

    /** 判断某个工参列是否是必填字段。 */
    fun isRequired(col: Int): Boolean = FIELDS.firstOrNull { it.col == col }?.required ?: false

    /**
     * 校验用户列映射：检查必填字段是否都已映射。
     * @return 缺失的必填字段显示名列表；空列表表示全部就绪。
     */
    fun missingRequired(selection: Map<Int, Int>): List<String> {
        val missing = ArrayList<String>()
        for (f in FIELDS) {
            if (f.required && (selection[f.col] ?: -1) < 0) {
                missing.add(f.label)
            }
        }
        return missing
    }
}
