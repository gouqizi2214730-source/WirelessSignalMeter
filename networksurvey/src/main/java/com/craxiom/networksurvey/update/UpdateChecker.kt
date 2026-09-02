package com.craxiom.networksurvey.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检测：拉取后端 /api/version，与本地 versionCode 比较。
 * 命中新版本 → 写入 [pendingUpdate]（自动检测弹窗）或 [checkState]（设置页手动检测）。
 *
 * 支持「主 + 备用」多服务器容灾：主服务器不可达时依次尝试 [BACKUP_URLS]，
 * 命中哪个就用哪个的地址拼 APK 下载链接（[RemoteVersion.apkUrl] 存绝对地址）。
 *
 * ⚠️ 服务器地址（http 明文，未开 HTTPS；network_security_config 已全局放行 cleartext）：
 *    - [PRIMARY_URL]：主更新服务器（47.108.209.162:9000，v3.87 起迁移）
 *    - [BACKUP_URLS]：备用服务器列表（rsrp.cn 备用域名，rsrp.top 已弃用）。每个元素为完整 base URL。
 *      备用服务器需部署相同的 update-server（同样的 version.json + APK）。
 *    若日后开启 HTTPS 反代，把地址改回 https:// 并重新构建即可。
 */

/**
 * 服务端返回的新版本信息（顶层 data class，供同包各文件直接引用）。
 */
data class RemoteVersion(
    val versionCode: Int,
    val versionName: String,
    /** 绝对下载地址（已用命中服务器的 baseUrl 拼好） */
    val apkUrl: String,
    val forceUpdate: Boolean,
    val title: String,
    val releaseNotes: List<String>,
    val publishDate: String,
    /** 实际提供该版本的服务器地址（用于 UI 提示是否走了备用） */
    val source: String = ""
)

object UpdateChecker {
    /** 主更新服务器：NSRebuild 独立通道（/nsrebuild/ 前缀，与 WirelessSignalMeter 版本隔离） */
    const val PRIMARY_URL = "http://47.108.209.162:9000/nsrebuild"
    /**
     * 备用服务器列表：主不可达时依次尝试。
     *  - rsrp.cn:9000 ：备用域名（解析到主服务器同一 Node 服务，多域名冗余；rsrp.top 已弃用）
     * 备用服务器需部署相同的 update-server（同样的 version.json + APK）。
     */
    private val BACKUP_URLS: List<String> = listOf("http://rsrp.cn:9000/nsrebuild")

    /**
     * 版本清单取用路径，按顺序尝试（只有拿到"更高版本"才停）：
     *  - /api/version  ：后端接口（读服务端 version.json）
     *  - /version.json ：静态清单兜底。后端进程若未重启/接口不可用时仍能发版，
     *                    只需把新的 version.json 传到 public/ 下即可。
     */
    private val VERSION_PATHS = listOf("/api/version", "/version.json")
    internal const val LOG_PATH = "/api/log"
    private const val PREFS = "update_check_prefs"
    private const val KEY_LAST = "last_check_ts"
    private const val KEY_IGNORED = "ignored_version_code"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000 // 每天最多检查一次（手动/强提醒时 force=true 跳过）
    private const val TIMEOUT_MS = 8000

    /** 供 Compose 读取以弹窗（新版本到来时置位，自动检测用） */
    val pendingUpdate = mutableStateOf<RemoteVersion?>(null)

    /** 手动检测（设置页）的 UI 状态机 */
    sealed interface CheckUiState {
        data object Idle : CheckUiState
        data object Checking : CheckUiState
        data object UpToDate : CheckUiState
        data class HasUpdate(val rv: RemoteVersion) : CheckUiState
        data class Error(val msg: String) : CheckUiState
    }
    val checkState = mutableStateOf<CheckUiState>(CheckUiState.Idle)

    /** 候选服务器（主 + 备用，去重，统一去尾斜杠） */
    internal fun candidateUrls(): List<String> {
        val set = LinkedHashSet<String>()
        set.add(PRIMARY_URL.trimEnd('/'))
        BACKUP_URLS.forEach { if (it.isNotBlank()) set.add(it.trimEnd('/')) }
        return set.toList()
    }

    fun check(context: Context, force: Boolean = false, manual: Boolean = false) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong(KEY_LAST, 0) < CHECK_INTERVAL_MS) return
        sp.edit().putLong(KEY_LAST, now).apply()

        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                if (manual) main.post { checkState.value = CheckUiState.Checking }
                val currentVersion = getCurrentVersionCode(context)
                var updated: RemoteVersion? = null
                var anySuccess = false
                outer@ for (base in candidateUrls()) {
                    for (p in VERSION_PATHS) {
                        when (val r = fetchVersion(base, p, currentVersion)) {
                            is FetchResult.Updated -> { updated = r.rv; break@outer }
                            // 该路径版本不比本地新：继续试下一个路径/服务器（可能静态清单更新）
                            is FetchResult.UpToDate -> anySuccess = true
                            is FetchResult.Fail -> { /* 试下一个 */ }
                        }
                    }
                }
                when {
                    updated != null -> {
                        if (manual) {
                            main.post { checkState.value = CheckUiState.HasUpdate(updated) }
                        } else if (!isIgnored(context, updated.versionCode)) {
                            main.post { pendingUpdate.value = updated }
                        }
                        // 自动检测命中但已被"忽略此版本"：保持静默
                    }
                    anySuccess -> {
                        if (manual) main.post { checkState.value = CheckUiState.UpToDate }
                        // 自动检测：已最新则静默
                    }
                    else -> {
                        if (manual) main.post { checkState.value = CheckUiState.Error("网络异常，请检查网络或稍后重试") }
                        // 自动检测：全部失败则静默
                    }
                }
            } catch (_: Exception) {
                if (manual) main.post { checkState.value = CheckUiState.Error("网络异常，请检查网络或稍后重试") }
            }
        }.start()
    }

    /** 从单个 baseUrl + path 拉取并解析版本；按结果分类 */
    private fun fetchVersion(baseUrl: String, path: String, currentVersion: Int): FetchResult {
        return try {
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val codeResp = conn.responseCode
            if (codeResp != 200) { conn.disconnect(); return FetchResult.Fail }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val j = JSONObject(body)
            val code = j.optInt("versionCode", 0)
            if (code <= currentVersion) return FetchResult.UpToDate // 已是最新
            val notes = ArrayList<String>()
            (j.optJSONArray("releaseNotes") as? JSONArray)?.let { arr ->
                for (i in 0 until arr.length()) notes.add(arr.optString(i, ""))
            }
            val rel = j.optString("apkUrl", "")
            if (rel.isEmpty()) return FetchResult.Fail
            val absApk = if (rel.startsWith("http")) rel else baseUrl.trimEnd('/') + rel
            FetchResult.Updated(
                RemoteVersion(
                    code,
                    j.optString("versionName", ""),
                    absApk,
                    j.optBoolean("forceUpdate", false),
                    j.optString("title", "发现新版本"),
                    notes,
                    j.optString("publishDate", ""),
                    source = baseUrl
                )
            )
        } catch (_: Exception) {
            FetchResult.Fail
        }
    }

    private sealed class FetchResult {
        data class Updated(val rv: RemoteVersion) : FetchResult()
        data object UpToDate : FetchResult()
        data object Fail : FetchResult()
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") pi.versionCode
            }
        } catch (_: Exception) { 0 }
    }

    /** 忽略某个版本（自动弹窗不再提示，直到该版本被更新或用户手动检测） */
    fun ignoreVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_IGNORED, versionCode).apply()
    }

    private fun isIgnored(context: Context, versionCode: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_IGNORED, 0) == versionCode

    /** 把后端返回的相对/绝对 apkUrl 拼成完整下载地址（绝对地址直接返回） */
    fun fullApkUrl(relativeOrAbsolute: String): String =
        if (relativeOrAbsolute.startsWith("http")) relativeOrAbsolute
        else PRIMARY_URL.trimEnd('/') + relativeOrAbsolute
}
