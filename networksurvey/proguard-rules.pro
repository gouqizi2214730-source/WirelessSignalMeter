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

# ===== 百度地图 SDK 混淆豁免 =====
-keep class com.baidu.** { *; }
-keep class com.baidu.mapapi.** { *; }
-keep class com.baidu.lbsapi.** { *; }
-keep class com.baidu.platform.** { *; }
-keep class vi.com.gdi.** { *; }
-dontwarn com.baidu.**
-dontwarn vi.com.gdi.**
