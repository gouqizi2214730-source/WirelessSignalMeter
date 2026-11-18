# T-Phone APK 调查

调查日期：2026-11-18。输入为用户提供的 `tphone.main.MainApp.apk`，
包名 `tphone.main`，versionName `1.3.6`，versionCode `202603061`，
minSdk 27、targetSdk 31，arm64。
APK SHA-256：`A11113D2FBDE9B4345C8C1AEDFDC6E33495C133CA256E2514264F28E34183C76`。

使用 JADX 1.5.6 静态分析；反编译报告 70 个方法错误、166 条告警，
下列关键调用链可以阅读，但未完整还原所有路径，也未运行该应用。
以下路径均相对于本地忽略目录 `.temp/tphone-jadx/sources/`；
行号对应这次反编译输出，不能当作上游源码行号。

## LTE SINR 的实际来源

1. `tphone/main/k4.java`（MainSocket）约 1000 行连接
   `tphonelog.localsocket`。`Q0()` 约 1197 行使用小端序构造
   “2 字节总长度 + 4 字节命令 + 负载”。约 1031 行按
   `0x0301` / `0x0401` 选择不同的 LTE 负载解析器。
2. `g/x/b/l0.java`（LteInfoProcess）在两套解析器内通过
   `pVar.v().B(byteBufferL.getShort())` 保存原始测量。
   `g/x/b/z.java` 中 `B(short)` 写入字段，`j()` 读取同一字段。
3. `l0.java` 的 `i()` 先检查 `N() && K() && L()` 三个有效性标志，
   再将 LTE SINR 按 `raw / 5.0f - 20.0f` 写入存储。相邻
   RSRP、RSRQ、RSSI 使用各自的 `/10` 换算。
4. `g/y/b/g.java`（StorageRadioLteInfo）将 `m()` 对应项命名为 SINR；
   `CommonUseLteFragment` 和 `tphone/main/j4.java` 显示该项。
   `g/y/b/b.java`（StorageFloatItem）区分有效数字、`--` 和 `n/a`。

这证明已检查的主测量链来自本地服务的私有二进制协议。
APK 本身不能证明任意手机上都存在该服务，或普通安装后就有相应权限。
外部 WNL Binder 控制接口不能与这个核心测量 socket 混为一谈。
普通 `getRssnr()` 的一些命中来自随包的定位 SDK，不能据此推断主界面来源。

## 单位必须按数据源和版本确定

| 已检查的数据源 | 已见换算 | 对项目的意义 |
| --- | --- | --- |
| T-Phone LTE 本地服务负载 | `raw / 5 - 20` | 只适用于已确认的字段布局 |
| T-Phone NR 的部分负载 | `10 * log10(raw / 1024)` | 先检查原始值有效性，按该布局处理 |
| T-Phone NR 的另一类负载 | `10 * log10(unsignedRaw / 65536)` | 有符号性和缩放都不同 |
| 本项目现代 Android LTE `getRssnr()` | 直接使用 dB | 不套用上述私有协议公式 |

NR 证据在 `g/x/b/p0.java`（NrInfoProcess）的 `G()`、`M()`、`N()`，
约 302-316、460-488 行。部分布局在四路接收链中选最大正值后换算；
这不能推广为对 Android SS/CSI 等不同含义指标任意取最大值。
`N()` 在无新有效值时还可能保留旧值，本项目仍须遵守自身采样时效规则。

## 继续深入后的可借鉴内容

### DIAG 配置重发与分层配置

`g/t/d.java:m()/g()` 分组构建并串行发送 log/event 配置，
保存当前配置；`o()` 重发这两份配置。调用者已经确认：
`tphone/main/k4.java:J0()` 约 344-390 行在服务报告 `resume`、
以及特定重置通知后重发 mask；`K0()` 约 396-410 行处理
`Diag no output` 通知。对方本地服务给出这些状态，不能把私有通知命令
直接搬到本项目 libdiag DCI。

`g/t/i/h.java` 按设备分类选择配置实现，并组合基础日志、额外日志和
用户自定义日志。`j.java` / `k.java` 分别标记 Z50 / Z70 配置；
这不是任意 Android API 版本通用的日志布局表。

本项目 `root_diag.c:dci_start()` 已有整组订阅失败后逐项订阅、
可选邻区探测及订阅结果记录，主循环已有心跳/最后报文年龄。
值得补的是“采集进程仍有心跳，但预期报文长期不来”的独立状态与
有上限、带退避的重订阅策略。应区分空闲网络暂时没有某种报文、
已接收但版本不认识、以及所有报文都停止，不能一缺 SINR 就重置基带。

`g/t/f.java` ResponseManager 将响应解析与请求分层，并有约 1 秒等待上限。
`g/t/g.java` ResponsePool 只有一个槽、按子命令匹配并轮询，不能直接用作
本项目并发事务设计。借鉴明确超时和响应校验即可。

### 有效性与缺失原因

`g/y/b/a.java` / `b.java` 保存 -1、0、1 三态；
float 项显示为 `n/a`、`--`、格式化数字。LTE 处理器先检查有效标志，
再按该私有协议的单位换算。它们没有采样时间戳，部分 NR 分支还保留旧值。

本项目已有采样超时、来源、缓存标记和未知布局记录，应在这些能力上补
“系统未提供 / 采样过期 / 归属不明 / 解码布局未知”的诊断原因。
不能用对方三态替代本项目时间校验。无 Root Android LTE 的原始值与
采用原因导出已在本轮独立加入，见 [ANDROID-LTE-SINR-390.md](ANDROID-LTE-SINR-390.md)。

### 任务状态、超时与结果关联

`tphone/onekey/OnekeyTaskManager.java` 可以核实以下结构：

- `OnekeyTaskHandler` 先等待定位，再等日志启动、锁网完成，随后执行测试项；
  停止后保存日志/数据库、恢复部分状态并清理 timer 和子任务服务。
- `waitStartgpsTimeoutEvent()` 每 10 秒检查、超过 30 次结束；
  `waitStartlogTimeoutEvent()` 每 6 秒检查、累计 3 次结束；
  `1023` 单项等待及总任务时长也有退出路径。这些是对方策略，
  不是本项目应照抄的默认时长。
- `sendOneKeyTaskMsg()` 比较执行项索引、拒绝不属于当前项的消息。
  可借鉴回调归属检查，但我们应使用每轮唯一 ID，防止重复运行时索引复用。
- `OneKeyTaskLogBean` 有 taskId、任务名、重测次数、目标小区、
  日志文件及状态；`OneKeyTestItemInfo` 有执行/失败次数与 summaryKPI。

本项目路测已有分轨、回放、平均 RSRP、弱覆盖连续区间和 CSV，
无需重复开发这些功能。更有价值的是先补本地 session/task ID、场景标签、
开始/结束/取消/失败事件，再按同一任务关联地图、信号与诊断导出。
主动 Ping/HTTP 测试可作为后续独立步骤；本次未移植其远程下发或上传。
其恢复网络模式的代码按环境推导模式，不是完整原始配置事务，
不能代替本项目锁网备份/恢复。

### 定位质量与室内测试

`LostGpsChecker.java` 使用 `elapsedRealtime()` 判断位置年龄，
通过广播报告丢失/恢复；出现位置超时后的检查还会定期报告。
60 秒是对方阈值。`OnekeyTaskManager.newRealpathPointEvent()` 保存
`GpsType`、经纬度；`IndoorPosData` 另外保存 TICK、floorid、图上 x/y。
`IndoorDtScene` 区分覆盖、泄漏、进出楼、楼梯、楼层、电梯、沿线等场景。

本项目 `ForegroundMapTrack.kt` 已过滤超过 10 秒的位置，并按精度择优，
不是完全没有定位时效保护。差距是 `MapPointRecord` / `RoadSample` 未保存
定位来源、精度、位置采样时刻、手工标记或楼层；无新位置直接不打点，
报告难以区分“无线采样中断”和“定位失效”。可先增加质量元数据及
中断事件；室内底图和楼层点位属于后续功能，不能拿假经纬度填室内坐标。

### KPI 注册与展示

`tphone/onekey/kpis/KpiManager.java` 管理分类、字段顺序、启用项及观察者；
`KpiProxy` 仅更新字符串，`KpiValue` 通知界面。
这些证据支持可配置 KPI 展示，不能证明存在分位数或覆盖率统计引擎。
本项目已有固定参数和统计后，只有实际出现多套场景字段需求时才值得抽取注册层。

## 建议落地顺序

| 优先级 | 内容及证据 | 本项目接入方式 |
| --- | --- | --- |
| 高，Android | 缺失值原因与原始测量诊断 | 已新增 Android LTE JSONL 记录；先用故障机数据确认读取或判定问题 |
| 高，Root 可选 | DIAG mask 重发、设备配置分层 | 在现有逐项订阅和未知布局记录上增加无输出诊断及有上限恢复，需真机样本 |
| 中 | 定位失效独立状态、GpsType | 保留现有 10 秒过滤，保存来源/精度/位置年龄，报告记录定位中断 |
| 中 | 任务状态和 taskId 关联 | 先增加本地场景、任务事件和导出关联，再扩展主动测试 |
| 后续 | 室内楼层与图上坐标 | 独立模型/坐标系，不污染现有地图轨迹 |
| 后续 | KPI 分类注册 | 有多场景字段配置需求时再做，不当作统计算法移植 |

已检查的 `g/u/e.java:z()` 主要按 Android 版本反射选择接口；
经 `Netmode → x.l0()` 调用 `setPreferredNetworkType` 等旧隐藏接口，
不能视为现代普通应用的通用锁频方案，也不能声称已还原所有频段锁定协议。

## 本次不作移植的部分

- 未将该 APK 的源码或私有 native 库加入项目。
- `libdevice.so` 对应 `com.woncan.device.JniImp` 的定位/RTK 相关接口，
  `libIoWrapperDll.so` 对应 `de.opticom.io` 音频接口，不能当作 DIAG 引擎使用。
- 其百度地图版本为 7.6.6，没有理由据此回退本项目已验证对齐的 7.6.7。
- 未连接其 socket、执行锁网命令、访问应用服务端或上传日志。

本次调查能指导后续 DIAG 布局验证，不能让其私有测量协议用于无 Root 普通采集。
用户的 v1.2.0 4G 闪退已独立定位为 LTE 工具类正则中未转义的右花括号，
并已有修复包；见 [CRASH-COMPATIBILITY.md](CRASH-COMPATIBILITY.md)。
用户随后提供的同机旧版 3.90 对无 Root LTE SINR 排查更直接，
对照结果和本轮接入见 [ANDROID-LTE-SINR-390.md](ANDROID-LTE-SINR-390.md)。
