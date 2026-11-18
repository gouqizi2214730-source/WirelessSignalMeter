# 无 Root LTE SINR 对齐 Network Survey

2026-11-18 用户安装本次测试包后反馈“现在 sinr 正常了”，并授权推送 GitHub。
这是用户在原问题场景下的验证结果；未据此宣称所有机型、双卡和 CA 场景均已通过。

调查日期：2026-11-18。用户指定普通 Android、无 Root 采集优先参考
`christianrowlands/android-network-survey`。本次实际经用户指定 SOCKS5 代理获取
上游默认 `develop` 分支，固定提交：

`4b92316d50a578926b3e82559c4313d252f131ec`（提交日期 2026-11-07）。

只调整 Android LTE SINR 的来源与判定，不合入上游整套应用。
这取代上一轮“CellInfo 优先、快照仅补缺”的策略；旧版 3.90 静态证据保留在
[ANDROID-LTE-SINR-390.md](ANDROID-LTE-SINR-390.md)。

## 上游实际行为

固定提交的源码：

- [CellularController.java](https://github.com/christianrowlands/android-network-survey/blob/4b92316d50a578926b3e82559c4313d252f131ec/networksurvey/src/main/java/com/craxiom/networksurvey/services/controller/CellularController.java#L646)：
  为订阅创建 `TelephonyManager`；第 706 行在 CellInfo 回调中直接读取同一
  manager 的 `getSignalStrength()`。旧扫描路径第 959、1126 行也直接读订阅。
- [SurveyRecordProcessor.java](https://github.com/christianrowlands/android-network-survey/blob/4b92316d50a578926b3e82559c4313d252f131ec/networksurvey/src/main/java/com/craxiom/networksurvey/services/SurveyRecordProcessor.java#L1843)：
  仅为注册 LTE 小区使用 SignalStrength，取首个 LTE 项的 `getRssnr()`，
  排除 `Integer.MIN_VALUE` / `CellInfo.UNAVAILABLE` 后直接写 SNR。
- 同文件第 1853–1862 行明确停用 CellInfo 的 RSSNR，注释解释其曾得到错误
  0/1，并指出部分设备两条来源可能存在缩放差异。
  这是上游对其设备的说明，不是对用户故障机固件的实测结论。
- `getLteRssnr()` 从第 1873 行开始；没有检查采样年龄、小区数量或样本数量，
  遇到首个 LTE 项后立即停止。读取及写入不除以 10。

此前本地优先保留 CellInfo 的 0/1，即使同订阅快照提供 17 也不会覆盖；
快照还受 10 秒年龄、5 秒间隔和唯一小区/唯一样本限制。这些均是本地额外策略，
不是本次核对到的上游行为。

## 当前实现与保留差异

| 场景 | 当前处理 |
| --- | --- |
| 同订阅 SignalStrength 有首个 LTE 值 | 优先采用，包括覆盖 CellInfo 的 0、1 或其它值 |
| 首个 LTE RSSNR 是 MIN_VALUE / MAX_VALUE | 视为缺失；不跳到第二个 LTE 项 |
| 公共 RSSNR 超过 -20..30 | 按上游保留原值，不裁剪、不猜测 `/10`；日志可以检查异常原值 |
| CellInfo/SignalStrength 时间差大、时间为 0、多份 LTE 样本 | 时间和数量只用于诊断，不再阻止直接采样 |
| 直接 getSignalStrength 返回 null 或抛出运行时/链接异常 | 保留之前的同订阅回调缓存补读和去重告警；缓存仍有 10 秒时效 |
| 公共快照 LTE 值缺失 | 保留明确 LTE 厂商方法/字段/文本兼容读取；这些非标准来源仍限制 -20..30 |
| 快照及其兼容来源均缺失或读取异常 | 保留自身 CellInfo 的有效值作为兼容补读；全部无值时保持缺失 |
| 未注册邻区 | 只读自己的 CellInfo，不借用服务小区快照 |
| 明确标记为 CA SECONDARY_SERVING 的 LTE 小区 | 保留本地保护，不借用主载波快照 |

后四项补读/缓存/CA 保护属于本地兼容行为，不宣称与上游完全相同。
同订阅多个角色未知的注册 LTE 小区按上游接受同一快照，不再因为计数拒绝；
该快照本身没有小区身份，不能据此把它当作每条载波的独立测量。
双卡继续使用各订阅专属 manager，不混用默认数据卡的快照。

`LteSinrUtils.isReported()` 用于公共 API 与最终值的缺失检查；
`isValid()` 的较窄范围仅用于兼容读取。诊断 JSON 与 LTE protobuf 使用一致的
公共值判定，避免标准值已写入但导出被另一套范围检查过滤。
主信号页的 Android LTE 合并显示也移除 -20..30 二次过滤，保留有限数值检查，
不改变已有 DIAG SINR 的优先级。
不使用 CQI 或 NR SINR 补充 LTE，不引入 Root、隐藏基带 socket 或其它应用进程。

## 日志与同机验证

常规 JSONL 的头部新增：

`android_lte_sinr_policy=network_survey_signal_strength_first_v1`

继续保留最近 128 条注册 LTE 数值样本。重点字段：

- `cell_rssnr_raw` 与 `snapshot_rssnr_raw`：对照两条系统来源。
- `selected_source=signal_strength`、`snapshot_decision=accepted`：
  首个公共 LTE 快照值被采用。
- `selected_source=signal_strength_compat`、`snapshot_decision=accepted_compat`：
  公共值缺失后采用明确 LTE 兼容来源。
- `no_snapshot` / `snapshot_no_sinr` / `not_primary` / `snapshot_error`：
  记录快照缺失、无 LTE 值、不适用的连接角色或异常；最终值可能来自 CellInfo 补读，
  应同时看 `selected_source` / `selected_sinr_db`。
- 时间戳与 `eligible_lte_cells` 保留用于分析，不再作为直接采样的拒绝原因。

在原故障机上用新包冷启动，双卡分别在 LTE 下采样 15–30 秒；
比较旧版 3.90 的数值，再导出常规 JSONL。若最终值存在而界面不显示，
继续排查所选 SIM 与 UI；若两条系统来源都返回缺失标志，日志应直接证明这一点。

旧日志仅有 5 条记录，缺少这些 Android 原值，无法确认上述差异是否是该手机
唯一的故障原因。没有 ADB 设备或模拟器，本轮未进行真机验证。

## 验证

- `test_android_lte_sinr.ps1`：35 项生产读取器主机检查通过，
  包含 0/1 被快照覆盖、真实 0/负数、首项选择、两种缺失标志、时间/数量不阻断、
  厂商补读、CA/邻区边界、异常与致命错误、双订阅诊断、128 条上限。
- `LteSinrUtilsTest`：21 项纯 JVM 测试通过；移除已不再使用的时间/数量策略测试。
- `test_android_lte_presentation.ps1`：29 项检查通过，提取实际生产 Kotlin 的
  LTE 显示合并、JSON 数值读取和诊断序列化方法，使用真实 protobuf / JSON 库，
  验证读取器结果经过记录、显示数据与导出后保持 17、0、负数及公共原值。
  Android 边界与未使用的频段转换使用替身，未运行 Compose。
  使用 `-SourceRevision 03ecd62` 对照旧显示方法时，按预期在 180 原值显示检查失败；
  当前方法通过。该数值用于证明范围过滤差异，不证明真实设备的 180 是正确 dB。
- `test_signal_snapshot.ps1`：直接读取优先、null/异常时缓存补读、日志去重和致命错误检查通过。
- `test_lte_regex_icu.ps1`：Windows ICU 72.1.0.4 检查两个实际生产正则通过。
- Android/Robolectric/UI 套件未执行，主机检查不代表故障机已修复。

## 测试包

`released_apks/WirelessSignalMeter-1.2.0-android-sinr-network-survey-20261118.apk`

SHA-256：
`145302D9A969D0D823EB27ADFC08EC9BEAF0C6AF5AA04EF4AF309FABD73E18FC`。
26,190,449 字节；包名仍为 `com.wireless.signalmeter.new`，版本
`1.2.0 / 120002`，arm64，最低 Android 13。签名与上一测试包一致，
同名 `.mapping.txt` 已保存。

包含显示端调整的最终 Release 构建耗时 3m41s，日志
`.temp/android-sinr-network-survey-build.log`，退出文件为 0。
先前 3m54s 构建尚不包含最后的显示端调整，仅作历史记录：
`.temp/android-sinr-network-survey-pre-ui-build.log`。
沿用既有 lintVital 排除；完整 lint 未运行，百度依赖的 R8 stack-map 告警仍存在。
`apksigner verify`、ZIP 16 KB 对齐、全部八个原生库的 ELF LOAD 16 KB 对齐通过。
最终 APK 定点核对了公共 RSSNR 缺失标志、修正的 ICU 正则、
主信号页有限数值判断、诊断序列化及导出策略标记。
R8 将读取器内联到处理器；处理器反编译有控制流重建告警，UI/导出类也有
局部 JADX 诊断，不将其输出作为完整可还原源码或运行时正确性的证明。

按用户确认结果整理到 `fix/lte-sinr-cellular-pro` 分支，包含此前地图/闪退修复、
测试及调查记录；不包含 APK、反编译材料或未合并 PR #4 的代码。
用户已确认本次 SINR 恢复；其它机型及完整回归仍需验证。
