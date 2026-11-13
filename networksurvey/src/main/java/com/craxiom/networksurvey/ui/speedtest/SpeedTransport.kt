package com.craxiom.networksurvey.ui.speedtest

import android.util.Xml
import kotlinx.coroutines.*
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.time.Duration
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

internal enum class SpeedProtocol(val label: String) { HTTP("HTTP(S)"), WebDAV("WebDAV"), FTP("FTP") }

internal data class SpeedServer(
    val protocol: SpeedProtocol,
    val address: String = "",
    val downloadPath: String = "",
    val uploadPath: String = "",
    val user: String = "",
    val password: String = "",
    val passive: Boolean = true,
) {
    fun http(): HttpUrl {
        val url = address.trim().let { if ("://" in it) it else "https://$it" }.toHttpUrlOrNull()
        require(url != null && url.username.isEmpty() && url.password.isEmpty()) { "请输入有效的 HTTP(S) 地址" }
        return url.newBuilder().fragment(null).build()
    }

    fun ftp(): URI {
        val uri = runCatching {
            URI(address.trim().let { if ("://" in it) it else "ftp://$it" }.replace(" ", "%20"))
        }.getOrNull()
        require(uri != null && uri.scheme.equals("ftp", true) && uri.host != null && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)) {
            "请输入有效的 FTP 地址，账号请填在独立字段"
        }
        return uri
    }

    fun download(): String = "/" + downloadPath.trim().ifEmpty {
        if (protocol == SpeedProtocol.FTP) ftp().path.orEmpty() else http().encodedPath
    }.trimStart('/')

    fun upload(): String {
        val configured = uploadPath.trim()
        if (configured.isNotEmpty()) return configured.trim('/').let { if (it.isEmpty()) "/" else "/$it/" }
        return download().let { if (it.endsWith('/')) it else parentDirectory(it) }
    }

    fun endpoint(upload: Boolean): String {
        if (protocol == SpeedProtocol.HTTP) return http().toString()
        val path = if (upload) upload() else download().also {
            require(!it.endsWith('/')) { "请选择具体的下载测速文件" }
        }
        require(path.none { it == '\r' || it == '\n' || it == '\u0000' }) { "服务器路径含有无效字符" }
        return if (protocol == SpeedProtocol.FTP) ftp().let { URI("ftp", null, it.host, it.port, path, null, null).toASCIIString() }
        else dav(path).toString()
    }

    fun dav(path: String): HttpUrl = http().newBuilder().encodedPath(path
        .replace(" ", "%20").replace("#", "%23").replace("?", "%3F")).query(null).build()
}

internal fun parentDirectory(path: String) = path.trimEnd('/').substringBeforeLast('/', "") + "/"
internal data class SpeedEntry(val name: String, val path: String, val directory: Boolean)
internal data class SpeedSample(val bytes: Long, val bitsPerSecond: Long)
internal data class SpeedResult(val bytes: Long, val seconds: Double, val peak: Long) {
    val average get() = speedRate(bytes, seconds)
}

private fun speedRate(bytes: Long, seconds: Double) =
    (bytes.toDouble() * 8.0 / seconds.coerceAtLeast(0.001)).roundToLong()

private const val BUFFER_SIZE = 64 * 1024
private val redirects = setOf(301, 302, 303, 307, 308)
private val binary = "application/octet-stream".toMediaType()
private val propfind = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
    .toRequestBody("application/xml; charset=utf-8".toMediaType())

internal class SpeedTransport(private val server: SpeedServer) : Closeable {
    private val closed = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val sockets = ConcurrentHashMap.newKeySet<Closeable>()
    private val origin = if (server.protocol == SpeedProtocol.FTP) null else server.http()
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        calls.forEach(Call::cancel)
        sockets.forEach { runCatching { it.close() } }
        client.connectionPool.evictAll()
    }

    private fun checkOpen() {
        if (closed.get()) throw IOException("测速已停止")
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.host == b.host && a.port == b.port && a.scheme == b.scheme

    private fun request(url: HttpUrl): Request.Builder {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .cacheControl(CacheControl.FORCE_NETWORK)
        if (server.protocol == SpeedProtocol.WebDAV && server.user.isNotEmpty() && origin?.let { sameOrigin(it, url) } == true) {
            request.header("Authorization", Credentials.basic(server.user, server.password, Charsets.UTF_8))
        }
        return request
    }

    private fun <T> execute(request: Request, block: (Response) -> T): T {
        val call = client.newCall(request)
        calls.add(call)
        try {
            checkOpen()
            return call.execute().use(block)
        } finally {
            calls.remove(call)
        }
    }

    private fun <T : Any> read(
        url: HttpUrl, sameServer: Boolean = false,
        build: Request.Builder.() -> Unit = {}, block: (Response, HttpUrl) -> T,
    ): T {
        var current = url
        repeat(6) {
            val (next, value) = execute(request(current).apply(build).build()) { response ->
                if (response.code !in redirects) null to block(response, current)
                else {
                    val next = response.header("Location")?.let(current::resolve)
                        ?: throw IOException("服务器重定向地址无效")
                    if ((current.isHttps && !next.isHttps) || (sameServer && !sameOrigin(url, next)) ||
                        next.username.isNotEmpty() || next.password.isNotEmpty()) {
                        throw IOException("服务器返回不允许的重定向")
                    }
                    next to null
                }
            }
            if (next == null) return requireNotNull(value)
            current = next
        }
        throw IOException("服务器重定向次数过多")
    }

    private fun Response.success() {
        if (!isSuccessful) throw IOException(when (code) {
            401, 403 -> "服务器拒绝访问（HTTP $code）"
            404 -> "找不到服务器文件（HTTP 404）"
            else -> "服务器返回 HTTP $code"
        })
    }

    private fun Response.file() {
        success()
        if (code != 200) throw IOException("下载响应 HTTP $code，预期完整文件")
        if (body?.contentType()?.subtype in setOf("html", "xhtml+xml")) throw IOException("服务器返回网页，请使用文件直链")
        if (request.method != "HEAD" && body?.contentLength() == 0L) throw IOException("测速文件为空")
    }

    private fun <T> ftp(block: (FTPClient) -> T): T {
        val uri = server.ftp()
        val ftp = FTPClient()
        val disconnect = Closeable { if (ftp.isConnected) ftp.disconnect() }
        sockets.add(disconnect)
        try {
            checkOpen()
            require(listOf(server.user, server.password).none { value ->
                value.any { it == '\r' || it == '\n' || it == '\u0000' }
            }) { "FTP 凭据含有无效字符" }
            ftp.connectTimeout = 8_000
            ftp.defaultTimeout = 8_000
            ftp.setDataTimeout(Duration.ofSeconds(8))
            ftp.controlEncoding = "UTF-8"
            ftp.setAutodetectUTF8(true)
            ftp.connect(uri.host, if (uri.port >= 0) uri.port else 21)
            checkOpen()
            ftp.soTimeout = 8_000
            if (!FTPReply.isPositiveCompletion(ftp.replyCode) ||
                !ftp.login(server.user.ifEmpty { "anonymous" }, server.password.ifEmpty {
                    if (server.user.isEmpty()) "anonymous@" else ""
                })) throw IOException("FTP 登录失败（${ftp.replyCode}）")
            if (server.passive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
            if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) throw IOException("FTP 服务器拒绝二进制模式")
            return block(ftp)
        } finally {
            sockets.remove(disconnect)
            runCatching { disconnect.close() }
        }
    }

    private fun <T : Closeable> stream(stream: T, block: (T) -> Unit) {
        sockets.add(stream)
        try {
            stream.use {
                checkOpen()
                block(it)
            }
        } finally {
            sockets.remove(stream)
        }
    }

    suspend fun check(upload: Boolean): String = withContext(Dispatchers.IO) {
        val target = server.endpoint(upload)
        if (upload || server.protocol == SpeedProtocol.FTP) {
            if (server.protocol == SpeedProtocol.FTP && !upload) ftp {
                val input = it.retrieveFileStream(server.download()) ?: throw IOException("FTP 无法读取该文件（${it.replyCode}）")
                stream(input) { source -> if (source.read() < 0) throw IOException("测速文件为空") }
            } else list(server.upload())
        } else {
            val url = requireNotNull(target.toHttpUrlOrNull())
            val hasHead = read(url, build = { head() }) { response, _ ->
                if (response.code == 405 || response.code == 501) false else { response.file(); true }
            }
            if (!hasHead) read(url) { response, _ ->
                response.file()
                if (requireNotNull(response.body).byteStream().read() < 0) throw IOException("测速文件为空")
            }
        }
        if (upload) "上传目录可访问" else "测速文件可访问"
    }

    suspend fun list(path: String): List<SpeedEntry> = withContext(Dispatchers.IO) {
        val directory = path.trimEnd('/') + "/"
        require(directory.none { it == '\r' || it == '\n' || it == '\u0000' }) { "服务器路径含有无效字符" }
        val entries = if (server.protocol == SpeedProtocol.FTP) ftp { ftp ->
            if (!ftp.changeWorkingDirectory(directory)) throw IOException("FTP 目录无法访问（${ftp.replyCode}）")
            val files = ftp.listFiles()
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) throw IOException("FTP 无法列目录（${ftp.replyCode}）")
            files.filter { it.name != "." && it.name != ".." }.map {
                SpeedEntry(it.name, directory + it.name + if (it.isDirectory) "/" else "", it.isDirectory)
            }
        } else read(server.dav(directory), sameServer = true, build = { header("Depth", "1").method("PROPFIND", propfind) }) { response, url ->
            response.success()
            if (response.code != 207) throw IOException("WebDAV 目录预期返回 HTTP 207")
            parseDav(requireNotNull(response.body).byteStream(), url)
        }
        entries.sortedWith(compareByDescending<SpeedEntry> { it.directory }.thenBy { it.name.lowercase() })
    }

    private fun parseDav(stream: InputStream, root: HttpUrl): List<SpeedEntry> {
        val xml = Xml.newPullParser()
        xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        xml.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        xml.setInput(stream, null)
        val entries = ArrayList<SpeedEntry>()
        var href: String? = null
        var collection = false
        val directory = root.encodedPath.trimEnd('/') + "/"
        while (xml.eventType != XmlPullParser.END_DOCUMENT && entries.size < 2000) {
            if (xml.eventType == XmlPullParser.START_TAG && xml.namespace == "DAV:") {
                when (xml.name) {
                    "response" -> { href = null; collection = false }
                    "href" -> if (href == null) href = xml.nextText().trim()
                    "collection" -> collection = true
                }
            } else if (xml.eventType == XmlPullParser.END_TAG && xml.namespace == "DAV:" && xml.name == "response") {
                val target = href?.let(root::resolve)
                val path = target?.encodedPath.orEmpty().trimEnd('/')
                if (target != null && sameOrigin(root, target) && parentDirectory(path) == directory) {
                    val name = target.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
                    if (name.isNotEmpty()) entries.add(SpeedEntry(name, path + if (collection) "/" else "", collection))
                }
            }
            xml.next()
        }
        return entries.distinctBy { it.path }
    }

    suspend fun download(seconds: Int, streams: Int, report: (SpeedSample) -> Unit): SpeedResult =
        measure(streams, seconds, report) { _, running, count ->
            val target = server.endpoint(false)
            val buffer = ByteArray(BUFFER_SIZE)
            while (running.get()) {
                var received = false
                fun consume(source: InputStream) {
                    while (running.get()) {
                        val size = source.read(buffer)
                        if (size < 0) break
                        if (size == 0) continue
                        received = true
                        count(size)
                    }
                }
                if (server.protocol == SpeedProtocol.FTP) ftp {
                    val input = it.retrieveFileStream(server.download())
                        ?: throw IOException("FTP 下载失败（${it.replyCode}）")
                    stream(input, ::consume)
                    if (running.get() && !it.completePendingCommand()) throw IOException("FTP 下载未确认（${it.replyCode}）")
                } else read(requireNotNull(target.toHttpUrlOrNull())) { response, _ ->
                    response.file()
                    requireNotNull(response.body).byteStream().use(::consume)
                }
                if (!received && running.get()) throw IOException("测速文件为空")
            }
        }

    suspend fun upload(bytes: Long, streams: Int, report: (SpeedSample) -> Unit): SpeedResult =
        measure(streams, null, report) { index, running, count ->
            val size = bytes / streams + if (index < bytes % streams) 1 else 0
            val path = server.upload().trimEnd('/') + "/wsm-speed-${UUID.randomUUID()}.bin"
            val buffer = ByteArray(BUFFER_SIZE)
            val random = Random(index.toLong() + System.nanoTime())
            fun write(sink: (ByteArray, Int) -> Unit) {
                var remaining = size
                while (remaining > 0) {
                    if (!running.get()) throw IOException("测速已停止")
                    val amount = minOf(buffer.size.toLong(), remaining).toInt()
                    random.nextBytes(buffer)
                    sink(buffer, amount)
                    count(amount)
                    remaining -= amount
                }
            }
            when (server.protocol) {
                SpeedProtocol.FTP -> ftp {
                    val output = it.storeFileStream(path) ?: throw IOException("FTP 上传失败（${it.replyCode}）")
                    stream(output) { sink -> write { buffer, amount -> sink.write(buffer, 0, amount) } }
                    if (!it.completePendingCommand()) throw IOException("FTP 上传未确认（${it.replyCode}）")
                    if (!it.deleteFile(path)) throw IOException("上传已完成，服务器拒绝清理测速临时文件")
                }
                SpeedProtocol.WebDAV -> {
                    val url = server.dav(path)
                    val body = object : RequestBody() {
                        override fun contentType() = binary
                        override fun contentLength() = size
                        override fun isOneShot() = true
                        override fun writeTo(sink: BufferedSink) = write { bytes, count -> sink.write(bytes, 0, count) }
                    }
                    execute(request(url).header("If-None-Match", "*").put(body).build()) {
                        it.success()
                        if (it.code !in setOf(200, 201, 204)) throw IOException("WebDAV 未确认上传（HTTP ${it.code}）")
                    }
                    execute(request(url).delete().build()) {
                        if (!it.isSuccessful && it.code != 404) throw IOException("上传已完成，服务器拒绝清理测速临时文件")
                    }
                }
                SpeedProtocol.HTTP -> throw IOException("HTTP 文件直链只提供下载测速")
            }
        }

    private suspend fun measure(
        streams: Int, seconds: Int?, report: (SpeedSample) -> Unit,
        transfer: (Int, AtomicBoolean, (Int) -> Unit) -> Unit,
    ): SpeedResult = coroutineScope {
        val bytes = AtomicLong()
        val firstByte = AtomicLong()
        val lastByte = AtomicLong()
        val running = AtomicBoolean(true)
        val error = AtomicReference<Exception?>()
        val started = System.nanoTime()
        val tasks = List(streams) { index ->
            async(Dispatchers.IO) {
                try {
                    transfer(index, running) { size ->
                        val now = System.nanoTime()
                        firstByte.compareAndSet(0, now)
                        lastByte.accumulateAndGet(now, ::maxOf)
                        bytes.addAndGet(size.toLong())
                    }
                } catch (failure: Exception) {
                    if (running.get()) error.compareAndSet(null, failure)
                }
            }
        }
        var previousTime = started
        var previousBytes = 0L
        var peak = 0L
        try {
            while (true) {
                delay(300)
                val now = System.nanoTime()
                val count = bytes.get()
                val recent = speedRate(count - previousBytes, (now - previousTime) / 1e9)
                peak = maxOf(peak, recent)
                if (count > 0) report(SpeedSample(count, recent))
                previousTime = now
                previousBytes = count
                if (tasks.all { it.isCompleted }) break
                if (seconds != null && firstByte.get() > 0 && (now - firstByte.get()) / 1e9 >= seconds) break
                if ((seconds == null && error.get() != null) ||
                    (firstByte.get() == 0L && (now - started) / 1e9 >= 20) || (now - started) / 1e9 >= 180) {
                    throw error.get() ?: IOException("测速超时")
                }
            }
        } finally {
            running.set(false)
            withContext(Dispatchers.IO + NonCancellable) { close() }
        }
        tasks.awaitAll()
        val count = bytes.get()
        error.get()?.let { throw it }
        if (count == 0L) throw IOException("未收到有效的测速数据")
        val end = if (seconds != null && (System.nanoTime() - firstByte.get()) / 1e9 >= seconds) {
            firstByte.get() + seconds * 1_000_000_000L
        } else lastByte.get()
        val duration = (end - firstByte.get()) / 1e9
        val rate = speedRate(count, duration.coerceAtLeast(0.3))
        SpeedResult(count, duration.coerceAtLeast(0.3), maxOf(peak, rate))
    }
}

internal fun speedError(error: Throwable): String = when (error) {
    is javax.net.ssl.SSLHandshakeException, is javax.net.ssl.SSLPeerUnverifiedException ->
        "HTTPS 证书验证失败，请检查服务器证书和域名"
    is java.net.SocketTimeoutException -> "连接或数据传输超时"
    else -> error.message.orEmpty().ifBlank { "测速失败" }.take(140)
}
