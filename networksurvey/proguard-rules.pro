# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
# -dontobfuscate  # 已启用混淆以减小APK体积

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keep class com.google.android.material.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== 以下规则由 R8 自动生成（缺失的可选依赖类，MQTT/netty 等）=====
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn io.netty.channel.epoll.Epoll
-dontwarn io.netty.channel.epoll.EpollEventLoopGroup
-dontwarn io.netty.channel.epoll.EpollSocketChannel
-dontwarn io.netty.handler.codec.http.FullHttpResponse
-dontwarn io.netty.handler.codec.http.HttpClientCodec
-dontwarn io.netty.handler.codec.http.HttpHeaders
-dontwarn io.netty.handler.codec.http.HttpObjectAggregator
-dontwarn io.netty.handler.codec.http.HttpRequest
-dontwarn io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.PingWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.PongWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.TextWebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker
-dontwarn io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
-dontwarn io.netty.handler.codec.http.websocketx.WebSocketFrame
-dontwarn io.netty.handler.codec.http.websocketx.WebSocketHandshakeException
-dontwarn io.netty.handler.codec.http.websocketx.WebSocketVersion
-dontwarn io.netty.handler.proxy.HttpProxyHandler
-dontwarn io.netty.handler.proxy.ProxyHandler
-dontwarn io.netty.handler.proxy.Socks4ProxyHandler
-dontwarn io.netty.handler.proxy.Socks5ProxyHandler
-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.persistence.Basic
-dontwarn javax.persistence.Column
-dontwarn javax.persistence.Entity
-dontwarn javax.persistence.EnumType
-dontwarn javax.persistence.Enumerated
-dontwarn javax.persistence.FetchType
-dontwarn javax.persistence.GeneratedValue
-dontwarn javax.persistence.Id
-dontwarn javax.persistence.JoinColumn
-dontwarn javax.persistence.ManyToOne
-dontwarn javax.persistence.OneToMany
-dontwarn javax.persistence.OneToOne
-dontwarn javax.persistence.Table
-dontwarn javax.persistence.Version
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.eclipse.jetty.alpn.ALPN$ClientProvider
-dontwarn org.eclipse.jetty.alpn.ALPN$Provider
-dontwarn org.eclipse.jetty.alpn.ALPN$ServerProvider
-dontwarn org.eclipse.jetty.alpn.ALPN
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego
-dontwarn org.slf4j.ILoggerFactory
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.slf4j.Marker
-dontwarn org.slf4j.helpers.FormattingTuple
-dontwarn org.slf4j.helpers.MessageFormatter
-dontwarn org.slf4j.helpers.NOPLoggerFactory
-dontwarn org.slf4j.spi.LocationAwareLogger

# ===== 百度地图 SDK 混淆豁免 =====
-keep class com.baidu.** { *; }
-keep class com.baidu.mapapi.** { *; }
-keep class com.baidu.lbsapi.** { *; }
-keep class com.baidu.platform.** { *; }
-keep class vi.com.gdi.** { *; }
-dontwarn com.baidu.**
-dontwarn vi.com.gdi.**
