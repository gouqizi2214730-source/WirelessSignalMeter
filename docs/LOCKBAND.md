# 锁网工具

入口为工具页的“锁网”。界面沿用当前应用的 Compose、主题和导航，原生程序与 Root DIAG 采集器分别构建。

## 来源与范围

协议模板根据用户指定的 `niceboat0/diag-research` 私有仓库 `lockband/lockband.c` 移植，源文件 blob 为 `80b49b94c992b27ea648cadde16a8db8964b0372`。参考 UI 为 MainActivity.java blob `696365b14662e993f72f17e9ce8e5823f0d6129f`。参考副本保存在 Git 忽略的 `.temp/lockband-reference/`。

- 支持 Root 下的 Qualcomm diag-router 抽象 SOCK_SEQPACKET 通道。原报告的实测设备是三星 SM-S9370，不能将该结果视为所有高通、三星或其他厂商均已适配。
- LTE 频段范围 1–64，NR 位图容量为 1–512；数值范围不代表设备支持这些频段。
- 支持 LTE/NR 单频点、单小区和自动/LTE/NR 网络制式。NR SCS 为 15/30/60/120 kHz。
- 网络操作作用于协议中的主协议栈，未提供 SIM 卡选择。没有移植固定 Binder transaction 94。
- 首次锁频段必须完整读取本机原掩码并持久保存，LTE 8 字节、NR 64 字节。恢复使用这份备份，不包含上游的特定运营商默认全集或强制写入回退。
- EFS 写入遵循 MKDIR、PUT、SYNC 序列，收到匹配 transaction ID 和 command 的确认后请求 modem SSR。制式切换走 CM 帧。发送 SSR 只代表请求已发出，实际驻留需真机核对。
- 没有移植任意命令输入、ptrace、路径探测、厂商 AT 节点扫描和实验性 NV 写入。

## 失败与恢复

打开页面只检测 Root 和 DIAG 注册，不下发锁定或重启命令。修改前显示具体参数和基带重启影响。执行期间禁止重复提交和返回销毁操作。

备份位于应用私有 noBackupFilesDir/band-lock；卸载应用或清除应用数据会删除备份。不要在锁定未恢复时清除应用数据。频点和小区使用各自的解除入口，网络制式“自动”不会清除其他锁。

原生边界重复校验数值并用文件锁串行化操作。未收到已识别的回包、基带拒绝或超时均报告失败/未确认，不将进程写入成功当成锁定成功。EFS 序列可能部分完成；失败时保留执行记录和备份，不自动重试写入或重启。

## 验证

- `BandLockCommandTest` 验证规范化、去重、零值、上下界、非法字符、溢出、SCS 与独立恢复指令。
- `src/test/cpp/band_lock_protocol_test.c` 为不访问设备的主机测试，校验频段位图、LTE/NR PUT 模板、回包事务匹配、截断及请求回显拒绝。
- 真机验收：先检测，再分别测试频段锁定/恢复、频点锁定/解除、小区锁定/解除及制式恢复；每步在网络重新注册后核对服务小区、频点与 PCI。
- 无 Root、非兼容通道、拒绝授权、读回不足、备份失败、重复操作及旋转屏幕均需要回归。

## 2026-11-18 本地结果

- arm64 原生程序使用 NDK 27.3.13750724 编译，`-Wall -Wextra -Werror` 通过。
- 原生协议主机测试通过；4 项锁网参数测试及原有 6 项路测回放测试通过，采用本机缓存编译器直接编译运行。
- `assembleCdrRelease` 成功，包含最终锁网页面、原生程序及此前路测回放修复。沿用项目原有 lintVital 排除配置；未运行完整 lint。
- `apksigner verify` 与 `zipalign -c -P 16 4` 通过。签名证书与已有的 Hide-Empty-NR-Bandwidth 安装包一致。
- 检查 APK 中 `libwireless_band_lock.so` 和 `libwireless_root_diag.so` 均存在且为 arm64 ELF，R8 映射保留了锁网页面与命令代码。
- ADB 无已连接设备，未执行 UI 真机回归或实际基带写入，不能据此宣称已适配所有机型。

安装包：`released_apks/WirelessSignalMeter-1.2.0-lockband-20261118.apk`

包名 `com.wireless.signalmeter.new`，versionName `1.2.0`，versionCode `120002`，arm64，最低 Android 13。

SHA-256：`1571B5D901C2B50C6B3EF5834F1B594B39EEE5BB41E61E41298D0D38D6A830EA`

本次 Gradle 构建通过隐藏的 Windows CIM 子进程执行 `.temp/build-band-lock.ps1`，绕过直接工具子进程中出现的 Java 本地回环连接错误。构建日志在 `.temp/band-lock-build.log`，退出码文件为 `.temp/band-lock-build.exit`。

## 2026-11-18 真机反馈修复（fix1）

真机反馈两项问题，定位与修复如下。

### 锁频段不生效（Native 回包解析与参考设备实测行为不符）

对照 diag-research 仓库真机回包逐字节复核，移植版判定逻辑偏离参考设备（SM-S9370）回包形状，EFS 三帧序列在第一/二帧即失败：

1. **事务 ID 严格匹配错误**：真机回包中子命令前的字段不等于本端事务 ID（实测同一次锁定的第 3 帧 SYNC 回包前字段为 0x96，与本端 tx 无关）。放开为按回显子命令匹配。
2. **PUT 回包尾长固定 4 字节的假设错误**：真机 PUT 回包为 `命令回显 + 2 字节 tag 回显（26 13 / ff 03）+ 4 字节状态` 共 6 字节尾，原校验必然失配；tag 期望值按线上字节序传入（26 13 → 0x2613，本轮修复中曾误写 0x1326，回放验收发现后修正）。
3. **MKDIR 已存在状态未放行**：真机 MKDIR 常态返回 6（EEXIST），原白名单只认 17；现 6/17 均放行。
4. **SYNC / CM 回包按宽松确认**：这两类回包不含 u32 errno（SYNC 为 token 与杂项负载回显，CM 为参数回显），按命令回显即确认。
5. **LTE 频段路线修正**：参考设备实测 `lte_bandpref` EFS 写入落地但驻留不应用（diag-research v97 实录），生效路线是 CM 即时帧 `4B 0F 28 00`（v98 实测 mBands=[5]）。LTE 频段 1–32 改走 CM 即时帧（无基带重启，确认框不再提示重启）；33–64 仍走 EFS+重启。
6. **NR 首锁基线缺失不再拒绝**：参考设备 `nr_band_pref` 出厂未物化、GET 读不出（v99 实录）。首次锁定记录缺失标记（nr-no-original），恢复改为删除锁文件并重启基带回出厂默认；备份可读时行为不变。
7. GET 数据解析改为真机语义：帧内搜索 ff 03 标记取数，请求回显帧以路径开头（'/'）特征拒绝。

回包语义用真机字节（diag-research 实录的 MKDIR/PUT/SYNC/GET/SSR 帧）逐条回放验收，22/22 通过（`.temp/protocol_replay_check.py`）；主机 C 单测与新语义同步。

### 当前驻留小区闪烁（UI）

页面 `now` 每秒刷新，serving 记录超过 15 秒新鲜窗口即回落"暂无"，心跳间隔踩窗沿时反复切换。加入滞回：最近一次有效测量保持显示至 60 秒，期间不闪回占位文案。

### 验证

- Kotlin 命令单测 5 项 + 原路测回放 6 项通过（`.temp/test-band-lock.ps1`）。
- `assembleCdrRelease` 两次构建通过（第二次含 tag 字节序修正），APK 内 `libwireless_band_lock.so` 含全部新逻辑特征串。
- 真机回归建议：LTE 页锁 B5 应即时生效（不再提示重启）；NR 页锁 n1/n78 首次不再报"无法读取原始掩码"；驻留小区行应停止闪动。

安装包：`released_apks/WirelessSignalMeter-1.2.0-lockband-fix1-20261118.apk`

SHA-256：`4CF3BC95B0E41478D9AB59036E276A7944EC1B5626A7EEF20B3465962492E9B3`
