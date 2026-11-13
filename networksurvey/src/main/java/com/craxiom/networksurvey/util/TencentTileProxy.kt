package com.craxiom.networksurvey.util

import android.util.LruCache
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * 腾讯电子地图本地瓦片代理服务。
 *
 * 专门适配奥维/MOBAC 格式的腾讯电子地图源：
 * - 协议：HTTP
 * - 域名：m0~m3.map.gtimg.com (负载均衡)
 * - 接口：/hwap?z={$z-1}&x={$x}&y={$y!}&styleid=1000&scene=0&version=1246
 * - 瓦片尺寸：256
 *
 * MapLibre 请求 http://127.0.0.1:18088/tile/{z}/{x}/{y}.jpg 时，
 * 本地代理自动计算 z-1 以及 y 翻转 (1 shl z) - 1 - y，并带 LRU 内存缓存加速渲染。
 */
object TencentTileProxy {
    const val PORT = 18088
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(8)
    @Volatile private var isRunning = false

    // 内存缓存最近 300 张瓦片，快速拖动缩放无需重复请求网络
    private val tileCache = LruCache<String, ByteArray>(300)

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
                Timber.i("TencentTileProxy started on port $PORT")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Timber.e(e, "TencentTileProxy server socket error")
                }
            }
        }.apply {
            isDaemon = true
            name = "TencentTileProxyThread"
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 6000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val reader = input.bufferedReader()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size >= 2 && parts[0] == "GET") {
                val path = parts[1].substringBefore("?").removePrefix("/tile/").removeSuffix(".jpg")
                val xyz = path.split("/")
                if (xyz.size == 3) {
                    val z = xyz[0].toIntOrNull() ?: return
                    val x = xyz[1].toIntOrNull() ?: return
                    val y = xyz[2].toIntOrNull() ?: return

                    val cacheKey = "$z/$x/$y"
                    var data = tileCache.get(cacheKey)

                    if (data == null) {
                        // 腾讯地图规则：
                        // z 需减 1: {$z-1}
                        // y 翻转: {$y!} = (2^z - 1) - y
                        val txZ = z - 1
                        val txY = (1 shl z) - 1 - y
                        val serverPart = Math.floorMod(x + y, 4) // 0-3 负载均衡
                        val targetUrl = "http://m$serverPart.map.gtimg.com/hwap?z=$txZ&x=$x&y=$txY&styleid=1000&scene=0&version=1246"

                        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                            setRequestProperty("User-Agent", "Mozilla/5.0")
                        }

                        if (conn.responseCode == 200) {
                            data = conn.inputStream.use { it.readBytes() }
                            if (data.isNotEmpty()) {
                                tileCache.put(cacheKey, data)
                            }
                        }
                    }

                    if (data != null && data.isNotEmpty()) {
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Content-Length: ${data.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(header.toByteArray())
                        output.write(data)
                        output.flush()
                        return
                    }
                }
            }

            output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
            output.flush()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }
}
