/*
 * 重构版 CsvParamParser：对外静态签名保持完全兼容，
 * 内部委托给领域层纯 Kotlin 引擎 com.craxiom.networksurvey.domain.CsvParsingEngine。
 *
 * 变更要点：
 *  - parse(InputStream) / parse(InputStream, Map) / readHeaders / readPreview / autoMap
 *    的行为与旧版一致（编码探测、分隔符、列匹配、必填校验、跳过逻辑）。
 *  - 逻辑主体移至可单测的 Kotlin 领域层，本类只做薄委托，不承载 Android 逻辑。
 */
package com.craxiom.networksurvey.util;

import com.craxiom.networksurvey.domain.CsvParsingEngine;
import com.craxiom.networksurvey.model.EngineeringParam;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class CsvParamParser {

    /** 兼容旧版：按表头关键词自动匹配解析。 */
    public static ParseResult parse(InputStream is) throws IOException {
        CsvParsingEngine.ParseResult r = CsvParsingEngine.INSTANCE.parseAuto(is);
        return toCompatResult(r);
    }

    /** 兼容旧版：使用用户指定的列映射解析。 */
    public static ParseResult parse(InputStream is, Map<Integer, Integer> columnMap) throws IOException {
        CsvParsingEngine.ParseResult r = CsvParsingEngine.INSTANCE.parse(is, columnMap);
        return toCompatResult(r);
    }

    /** 读取表头（供导入 UI 下拉框选择列）。 */
    public static List<String> readHeaders(InputStream is) throws IOException {
        return CsvParsingEngine.INSTANCE.readPreview(is).getHeaders();
    }

    /** 读取表头 + 每列第一条非空数据行示例值。 */
    public static CsvParsingEngine.Preview readPreview(InputStream is) throws IOException {
        return CsvParsingEngine.INSTANCE.readPreview(is);
    }

    /** 按表头关键词自动匹配列映射（辅助，仅供快速参考）。 */
    public static Map<Integer, Integer> autoMap(List<String> headers) {
        return CsvParsingEngine.INSTANCE.autoMap(headers);
    }

    /** 兼容旧版 ParseResult 结构（保持旧调用方无感）。 */
    public static class ParseResult {
        public List<EngineeringParam> params = new java.util.ArrayList<>();
        public int totalLines = 0;
        public int skippedLines = 0;
        public String message = "";
    }

    private static ParseResult toCompatResult(CsvParsingEngine.ParseResult r) {
        ParseResult compat = new ParseResult();
        compat.params = r.getParams();
        compat.totalLines = r.getTotalLines();
        compat.skippedLines = r.getSkippedLines();
        compat.message = r.getMessage();
        return compat;
    }
}
