package com.craxiom.networksurvey.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.craxiom.networksurvey.update.UpdateChecker
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * 日志上传：把 AppLogger 落盘的 filesDir/logs/app.log + 设备信息 + 用户描述，
 * POST JSON 到更新服务器 /api/log（与 WirelessSignalMeter 旧版「上传日志」一致）。
 * 服务器收到后写入 logs/ 时间戳文件。回调在主线程执行。
 */
object LogUploader {

    private const val TIMEOUT_MS = 15000
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 上传日志。
     * @param description 用户填写的问题描述（可空）
     * @param onResult 回调 (成功, 提示信息) —— 主线程
     */
    fun upload(context: Context, description: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            var ok = false
            var msg = "无法连接服务器"
            try {
                val logText = AppLogger.readLog() ?: "（无日志内容）"
                val device = AppLogger.deviceInfo(context)
                val payload = JSONObject().apply {
                    put("description", description.ifBlank { "（未填写）" })
                    put("device", device)
                    put("log", logText.take(300_000)) // 上限 ~300KB，防止超大 payload
                    put("app", "wireless-signalmeter")
                    put("ts", System.currentTimeMillis())
                }
                val body = payload.toString().toByteArray(Charset.forName("UTF-8"))

                var lastError = "无法连接服务器"
                for (base in UpdateChecker.candidateUrls()) {
                    val url = base.trimEnd('/') + "/api/log"
                    try {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = TIMEOUT_MS
                        conn.readTimeout = TIMEOUT_MS
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Accept", "*/*")
                        conn.setFixedLengthStreamingMode(body.size)
                        val out: OutputStream = conn.outputStream
                        try {
                            out.write(body)
                        } finally {
                            runCatching { out.close() }
                        }
                        val code = conn.responseCode
                        if (code in 200..299) {
                            ok = true
                            msg = "日志上传成功"
                            break
                        }
                        lastError = "服务器返回 $code"
                        runCatching { conn.errorStream?.close() }
                    } catch (e: Exception) {
                        lastError = e.message ?: "网络异常"
                    }
                }
                if (!ok) msg = "上传失败：$lastError"
            } catch (t: Throwable) {
                msg = "上传异常：${t.message}"
            }
            val finalOk = ok
            val finalMsg = msg
            mainHandler.post { onResult(finalOk, finalMsg) }
        }.start()
    }
}
