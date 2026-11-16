# 无线无忧 · WirelessSignalMeter

无线无忧是基于 [Network Survey](https://github.com/christianrowlands/android-network-survey) 开发的 Android 蜂窝网络测量工具，用于查看 LTE / 5G NR 服务小区、邻区和基带信令，也支持基站工参导入和地图匹配。

## 功能

- **信号页**显示运营商、注册及广播 PLMN、小区标识、上下行参考频点及频率、双工模式和信号趋势。邻区按 RSRP 排序。5G 带宽有可用值时显示。
- **Root DIAG**在兼容的 Qualcomm 设备上读取基带诊断，显示支持的 RRC / NAS 等信令和分层吞吐率，并可录制 QMDL。可用日志和字段取决于设备及基带版本。
- **基站工参和地图**支持导入 CSV、按基站 ID 搜索。地图以蓝线连接服务小区，以橙线连接匹配到工参的实测邻区。
- **其他工具**包括 Wi-Fi 信号、GNSS 卫星信息和服务器测速。

## 使用

当前构建支持 Android 13 及以上的 `arm64-v8a` 设备。无 Root 模式使用 Android 提供的小区与信号数据。Root 诊断需要设备授权及可用的 Qualcomm DIAG 接口。

- 在 **设置 → 基站工参**导入 CSV，检查字段映射后保存。在 **工具 → 基站地图**授权定位即可查看匹配结果。
- 在 **工具 → 信令**的筛选菜单开始或停止录制 QMDL，再选择导出。需要保留上一份录制时请先导出。
- 在 **设置 → 导出诊断日志**保存 JSONL。提交问题时请先阅读[数据与隐私说明](privacy_policy.md)。

## 从源码构建

安装 Android Studio、Android SDK Platform 37 和 NDK `27.3.13750724`。将 Gradle JDK 配置为 Android Studio 自带的 JBR。模块为 `networksurvey`，当前构建变体为 `cdr`，发布包名为 `com.wireless.signalmeter.new`。

```sh
./gradlew :networksurvey:assembleCdrRelease
./gradlew :networksurvey:testCdrDebugUnitTest
```

Windows PowerShell 下将 `./gradlew` 替换为 `.\gradlew.bat`。APK 输出到 `networksurvey/build/outputs/apk/cdr/release/`。安装前可运行 `apksigner verify --print-certs <APK文件>` 检查签名。覆盖安装要求包名和签名与已安装版本一致。当前源码的 Release 配置使用构建环境的调试签名。

设备回归检查见[测试指南](docs/TESTING.md)，版本变更见[更新记录](CHANGELOG.md)。

## 调查记录与集成

以下集成沿用上游 Network Survey 的协议说明。相关功能与配置以当前构建为准。

### gRPC Survey Record Streaming

The Network Survey app supports streaming GSM, CDMA, UMTS, LTE, and 802.11 survey records to a gRPC
server. More specifically,
the [Network Survey Messaging](https://github.com/christianrowlands/network-survey-messaging)
library can be used to stand up a gRPC server. From there it is up to the implementation to handle
the incoming survey messages.

If you want to see how to use the generated Go code to create a gRPC server, check out the code at
https://github.com/christianrowlands/ns-messaging-go-grpc-server

The server does not do anything useful, but serves as an example of how one might be created in
GoLang.

### MQTT Broker Survey Record Streaming

Currently, GSM, CDMA, UMTS, LTE, NR, 802.11, Bluetooth and GNSS survey records are sent to a
connected MQTT broker. They are published on the following MQTT Topics:

* gsm_message
* cdma_message
* umts_message
* lte_message
* nr_message
* 80211_beacon_message
* bluetooth_message
* gnss_message

There is also a DeviceStatus and a PhoneState message that is published on
the `device_status_message` topic.

The MQTT Broker connection supports both plain text and TLS/SSL connections.

The survey messages are sent in JSON format following the protobuf definitions from
the [Network Survey Messaging](https://github.com/christianrowlands/network-survey-messaging)
library. [The API documentation is published to a web page here](https://messaging.networksurvey.app/)
.

QR Code for MQTT Broker connection setting needs to provide a JSON string with the following fields.

The `mqtt_client` field is optional and will default to the App's auto-generated value.

The `mqtt_topic_prefix` field is used to prefix the MQTT topics that the survey records are
published
to. The default topics are listed above (e.g. `lte_message`), but if you want to add a custom
prefix,
you can use the `mqtt_topic_prefix` field to change the topic to something like
`my/custom/topic/lte_message` by setting the value to `"mqtt_topic_prefix": "my/custom/topic/"`
(notice the trailing slash).

```json
{
  "mqtt_host": "cloud.mymqttserver.com",
  "mqtt_port": 8883,
  "mqtt_tls": true,
  "mqtt_client": "aclient",
  "mqtt_username": "auser",
  "mqtt_password": "apassword",
  "mqtt_topic_prefix": "my/custom/topic/path/",
  "mqtt_qos": 1,
  "cellular_stream_enabled": true,
  "phone_state_stream_enabled": true,
  "wifi_stream_enabled": true,
  "bluetooth_stream_enabled": true,
  "gnss_stream_enabled": true,
  "device_status_stream_enabled": true
}
```

### Intent API

The Network Survey app supports an Intent API that allows other apps to start and stop a survey. The
Intent API is documented on the [Intent API website](https://www.networksurvey.app/intent-api).

## 反馈与许可

一般问题请通过本仓库的 [GitHub Issues](https://github.com/niceboat0/WirelessSignalMeter/issues) 提交。安全问题请参阅[安全报告说明](SECURITY.md)。

本仓库沿用 [Apache License 2.0](LICENSE)。感谢上游 [Network Survey](https://github.com/christianrowlands/android-network-survey) 的维护者和贡献者，以及 [GPSTest](https://github.com/barbeau/gpstest) 与 [Tower Collector](https://github.com/zamojski/TowerCollector) 项目提供的相关组件和实现参考。
