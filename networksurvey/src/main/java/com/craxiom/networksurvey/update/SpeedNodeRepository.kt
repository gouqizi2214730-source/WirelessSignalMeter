package com.craxiom.networksurvey.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 测速节点仓库（v3.74 起全部从服务器获取）：
 * 依次尝试 主/备用服务器 × /api/nodes|/nodes.json，成功后写入 SharedPreferences 缓存；
 * 全部失败时用 [cached] 回退（无内置预设兜底）。
 * DAD 反编译 SpeedNodeRepository(Ln72) 还原：
 *   - 候选路径 ["/api/nodes", "/nodes.json"]
 *   - 解析支持 ["[...]"] 数组与 {"nodes":[...]} 对象两种格式
 */
object SpeedNodeRepository {
    private val PATHS = listOf("/api/nodes", "/nodes.json")
    private const val PREFS = "speed_node_prefs"
    private const val KEY_CACHE = "cached_nodes"
    private const val TIMEOUT_MS = 8000

    /** fetch 回调结果：nodes 非空即成功；source 为命中地址；error 供 UI 提示 */
    data class Result(
        val nodes: List<SpeedNode>,
        val source: String,
        val error: String = ""
    )

    /** 本地缓存回退（服务器不可达时） */
    fun cached(context: Context): List<SpeedNode> {
        return try {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CACHE, "") ?: return emptyList()
            parse(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 异步拉取节点：主+备用服务器 × 双路径，命中即回调（主线程）；成功时写缓存 */
    fun fetch(context: Context, onResult: (Result) -> Unit) {
        Thread {
            val main = Handler(Looper.getMainLooper())
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            outer@ for (base in UpdateChecker.candidateUrls()) {
                for (path in PATHS) {
                    try {
                        val url = base.trimEnd('/') + path
                        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = TIMEOUT_MS
                            readTimeout = TIMEOUT_MS
                        }
                        val ok = conn.responseCode == 200
                        val body = if (ok) conn.inputStream.bufferedReader().use { it.readText() } else ""
                        conn.disconnect()
                        if (ok && body.isNotEmpty()) {
                            val nodes = parse(body)
                            if (nodes.isNotEmpty()) {
                                sp.edit().putString(KEY_CACHE, body).apply()
                                main.post { onResult(Result(nodes, url)) }
                                return@Thread
                            }
                        }
                    } catch (_: Exception) {
                        // 试下一个路径/服务器
                    }
                }
            }
            main.post { onResult(Result(emptyList(), "", "服务器不可达")) }
        }.start()
    }

    /** 解析节点 JSON：["{name,url}"] 数组或 {"nodes":[...]} 对象；name 非空且 url 以 http 开头才收录 */
    private fun parse(json: String): List<SpeedNode> {
        return try {
            val arr: JSONArray? = if (json.trimStart().startsWith("[")) {
                JSONArray(json)
            } else {
                JSONObject(json).optJSONArray("nodes")
            }
            val list = ArrayList<SpeedNode>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    val url = o.optString("url", "")
                    if (name.isNotEmpty() && url.startsWith("http")) {
                        list.add(SpeedNode(name, url))
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
