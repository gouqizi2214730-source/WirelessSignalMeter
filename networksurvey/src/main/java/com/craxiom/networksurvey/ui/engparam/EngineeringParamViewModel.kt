package com.craxiom.networksurvey.ui.engparam

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.domain.CsvParsingEngine
import com.craxiom.networksurvey.domain.EngineeringParamRepository
import com.craxiom.networksurvey.domain.ImportFieldSpec
import com.craxiom.networksurvey.model.EngineeringParam
import com.craxiom.networksurvey.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 工参导入页的 ViewModel。
 *
 * 职责：持有页面 [UiState]，用协程编排「选文件 → 读表头/示例 → 用户列映射 →
 * 解析入库 → 刷新列表」。所有 IO 在 [Dispatchers.IO]，完成后回主线程更新状态。
 *
 * 关键设计：这里用 [viewModelScope]（进程级，不受页面重组影响，永不因离开组合被取消），
 * 彻底杜绝旧版 rememberCoroutineScope() 随页面重建被取消而抛
 * "The coroutine scope left the composition" 的问题。
 *
 * 异常策略：解析/入库失败**不吞**，记录带堆栈的日志，并转为 UiState.message 供用户可见。
 */
class EngineeringParamViewModel(
    private val context: Context,
    private val repository: EngineeringParamRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EngParam"
    }

    /** 页面 UI 状态（唯一被 Compose 观察的状态源）。 */
    data class UiState(
        val params: List<EngineeringParam> = emptyList(),
        val headers: List<String> = emptyList(),
        val samples: List<String> = emptyList(),
        val showMappingDialog: Boolean = false,
        val pendingUri: Uri? = null,
        val importing: Boolean = false,
        val message: String? = null,
        val showClearConfirm: Boolean = false,
        val pendingDeleteId: Long = -1L
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 用户选中文件后调用：读取表头 + 示例值，弹出列映射对话框。
     */
    fun onFileSelected(uri: Uri) {
        AppLogger.i(TAG, "选文件完成，开始读表头 uri=$uri")
        _uiState.value = _uiState.value.copy(pendingUri = uri, importing = true)
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    repository.readPreview(uri)
                }
                AppLogger.i(TAG, "读表头完成 headers=${preview.headers.size} 列")
                if (preview.headers.isEmpty()) {
                    AppLogger.w(TAG, "表头为空，无法读取文件")
                    _uiState.value = _uiState.value.copy(
                        importing = false,
                        pendingUri = null,
                        message = "无法读取文件"
                    )
                } else {
                    // 注意：pendingUri 保留（供确认映射后解析用），仅标记读取完成
                    _uiState.value = _uiState.value.copy(
                        headers = preview.headers,
                        samples = preview.samples,
                        showMappingDialog = true,
                        importing = false
                    )
                    AppLogger.i(TAG, "显示列映射对话框 ${preview.headers.joinToString(",")}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "读表头异常", e)
                _uiState.value = _uiState.value.copy(
                    importing = false,
                    pendingUri = null,
                    message = "导入失败：${e.message ?: "解析错误"}"
                )
            }
        }
    }

    /**
     * 用户确认列映射后调用：解析入库并刷新列表。
     * @param mapping 工参列索引 -> CSV 列索引
     */
    fun onConfirmMapping(mapping: Map<Int, Int>) {
        val uri = _uiState.value.pendingUri ?: return
        AppLogger.i(TAG, "用户点击导入，映射=$mapping")
        _uiState.value = _uiState.value.copy(showMappingDialog = false, importing = true)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.parseAndInsert(uri, mapping)
                }
                AppLogger.i(TAG, "导入结果: total=${result.totalLines} 命中=${result.params.size} 跳过=${result.skippedLines}")
                val msg = if (result.params.isNotEmpty()) {
                    "已导入 ${result.params.size} 条工参" +
                        if (result.skippedLines > 0) " · 跳过 " + result.skippedLines + " 行" else ""
                } else {
                    "导入失败：${result.message}"
                }
                AppLogger.i(TAG, msg)
                _uiState.value = _uiState.value.copy(
                    importing = false,
                    pendingUri = null,
                    message = msg,
                    params = withContext(Dispatchers.IO) { repository.getAll() }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "导入解析异常", e)
                _uiState.value = _uiState.value.copy(
                    importing = false,
                    message = "导入失败：${e.message ?: "解析错误"}"
                )
            }
        }
    }

    /** 取消列映射对话框。 */
    fun onDismissMapping() {
        _uiState.value = _uiState.value.copy(showMappingDialog = false, pendingUri = null)
    }

    /** 请求清空确认。 */
    fun onRequestClear() {
        _uiState.value = _uiState.value.copy(showClearConfirm = true)
    }

    /** 取消清空。 */
    fun onDismissClear() {
        _uiState.value = _uiState.value.copy(showClearConfirm = false)
    }

    /** 确认清空。 */
    fun onConfirmClear() {
        _uiState.value = _uiState.value.copy(showClearConfirm = false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearAll() }
            AppLogger.i(TAG, "清空全部工参")
            _uiState.value = _uiState.value.copy(
                message = "已清空全部工参",
                params = withContext(Dispatchers.IO) { repository.getAll() }
            )
        }
    }

    /** 请求删除单条工参（弹确认框）。 */
    fun onRequestDelete(id: Long) {
        _uiState.value = _uiState.value.copy(pendingDeleteId = id)
    }

    /** 取消删除。 */
    fun onDismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteId = -1L)
    }

    /** 确认删除单条工参。 */
    fun onConfirmDelete() {
        val id = _uiState.value.pendingDeleteId
        if (id < 0) return
        _uiState.value = _uiState.value.copy(pendingDeleteId = -1L)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteById(id) }
            AppLogger.i(TAG, "删除工参 id=$id")
            _uiState.value = _uiState.value.copy(
                message = "已删除该条工参",
                params = withContext(Dispatchers.IO) { repository.getAll() }
            )
        }
    }

    /** 初始加载列表。 */
    fun load() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.getAll() }
            _uiState.value = _uiState.value.copy(params = list)
        }
    }

    /** 清除提示消息（toast 超时后调用）。 */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
