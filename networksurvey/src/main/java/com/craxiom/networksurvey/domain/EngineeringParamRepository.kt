package com.craxiom.networksurvey.domain

import android.content.Context
import android.net.Uri
import com.craxiom.networksurvey.db.EngineeringParamDbHelper
import com.craxiom.networksurvey.model.EngineeringParam
import java.io.InputStream

/**
 * 工参数据仓库（数据层）。
 *
 * 是 UI/ViewModel 访问工参数据的唯一入口，屏蔽 [EngineeringParamDbHelper] 内部细节。
 * 方法均在调用方协程的 IO 上下文中执行；本层**不吞异常**，解析/入库失败会原样上抛，
 * 由 ViewModel 记录日志并转为用户可读提示。
 */
class EngineeringParamRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val dbHelper: EngineeringParamDbHelper =
        EngineeringParamDbHelper.getInstance(appContext)

    /**
     * 读取 CSV 表头 + 示例值（用于列映射对话框）。
     * @param uri 用户通过 SAF 选择的文件 URI
     * @return 预览（表头 + 每列示例值）
     */
    fun readPreview(uri: Uri): CsvParsingEngine.Preview {
        val stream: InputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
        return stream.use { CsvParsingEngine.readPreview(it) }
    }

    /**
     * 解析 CSV 并入库。
     * @param uri 用户选择的文件 URI
     * @param mapping 用户列映射（工参列索引 -> CSV 列索引）
     * @return 解析结果（含命中条数 / 跳过条数 / 提示信息）
     */
    fun parseAndInsert(uri: Uri, mapping: Map<Int, Int>): CsvParsingEngine.ParseResult {
        val stream: InputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("文件已失效")
        val result = stream.use { CsvParsingEngine.parse(it, mapping, requireAllFields = true) }
        if (result.params.isNotEmpty()) {
            dbHelper.insertAll(result.params)
        }
        return result
    }

    /** 读取全部工参（用于列表刷新）。 */
    fun getAll(): List<EngineeringParam> = dbHelper.getAll()

    /** 按 id 删除单条工参。 */
    fun deleteById(id: Long) = dbHelper.deleteById(id)

    /** 清空全部工参。 */
    fun clearAll() = dbHelper.clearAll()

    /** 当前工参条数。 */
    fun getCount(): Int = dbHelper.getCount()
}
