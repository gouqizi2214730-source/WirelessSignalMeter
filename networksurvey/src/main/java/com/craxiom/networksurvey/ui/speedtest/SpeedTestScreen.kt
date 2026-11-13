package com.craxiom.networksurvey.ui.speedtest

import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.db.SpeedTestDbHelper
import com.craxiom.networksurvey.model.SpeedTestRecord
import com.craxiom.networksurvey.update.SpeedNode
import com.craxiom.networksurvey.update.SpeedNodeRepository
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.ui.dashboard.DashboardCardHeader
import com.craxiom.networksurvey.util.AppLogger

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.TreeSet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// v3.90 新增：折线图绘制 + 实时刷新协程
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PREFS_NAME = "speed_servers"
private const val KEY_SERVERS = "saved_servers"
private const val TAG = "SpeedTest"
private const val SAMPLE_MAX = 120
// v3.93：WebDAV 列目录用标准 PROPFIND 请求体（部分服务器对目录 GET 返回 405 Method Not Allowed）
private val PROPFIND_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>".toByteArray(Charsets.UTF_8)

// v3.93 修复：Android HttpURLConnection.setRequestMethod 有方法白名单
// （OPTIONS/GET/HEAD/POST/PUT/DELETE/TRACE/PATCH），PROPFIND 不在其中 → 直接抛 ProtocolException。
// 用反射改实现类私有 method 字段绕过白名单；失败返回 false（调用方回退 GET）。
private fun setRawHttpMethod(http: HttpURLConnection, method: String): Boolean {
    return try {
        var cls: Class<*>? = http.javaClass
        var f: java.lang.reflect.Field? = null
        while (cls != null && cls != Any::class.java) {
            f = try { cls.getDeclaredField("method") } catch (_: Exception) { null }
            if (f != null) break
            cls = cls.superclass
        }
        if (f == null) false
        else {
            f.isAccessible = true
            f.set(http, method)
            true
        }
    } catch (_: Exception) { false }
}

// v4.05：恢复 propfindRaw（裸 Socket PROPFIND + body）——用户确认 01:30 的 SocketTimeoutException
// 是「没连内网」所致，socket 方案本身可用（5005/5006 同一文件夹仅 http/https 区别）。
// 裸 Socket 无方法白名单限制，PROPFIND+body 100% 可靠发出（HttpURLConnection 无 body 是 405 根因）。
// 不走系统代理正好适合内网直连。返回 (HTTP状态码, 响应体)；网络异常抛给调用方转友好提示。
private fun propfindRaw(urlStr: String, user: String, pass: String): Pair<Int, String> {
    val uri = java.net.URI(urlStr)
    val isHttps = uri.scheme == "https"
    val host = uri.host ?: throw java.io.IOException("无法解析主机地址")
    val port = if (uri.port > 0) uri.port else if (isHttps) 443 else 80
    // v4.03：路径段编码——含非 ASCII/空格的段做 percent-encode（已含 %XX 视为已编码跳过）。
    fun encSeg(s: String): String =
        if (s.isEmpty() || s.contains('%') || s.all { it.code < 128 && it != ' ' }) s
        else java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    val path = ("/" + (uri.path ?: "").trimStart('/')).split("/").joinToString("/") { encSeg(it) }
    trustAllCertificatesOnce()
    val sock: java.net.Socket = if (isHttps) {
        val sf = trustAllSf ?: javax.net.ssl.SSLSocketFactory.getDefault()
        val s = sf.createSocket() as javax.net.ssl.SSLSocket
        s.connect(java.net.InetSocketAddress(host, port), 15000)
        s.startHandshake()
        s
    } else {
        java.net.Socket()
    }
    sock.soTimeout = 15000
    try {
        if (!isHttps) sock.connect(java.net.InetSocketAddress(host, port), 15000)
        val req = StringBuilder()
        req.append("PROPFIND ").append(path).append(" HTTP/1.1\r\n")
        req.append("Host: ").append(host).append(if (port == 80 || port == 443) "" else ":$port").append("\r\n")
        if (user.isNotEmpty()) {
            val auth = android.util.Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            req.append("Authorization: Basic ").append(auth).append("\r\n")
        }
        req.append("Depth: 1\r\n")
        req.append("Content-Type: application/xml; charset=utf-8\r\n")
        req.append("Accept-Encoding: identity\r\n")
        req.append("Content-Length: ").append(PROPFIND_BODY.size).append("\r\n")
        req.append("Connection: close\r\n\r\n")
        val os = sock.getOutputStream()
        os.write(req.toString().toByteArray(Charsets.UTF_8))
        os.write(PROPFIND_BODY)
        os.flush()
        val ins = sock.getInputStream()
        val statusLine = readHttpLine(ins) ?: throw java.io.IOException("服务器无响应")
        val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: -1
        var contentLength = -1
        var chunked = false
        while (true) {
            val h = readHttpLine(ins) ?: break
            if (h.isEmpty()) break
            val idx = h.indexOf(':')
            if (idx > 0) {
                val k = h.substring(0, idx).trim().lowercase()
                val v = h.substring(idx + 1).trim()
                if (k == "content-length") contentLength = v.toIntOrNull() ?: -1
                if (k == "transfer-encoding" && v.contains("chunked")) chunked = true
            }
        }
        val body = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        fun readN(n: Int) {
            var remain = n
            while (remain > 0) {
                val r = ins.read(buf, 0, minOf(remain, buf.size))
                if (r < 0) break
                body.write(buf, 0, r); remain -= r
            }
        }
        if (chunked) {
            while (true) {
                val sizeLine = readHttpLine(ins) ?: break
                val size = sizeLine.trim().substringBefore(';').toIntOrNull(16) ?: 0
                if (size <= 0) break
                readN(size)
                readHttpLine(ins) // 块尾 CRLF
            }
        } else if (contentLength >= 0) readN(contentLength)
        else {
            while (true) { val r = ins.read(buf); if (r < 0) break; body.write(buf, 0, r) }
        }
        return code to String(body.toByteArray(), Charsets.UTF_8)
    } finally { try { sock.close() } catch (ignored: Exception) {} }
}

// 读一行（\r\n 或 \n 结尾），流结束且无内容返回 null
private fun readHttpLine(ins: java.io.InputStream): String? {
    val sb = StringBuilder()
    while (true) {
        val b = ins.read()
        if (b < 0) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code) break
        if (b != '\r'.code) sb.append(b.toChar())
    }
    return sb.toString()
}

private val UPLOAD_SIZE_OPTIONS = listOf("1 MB", "5 MB", "10 MB", "50 MB", "100 MB", "500 MB")
private val UPLOAD_SIZE_BYTES = longArrayOf(
    1L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024,
    50L * 1024 * 1024, 100L * 1024 * 1024, 500L * 1024 * 1024
)

// v4.01：全局保存 trust-all 的 SSL 工厂/HostnameVerifier，供 applyTrustAll 对每个 https 连接显式设置
//（不依赖 HttpsURLConnection 静态默认，确保浏览/下载/上传任何 https 自签名证书都被忽略）
private var trustAllSf: javax.net.ssl.SSLSocketFactory? = null
private var trustAllHv: javax.net.ssl.HostnameVerifier? = null

@Synchronized
private fun trustAllCertificatesOnce() {
    try {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAll, SecureRandom())
        trustAllSf = sc.socketFactory
        trustAllHv = javax.net.ssl.HostnameVerifier { _, _ -> true }
        HttpsURLConnection.setDefaultSSLSocketFactory(trustAllSf)
        HttpsURLConnection.setDefaultHostnameVerifier(trustAllHv)
    } catch (ignored: Exception) {}
}

// v4.01：对指定连接显式应用 trust-all（https 自签名/过期证书一律忽略，用户确认这是预期行为）
private fun applyTrustAll(http: HttpURLConnection) {
    if (http is HttpsURLConnection) {
        try { if (trustAllSf != null) http.sslSocketFactory = trustAllSf } catch (ignored: Exception) {}
        try { if (trustAllHv != null) http.hostnameVerifier = trustAllHv } catch (ignored: Exception) {}
    }
}

private fun apiToast(c: Context, msg: String) {
    val app = c.applicationContext
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
    }
}

private fun formatSpeed(bps: Long): String =
    if (bps >= 1_000_000) String.format("%.1f Mbps", bps / 1_000_000.0)
    else if (bps >= 1_000) String.format("%.1f Kbps", bps / 1_000.0)
    else "$bps bps"

private fun formatBytes(bytes: Long): String =
    if (bytes >= 1_048_576) String.format("%.1f MB", bytes / 1_048_576.0)
    else if (bytes >= 1_024) String.format("%.1f KB", bytes / 1_024.0)
    else "$bytes B"

private fun sortFiles(files: List<String>): List<String> =
    files.filter { it.endsWith("/") } + files.filter { !it.endsWith("/") }

// v4.01：FTP LIST 行解析——兼容 Unix(ls -l)/Windows(IIS/Serv-U 的 <DIR>)/MLSD/兜底取末 token。
// 返回 (名称, 是否目录)；无法识别返回 null（调用方跳过该行）。
// 修复：原实现只认 Unix 9 列格式(arr.size>=9 取 arr[8])，Windows FTP 服务器输出全部被丢弃 → "目录为空 /"。
private fun parseFtpListLine(line: String): Pair<String, Boolean>? {
    val t = line.trim()
    if (t.isEmpty() || t.startsWith("total ")) return null
    val arr = t.split(Regex("\\s+"))
    // Unix：drwxr-xr-x  2 user group 4096 Aug 20 12:00  name（文件名可能含空格）
    if (arr.isNotEmpty() && (arr[0].startsWith("d") || arr[0].startsWith("-") || arr[0].startsWith("l"))) {
        if (arr.size >= 9) {
            val name = arr.subList(8, arr.size).joinToString(" ")
            if (name == "." || name == "..") return null
            return name to arr[0].startsWith("d")
        }
        // 紧凑 Unix（部分服务器省略组/日期列）：drwxr-xr-x  2 root 4096  name
        if (arr.size >= 5 && arr[4].toLongOrNull() != null) {
            val name = arr.subList(5, arr.size).joinToString(" ")
            if (name == "." || name == "..") return null
            return name to arr[0].startsWith("d")
        }
    }
    // Windows 目录：08-24-26  10:00AM       <DIR>          folder
    if (t.contains("<DIR>")) {
        val name = t.substringAfterLast("<DIR>").trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        return name to true
    }
    // Windows 文件：08-24-26  10:00AM             123456  file.bin
    val win = Regex("^\\d{2}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}[AP]M\\s+\\d+\\s+(.+)$").find(t)
    if (win != null) {
        val name = win.groupValues[1].trim()
        if (name == "." || name == "..") return null
        return name to false
    }
    // MLSD：type=dir;modify=20240101000000; folder
    if (t.contains("type=dir") || t.contains("type=file")) {
        val name = t.substringAfterLast(';').trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        return name to t.contains("type=dir")
    }
    // 兜底：取最后一个 token 作为文件名
    if (arr.isNotEmpty()) {
        val name = arr.last()
        if (name == "." || name == ".." || name.isEmpty()) return null
        return name to false
    }
    return null
}

// ---- v4.10：FTP 全部改用 Apache Commons Net FTPClient（开源工业标准）----
// 背景：v4.08 手写最小 FTP 协议（USER/PASS/TYPE I/PASV/REST/RETR）在用户服务器上 RETR 全挂
// （日志 425 Can't open data connection，pool=1 也挂；LIST/上传却正常）。根因是 PASV 后须先建立
// 数据连接再发 RETR，手写时序反了；v4.09 修正后用户反馈「还是不行」。用户点名借鉴开源项目——
// Commons Net 是 Apache 维护的工业标准 FTP 客户端，PASV/PORT、REST 偏移、多行响应、数据连接
// 时序全部自动处理（Android minSdk 33 兼容 Java 8+）。浏览/下载分片/上传三处统一换掉。

// 探测 FTP 文件大小（SIZE 命令），失败返回 -1
private fun ftpFileSize(host: String, port: Int, user: String, pass: String, remotePath: String, passive: Boolean = true): Long {
    var ftp: org.apache.commons.net.ftp.FTPClient? = null
    return try {
        ftp = org.apache.commons.net.ftp.FTPClient()
        ftp.setConnectTimeout(8000)
        ftp.connect(host, port)
        if (!ftp.login(if (user.isEmpty()) "anonymous" else user, if (pass.isEmpty() && user.isEmpty()) "guest@" else pass)) return -1L
        ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
        if (passive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
        ftp.setSoTimeout(15000)
        ftp.setBufferSize(2 * 1024 * 1024)  // v4.17: 内核SO_RCVBUF/SNDBUF=1MB(Commons Net数据socket缓冲=setBufferSize值), 高带宽xRTT窗口  // v4.14：调大内部缓冲
        // SIZE 是控制连接命令，Commons Net 无专用 API，用 sendCommand + 解析 213 响应
        ftp.sendCommand("SIZE", remotePath)
        if (ftp.replyCode == 213) {
            ftp.replyString.trim().substringAfter(' ').trim().toLongOrNull() ?: -1L
        } else -1L
    } catch (e: Exception) { -1L }
    finally { try { ftp?.disconnect() } catch (ignored: Exception) {} }
}

// 下载一个分片：start 偏移（>=0 走 REST），length>0 读该字节数即停 / length<0 读到 EOF。
// 返回实际读到的字节数；异常直接抛出由调用方处理。shouldStop 为 true 时立即返回。
// Commons Net retrieveFileStream 内部已按 RFC 959 正确时序处理：
// PASV → 建立数据连接 → REST(start) → RETR，再返回数据流（读完后须 completePendingCommand 收 226）。
private fun ftpDownloadChunk(
    host: String, port: Int, user: String, pass: String,
    remotePath: String, start: Long, length: Long,
    buf: ByteArray, passive: Boolean = true,
    onProgress: ((Int) -> Unit)? = null, shouldStop: () -> Boolean
): Long {
    var ftp: org.apache.commons.net.ftp.FTPClient? = null
    var ins: java.io.InputStream? = null
    try {
        ftp = org.apache.commons.net.ftp.FTPClient()
        ftp.setConnectTimeout(8000)
        ftp.connect(host, port)
        if (!ftp.login(if (user.isEmpty()) "anonymous" else user, if (pass.isEmpty() && user.isEmpty()) "guest@" else pass))
            throw java.io.IOException("FTP 登录失败 (${ftp.replyCode})")
        ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
        if (passive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
        ftp.setSoTimeout(20000)
        ftp.setDataTimeout(20000)
        ftp.setBufferSize(2 * 1024 * 1024)  // v4.17: 内核SO_RCVBUF/SNDBUF=1MB(Commons Net数据socket缓冲=setBufferSize值), 高带宽xRTT窗口  // v4.14：Commons Net 默认 1024B 内部缓冲，调大减少小包
        if (start > 0) ftp.setRestartOffset(start)
        ins = ftp.retrieveFileStream(remotePath)
            ?: throw java.io.IOException("FTP RETR 失败 (${ftp.replyCode}) $remotePath")
        var total = 0L
        while (!shouldStop() && (length < 0 || total < length)) {
            val want = if (length < 0) buf.size else minOf(buf.size.toLong(), length - total).toInt()
            val n = ins.read(buf, 0, want)
            if (n < 0) break
            total += n.toLong()
            // v4.12：每读一块立即回调（驱动实时速率上报）——修复 FTP 下载时顶部仪表无速率：
            // 此前 report 只在分片读完后调用，单连接读大文件期间 UI 全程无速率。
            onProgress?.invoke(n)
        }
        // 读完后必须 completePendingCommand() 消费 226，否则控制连接残留半开状态
        if (total > 0 && !shouldStop()) {
            try { ftp.completePendingCommand() } catch (ignored: Exception) {}
        } else {
            try { ftp.abort() } catch (ignored: Exception) {}
        }
        return total
    } finally {
        try { ins?.close() } catch (ignored: Exception) {}
        try { ftp?.disconnect() } catch (ignored: Exception) {}
    }
}

// v4.15：FTP 下载连接复用——每线程持有一个 FTPClient 持续拉段（段间只发 PASV/REST/RETR，
// 不再重复 connect/login）。修复「下载一闪一闪（2M↔100M）」：v4.14 每 8MB 段新建连接，
// 高速下段瞬间拉完 + 重连空窗（公网 RTT 3 次往返）→ 速率周期性掉零。
private class FtpChunkDownloader(
    private val host: String, private val port: Int,
    private val user: String, private val pass: String,
    private val passive: Boolean
) {
    private var ftp: org.apache.commons.net.ftp.FTPClient? = null

    private fun ensure(): org.apache.commons.net.ftp.FTPClient {
        val f = ftp
        if (f != null && f.isConnected) return f
        val n = org.apache.commons.net.ftp.FTPClient()
        n.setConnectTimeout(8000)
        n.connect(host, port)
        if (!n.login(if (user.isEmpty()) "anonymous" else user, if (pass.isEmpty() && user.isEmpty()) "guest@" else pass))
            throw java.io.IOException("FTP 登录失败 (${n.replyCode})")
        n.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
        if (passive) n.enterLocalPassiveMode() else n.enterLocalActiveMode()
        n.setSoTimeout(20000); n.setDataTimeout(20000); n.setBufferSize(2 * 1024 * 1024)  // v4.17: 内核SO_RCVBUF/SNDBUF=1MB(Commons Net数据socket缓冲=setBufferSize值), 高带宽xRTT窗口
        ftp = n
        return n
    }

    /** 拉一段；异常时断开并置空（下次调用自动重建连接） */
    fun chunk(remotePath: String, start: Long, length: Long, buf: ByteArray,
              onProgress: ((Int) -> Unit)?, shouldStop: () -> Boolean): Long {
        val f = ensure()
        var ins: java.io.InputStream? = null
        try {
            if (start > 0) f.setRestartOffset(start)
            ins = f.retrieveFileStream(remotePath)
                ?: throw java.io.IOException("FTP RETR 失败 (${f.replyCode}) $remotePath")
            var total = 0L
            while (!shouldStop() && (length < 0 || total < length)) {
                val want = if (length < 0) buf.size else minOf(buf.size.toLong(), length - total).toInt()
                val n = ins.read(buf, 0, want)
                if (n < 0) break
                total += n.toLong()
                onProgress?.invoke(n)
            }
            if (total > 0 && !shouldStop()) {
                try { f.completePendingCommand() } catch (ignored: Exception) {}
            } else {
                try { f.abort() } catch (ignored: Exception) {}
            }
            return total
        } catch (e: Exception) {
            try { f.disconnect() } catch (ignored: Exception) {}
            ftp = null
            throw e
        } finally {
            try { ins?.close() } catch (ignored: Exception) {}
        }
    }

    fun close() {
        try { ftp?.disconnect() } catch (ignored: Exception) {}
        ftp = null
    }
}

private fun extractTagContent(str: String, openTag: String, closeTag: String): String? {
    val start = str.indexOf(openTag)
    if (start < 0) return null
    val cs = start + openTag.length
    val end = str.indexOf(closeTag, cs)
    if (end < 0 || end < cs) return null
    return try { str.substring(cs, end) } catch (_: Exception) { null }
}

private fun parseWebDavHrefs(xml: String, currentPath: String): List<String> {
    val files = ArrayList<String>()
    // v3.46：空 XML 直接返回，避免 split/遍历对空字符串做无意义处理。
    if (xml.isNullOrBlank()) return files
    val np = if (!currentPath.startsWith("/")) "/$currentPath" else currentPath
    val npNoSlash = if (np.endsWith("/") && np.length > 1) np.substring(0, np.length - 1) else np
    // v3.46：容忍不同 WebDAV 实现的命名空间前缀(D/d/无前缀)与结构性异常——单个 response 块解析失败
    // 只跳过该条目，不影响整体列表（之前某服务器返回的结构让此处抛 NPE → toast"列表失败"）。
    for (block in xml.split("<D:response>", "<d:response>", "<response>")) {
        try {
            var href = extractTagContent(block, "<D:href>", "</D:href>")
                ?: extractTagContent(block, "<d:href>", "</d:href>")
                ?: extractTagContent(block, "<href>", "</href>")
                ?: continue
            href = href.trim()
            try { href = java.net.URLDecoder.decode(href, "UTF-8") } catch (ignored: Exception) {}
            if (href == np || href == npNoSlash || href == npNoSlash + "/") continue
            if (href.startsWith("/")) href = href.substring(1)
            var endsSlash = href.endsWith("/")
            if (endsSlash) href = href.substring(0, href.length - 1)
            val lastSlash = href.lastIndexOf('/')
            if (lastSlash >= 0) href = href.substring(lastSlash + 1)
            val isDir = block.contains("<D:collection") || block.contains("<d:collection") || block.contains("collection")
            if (href.isNotEmpty()) {
                files.add(if (isDir || endsSlash && !href.endsWith("/")) "$href/" else href)
            }
        } catch (_: Exception) { /* 单条目解析异常跳过，不拖垮整个列表 */ }
    }
    return files
}

// v3.90：完整 URL 合一解析（rclone 风格：协议://主机:端口/路径，端口不再单独填）
private data class ParsedUrl(val scheme: String, val host: String, val port: Int, val path: String)

// v3.99：协议独立配置——FTP/WebDAV/HTTP 各自保存一份，切换协议互不污染
private data class ProtoCfg(
    var host: String = "", var port: String = "", var path: String = "",
    var username: String = "", var password: String = "",
    var fullUrl: String = "", var upDir: String = "",
    var httpsMode: Boolean = false, var ftpPassive: Boolean = true
)

// v4.03：URL 是否显式写了端口（主机部分含冒号）。parseFullUrl 对无端口 URL 返回默认端口
// (https:443/http:80/ftp:21)，若无条件回填会把用户端口框填的值覆盖成默认值（点浏览端口变 21）。
private fun hasExplicitPort(fu: String): Boolean {
    val u = fu.trim().substringAfter("://", fu.trim())
    return u.substringBefore("/").contains(":")
}

private fun parseFullUrl(urlStr: String): ParsedUrl? {
    if (urlStr.isBlank()) return null
    var u = urlStr.trim()
    var scheme = "https"
    when {
        u.startsWith("https://") -> { scheme = "https"; u = u.substring(8) }
        u.startsWith("http://") -> { scheme = "http"; u = u.substring(7) }
        u.startsWith("ftp://") -> { scheme = "ftp"; u = u.substring(6) }
    }
    // IPv6 形如 [::1]:8080 —— 简单处理：取 ']' 之后才找冒号
    val slash = u.indexOf('/')
    val hostPort = if (slash >= 0) u.substring(0, slash) else u
    val path = if (slash >= 0) u.substring(slash) else "/"
    if (hostPort.isEmpty()) return null
    var host = hostPort
    var port = when (scheme) { "https" -> 443; "http" -> 80; "ftp" -> 21; else -> -1 }
    var hostStart = 0
    if (hostPort.startsWith("[")) {
        val rb = hostPort.indexOf(']')
        if (rb > 0) { host = hostPort.substring(1, rb); hostStart = rb + 1 }
    }
    val tail = hostPort.substring(hostStart)
    val colon = tail.lastIndexOf(':')
    if (colon > 0) {
        val pp = tail.substring(colon + 1)
        val pv = pp.toIntOrNull()
        if (pv != null && pv in 1..65535) {
            if (!hostPort.startsWith("[")) host = tail.substring(0, colon)
            port = pv
        }
    }
    if (host.isEmpty()) return null
    return ParsedUrl(scheme, host, port, if (path.isEmpty()) "/" else path)
}

// ==================== 主界面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dbHelper = remember { SpeedTestDbHelper.getInstance(ctx.applicationContext) }
    val runFlag = remember { AtomicBoolean(false) }
    val totalBytes = remember { AtomicLong(0) }
    val loopRef = remember { AtomicInteger(0) }
    // v4.00/v4.06：当前测试的活动连接集合（多连接测速后改为列表登记，stopTest/startTest 全部断开）。
    // 上传 PUT 阻塞在 os.write() 时 runFlag=false 无法中断线程，残留线程会与下一次测试争带宽
    // （日志 08-24 22:20 下载被拖到 0.3MB/s）。断开底层 socket 让卡住的 write/read 抛异常退出。
    val activeConns = remember { java.util.Collections.synchronizedList(mutableListOf<HttpURLConnection>()) }

    var protocol by remember { mutableStateOf("HTTP") }
    var ftpPassive by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // v3.90 WebDAV/FTP：完整 URL 输入框（协议://主机:端口/路径，rclone 风格），替代分离的 host/port/path
    var fullUrl by remember { mutableStateOf("") }
    // v3.96：记录用户 URL 显式指定的 https——非标准 HTTPS 端口（如 5006）不在端口白名单里，
    // 必须记住 scheme 否则 buildUrl 会错误组装成 http:// → HTTPS 端口收明文 → 服务器 400
    var httpsMode by remember { mutableStateOf(false) }
    var testMode by remember { mutableStateOf("download") }   // download / upload
    var uploadSizeIndex by remember { mutableStateOf(2) }
    // v4.14：上传大小自定义（MB 数值输入），预设选项点击时回填此值
    var uploadSizeMb by remember { mutableStateOf("10") }
    var loopCount by remember { mutableStateOf("1") }
    var loopInterval by remember { mutableStateOf("5") }
    // v4.08：并发连接数（线程）——下载/上传共用的并发数，用户填写（1-64）。恢复 v3.x 的线程框，
    // 取代 v4.06 的普通/极限模式（用户实测模式自动决定连接数不好用，改回手动填）。
    var threadCount by remember { mutableStateOf("4") }

    var isRunning by remember { mutableStateOf(false) }
    var resultVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("就绪") }
    var loopInfo by remember { mutableStateOf("") }
    var speedValText by remember { mutableStateOf("--") }
    var trafficText by remember { mutableStateOf("--") }
    var speedLabel by remember { mutableStateOf("下载速度") }
    var progressVisible by remember { mutableStateOf(false) }
    var lastServerUri by remember { mutableStateOf("") }

    // v3.90 折线图采样点：(时间ms, 瞬时速率bps, 方向) —— 上行 true / 下行 false
    var speedSamples by remember { mutableStateOf<List<Triple<Long, Double, Boolean>>>(emptyList()) }

    var records by remember { mutableStateOf<List<SpeedTestRecord>>(emptyList()) }

    // 弹窗状态（统一用 Compose M3，保证风格协调）
    var showUploadDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showPresetDialogState by remember { mutableStateOf(false) }
    var showFileDialog by remember { mutableStateOf(false) }
    var fileDialogItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var fileDialogPath by remember { mutableStateOf("") }
    // v3.96：是否通过浏览选中了具体文件（false=没选/停在目录，下载前校验防 405 下载目录）
    var pickedFile by remember { mutableStateOf(false) }
    // v3.98：上行目录（上传模式/双向模式的上传目标目录）；当前浏览弹窗是否用于选择上传目录
    var upDir by remember { mutableStateOf("") }
    var fileDialogForUpload by remember { mutableStateOf(false) }
    // v4.07：浏览防抖——FTP/WebDAV 浏览请求未返回前忽略重复点击，防止并发连接风暴
    //（日志 08-25 09:37:43 一次点击触发 8 个并发 browse，全部 CWD 550 排队失败）
    var isBrowsing by remember { mutableStateOf(false) }
    // v3.99：协议独立配置备份（FTP/WebDAV/HTTP 切换互不污染）
    val protoCfgMap = remember { mutableStateMapOf<String, ProtoCfg>() }
    var loadDialogEntries by remember { mutableStateOf<List<String>>(emptyList()) }

    // ===== 测速预设节点：服务器下发（/api/nodes）→ 本地缓存 → 内置兜底 =====
    var nodeList by remember { mutableStateOf<List<SpeedNode>>(emptyList()) }
    var nodeSource by remember { mutableStateOf("") }
    var nodeLoading by remember { mutableStateOf(false) }

    fun refreshNodesFromServer() {
        if (nodeLoading) return
        nodeLoading = true
        SpeedNodeRepository.fetch(ctx) { r ->
            nodeLoading = false
            if (r.nodes.isNotEmpty()) {
                nodeList = r.nodes
                nodeSource = "服务器 " + r.source.removePrefix("http://").removePrefix("https://")
            } else if (nodeList.isEmpty()) {
                val cache = SpeedNodeRepository.cached(ctx)
                if (cache.isNotEmpty()) {
                    nodeList = cache
                    nodeSource = "本地缓存（服务器不可达）"
                } else {
                    nodeSource = "服务器不可达"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val cache = SpeedNodeRepository.cached(ctx)
        if (cache.isNotEmpty()) {
            nodeList = cache
            nodeSource = "本地缓存"
        }
        refreshNodesFromServer()
    }

    val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun loadRecords() {
        try { dbHelper.cleanupOldRecords(10); records = dbHelper.getRecent(10) } catch (ignored: Exception) {}
    }

    // ---- 工具函数（置于 LaunchedEffect 之前：applyFullUrl 被其调用，Kotlin 局部函数必须先声明后使用）----
    fun getPort(): Int {
        val p = port.trim()
        if (p.isEmpty()) return when (protocol) { "FTP" -> 21; "WebDAV" -> 443; else -> 80 }
        return try { p.toInt() } catch (e: Exception) { if (protocol == "FTP") 21 else 80 }
    }
    fun cleanHost(h: String): String {
        if (h.startsWith("https://")) return h.substring(8)
        if (h.startsWith("http://")) return h.substring(7)
        return h
    }
    // v3.96：加入 httpsMode（URL 显式 https），非标准 HTTPS 端口（5006 等）不再被误判成 http
    fun isHttps(h: String, p: Int): Boolean = h.startsWith("https://") || httpsMode || p == 443 || p == 8443 || p == 10443 || p == 1443

    // v3.99：组装纯服务器地址（协议://主机:端口，不含路径）——URL 框显示用
    fun buildBaseUrl(): String {
        val h = cleanHost(host)
        val po = getPort()
        return when {
            protocol == "FTP" -> "ftp://$h:$po"
            isHttps(host, po) -> "https://$h:$po"
            else -> "http://$h:$po"
        }
    }

    // v3.90：完整 URL → 解析回填 host/port（v3.99：路径拆到「下载目录」框，URL 框只保留协议://主机:端口）
    // v4.02：overwritePath=false 用于浏览场景——用户在文件对话框点目录后 path 已回填，若 URL 框带路径
    // 再覆盖会把刚选的目录冲掉（FTP 浏览「目录为空」根因之一）
    fun applyFullUrl(fu: String, overwritePath: Boolean = true) {
        val p0 = parseFullUrl(fu) ?: return
        // v4.01：FTP 协议下无协议前缀的输入按 ftp 解析（parseFullUrl 默认 https:443 会把端口框带成 443）
        val p = if (protocol == "FTP" && !fu.contains("://") && p0.scheme != "ftp") {
            p0.copy(scheme = "ftp", port = if (p0.port == 443) 21 else p0.port)
        } else p0
        host = p.host
        // v4.03：仅当 URL 显式写了端口才回填端口框——parseFullUrl 对无端口 URL 返回默认
        // (ftp:21/https:443/http:80)，无条件覆盖会把用户单独填的端口清掉（点浏览端口自动变 21）。
        if (hasExplicitPort(fu)) port = p.port.toString()
        // v3.96：记住用户 URL 里显式写的 scheme（https 非标准端口如 5006 无法靠端口白名单判断）
        // v4.01：FTP 协议下 httpsMode 强制 false（防止遗留 https 标记把 URL 组装成 https）
        httpsMode = if (protocol == "FTP") false else p.scheme == "https"
        // v3.99：URL 里的路径拆分到「下载目录」框（兼容老配置含路径的 URL；框里显示完整文件路径或目录）
        val pp = p.path
        if (overwritePath && pp.isNotEmpty() && pp != "/") path = pp
        // 协议联动：ftp:// 开头自动切 FTP，https:// 的 WebDAV 保持
        if (p.scheme == "ftp" && protocol != "FTP") protocol = "FTP"
        // v4.01：不再用 buildBaseUrl() 覆盖 fullUrl——URL 框保持用户输入原样
        //（用户反馈点浏览后 URL 框自动变成 ftp://host:port 难看；解析只回填 host/port/path）
    }

    // 首次初始化挪到 IO 线程，避免切页首次组合时在主线程做 SSL/共享参数/DB 查询而整帧卡顿
    // （用户实测：信号页 curr avg=2ms 流畅，切设置页 → 中间 SpeedTest/Tools 页被首组合，
    //  其 LaunchedEffect 主线程 DB/SSL 初始化与切页动画叠加导致 curr avg 飙到 99ms）。
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            trustAllCertificatesOnce()
            try {
                val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                protocol = sp.getString("last_type", "WebDAV")!!
                // 不再预填默认 FTP/WebDAV 服务器账号（此前预填了自建站 101.132.159.118/niceboat）
                host = sp.getString("last_host", "")!!
                val defPort = sp.getInt("last_port", -1)
                port = if (defPort > 0) defPort.toString() else ""
                path = sp.getString("last_path", "")!!
                username = sp.getString("last_user", "")!!
                password = sp.getString("last_pass", "")!!
                ftpPassive = sp.getBoolean("last_ftp_passive", true)
                // v4.08：恢复并发线程数（默认 4）
                threadCount = sp.getString("last_thread_count", "4") ?: "4"
                // v3.90：恢复完整 URL（优先 last_url；老配置则用 host/port/path 内联组装）
                val savedUrl = sp.getString("last_url", "") ?: ""
                if (savedUrl.isNotBlank()) {
                    // v4.05：先显式恢复 URL 框内容——applyFullUrl 从 v4.01 起不再写回 fullUrl
                    // （避免覆盖用户输入），若这里不赋值，重进页面 URL 框就是空（用户反馈「退出自动清空」）
                    fullUrl = savedUrl
                    applyFullUrl(savedUrl)
                }
                else if (host.isNotEmpty() && protocol != "HTTP") {
                    val pp = port.toIntOrNull() ?: when (protocol) { "FTP" -> 21; "WebDAV" -> 443; else -> 80 }
                    val scheme = when {
                        protocol == "FTP" -> "ftp://"
                        pp == 443 || pp == 8443 || pp == 10443 || pp == 1443 -> "https://"
                        else -> "http://"
                    }
                    // v3.96：老配置组装时同步 scheme 标记（443 等 https 端口）
                    httpsMode = scheme == "https://"
                    // v3.92：FTP 端口始终显式写入 URL（非默认端口服务器多，所见即所得，勿省略 21）
                    val hp = "$host:$pp"
                    fullUrl = "$scheme$hp${path.trim().ifEmpty { "/" }}"
                }
                // v3.98：恢复上行目录（老配置无此键 → 空串，用下载同目录）
                upDir = sp.getString("last_up_dir", "") ?: ""
                // v4.05：恢复后把当前协议配置写入 protoCfgMap——否则首次进页 protoCfgMap 全空，
                // 用户切走协议再切回时 restoreProto 拿到 null 直接清空（URL 框内容丢失的根因之一）。
                // 注意：此处不能调用 rememberCurrent()（其定义在下方，Kotlin 局部函数必须先声明后使用），
                // 因此内联写入。
                protoCfgMap[protocol] = ProtoCfg(host, port, path, username, password, fullUrl, upDir, httpsMode, ftpPassive)
                loadRecords()
            } catch (ignored: Exception) {}
        }
    }

    // v3.94：路径分段 URL 编码（中文/空格目录名），避免下载请求行含非法字符。
    // 已含 %XX 的段视为已编码跳过，防止双重编码。
    fun encodePathSegments(p: String): String {
        return try {
            p.split("/").joinToString("/") { seg ->
                if (seg.isEmpty() || seg == "." || seg == "..") seg
                else if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(seg)) seg
                else java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
        } catch (e: Exception) { p }
    }
    // v3.90：由内部 host/port 反向组装完整 URL（v3.99：只组装 协议://主机:端口，路径已独立为下载/上传目录框）
    // v3.92：FTP 端口始终显式写入 URL（非默认端口服务器多，所见即所得，勿省略 21）
    // v4.02：overwrite=true 仅用于加载老配置时补全 URL；文件对话框点目录/端口框改端口传 false——
    // 用户反馈「点浏览 URL 框自动带上 ftp://和端口」，URL 框应保持用户输入原样，由 host/port 驱动请求
    fun syncFullUrl(overwrite: Boolean = true) {
        if (protocol == "HTTP") return
        val h = host.trim(); if (h.isEmpty()) return
        if (overwrite) fullUrl = buildBaseUrl()
    }
    fun buildUrl(): String? {
        val h = host.trim(); if (h.isEmpty()) return null
        if (protocol == "HTTP") {
            var u = h
            if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("ftp://")) u = "https://" + u
            return u
        }
        var p = path.trim(); if (p.isEmpty()) p = "/"; if (!p.startsWith("/")) p = "/" + p
        // v3.94：路径分段 URL 编码（中文/空格目录），防止下载/上传请求行非法
        val ep = encodePathSegments(p)
        val cHost = cleanHost(h); val po = getPort()
        // v3.92：FTP 端口始终显式写入 URL（非默认端口服务器多，所见即所得，勿省略 21）
        if (protocol == "FTP") return "ftp://$cHost:$po$ep"
        val scheme = if (isHttps(h, po)) "https" else "http"
        return "$scheme://$cHost:$po$ep"
    }
    // v3.98：上传目录 URL（上行目录 upDir，空则回退下载路径 path 的父目录）；始终以 / 结尾
    fun buildUpUrl(): String? {
        val h = host.trim(); if (h.isEmpty()) return null
        var up = upDir.trim()
        if (up.isEmpty()) {
            val dp = path.trim()
            up = if (dp.isEmpty() || dp.endsWith("/")) dp.ifEmpty { "/" }
            else { val si = dp.lastIndexOf('/'); if (si > 0) dp.substring(0, si + 1) else "/" }
        }
        if (!up.startsWith("/")) up = "/" + up
        if (!up.endsWith("/")) up += "/"
        val ep = encodePathSegments(up)
        val cHost = cleanHost(h); val po = getPort()
        if (protocol == "FTP") return "ftp://$cHost:$po$ep"
        val scheme = if (isHttps(h, po)) "https" else "http"
        return "$scheme://$cHost:$po$ep"
    }
    fun setHttpAuth(http: HttpURLConnection, user: String, pass: String) {
        if (user.isNotEmpty()) {
            val auth = "$user:$pass"
            http.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP))
        }
    }
    fun buildFtpUrl(urlStr: String): String {
        if (protocol != "FTP") return urlStr
        val user = username.trim(); val pass = password.trim()
        if (user.isEmpty()) return urlStr
        return try {
            val u = URL(urlStr)
            "ftp://${java.net.URLEncoder.encode(user, "UTF-8")}:${java.net.URLEncoder.encode(pass, "UTF-8")}@${u.host}:${if (u.port > 0) u.port else 21}${u.path}"
        } catch (e: Exception) { urlStr }
    }
    fun saveCurrentConfig() {
        try {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().putString("last_type", protocol).putString("last_host", host.trim())
                .putInt("last_port", getPort()).putString("last_path", path.trim())
                .putString("last_user", username.trim()).putString("last_pass", password.trim())
                .putBoolean("last_ftp_passive", ftpPassive).putString("last_url", fullUrl)
                .putString("last_up_dir", upDir.trim())
                // v4.08：并发线程数持久化
                .putString("last_thread_count", threadCount).apply()
        } catch (ignored: Exception) {}
    }

    // ---- v3.99 协议独立：切换协议时把当前配置存入备份，再恢复目标协议上次的配置（互不污染）----
    fun rememberCurrent() {
        protoCfgMap[protocol] = ProtoCfg(host, port, path, username, password, fullUrl, upDir, httpsMode, ftpPassive)
    }
    fun restoreProto(p: String) {
        val c = protoCfgMap[p]
        if (c == null) {
            host = ""; port = when (p) { "FTP" -> "21"; "WebDAV" -> "443"; else -> "" }
            path = ""; username = ""; password = ""; fullUrl = ""; upDir = ""
            httpsMode = false; ftpPassive = true
        } else {
            host = c.host; port = c.port; path = c.path
            username = c.username; password = c.password
            fullUrl = c.fullUrl; upDir = c.upDir
            httpsMode = c.httpsMode; ftpPassive = c.ftpPassive
        }
    }
    fun switchProtocol(p: String) {
        if (p == protocol) return
        rememberCurrent()
        protocol = p
        restoreProto(p)
    }

    fun saveServer() {
        if (host.trim().isEmpty()) { apiToast(ctx, "请先输入服务器地址"); return }
        try {
            val entry = "$protocol|${host.trim()}|${getPort()}|${path.trim()}|${username.trim()}|${password.trim()}|$ftpPassive|$fullUrl|${upDir.trim()}"
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val servers = TreeSet(sp.getStringSet(KEY_SERVERS, TreeSet()) as Set<String>)
            servers.add(entry)
            sp.edit().putStringSet(KEY_SERVERS, servers).apply()
            saveCurrentConfig(); apiToast(ctx, "已保存 $protocol ${host.trim()}")
        } catch (e: Exception) { apiToast(ctx, "保存失败: ${e.message}") }
    }
    fun loadServer() {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val servers = sp.getStringSet(KEY_SERVERS, null)
        if (servers == null || servers.isEmpty()) { apiToast(ctx, "没有已保存的服务器"); return }
        loadDialogEntries = servers.toList()
        showLoadDialog = true
    }
    fun applyLoadedEntry(entry: String) {
        val a = entry.split('|')
        if (a.size >= 4) {
            when (a[0]) { "FTP" -> switchProtocol("FTP"); "WebDAV" -> switchProtocol("WebDAV"); else -> switchProtocol("HTTP") }
            host = a[1]; port = a[2]; path = a[3]
            if (a.size >= 5) username = a[4]
            if (a.size >= 6) password = a[5]
            if (a.size >= 7) ftpPassive = a[6] == "true"
            // v3.90：新条目带完整 URL；老条目无则用 host/port/path 组装
            // v4.07：老条目 syncFullUrl(false)——不覆盖 URL 框。用户反馈「点加载后 URL 框冒出 ftp:// 和端口」，
            // 根因是 syncFullUrl(overwrite=true) 用 buildBaseUrl() 组装 ftp://host:port/path 塞回 URL 框，
            // 而 URL 框应保持用户输入原样（v4.01 设计），host/port/path 分离框已填好，请求不受影响。
            if (a.size >= 8 && a[7].isNotBlank()) { fullUrl = a[7]; applyFullUrl(a[7]) }
            else syncFullUrl(false)
            // v3.98：新条目带上行目录；老条目无则保持当前 upDir
            if (a.size >= 9) upDir = a[8]
            saveCurrentConfig(); apiToast(ctx, "已加载 $a[0] ${a[1]}")
        } else apiToast(ctx, "该服务器记录格式无效")
    }
    fun deleteAllServers() {
        try {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().remove(KEY_SERVERS).apply(); loadDialogEntries = emptyList(); showLoadDialog = false
            apiToast(ctx, "已删除全部保存的服务器")
        } catch (e: Exception) { apiToast(ctx, "删除失败: ${e.message}") }
    }
    // v4.13：删除单个已保存的服务器（用户反馈「加载服务器里没有删除单个服务器的选项」）
    fun deleteServer(entry: String) {
        try {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val servers = TreeSet(sp.getStringSet(KEY_SERVERS, TreeSet()) as Set<String>)
            servers.remove(entry)
            sp.edit().putStringSet(KEY_SERVERS, servers).apply()
            loadDialogEntries = servers.toList()
            apiToast(ctx, "已删除该服务器")
        } catch (e: Exception) { apiToast(ctx, "删除失败: ${e.message}") }
    }
    fun applyPresetNode(url: String) {
        // v3.99：预设都是 HTTP(S) 直链——保存当前协议配置，恢复 HTTP 上次配置再覆盖地址
        rememberCurrent()
        protocol = "HTTP"
        restoreProto("HTTP")
        host = url
        httpsMode = url.startsWith("https://")
    }
    fun testServerConnection() {
        if (protocol != "HTTP" && fullUrl.isNotBlank()) applyFullUrl(fullUrl)
        val hh = host.trim(); if (hh.isEmpty()) { apiToast(ctx, "请先输入服务器地址"); return }
        val sType = protocol; val user = username.trim(); val pass = password.trim()
        val po = getPort()
        Thread {
            var result: String
            try {
                if (sType == "FTP") {
                    val cHost = cleanHost(hh)
                    // v3.92：FTP 端口始终显式（非默认端口服务器多，勿省略 21）
                    val hp = "$cHost:$po"
                    val u = if (user.isEmpty()) "ftp://$hp/"
                        else "ftp://${java.net.URLEncoder.encode(user, "UTF-8")}:${java.net.URLEncoder.encode(pass, "UTF-8")}@$hp/"
                    val conn = URL(u).openConnection(); conn.setConnectTimeout(10000); conn.setReadTimeout(10000)
                    conn.connect(); try { conn.getInputStream().close() } catch (ignored: Exception) {}
                    result = "连接成功 FTP服务器正常"
                } else {
                    // v3.99：测试连接用纯服务器地址（协议://主机:端口），不含文件路径
                    val url = buildBaseUrl()
                    if (url == null) result = "请先输入服务器地址"
                    else {
                        val http = URL(url).openConnection() as HttpURLConnection
                        http.setConnectTimeout(10000); http.setReadTimeout(10000)
                        http.setInstanceFollowRedirects(true); http.setRequestMethod("GET")
                        // v4.01：https 测试连接显式忽略证书
                        applyTrustAll(http)
                        setHttpAuth(http, user, pass)
                        val code = http.responseCode
                        result = when {
                            code == 200 || code == 207 -> "连接成功 ($code)  ${sType}服务器正常"
                            code == 401 || code == 403 -> "鉴权失败 ($code) —— 请检查账号密码"
                            code >= 400 -> "服务器返回 $code —— 路径可能不存在"
                            else -> "响应码 $code"
                        }
                        http.disconnect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "testServerConnection NPE/crash:", e)
                val m = e.message
                result = if (m != null && (m.contains("401") || m.contains("403"))) "鉴权失败 —— 请检查账号密码"
                    else "连接失败: ${e.javaClass.simpleName}" + (if (m != null) "\n$m" else "")
            }
            main.post { apiToast(ctx, result) }
        }.start()
    }
    // v3.98：浏览（forUpload=true 时浏览上传目录，选目录回填 upDir；默认浏览下载目录选文件）
    fun browseFiles(forUpload: Boolean = false) {
        if (protocol != "FTP" && protocol != "WebDAV") { apiToast(ctx, "文件浏览仅支持FTP/WebDAV"); return }
        // v4.07：浏览防抖——上一个浏览线程未返回时忽略重复点击
        if (isBrowsing) { apiToast(ctx, "正在浏览，请稍候..."); return }
        // v3.96：打开浏览=在看目录，重置文件选中标记（防止之前选过文件后浏览目录又关闭，下载误放行）
        // v3.98：仅下载浏览重置 pickedFile；上传目录浏览不影响已选的下载文件
        // v4.12：重置前先记录「已选中文件」——已选文件后再点浏览应回到其父目录，
        // 否则无扩展名文件（如 /down/100GB）会被下方「补斜杠当目录」逻辑浏览成空目录
        //（用户反馈「下载目录当前选的是文件还是让我选文件」：选中 100GB 后再次浏览进了空目录）。
        val wasFile = pickedFile && !forUpload
        if (!forUpload) pickedFile = false
        fileDialogForUpload = forUpload
        isBrowsing = true
        // v3.15 加固：整个浏览入口包 try/catch，确保任何异常（含 NPE）都不致崩溃，
        // 转成 toast 提示，而不是 crash。
        try {
            // v3.90：浏览前先从完整 URL 解析（用户可能只改了 URL 框没点别处）
            // v4.02：overwritePath=false——保留用户已在文件对话框选中的 path，URL 框带路径也不覆盖
            if (fullUrl.isNotBlank()) applyFullUrl(fullUrl, overwritePath = false)
            // v3.45：浏览前同步确保自签名证书被信任（trustAllCertificatesOnce @Synchronized 幂等），
            // 消除"首次进页 LaunchedEffect 的 IO 线程尚未执行完 → 点浏览 SSL 握手失败"竞态。
            trustAllCertificatesOnce()
            val hh = host.trim(); if (hh.isEmpty()) { apiToast(ctx, "请先输入服务器地址"); isBrowsing = false; return }
            // v3.98：上传目录浏览以 upDir 为基准（空则用下载 path）
            var p = (if (forUpload) upDir.trim().ifEmpty { path.trim() } else path.trim())
            if (p.isEmpty()) p = "/"; if (!p.startsWith("/")) p = "/" + p
            // v4.12：下载模式已选文件 → 浏览其父目录（回到选择起点，方便换文件）
            if (wasFile && !p.endsWith("/")) {
                val si = p.lastIndexOf('/')
                p = if (si > 0) p.substring(0, si + 1) else "/"
            }
            // v4.07：上传浏览起点不能用下载「选中的文件」路径——pickedFile=true 时 path 指向具体文件
            //（如 /down/100GB 无后缀测试文件），直接 CWD 550 directory not found（日志 09:35-09:37 反复失败）。
            // 上传目录与下载目录独立，起点取下载文件的父目录，不再把下载 path 当目录浏览。
            if (forUpload && pickedFile && !p.endsWith("/")) {
                val si = p.lastIndexOf('/')
                p = if (si > 0) p.substring(0, si + 1) else "/"
            }
            // v3.91 修复：URL 合一后无尾斜杠的路径按【目录】补 "/"（用户填 https://host/Downloads 期望浏览该目录，
            // 之前被 lastIndexOf 截断成浏览根目录 "/" → 根目录无内容时误报"目录为空"）。
            // 仅当路径末段含扩展名（如 /test.bin）才视为具体文件 → 浏览其父目录。
            if (!p.endsWith("/")) {
                val slash = p.lastIndexOf('/')
                val name = if (slash >= 0) p.substring(slash + 1) else p
                p = if (name.contains(".")) { if (slash > 0) p.substring(0, slash + 1) else "/" } else "$p/"
                // v4.07：仅下载浏览回写 path——上传浏览不得覆盖下载目录
                //（用户反馈「选完下载再选上传会影响下载的文件目录」，两目录应完全独立）
                if (!forUpload) path = p
            }
            val fPath = p; val user = username.trim(); val pass = password.trim()
            val proto = protocol; val po = getPort()
            Thread {
                var files: List<String> = emptyList(); var errMsg: String? = null
                // v3.98：TLS 握手失败（端口实际是明文 HTTP）自动回退 http 重试一次。
                // 非标准 HTTPS 端口（如 5006）若实际是 http 服务，https 握手报 "Unable to parse TLS packet header"。
                var schemeUsed = if (proto == "FTP") "" else if (isHttps(hh, po)) "https" else "http"
                for (attempt in 0..1) {
                    // v4.03：修复 FTP 浏览被 break 短路——原 `if (proto == "FTP") break` 让 FTP 第一轮
                    // 就退出循环，循环体里的 FTP LIST 代码从未执行（日志里连 browse FTP 埋点都没有，
                    // 直接落进「目录为空」）。FTP 不走 TLS 回退，但必须至少执行第一轮。
                    if (proto == "FTP" && attempt > 0) break
                    if (attempt == 1) {
                        // 仅当首次用 https 且失败是 TLS 相关时才回退 http
                        if (schemeUsed != "https" || errMsg == null || !errMsg.contains("TLS")) break
                        schemeUsed = "http"
                        AppLogger.w("SpeedTest", "browse TLS failed, retry with http")
                    }
                    errMsg = null; files = emptyList()
                    try {
                        if (proto == "FTP") {
                            val cHost = cleanHost(hh)
                            // v4.00：FTP 浏览 URL 埋点（密码打码），排查"FTP连接失败"时定位主机/端口/路径
                            AppLogger.i("SpeedTest", "browse FTP ftp://${if (user.isEmpty()) "" else "***:***@"}$cHost:$po$fPath (port=$po)")
                            // v4.10：FTP 浏览改用 Commons Net FTPClient.listFiles —— 取代 URLConnection(foo;type=d)，
                            // 数据连接/多行响应/目录判断全由开源库处理；listFiles 返回 FTPFile[] 直接给 isDirectory。
                            val ftp = org.apache.commons.net.ftp.FTPClient()
                            try {
                                ftp.setConnectTimeout(15000)
                                ftp.connect(cHost, po)
                                if (!ftp.login(if (user.isEmpty()) "anonymous" else user, if (pass.isEmpty() && user.isEmpty()) "guest@" else pass))
                                    throw java.io.IOException("FTP 登录失败 (${ftp.replyCode})")
                                ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
                                if (ftpPassive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
                                // v4.08：超时提到 20s——日志 09:58/09:59 大目录（/ul/ 888 项）8s 读超时 Read timed out
                                ftp.setSoTimeout(20000); ftp.setDataTimeout(20000)
                                ftp.setBufferSize(2 * 1024 * 1024)  // v4.17: 内核SO_RCVBUF/SNDBUF=1MB(Commons Net数据socket缓冲=setBufferSize值), 高带宽xRTT窗口  // v4.14：调大内部缓冲
                                val arr = ftp.listFiles(fPath)
                                val list = ArrayList<String>()
                                var rawCount = 0
                                val rawBuf = StringBuilder()
                                if (arr != null) {
                                    for (f in arr) {
                                        val nm = f.name
                                        if (nm == "." || nm == ".." || nm.isEmpty()) continue
                                        if (rawCount < 5) { rawBuf.append(nm).append(" | "); rawCount++ }
                                        list.add(if (f.isDirectory) "$nm/" else nm)
                                    }
                                }
                                AppLogger.i("SpeedTest", "browse FTP list raw=[$rawBuf] parsed=${list.size}")
                                files = list
                                try { ftp.logout() } catch (ignored: Exception) {}
                            } catch (ce: java.net.ConnectException) {
                                AppLogger.e("SpeedTest", "browse FTP connect refused: $po ${ce.message}")
                                errMsg = "FTP连接被拒绝（端口${po}不通）—— 服务器可能仅支持WebDAV/HTTPS"
                            } catch (ioe: java.io.IOException) {
                                AppLogger.e("SpeedTest", "browse FTP IO: ${ioe.message}")
                                errMsg = "FTP连接失败 —— 服务器可能仅支持WebDAV/HTTPS，请切换协议后重试"
                            } finally { try { ftp.disconnect() } catch (ignored: Exception) {} }
                        } else {
                            val cHost = cleanHost(hh)
                            val listUrl = "$schemeUsed://$cHost:$po$fPath"
                            var http: HttpURLConnection? = null; var is0: InputStream? = null; var rd: BufferedReader? = null
                            try {
                                // v4.05：WebDAV 浏览用裸 Socket PROPFIND + body（propfindRaw）优先。
                                // 根因回顾：HttpURLConnection.getOutputStream() 对非 POST/PUT 方法(PROPFIND)抛
                                // ProtocolException，旧代码 catch 吞掉 → body 从未发送 → 无 body 的 PROPFIND
                                // 在宽容服务器(5005)返回 207、严格服务器(5006 https)返回 405。裸 Socket 无方法
                                // 白名单限制，PROPFIND+body 100% 可靠发出（用户确认 01:30 超时是「没连内网」，
                                // 非 socket 方案问题）。连接失败时给出友好中文提示。
                                var code: Int = -1
                                var xmlStr: String = ""
                                try {
                                    val (rc, body) = propfindRaw(listUrl, user, pass)
                                    code = rc; xmlStr = body
                                    AppLogger.i("SpeedTest", "browse PROPFIND(raw) $listUrl -> HTTP $code body=${body.length}B")
                                } catch (pe: Exception) {
                                    // v4.05：连接失败 → 友好提示（区分常见场景）
                                    val hint = when (pe) {
                                        is java.net.SocketTimeoutException -> "连接超时——请确认已连接内网/VPN，或检查主机地址与端口是否正确"
                                        is java.net.ConnectException -> "无法连接到主机——请检查主机地址/端口是否正确，以及是否已连接内网/VPN"
                                        is java.net.UnknownHostException -> "无法解析主机地址——请检查 URL 是否填对"
                                        is javax.net.ssl.SSLException -> "SSL 握手失败——请检查 httpsMode/端口是否匹配服务器协议"
                                        else -> "${pe.javaClass.simpleName}: ${pe.message ?: "未知错误"}"
                                    }
                                    AppLogger.w("SpeedTest", "browse PROPFIND(raw) failed: ${pe.javaClass.simpleName} ${pe.message} -> $hint")
                                    errMsg = hint
                                }
                                if (!errMsg.isNullOrEmpty()) {
                                    // 连接失败已给友好提示，不再回退 GET（避免再报一次原生异常）
                                } else if (code == 207 || code == 200) {
                                    AppLogger.i("SpeedTest", "browse step: parse xml=${xmlStr.length}B")
                                    files = parseWebDavHrefs(xmlStr, fPath)
                                    AppLogger.i("SpeedTest", "browse parsed ${files.size} entries @ $fPath (xml ${xmlStr.length}B)")
                                    if (files.isEmpty()) {
                                        AppLogger.w("SpeedTest", "browse EMPTY raw=head=${xmlStr.substring(0, xmlStr.length.coerceAtMost(300))}")
                                    }
                                } else if (code == 401 || code == 403) {
                                    errMsg = "鉴权失败 ($code) —— 请检查账号密码"
                                } else {
                                    // PROPFIND 返回非 207/200（如 405/501/415）→ 回退 HttpURLConnection GET（旧路径）
                                    AppLogger.w("SpeedTest", "browse PROPFIND(raw) code=$code fallback GET")
                                    http = URL(listUrl).openConnection() as HttpURLConnection
                                    http.setConnectTimeout(15000); http.setReadTimeout(15000)
                                    http.setInstanceFollowRedirects(true); http.setRequestMethod("GET")
                                    applyTrustAll(http); setHttpAuth(http, user, pass)
                                    code = http.responseCode
                                    AppLogger.i("SpeedTest", "browse GET fallback $listUrl -> HTTP $code")
                                    if (code == 207 || code == 200) {
                                        is0 = http.inputStream
                                        if (is0 == null) { errMsg = "服务器返回空内容(HTTP $code)" }
                                        else {
                                            rd = BufferedReader(InputStreamReader(is0, "UTF-8"))
                                            val xml = StringBuilder()
                                            var line: String?
                                            while (rd.readLine().also { line = it } != null) xml.append(line)
                                            AppLogger.i("SpeedTest", "browse step: parse xml=${xml.length}B")
                                            files = parseWebDavHrefs(xml.toString(), fPath)
                                            AppLogger.i("SpeedTest", "browse parsed ${files.size} entries @ $fPath (xml ${xml.length}B)")
                                            if (files.isEmpty()) {
                                                AppLogger.w("SpeedTest", "browse EMPTY raw=head=${xml.substring(0, xml.length.coerceAtMost(300))}")
                                            }
                                        }
                                    } else if (code == 401 || code == 403) {
                                        errMsg = "鉴权失败 ($code) —— 请检查账号密码"
                                    } else {
                                        errMsg = "服务器返回 HTTP $code"
                                    }
                                }
                            } finally { try { rd?.close() } catch (ignored: Exception) {}; try { is0?.close() } catch (ignored: Exception) {}; try { http?.disconnect() } catch (ignored: Exception) {} }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "browseFiles IO NPE:", e)
                        // v3.47：把完整堆栈(不只是 message)写进 app.log——message 只含 'getClass() on null'，
                        // 无法定位行号；带堆栈后下一次导出即可见确切的 NPE 抛出位置。
                        AppLogger.e("SpeedTest", "browseFiles IO: ${e.javaClass.simpleName}: ${e.message}", e)
                        errMsg = e.javaClass.simpleName + ": " + (e.message ?: e.toString())
                    }
                    if (proto == "FTP" || schemeUsed == "http") break
                }
                val ff = files; val fe = errMsg
                main.post {
                    try {
                        // v4.07：先复位防抖标志再显示结果——否则弹窗出现瞬间 isBrowsing 仍为 true，
                        // 用户立即点目录进下级会被「正在浏览」误拦截
                        isBrowsing = false
                        when {
                            fe != null && fe.isNotEmpty() && ff.isEmpty() -> {
                                AppLogger.w("SpeedTest", "browse result: 列表失败 $fe @ $fPath")
                                apiToast(ctx, "列表失败: $fe")
                            }
                            ff.isEmpty() -> { AppLogger.w("SpeedTest", "browse result: 目录为空 @ $fPath"); apiToast(ctx, "目录为空: $fPath") }
                            else -> { fileDialogItems = sortFiles(ff); fileDialogPath = fPath; showFileDialog = true }
                        }
                    } catch (npe: Exception) { Log.e(TAG, "browseFiles UI NPE:", npe); AppLogger.e("SpeedTest", "browseFiles UI: ${npe.message}"); apiToast(ctx, "浏览出错，请重试") }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "browseFiles entry NPE:", e)
            AppLogger.e("SpeedTest", "browseFiles entry: ${e.message}")
            apiToast(ctx, "浏览出错: ${e.message}")
            isBrowsing = false
        }
    }

    // ---- v4.08 多连接并发（必须先于 executeDownload/executeUpload 声明，Kotlin 局部函数先声明后使用）----
    // 并发连接数 = 用户填的线程数（1-64），下载/上传共用。v4.06 的普通/极限模式已移除（用户实测不好用）。
    fun connCount(): Int = maxOf(1, minOf((threadCount.trim().toIntOrNull() ?: 4), 64))

    fun executeDownload(url: String) {
        if (!runFlag.get()) return
        loopRef.incrementAndGet(); val thisLoop = loopRef.get()
        val lc = (loopCount.trim().toIntOrNull() ?: 1)
        status = "下载中..."; loopInfo = if (lc > 1) "$thisLoop/$lc" else ""
        status = "下载中..."
        val isFtp = protocol == "FTP"; val isHttpMode = protocol == "HTTP"
        val downloadUrl = if (isFtp) buildFtpUrl(url) else url
        val srvType = protocol; val bg = totalBytes
        Thread {
            var fBytes = 0L; var finalSpeed = 0L; var dur = 1; var finalError: String? = null; var stopped = false
            if (isHttpMode) {
                // v4.08：连接数 = 用户填的线程数
                val poolSize = connCount()
                val tg = AtomicLong(0); val sg = AtomicLong(System.currentTimeMillis())
                val errRef = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val lastReport = AtomicLong(0)
                // v3.90 上报函数：每 100ms 刷新一次速率/流量（原 250ms 用户嫌慢），瞬时速率进折线图采样（下行）
                fun report() {
                    val now = System.currentTimeMillis()
                    val last = lastReport.get()
                    if (now - last >= 100 && lastReport.compareAndSet(last, now)) {
                        val el = now - sg.get(); val tb = tg.get()
                        if (el > 0 && tb > 0) {
                            val sp = tb * 1000L / el * 8
                            val bt = bg.get()
                            main.post {
                                speedValText = formatSpeed(sp)
                                trafficText = formatBytes(bt)
                                speedSamples = (speedSamples + Triple(now, sp.toDouble(), false)).takeLast(SAMPLE_MAX)
                            }
                        }
                    }
                }
                // 方案 A：先 HEAD 探测是否支持 Range 分段下载（真正让多线程叠加带宽）
                // 支持 Range → 每线程下载不同字节段；不支持/失败 → 每线程整段重复拉取。
                var ranged = false
                var totalLen = -1L
                try {
                    val probe = URL(downloadUrl).openConnection() as? HttpURLConnection
                    if (probe != null) {
                        probe.setInstanceFollowRedirects(true)
                        probe.setRequestMethod("HEAD")
                        probe.setConnectTimeout(5000); probe.setReadTimeout(8000)
                        val code = probe.responseCode
                        if (code in 200..299) {
                            try { totalLen = probe.getHeaderFieldLong("Content-Length", -1) } catch (ig: Exception) { totalLen = -1 }
                            val acceptRanges = probe.getHeaderField("Accept-Ranges")
                            ranged = totalLen > 0 && "bytes".equals(acceptRanges, ignoreCase = true)
                        }
                        probe.disconnect()
                    }
                } catch (ignored: Exception) { ranged = false }

                val latch = LinkedBlockingQueue<Unit>()
                for (i in 0 until poolSize) {
                    val ti = i
                    Thread {
                        try {
                            var head = 0L; var tail = -1L
                            if (ranged) {
                                // 每线程均匀分一段 [start, end)，最后一段到文件尾
                                val seg = totalLen / poolSize
                                head = ti * seg
                                tail = if (ti == poolSize - 1) totalLen - 1 else (ti + 1) * seg - 1
                            }
                            while (runFlag.get()) {
                                var is0: InputStream? = null; var http: HttpURLConnection? = null
                                try {
                                    val cn = URL(downloadUrl).openConnection(); cn.setConnectTimeout(5000); cn.setReadTimeout(15000)
                                    http = cn as HttpURLConnection
                                    // v4.00/v4.06：登记活动连接（多连接共享列表，stopTest 断开全部触发 IOException 退出）
                                    activeConns.add(http)
                                    http.setInstanceFollowRedirects(true)
                                    http.setRequestProperty("Accept-Encoding", "identity")
                                    http.setRequestProperty("Connection", "keep-alive")
                                    http.setRequestProperty("Cache-Control", "no-cache")
                                    http.setRequestProperty("User-Agent", "Mozilla/5.0 SpeedTest")
                                    if (ranged) http.setRequestProperty("Range", "bytes=$head-$tail")
                                    cn.connect(); if (!runFlag.get()) return@Thread
                                    is0 = cn.getInputStream(); val buf = ByteArray(65536); var n: Int
                                    while (is0.read(buf).also { n = it } > 0 && runFlag.get()) {
                                        tg.addAndGet(n.toLong()); bg.addAndGet(n.toLong()); report()
                                    }
                                } catch (e: java.io.IOException) {
                                    if (!runFlag.get()) return@Thread
                                    try { Thread.sleep(500) } catch (ignored: InterruptedException) {}
                                    if (errRef.get() == null) errRef.set(e.javaClass.simpleName + ": " + e.message)
                                } catch (e: Exception) {
                                    if (!runFlag.get()) return@Thread
                                    if (errRef.get() == null) errRef.set(e.javaClass.simpleName + ": " + e.message)
                                    break
                                } finally { try { is0?.close() } catch (ignored: Exception) {}; try { activeConns.remove(http); http?.disconnect() } catch (ignored: Exception) {} }
                            }
                        } finally { latch.add(Unit) }
                    }.start()
                }
                for (i in 0 until poolSize) { try { latch.poll(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (ie: InterruptedException) {} }
                val el = System.currentTimeMillis() - sg.get()
                fBytes = tg.get(); finalSpeed = if (el > 0) fBytes * 1000L / el * 8 else 0; dur = maxOf(1, (el / 1000).toInt())
                finalError = errRef.get(); stopped = !runFlag.get()
            } else if (isFtp) {
                // v4.08：FTP 多连接分片下载——手写 REST 偏移（ftpDownloadChunk），多连接叠加带宽。
                // 修复用户「下载跑不满一直波动」：此前 FTP 单连接（URLConnection 无 Range），线程数无效；
                // 日志 09:57 upload pool=8 520MB/19s≈27MB/s 证明服务器/链路没问题，单连接就是瓶颈。
                val fPool = connCount()
                val fTg = AtomicLong(0); val fSg = AtomicLong(System.currentTimeMillis())
                val fErr = java.util.concurrent.atomic.AtomicReference<String?>(null)
                // v4.18：瞬时速率改【增量窗口】——记录上次采样 (时间, 总字节)，速率=100ms 增量/增量时间。
                // 修复「上传一开始飙到几万 Mbps / 速率虚高」：v4.17 累计平均在首个数据字节后 el<1ms 时
                // tg×8000/0.001s = 天文数字；增量窗口首采样后稳定。
                val fLast = AtomicLong(System.currentTimeMillis())
                val fLastTg = AtomicLong(0)
                // v4.17：速率计时从「首个数据字节到达」开始（fFirst）——排除 SIZE 探测/PASV/建连握手耗时。
                val fFirst = AtomicLong(0)
                fun fReport() {
                    val now = System.currentTimeMillis()
                    val last = fLast.get()
                    if (now - last >= 100 && fLast.compareAndSet(last, now)) {
                        val tb = fTg.get()
                        val lt = fLastTg.getAndSet(tb)
                        val t0 = fFirst.get()
                        val dt = if (last > 0) (now - last) else (now - (if (t0 > 0) t0 else fSg.get()))
                        val db = (tb - lt).coerceAtLeast(0L)
                        if (dt >= 50 && db > 0) {
                            val sp = db * 1000L / dt * 8
                            main.post {
                                speedValText = formatSpeed(sp)
                                trafficText = formatBytes(bg.get())
                                speedSamples = (speedSamples + Triple(now, sp.toDouble(), false)).takeLast(SAMPLE_MAX)
                            }
                        }
                    }
                }
                val fHost = cleanHost(host.trim())
                val fPort = getPort()
                val fUser = username.trim(); val fPass = password.trim()
                // v4.10：FTP 命令参数用原始路径（RETR/SIZE 是命令字面量，URL 编码会让服务器找不到
                // 中文/空格文件名；encodePathSegments 仅用于 HTTP/WebDAV 的 URL 语义）
                val fPath = path.trim().ifEmpty { "/" }
                val totalLen = ftpFileSize(fHost, fPort, fUser, fPass, fPath, ftpPassive)
                AppLogger.i("SpeedTest", "ftp download pool=$fPool size=$totalLen path=$fPath")
                val fLatch = LinkedBlockingQueue<Unit>()
                // v4.16：取消分段——每线程固定起始偏移 REST 一次连续读，零切换（不闪）。
                // v4.18：**读自己的区段末尾**（length=fSeg），不再读到整个文件 EOF——
                // 修复日志虚高：connN=64 时 1GB 文件读了 4.15GB（重叠重复读，速率虚高 4 倍）。
                // 每线程读 [head, head+fSeg) 不重叠，总字节=totalLen，速率真实。
                // 线程起始偏移均分文件：i * (totalLen/fPool)，末线程读余量。
                val fSeg = if (totalLen > 0 && fPool > 0) totalLen / fPool else 0L
                for (fi in 0 until fPool) {
                    val ti = fi
                    Thread {
                        val dl = FtpChunkDownloader(fHost, fPort, fUser, fPass, ftpPassive)
                        try {
                            // SIZE 失败（fSeg=0）：单线程从 0 读到 EOF；成功：读自己区段
                            val head = if (fSeg > 0) ti * fSeg else 0L
                            val len = if (fSeg > 0) {
                                if (ti == fPool - 1) totalLen - head else fSeg
                            } else -1L
                            var failCount = 0  // v4.09：连续失败计数，>=3 快速退出（防 425 无限重试干等）
                            while (runFlag.get()) {
                                try {
                                    // v4.17：应用层读缓冲 64KB→128KB（文档建议 64-256KB，减少 syscall 次数）
                                    val buf = ByteArray(131072)
                                    // v4.12：onProgress 每读一块即上报（实时速率）；length 读够即停（区段读满退出）
                                    dl.chunk(fPath, head, len, buf,
                                        { n -> fTg.addAndGet(n.toLong()); bg.addAndGet(n.toLong()); if (fFirst.get() == 0L) fFirst.compareAndSet(0L, System.currentTimeMillis()); fReport() }) { !runFlag.get() }
                                    break
                                } catch (e: java.io.IOException) {
                                    if (!runFlag.get()) break
                                    // v4.09：连续失败快速退出——425/连接断开类错误重试无意义
                                    failCount++
                                    if (failCount >= 3) {
                                        if (fErr.get() == null) fErr.set(e.javaClass.simpleName + ": " + e.message)
                                        break
                                    }
                                    if (fErr.get() == null) fErr.set(e.javaClass.simpleName + ": " + e.message)
                                    try { Thread.sleep(500) } catch (ignored: InterruptedException) {}
                                } catch (e: Exception) {
                                    if (!runFlag.get()) break
                                    if (fErr.get() == null) fErr.set(e.javaClass.simpleName + ": " + e.message)
                                    break
                                }
                            }
                        } finally { dl.close(); fLatch.add(Unit) }
                    }.start()
                }
                for (i in 0 until fPool) { try { fLatch.poll(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (ie: InterruptedException) {} }
                val el = System.currentTimeMillis() - fSg.get()
                fBytes = fTg.get(); finalSpeed = if (el > 0) fBytes * 1000L / el * 8 else 0; dur = maxOf(1, (el / 1000).toInt())
                finalError = fErr.get(); stopped = !runFlag.get()
                AppLogger.i("SpeedTest", "ftp download done pool=$fPool bytes=$fBytes el=${el}ms ${if (finalError != null) "err=$finalError" else ""}")
            } else {
                // v4.08：WebDAV 多连接下载——Range 分片并发（服务器支持时），连接数=用户填的线程数
                val wdPool = connCount()
                val wTg = AtomicLong(0); val wSg = AtomicLong(System.currentTimeMillis())
                val wErr = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val wLast = AtomicLong(0)
                // 同 HTTP 分支：100ms 刷新 + 折线图采样
                fun wdReport() {
                    val now = System.currentTimeMillis()
                    val last = wLast.get()
                    if (now - last >= 100 && wLast.compareAndSet(last, now)) {
                        val el = now - wSg.get(); val tb = wTg.get()
                        if (el > 0 && tb > 0) {
                            val sp = tb * 1000L / el * 8
                            main.post {
                                speedValText = formatSpeed(sp)
                                trafficText = formatBytes(bg.get())
                                speedSamples = (speedSamples + Triple(now, sp.toDouble(), false)).takeLast(SAMPLE_MAX)
                            }
                        }
                    }
                }
                // HEAD 探测 Range 支持（带鉴权/trustAll）；不支持则各线程整段重复拉取（同 HTTP 分支行为）
                var wRanged = false; var wTotal = -1L
                try {
                    val probe = URL(downloadUrl).openConnection() as HttpURLConnection
                    applyTrustAll(probe)
                    setHttpAuth(probe, username.trim(), password.trim())
                    probe.setInstanceFollowRedirects(true)
                    probe.setRequestMethod("HEAD")
                    probe.setConnectTimeout(5000); probe.setReadTimeout(8000)
                    val code = probe.responseCode
                    if (code in 200..299) {
                        wTotal = try { probe.getHeaderFieldLong("Content-Length", -1) } catch (ig: Exception) { -1L }
                        wRanged = wTotal > 0 && "bytes".equals(probe.getHeaderField("Accept-Ranges"), ignoreCase = true)
                    }
                    probe.disconnect()
                } catch (ignored: Exception) { wRanged = false }
                AppLogger.i("SpeedTest", "dav download pool=$wdPool ranged=$wRanged len=$wTotal")
                val wLatch = LinkedBlockingQueue<Unit>()
                for (wi in 0 until wdPool) {
                    val ti = wi
                    Thread {
                        try {
                            var head = 0L; var tail = -1L
                            if (wRanged) {
                                val seg = wTotal / wdPool
                                head = ti * seg
                                tail = if (ti == wdPool - 1) wTotal - 1 else (ti + 1) * seg - 1
                            }
                            while (runFlag.get()) {
                                var is0: InputStream? = null; var http: HttpURLConnection? = null
                                try {
                                    // v3.95/v3.96：手动跟随重定向（最多3次），跨主机丢弃 Basic Auth（OSS 签名直链）
                                    val origHost = try { URL(downloadUrl).host } catch (e: Exception) { "" }
                                    var curUrl = downloadUrl
                                    var redirects = 0
                                    while (redirects <= 3 && runFlag.get()) {
                                        val cn = URL(curUrl).openConnection() as HttpURLConnection
                                        http = cn
                                        applyTrustAll(cn)
                                        activeConns.add(cn)
                                        cn.setConnectTimeout(5000); cn.setReadTimeout(15000)
                                        cn.setInstanceFollowRedirects(false)
                                        cn.setRequestProperty("Accept-Encoding", "identity")
                                        cn.setRequestProperty("Connection", "keep-alive")
                                        cn.setRequestProperty("Cache-Control", "no-cache")
                                        cn.setRequestProperty("User-Agent", "Mozilla/5.0 SpeedTest")
                                        val curHost = try { URL(curUrl).host } catch (e: Exception) { "" }
                                        if (curHost == origHost && origHost.isNotEmpty()) setHttpAuth(cn, username.trim(), password.trim())
                                        if (wRanged) cn.setRequestProperty("Range", "bytes=$head-$tail")
                                        cn.connect(); if (!runFlag.get()) return@Thread
                                        val code = cn.responseCode
                                        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                                            val loc = cn.getHeaderField("Location")
                                            try { cn.errorStream?.close() } catch (ignored: Exception) {}
                                            try { activeConns.remove(cn); cn.disconnect() } catch (ignored: Exception) {}
                                            if (loc.isNullOrBlank()) { if (wErr.get() == null) wErr.set("服务器返回 HTTP $code（无跳转地址）"); return@Thread }
                                            curUrl = try { URL(URL(curUrl), loc).toString() } catch (e: Exception) { loc }
                                            redirects++; continue
                                        }
                                        if (code != 200 && code != 206 && code != 207) {
                                            if (wErr.get() == null) wErr.set("服务器返回 HTTP $code")
                                            try { cn.errorStream?.close() } catch (ignored: Exception) {}
                                            return@Thread
                                        }
                                        // 识别"网页拦截"：下载返回 HTML 页面（登录页/错误页）
                                        val ct = cn.getHeaderField("Content-Type")
                                        if (ct != null && ct.contains("text/html") && code == 200) {
                                            val cll = try { cn.getHeaderFieldLong("Content-Length", -1) } catch (ig: Exception) { -1L }
                                            if (cll < 0 || cll < 200_000) {
                                                if (wErr.get() == null) wErr.set("服务器返回网页(HTML $cll B)，文件下载被拦截，可能需要登录或专用接口")
                                                try { cn.errorStream?.close() } catch (ignored: Exception) {}
                                                return@Thread
                                            }
                                        }
                                        is0 = cn.getInputStream(); val buf = ByteArray(65536); var n: Int
                                        while (is0.read(buf).also { n = it } > 0 && runFlag.get()) {
                                            wTg.addAndGet(n.toLong()); bg.addAndGet(n.toLong()); wdReport()
                                        }
                                        break
                                    }
                                } catch (e: java.io.IOException) {
                                    if (!runFlag.get()) return@Thread
                                    try { Thread.sleep(500) } catch (ignored: InterruptedException) {}
                                    if (wErr.get() == null) wErr.set(e.javaClass.simpleName + ": " + e.message)
                                } catch (e: Exception) {
                                    if (!runFlag.get()) return@Thread
                                    if (wErr.get() == null) wErr.set(e.javaClass.simpleName + ": " + e.message)
                                    break
                                } finally { try { is0?.close() } catch (ignored: Exception) {}; try { activeConns.remove(http); http?.disconnect() } catch (ignored: Exception) {} }
                            }
                        } finally { wLatch.add(Unit) }
                    }.start()
                }
                for (i in 0 until wdPool) { try { wLatch.poll(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (ie: InterruptedException) {} }
                val el = System.currentTimeMillis() - wSg.get()
                fBytes = wTg.get(); finalSpeed = if (el > 0) fBytes * 1000L / el * 8 else 0; dur = maxOf(1, (el / 1000).toInt())
                finalError = wErr.get(); stopped = !runFlag.get()
            }
            // 保存记录
            if (fBytes > 0) {
                try {
                    val r = SpeedTestRecord()
                    r.serverType = srvType; r.serverUrl = url; r.downloadSpeed = finalSpeed
                    r.dataSize = fBytes; r.duration = dur; r.setContinuous(lc > 1); r.setTime(System.currentTimeMillis())
                    dbHelper.insert(r)
                } catch (ignored: Exception) {}
            }
            val isLast = thisLoop >= lc
            val saveUrl = url
            main.post {
                loadRecords()
                if (stopped) status = "已停止"
                        else if (finalError != null) status = "错误: $finalError"
                    else status = "下载完成 ↓" + formatSpeed(finalSpeed) + "  " + formatBytes(fBytes)
                if (!stopped && runFlag.get() && thisLoop < lc) {
                    val li = (loopInterval.trim().toIntOrNull() ?: 5) * 1000L
                    main.postDelayed({ executeDownload(saveUrl) }, li)
                } else {
                    runFlag.set(false); isRunning = false; progressVisible = false
                    if (!stopped && isLast && lc > 1) status = "全部完成 (${lc}次)"
                }
            }
        }.start()
    }

    fun executeUpload(url: String, uploadSize: Long) {
        if (!runFlag.get()) return
        loopRef.incrementAndGet(); val thisLoop = loopRef.get()
        val lc = (loopCount.trim().toIntOrNull() ?: 1)
        status = "上传中..."; loopInfo = if (lc > 1) "$thisLoop/$lc" else ""
        val srvType = protocol; val bg = totalBytes
        Thread {
            // v4.08：上传连接池——poolSize=用户填的线程数。多连接 + 64KB 大块写（减少小包）。
            val poolSize = connCount()
            // v4.16：取消分段——FTP 每线程一个连接写一个大文件（perConn 写到底，零切换），
            // 修复上传「一闪一闪」：v4.15 动态 4MB 段每段 STOR+complete 有 RTT 空窗，且并发段文件
            // 加剧服务器写竞争。每线程一连接一文件写到底最平滑；并行度 = 线程框。
            val perConn = maxOf(1L, uploadSize / poolSize)
            val tg = AtomicLong(0); val sg = AtomicLong(System.currentTimeMillis())
            val errRef = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val latch = LinkedBlockingQueue<Unit>()
            for (pi in 0 until poolSize) {
                val ti = pi
                Thread {
                    try {
                        var connBytes = 0L
                        var os: OutputStream? = null; var httpRef: HttpURLConnection? = null
                        var ftpRef: org.apache.commons.net.ftp.FTPClient? = null
                        // v4.15：统一 100ms 节流上报
                        // v4.17：速率计时从「首个数据字节发送」开始（upFirst）——排除 connect/login/PASV/STOR 握手耗时
                        // v4.18：瞬时速率改【增量窗口】（上次采样时间/字节 → 100ms 增量）——修复「上传一开始飙到几万 Mbps」：
                        // v4.17 累计平均在首个 write 后 el<1ms 时 tg×8000/0.001s 天文数字。
                        val upFirst = AtomicLong(0)
                        val upLast = AtomicLong(System.currentTimeMillis())
                        val upLastTg = AtomicLong(0)
                        fun upReport() {
                            val now = System.currentTimeMillis()
                            val last = upLast.get()
                            if (now - last >= 100 && upLast.compareAndSet(last, now)) {
                                val tb = tg.get()
                                val lt = upLastTg.getAndSet(tb)
                                val t0 = upFirst.get()
                                val dt = if (last > 0) (now - last) else (now - (if (t0 > 0) t0 else sg.get()))
                                val db = (tb - lt).coerceAtLeast(0L)
                                if (dt >= 50 && db > 0) {
                                    val inst = db * 1000L / dt * 8
                                    main.post {
                                        speedValText = formatSpeed(inst)
                                        trafficText = formatBytes(bg.get())
                                        speedSamples = (speedSamples + Triple(now, inst.toDouble(), true)).takeLast(SAMPLE_MAX)
                                    }
                                }
                            }
                        }
                        fun upMarkFirst() { if (upFirst.get() == 0L) upFirst.compareAndSet(0L, System.currentTimeMillis()) }
                        try {
                            val u = URL(url); var conn = u.openConnection()
                            conn.setConnectTimeout(15000); conn.setReadTimeout(60000)
                            if (protocol == "FTP") {
                                // v4.16：取消分段——每线程一个连接写一个大文件（perConn 字节写到底，零切换）
                                val user = username.trim(); val pass = password.trim()
                                val fName = "upload_" + System.currentTimeMillis() + "_$ti.bin"
                                val p = if (u.path.endsWith("/")) u.path + fName else u.path + "/" + fName
                                val ftp = org.apache.commons.net.ftp.FTPClient()
                                ftp.setConnectTimeout(15000)
                                ftp.connect(u.host, if (u.port > 0) u.port else 21)
                                if (!ftp.login(if (user.isEmpty()) "anonymous" else user, if (pass.isEmpty() && user.isEmpty()) "guest@" else pass))
                                    throw java.io.IOException("FTP 登录失败 (${ftp.replyCode})")
                                ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
                                if (ftpPassive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
                                ftp.setSoTimeout(60000); ftp.setDataTimeout(60000)
                                ftp.setBufferSize(2 * 1024 * 1024)  // v4.17: 内核SO_RCVBUF/SNDBUF=1MB(Commons Net数据socket缓冲=setBufferSize值), 高带宽xRTT窗口
                                os = ftp.storeFileStream(p)
                                    ?: throw java.io.IOException("FTP STOR 失败 (${ftp.replyCode}) $p")
                                ftpRef = ftp
                                if (os != null) {
                                    val random = java.util.Random(); val bufSize = 65536; val buf = ByteArray(bufSize); var remaining = perConn
                                    while (remaining > 0 && runFlag.get()) {
                                        val n = if (remaining < bufSize) remaining.toInt() else bufSize
                                        random.nextBytes(buf); os.write(buf, 0, n)
                                        connBytes += n.toLong(); tg.addAndGet(n.toLong()); bg.addAndGet(n.toLong()); remaining -= n.toLong()
                                        upMarkFirst()
                                        upReport()
                                    }
                                    os.flush(); os.close()
                                }
                                // v4.10：写完须 completePendingCommand() 等服务器 226 确认（否则文件不完整）
                                if (ftpRef != null) {
                                    try { ftpRef!!.completePendingCommand() } catch (ignored: Exception) {}
                                }
                            } else if (conn is HttpURLConnection) {
                                val user = username.trim(); val pass = password.trim()
                                // 每连接独立文件名（并发 PUT 互不覆盖）
                                val fName = "upload_" + System.currentTimeMillis() + "_$ti.bin"
                                val p = if (u.path.endsWith("/")) u.path + fName else u.path + "/" + fName
                                // 用原始 URL 的 scheme（http/https），避免默认端口(port==-1)时误判为 http
                                val scheme = if (url.startsWith("https://")) "https"
                                    else if (url.startsWith("http://")) "http"
                                    else if (isHttps(u.host, u.port)) "https" else "http"
                                val hostPart = u.host
                                val portPart = if (u.port > 0 && !((scheme == "http" && u.port == 80) || (scheme == "https" && u.port == 443))) ":${u.port}" else ""
                                httpRef = URL("$scheme://$hostPart$portPart$p").openConnection() as HttpURLConnection
                                // v4.01：https 上传连接显式忽略证书
                                applyTrustAll(httpRef)
                                // v4.00/v4.06：登记活动连接，stopTest/startTest 时全部 disconnect 强制中断卡在 write 的上传
                                activeConns.add(httpRef)
                                httpRef.setRequestMethod("PUT"); httpRef.setDoOutput(true)
                                httpRef.setFixedLengthStreamingMode(perConn)
                                setHttpAuth(httpRef, user, pass); httpRef.setRequestProperty("Content-Type", "application/octet-stream")
                                os = httpRef.getOutputStream()
                                if (os != null) {
                                    // v4.06：64KB 大块写（原 16KB）——大 buffer 减少小包数量，降低对上行队列的冲击
                                    val random = java.util.Random(); val bufSize = 65536; val buf = ByteArray(bufSize); var remaining = perConn
                                    while (remaining > 0 && runFlag.get()) {
                                        val n = if (remaining < bufSize) remaining.toInt() else bufSize
                                        random.nextBytes(buf); os.write(buf, 0, n)
                                        connBytes += n.toLong(); tg.addAndGet(n.toLong()); bg.addAndGet(n.toLong()); remaining -= n.toLong()
                                        upMarkFirst()
                                        upReport()
                                    }
                                    os.flush(); os.close()
                                }
                                if (httpRef != null) {
                                    val code = httpRef.responseCode
                                    if (code != 200 && code != 201 && code != 204 && code != 207 && errRef.get() == null) errRef.set("服务器返回: $code")
                                }
                            }
                            // v4.10：FTP 写完须 completePendingCommand() 等服务器 226 确认（段内已处理，兜底）
                            if (ftpRef != null && os != null) {
                                try { ftpRef!!.completePendingCommand() } catch (ignored: Exception) {}
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "executeUpload NPE:", e)
                            if (runFlag.get() && errRef.get() == null) errRef.set(e.javaClass.simpleName + ": " + e.message)
                        } finally { try { os?.close() } catch (ignored: Exception) {}; try { ftpRef?.disconnect() } catch (ignored: Exception) {}; try { activeConns.remove(httpRef); httpRef?.disconnect() } catch (ignored: Exception) {} }
                    } finally { latch.add(Unit) }
                }.start()
            }
            for (i in 0 until poolSize) { try { latch.poll(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (ie: InterruptedException) {} }
            val el = System.currentTimeMillis() - sg.get()
            val fBytes = tg.get(); val finalSpeed = if (el > 0) fBytes * 1000L / el * 8 else 0; val dur = maxOf(1, (el / 1000).toInt())
            val error = errRef.get()
            // v4.00/v4.06：上传完成打日志（连接池尺寸/总字节/耗时/错误，供服务器日志分析）
            AppLogger.i("SpeedTest", "upload done pool=$poolSize bytes=$fBytes el=${el}ms ${if (error != null) "err=$error" else ""}")
            if (fBytes > 0) {
                try {
                    val r = SpeedTestRecord()
                    r.serverType = srvType; r.serverUrl = url; r.uploadSpeed = finalSpeed
                    r.dataSize = fBytes; r.duration = dur; r.setContinuous(lc > 1); r.setTime(System.currentTimeMillis())
                    dbHelper.insert(r)
                } catch (ignored: Exception) {}
            }
            val stopped = !runFlag.get()
            val isLast = thisLoop >= lc
            val saveUrl = url
            val saveSize = uploadSize
            main.post {
                loadRecords()
                if (stopped) status = "已停止"
                        else if (error != null) status = "错误: $error"
                    else status = "上传完成 ↑" + formatSpeed(finalSpeed) + "  " + formatBytes(fBytes)
                if (!stopped && runFlag.get() && thisLoop < lc) {
                    val li = (loopInterval.trim().toIntOrNull() ?: 5) * 1000L
                    main.postDelayed({ executeUpload(saveUrl, saveSize) }, li)
                } else {
                    runFlag.set(false); isRunning = false; progressVisible = false
                    if (!stopped && isLast && lc > 1) status = "全部完成 (${lc}次)"
                }
            }
        }.start()
    }

    // ---- 测速引擎：启动/停止 ----
    // v4.06：断开当前登记的全部活动连接（多连接并发后逐个断开；synchronizedList 迭代需加锁）
    fun disconnectAllConns() {
        synchronized(activeConns) {
            for (c in activeConns) { try { c.disconnect() } catch (ignored: Exception) {} }
            activeConns.clear()
        }
    }

    fun startTest() {
        // v4.19：硬防重入——并发测速会互抢带宽致速率失真（日志 15:52:44/15:52:56 连续两个 connN=1 start，
        // 单线程被拖到 1.44MB/s；12:46 pool=1 与 pool=64 同跑同理）。runFlag 与 isRunning 双保险。
        if (runFlag.get() || isRunning) { apiToast(ctx, "测速进行中，请先停止再开始"); return }
        // v3.90：WebDAV/FTP 支持完整 URL 合一输入，先解析回填
        if (protocol != "HTTP" && fullUrl.isNotBlank()) applyFullUrl(fullUrl)
        val url = buildUrl(); if (url == null) { apiToast(ctx, "请输入服务器地址"); return }
        val isHttpMode = protocol == "HTTP"; val isUpload = testMode == "upload"
        if (!isHttpMode) {
            val p = path.trim()
            // v3.98：上传模式优先用上行目录 upDir；为空时才用 path
            val up = upDir.trim()
            if (p.isEmpty() || p == "/") { apiToast(ctx, "请选择或输入文件路径（不能是目录）"); return }
            if (isUpload) {
                if (up.isNotEmpty() && !up.endsWith("/")) { apiToast(ctx, "上传目录请以/结尾（如 /download/）"); return }
                if (up.isEmpty() && !p.endsWith("/")) { apiToast(ctx, "上传模式请选择文件夹路径（以/结尾），或填写上传目录"); return }
            }
            else {
                if (p.endsWith("/")) { apiToast(ctx, "下载模式请选择具体文件，而不是文件夹"); return }
                // v4.13：取消 pickedFile 校验——保存/加载的服务器配置不含「已选文件」标记，
                // 退出重进后加载的路径（如 /down/100GB 无后缀测试文件）被误判为目录
                //（用户反馈「每次退出都让我重新选下载文件，当前是目录」）。
                // 目录拦截已由上方 p.endsWith("/") 覆盖；无后缀文件直接放行。
            }
        }
        saveCurrentConfig()
        var lc = (loopCount.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
        val li = (loopInterval.trim().toIntOrNull() ?: 5).coerceAtLeast(1)
        loopCount = lc.toString(); loopInterval = li.toString()
        // v4.10：FTP 已改用 Commons Net（enterLocalPassiveMode 显式控制），System.setProperty("ftp.passive") 仅
        // 作用于已废弃的 URLConnection FTP，删除——被动/主动由 ftpPassive 传参给 FTPClient。
        // v4.00/v4.06：启动新测试前强制断开上一次残留的所有活动连接（PUT 卡在 write 的线程会一直占着带宽，
        // 与本次测试竞争导致下载暴跌）。disconnect 后旧线程 write/read 抛 IOException 自动退出。
        disconnectAllConns()
        runFlag.set(true); isRunning = true; totalBytes.set(0); loopRef.set(0)
        resultVisible = true; progressVisible = true
        status = "连接中..."; loopInfo = if (lc > 1) "1/$lc" else ""
        speedLabel = if (isUpload) "上传速度" else "下载速度"
        if (isUpload) {
            // v4.14：上传大小自定义（MB 数值），非法/空回退预设 10MB
            val upMb = uploadSizeMb.trim().toFloatOrNull()
            val upBytes = if (upMb != null && upMb > 0f) (upMb * 1024f * 1024f).toLong()
                else UPLOAD_SIZE_BYTES[uploadSizeIndex]
            executeUpload(buildUpUrl() ?: url, upBytes)
        }
        else executeDownload(url)
        // v3.42/v4.08 全软件日志：测速开始（含并发连接数）
        AppLogger.i("SpeedTest", "start mode=${testMode} protocol=$protocol loop=$lc connN=${connCount()}")
    }
    fun stopTest() {
        runFlag.set(false); isRunning = false; status = "已停止"; progressVisible = false; loadRecords()
        // v4.00/v4.06：断开全部活动连接，强制中断卡在 read/write 的线程（OutputStream.write 无超时，仅靠 runFlag 停不掉）
        disconnectAllConns()
        // v3.42 全软件日志：测速停止
        AppLogger.i("SpeedTest", "stop")
    }

    // ==================== UI ====================
    val isHttp = protocol == "HTTP"

    // v4.05：退出页面自动保存当前配置——用户反馈「URL 框退出会自动清空」：
    // 根因是 saveCurrentConfig 只在 startTest/saveServer 时调用，填了 URL 未测速就退出页面
    // → last_url 没保存，重进恢复的是旧值/空串。这里 onDispose 兜底保存（含 fullUrl）。
    DisposableEffect(Unit) {
        onDispose { saveCurrentConfig() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ================= 速度仪表主卡（KernelSU StatusCard 风格：大色块 + 仪表盘）=================
        item {
            val heroContainer = if (isRunning) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            val heroOnContainer = if (isRunning) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            Card(
                colors = CardDefaults.defaultColors(color = heroContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // 标题行（KernelSU 风格圆形图标徽章 + 标题）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_speed_test),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isRunning) "测速进行中" else "网络测速",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            ),
                            color = heroOnContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (loopInfo.isNotEmpty()) {
                            Text(loopInfo, style = MaterialTheme.typography.labelSmall, color = heroOnContainer.copy(alpha = 0.6f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 环形仪表 + 中央大数字速度（Ookla 风格表盘）
                    val curSpeed = speedSamples.lastOrNull()?.second ?: 0.0
                    val maxSpeed = speedSamples.maxOfOrNull { it.second } ?: 0.0
                    val ringProgress = if (maxSpeed > 0) (curSpeed / (maxSpeed * 1.15)).toFloat().coerceIn(0.02f, 1f) else 0f
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                        CircularProgressIndicator(
                            progress = { if (isRunning || resultVisible) ringProgress else 0f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 10.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = heroOnContainer.copy(alpha = 0.1f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (resultVisible) speedLabel else "就绪", style = MaterialTheme.typography.labelMedium,
                                color = heroOnContainer.copy(alpha = 0.6f))
                            val sparts = if (resultVisible) speedValText.split(" ") else listOf("--")
                            val sNum = sparts.getOrElse(0) { "" }
                            Text(if (sNum.isEmpty()) "--" else sNum,
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (isRunning) MaterialTheme.colorScheme.primary else heroOnContainer)
                            Text(sparts.getOrElse(1) { "" }, style = MaterialTheme.typography.labelMedium,
                                color = heroOnContainer.copy(alpha = 0.6f))
                        }
                    }
                    // 统计三列：平均 / 峰值 / 流量
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val avg = if (speedSamples.size >= 2) speedSamples.map { it.second }.average().toLong() else 0L
                        StatCell("平均", if (avg > 0) formatSpeed(avg) else "--")
                        StatCell("峰值", if (maxSpeed > 0) formatSpeed(maxSpeed.toLong()) else "--")
                        StatCell("流量", if (resultVisible) trafficText else "--")
                    }
                    // 实时速率折线图
                    if (speedSamples.size >= 2) {
                        Spacer(Modifier.height(10.dp))
                        SpeedChart(speedSamples)
                    }
                    // 状态行
                    Spacer(Modifier.height(6.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, color = heroOnContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    // 大号开始/停止按钮
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { if (isRunning) stopTest() else startTest() },
                        colors = if (isRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            else ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (isRunning) "停止测速" else "开始测速", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { testServerConnection() },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("测试连接", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }

        // ================= 服务器配置卡 =================
        item {
            Card(
                colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    DashboardCardHeader(
                        icon = R.drawable.ic_cloud_connection,
                        title = "服务器配置",
                        action = {
                            SmallChip("保存") { saveServer() }
                            SmallChip("加载") { loadServer() }
                            SmallChip("预设节点") { showPresetDialogState = true; refreshNodesFromServer() }
                        }
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {

                    // 协议 SegmentedButton（v4.11：RadioButton → 分段选择器，参照 Material3 规范）
                    Text("协议", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("FTP", "WebDAV", "HTTP").forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = protocol == t,
                                onClick = { switchProtocol(t) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                            ) { Text(t, style = MaterialTheme.typography.labelMedium) }
                        }
                    }

                    // FTP 被动模式（仅 FTP）
                    if (protocol == "FTP") {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FTP模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Row(Modifier.selectable(selected = ftpPassive, onClick = { ftpPassive = true }), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = ftpPassive, onClick = { ftpPassive = true }); Text("被动(PASV)", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            Row(Modifier.selectable(selected = !ftpPassive, onClick = { ftpPassive = false }), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !ftpPassive, onClick = { ftpPassive = false }); Text("主动(PORT)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 地址（HTTP：完整直链输入）
                    if (isHttp) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("地址")
                            OutlinedTextField(
                                value = host, onValueChange = { host = it },
                                placeholder = { Text("https://example.com/path/file.bin", fontSize = 12.sp) },
                                singleLine = true, modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // v3.93 完整 URL（WebDAV/FTP）：协议://主机:端口 合一输入 + 独立端口输入框（双向联动）
                    if (!isHttp) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("URL")
                            OutlinedTextField(
                                value = fullUrl,
                                // v3.93：输入 URL 时实时回填端口框（host/path 无独立框不动，避免输入抖动）
                                onValueChange = { fu ->
                                    fullUrl = fu
                                    // v4.01：FTP 协议下无协议前缀的输入按 ftp 解析——parseFullUrl 对纯主机
                                    // 默认 https:443（190行 scheme="https"），会把 FTP 端口框带成 443。
                                    val p = if (protocol == "FTP" && !fu.contains("://")) {
                                        val t = parseFullUrl(fu)
                                        if (t != null) t.copy(scheme = "ftp", port = if (t.port == 443) 21 else t.port) else null
                                    } else parseFullUrl(fu)
                                    // v4.03：仅显式写了端口才回填端口框（无端口输入不覆盖用户端口框的值）
                                    if (p != null && hasExplicitPort(fu) && p.port > 0 && p.port.toString() != port) port = p.port.toString()
                                    // v3.99：输入时同步 httpsMode（非标准 https 端口 5006 等不被白名单识别）
                                    // v4.01：FTP 协议下强制 false
                                    if (p != null) httpsMode = if (protocol == "FTP") false else p.scheme == "https"
                                },
                                placeholder = { Text(if (protocol == "FTP") "ftp://192.168.1.1:21" else "https://example.com:443", fontSize = 12.sp) },
                                singleLine = true, modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                        // v3.99：下载目录（下载测速的文件路径/目录，浏览选择回填；与 URL 分离）
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("下载目录")
                            OutlinedTextField(
                                value = path, onValueChange = { path = it; pickedFile = false },
                                placeholder = { Text("如 /dav/音乐/01.flac 或 /dav/", fontSize = 12.sp) },
                                singleLine = true, modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(onClick = { browseFiles() }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text("浏览", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        // v3.93：独立端口输入框——仅 FTP 显示（用户要求 WebDAV 不用填端口，默认 443 走 URL）
                        if (protocol == "FTP") {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FieldLabel("端口")
                                OutlinedTextField(
                                    value = port, onValueChange = { port = it; syncFullUrl(false) },
                                    placeholder = { Text("21", fontSize = 12.sp) },
                                    singleLine = true, modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("默认 21，留空用默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        // v3.98：上行目录（上传模式的上传目标；留空=用下载路径同目录）
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("上传目录")
                            OutlinedTextField(
                                value = upDir, onValueChange = { upDir = it },
                                placeholder = { Text("留空=与下载同目录，如 /dav/upload/", fontSize = 12.sp) },
                                singleLine = true, modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(onClick = { browseFiles(true) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text("浏览", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // 用户名 / 密码（非 HTTP）
                    if (!isHttp) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("用户")
                            OutlinedTextField(value = username, onValueChange = { username = it },
                                placeholder = { Text("（选填）", fontSize = 12.sp) }, singleLine = true,
                                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("密码")
                            OutlinedTextField(value = password, onValueChange = { password = it },
                                placeholder = { Text("（选填）", fontSize = 12.sp) }, singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(50.dp), textStyle = MaterialTheme.typography.bodySmall)
                        }
                    }
                    }
                }
            }
        }

        // ================= 测速参数卡 =================
        item {
            Card(
                colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    DashboardCardHeader(
                        icon = R.drawable.ic_settings,
                        title = "测速参数"
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                    // 测速模式（v4.11：RadioButton → SegmentedButton 下载/上传）
                    Text("测速模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = testMode == "download",
                            onClick = { testMode = "download"; speedLabel = "下载速度" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("下载测速", style = MaterialTheme.typography.labelMedium) }
                        SegmentedButton(
                            selected = testMode == "upload",
                            onClick = { testMode = "upload"; speedLabel = "上传速度" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("上传测速", style = MaterialTheme.typography.labelMedium) }
                    }

                    // 上传大小（上传模式）——v4.14：自定义 MB 输入框 + 预设选项按钮
                    if (testMode == "upload") {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("大小")
                            OutlinedTextField(
                                value = uploadSizeMb, onValueChange = { uploadSizeMb = it },
                                placeholder = { Text("10", fontSize = 12.sp) },
                                singleLine = true, modifier = Modifier.width(80.dp).height(48.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { showUploadDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("预设", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("(上传用)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // 持续测试（非 HTTP 显示 次数/间隔；线程常驻）
                    if (!isHttp) {
                        Spacer(Modifier.height(4.dp))
                        Text("持续测试", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        // v4.08：并发线程数输入框
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("线程", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(value = threadCount, onValueChange = { threadCount = it },
                                singleLine = true, modifier = Modifier.width(70.dp).height(48.dp),
                                shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text("(1-64)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (!isHttp) {
                            Spacer(Modifier.width(14.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("次数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(value = loopCount, onValueChange = { loopCount = it },
                                    singleLine = true, modifier = Modifier.width(60.dp).height(48.dp),
                                    shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("间隔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(value = loopInterval, onValueChange = { loopInterval = it },
                                    singleLine = true, modifier = Modifier.width(60.dp).height(48.dp),
                                    shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(2.dp))
                                Text("秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    // v4.17：FTP 单流限制提示（文档要点：单 TCP 流受带宽×时延乘积限制，高带宽高 RTT 跑不满属协议特性）
                    if (protocol == "FTP") {
                        Spacer(Modifier.height(6.dp))
                        Text("FTP 单连接测速受 TCP 单流限制，高带宽/高延迟下建议增加线程数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2)
                    }
                    }
                }
            }
        }

        // ---- 最近测速记录 ----
        item {
            Text(
                text = "最近测速记录",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("时间 / 服务器", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                Text("速度 / 流量", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
            }
        }
        if (records.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
                ) {
                    Text("暂无测速记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(20.dp))
                }
            }
        } else {
            items(records, key = { it.id }) { r -> SpeedRecordRow(r) }
        }
    }

    // ==================== 统一 Compose M3 弹窗 ====================
    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("选择上传文件大小") },
            text = {
                Column {
                    UPLOAD_SIZE_OPTIONS.forEachIndexed { i, opt ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = uploadSizeIndex == i,
                                onClick = {
                                    uploadSizeIndex = i
                                    // v4.14：选预设同步回填自定义输入框（数字部分，如 "50 MB" → "50"）
                                    uploadSizeMb = opt.removeSuffix(" MB").trim()
                                    showUploadDialog = false
                                })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uploadSizeIndex == i, onClick = {
                                uploadSizeIndex = i
                                uploadSizeMb = opt.removeSuffix(" MB").trim()
                                showUploadDialog = false
                            })
                            Text(opt, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUploadDialog = false }) { Text("取消") } }
        )
    }

    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("加载服务器") },
            text = {
                if (loadDialogEntries.isEmpty()) Text("暂无已保存的服务器", style = MaterialTheme.typography.bodyMedium)
                else {
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(loadDialogEntries) { e ->
                            val a = e.split('|')
                            val title = (a.getOrElse(0) { "?" }) + "  " + a.getOrElse(1) { "" }
                            val sub = if (a.size > 3) a[3] else ""
                            // v4.13：每条记录加「删除」按钮（用户反馈没有删除单个服务器的选项）
                            ListItem(
                                headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall) },
                                trailingContent = {
                                    IconButton(onClick = { deleteServer(e) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { applyLoadedEntry(e); showLoadDialog = false }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text("取消") }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllServers() }) { Text("删除全部", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (showPresetDialogState) {
        AlertDialog(
            onDismissRequest = { showPresetDialogState = false },
            title = { Text("选择测速节点") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (nodeLoading) "正在从服务器获取节点…"
                            else "来源：" + nodeSource + " · 共 " + nodeList.size + " 个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        TextButton(onClick = { refreshNodesFromServer() }, enabled = !nodeLoading) { Text("刷新") }
                    }
                    if (nodeLoading && nodeList.isEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                    LazyColumn(Modifier.heightIn(max = 330.dp)) {
                        items(nodeList) { node ->
                            Column(
                                Modifier.fillMaxWidth().clickable { applyPresetNode(node.url); showPresetDialogState = false }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(node.name, style = MaterialTheme.typography.bodyMedium)
                                Text(node.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresetDialogState = false }) { Text("取消") } }
        )
    }

    if (showFileDialog) {
        AlertDialog(
            onDismissRequest = { showFileDialog = false },
            title = { Text(if (fileDialogForUpload) "选择上传目录 - $fileDialogPath" else "选择文件 - $fileDialogPath") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    // v4.07：上传目录模式——确认按钮让位给「使用此目录」，返回上级改为列表顶部虚拟项
                    if (fileDialogForUpload) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val cp = fileDialogPath.trim()
                                    val idx = cp.lastIndexOf('/', cp.length - 2)
                                    val up = if (idx > 0) cp.substring(0, idx + 1) else "/"
                                    upDir = up; showFileDialog = false; browseFiles(true)
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\u2B06\uFE0F ", style = MaterialTheme.typography.bodyMedium)
                                Text("返回上级", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    items(fileDialogItems) { name ->
                        val isDir = name.endsWith("/")
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // v3.98：上传目录浏览——点目录进下级并回填 upDir；点文件回填文件所在目录
                                if (fileDialogForUpload) {
                                    if (isDir) {
                                        upDir = fileDialogPath + name
                                        showFileDialog = false
                                        browseFiles(true)
                                    } else {
                                        upDir = fileDialogPath
                                        showFileDialog = false
                                    }
                                } else {
                                    if (isDir) {
                                        pickedFile = false
                                        path = fileDialogPath + name
                                        syncFullUrl(false)
                                        showFileDialog = false
                                        browseFiles()
                                    } else {
                                        pickedFile = true
                                        path = fileDialogPath + name
                                        syncFullUrl(false)
                                        showFileDialog = false
                                    }
                                }
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isDir) "\uD83D\uDCC1 " else "\uD83D\uDCC4 ", style = MaterialTheme.typography.bodyMedium)
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (fileDialogForUpload) {
                    // v4.07：上传目录模式——「使用此目录」确认当前目录为上传目标（用户反馈上传选文件夹没有确认按钮）
                    TextButton(onClick = { upDir = fileDialogPath; showFileDialog = false }) { Text("使用此目录") }
                } else {
                    // 返回上级
                    TextButton(onClick = {
                        val cp = fileDialogPath.trim()
                        val idx = cp.lastIndexOf('/', cp.length - 2)
                        val up = if (idx > 0) cp.substring(0, idx + 1) else "/"
                        pickedFile = false
                        path = up
                        syncFullUrl(false)
                        showFileDialog = false
                        browseFiles()
                    }) { Text("返回上级") }
                }
            },
            dismissButton = { TextButton(onClick = { showFileDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

// v3.90 折线图：实时速率曲线（上行橙 / 下行蓝），自动缩放 y 轴、右侧标注峰值
@Composable
private fun SpeedChart(samples: List<Triple<Long, Double, Boolean>>) {
    val upColor = Color(0xFFFF9800)
    val downColor = Color(0xFF2196F3)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        if (samples.size < 2) return@Canvas
        val w = size.width; val h = size.height
        val t0 = samples.first().first
        val t1 = samples.last().first
        val span = (t1 - t0).coerceAtLeast(1L)
        val maxV = samples.maxOf { it.second }.coerceAtLeast(1.0)
        // 水平网格线（4 格）
        val gridColor = Color(0x22000000)
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        // 上行 / 下行折线（同方向连续段画线，方向切换时 moveTo 断线）
        val upPath = Path(); val downPath = Path()
        var upStarted = false; var downStarted = false
        samples.forEachIndexed { i, (t, v, up) ->
            val x = ((t - t0) * w / span).toFloat()
            val y = (h - (v / maxV * h).toFloat()).coerceIn(0f, h)
            if (up) {
                if (!upStarted) { upPath.moveTo(x, y); upStarted = true } else upPath.lineTo(x, y)
            } else {
                if (!downStarted) { downPath.moveTo(x, y); downStarted = true } else downPath.lineTo(x, y)
            }
        }
        if (downStarted) drawPath(downPath, downColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        if (upStarted) drawPath(upPath, upColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        // 峰值标注
        val maxEntry = samples.maxByOrNull { it.second }
        if (maxEntry != null) {
            val mx = ((maxEntry.first - t0) * w / span).toFloat()
            val my = (h - (maxEntry.second / maxV * h).toFloat()).coerceIn(0f, h)
            drawCircle(if (maxEntry.third) upColor else downColor, radius = 4f, center = Offset(mx, my))
        }
        // 图例 + 峰值文字（Compose Canvas 文字必须走 drawIntoCanvas）
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 26f
                color = android.graphics.Color.parseColor("#8A8A8A")
            }
            val peak = formatSpeed(maxV.toLong())
            c.nativeCanvas.drawText("↓ 下行", 10f, h - 10f, paint)
            c.nativeCanvas.drawText("↑ 上行", 86f, h - 10f, paint)
            paint.color = android.graphics.Color.parseColor("#666666")
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            c.nativeCanvas.drawText("峰值 $peak", w - 10f, h - 10f, paint)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    // v3.99：固定标签宽度（最长为4字）使各输入框左缘对齐
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(76.dp).padding(end = 6.dp, bottom = 0.dp))
}

// v4.11：速度仪表统计单元（Ookla 风格：平均/峰值/流量 三列）
@Composable
private fun StatCell(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun portHint(protocol: String): String = when (protocol) { "FTP" -> "21"; "WebDAV" -> "443"; else -> "80" }

@Composable
private fun SpeedRecordRow(r: SpeedTestRecord) {
    Card(
        colors = CardDefaults.defaultColors(color = DashboardCardDefaults.cardContainerColor()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.timeText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.height(2.dp))
                Text(r.serverType + "  " + r.serverUrl, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val speedText = if (r.uploadSpeed > 0 && r.downloadSpeed == 0L)
                    "↑" + formatSpeed(r.uploadSpeed)
                else "↓" + formatSpeed(r.downloadSpeed)
                Text(speedText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Text(r.dataSizeText + "  " + r.duration + "s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
