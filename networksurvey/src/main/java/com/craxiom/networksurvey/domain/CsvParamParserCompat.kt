package com.craxiom.networksurvey.domain

import com.craxiom.networksurvey.util.CsvParamParser

/**
 * 领域层对旧 CsvParamParser 的兼容门面。
 *
 * UI / ViewModel 通过本类访问列匹配能力，屏蔽 Java 静态方法的类型杂音，
 * 并把 Java 的 [CsvParamParser] 委托到 [CsvParsingEngine] 之后统一以 Kotlin 类型暴露。
 */
object CsvParamParserCompat {

    /** 按表头关键词自动匹配列映射（工参列索引 -> CSV 列索引）。 */
    fun autoMap(headers: List<String>): Map<Int, Int> =
        CsvParamParser.autoMap(headers).toMap()
}
