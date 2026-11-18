# Android LTE SINR 与旧版 3.90 对照

当前策略已按用户要求改为优先对齐 android-network-survey，
见 [ANDROID-NO-ROOT-NETWORK-SURVEY.md](ANDROID-NO-ROOT-NETWORK-SURVEY.md)。
本文保留旧 APK 证据及上一轮测试包记录；下文 CellInfo 优先、时间/数量限制
不再描述当前实现。

调查日期：2026-11-18。用户确认故障机没有 Root，Android 采集模式下 LTE
SINR 缺失，而以前安装的 3.90 可以显示。以下结论来自本地 APK 与日志，
不是在故障机上运行后的结果。

## 用户日志能确认什么

`wireless-diag-20261118-153346.jsonl` 共 5 条、1,071 字节：

- 设备 `Xiaomi 24122RKC7C`，SM8750，Android API 37，应用 `1.2.0 / 120002`。
- `signal_mode.mode=android`，`android_rat=LTE`，`sim_count=2`。
- `collector_state=Unavailable`，没有 DIAG 测量。这与用户没有 Root 相符，
  不是 Android SINR 缺失的根因证据。
- 旧导出没有 Android `CellInfo` / `SignalStrength` 的原始 RSSNR、
  时间及采用判定，因此不能区分系统未上报和应用拒绝了备用值。

## 旧 APK 的实际读取链

输入：`WirelessSignalMeter_v3.90.apk`。
包名 `com.wireless.signalmeter`，versionName `3.90`，versionCode `390`，
minSdk 33、targetSdk 37。
SHA-256：`EDF6297818842E7E85E6463E48E8705821841D6134B70C311B73DC94A73455BE`。

JADX 1.5.6 输出在忽略目录 `.temp/wireless-390-jadx/sources/`。
整包反编译报告 115 个错误，不能声称完整还原；以下方法输出可读。
行号仅对应这次反编译结果：

1. `defpackage/j32.java:a()` 约 89-143 行从 LTE `CellInfo` 读取
   `CellSignalStrengthLte.getRssnr()`，`MAX_VALUE` 转为缺失标志。
2. `j32.java:b()` 约 203-230 行直接调用传入的
   `TelephonyManager.getSignalStrength()`，遍历其中 LTE 项读取 `getRssnr()`，
   有值后覆盖服务小区 SINR，并记录 `sinrSource=global`。
   这里的 “global” 是旧版日志命名，不表示一定使用默认 SIM：
   调用方会传入所选订阅的 `TelephonyManager`。
3. `defpackage/rt0.java:b()` 约 1760-1785 行查找服务小区、
   `createForSubscriptionId()` 后调用上述补读；
   `defpackage/qr1.java` 约 813 行也调用同一补读函数。
4. `defpackage/i32.java` 的信号/小区变化回调触发刷新；
   `j32.java:d()` 配合约 1 秒的刷新任务。
5. `defpackage/t32.java:o()` 约 945-956 行直接格式化 SINR；
   读取链和显示链均未发现除以 10。

这条可读链使用普通 Android 接口，与 T-Phone 的私有 DIAG socket 不同。
它没有时间差/多样本限制，且会覆盖已有的 CellInfo 值。当前实现继续优先
保留同一 CellInfo 的有效测量，并限制备用值归属；不照搬旧版所有行为。

## 本轮改动

- `CellularController.getSignalStrengthForScan()` 改为优先直接读取所选订阅，
  返回 null 或抛出运行时异常/链接错误时再用回调缓存。
  旧实现会在回调缓存非 null 时跳过直接读取，这是已证实的版本差异；
  尚不能证明它就是这台手机的全部故障原因。
- `AndroidLteSinrReader` 集中执行原有 LTE 备用值判定，保留最近 128 个
  注册 LTE 样本的数值诊断。厂商备用读取异常不再丢弃已有有效 CellInfo 值。
- 常规 JSONL 增加 `android_lte_sinr_samples` 计数及 `android_lte_sinr` 条目。
  不依赖 Root；换卡重建扫描资源时清空旧采样。
  内容仅含订阅编号、连接角色、原始 RSSNR、解析值、相对启动时间和采用原因，
  不保存完整 `SignalStrength.toString()`、小区标识、经纬度或号码。
- 有效范围仍为 -20..30 dB，0 有效；不使用 CQI，不对 180 等原值猜测缩放。

原有备用值限制保持：唯一注册 LTE 主小区、最多一个 LTE 信号样本，
小区/信号采样都在 10 秒内、相差不超过 5 秒。
因此本包用于验证直接读取差异，并能给出仍缺失时的具体证据，
不承诺仅切换数据源即可解决全部机型。

## 新日志怎样判断

| 条件 | 含义 |
| --- | --- |
| `cell_rssnr_raw=2147483647` | CellInfo 标准 RSSNR 返回不可用标志 |
| `snapshot_rssnr_raw=[17]`，`snapshot_decision=accepted` | 同订阅备用值 17 dB 已采用 |
| `selected_sinr_db` 为数字 | 该值已写入 LTE 记录；若页面缺失，应继续检查所选 SIM 和 UI |
| `snapshot_decision=no_snapshot` | 直接读取和可用缓存都没有提供快照 |
| `snapshot_decision=snapshot_no_sinr` | 已读取备用对象，但各明确 LTE 来源没有有效 SINR |
| `cell_timestamp` / `snapshot_timestamp` / `sample_gap` | 时间条件拒绝，应对照原始时间判断 |
| `ambiguous_lte_cells` / `multiple_lte_samples` / `not_primary` | 归属存在歧义或为 CA 辅小区 |
| `snapshot_error` 非 null | 厂商备用访问异常，字段记录异常类 |

先确认使用新包，在 LTE 下分别查看双卡、保持页面采样 15-30 秒，
再导出 JSONL。还需比较同机旧版显示的具体数值，避免把旧版显示某个数值
直接等同于该数值的单位和归属都正确。

## 验证

- 23 项 `LteSinrUtilsTest` 通过。
- `test_signal_snapshot.ps1` 直接提取生产方法，验证当前读取优先、
  null/异常时缓存补读、错误去重与恢复、链接异常及致命错误传播，通过。
- `test_android_lte_sinr.ps1` 编译生产读取器，21 项主机检查通过：
  数值/来源、0、负数、不可猜测缩放、时间/多小区限制、异常隔离、
  双卡诊断、128 条上限与导出快照一致性。
- Windows ICU 72.1.0.4 检查两个实际生产 Pattern 通过。
- 无 ADB 设备或模拟器，尚未运行 Android 真机/UI 验证。

## 兼容测试包

`released_apks/WirelessSignalMeter-1.2.0-android-sinr-390-20261118.apk`

SHA-256：`64AF11755CD4653D6B49187258EC0AA771867C14C2B5CC7E2EE544C425FBBD70`。
26,190,525 字节；包名 `com.wireless.signalmeter.new`，
版本仍为 `1.2.0 / 120002`，arm64，最低 Android 13。
沿用当前测试包的签名，同名 `.mapping.txt` 已保存。

Release 构建 4m06s 成功；日志 `.temp/android-sinr-390-build.log`、
退出码 `.temp/android-sinr-390-build.exit=0`。
先前直接启动遇到本机 JBR 回环连接错误，隐藏 CIM 子进程重试后成功。
沿用既有 lintVital 排除，未执行完整 lint，百度依赖 R8 stack-map 告警仍存在。
签名、ZIP 16 KB 检查和全部 8 个原生库 ELF LOAD 对齐检查通过。
最终 APK 定点反编译核对了直接读取在缓存之前、修复后的 ICU 正则、
Android LTE JSON 序列化及导出入口；优化后的回调/导出类仍有局部
JADX 诊断，未将它们宣称为完整可还原源码。

本包保留此前正则闪退和地图库修复。未提交、推送或合并 PR；
需要用户在原故障机上验证 SINR。
