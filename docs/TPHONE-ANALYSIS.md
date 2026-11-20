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
无需重复开发这些功能。第二轮核对确认，服务层还已有 Mission ID 和开始/结束
生命周期；差距是地图打点、测速和独立诊断导出没有贯通这一关联。
更有价值的是复用现有能力，补齐路测会话、场景标签及取消/失败事件，
再按同一任务关联地图、信号与诊断导出。
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
这一组类只支持可配置 KPI 展示。第二轮在另一个 `tphone/radiokpi/` 包和
`g/m/l0.java` 中另行找到阈值分段、数据库计数和平均值查询，详见下文；
不能把展示注册层与统计引擎混为一谈，也未核实到分位数实现。

## 建议落地顺序

| 优先级 | 内容及证据 | 本项目接入方式 |
| --- | --- | --- |
| 已完成，Android | 缺失值原因与原始测量诊断 | 已新增 Android LTE JSONL；无 Root SINR 已经用户同机确认恢复，保留 Network Survey 优先策略 |
| 高，Root 可选 | DIAG mask 重发、设备配置分层 | 在现有逐项订阅和未知布局记录上增加无输出诊断及有上限恢复，需真机样本 |
| 中 | 定位失效独立状态、GpsType | 保留现有 10 秒过滤，保存来源/精度/位置年龄，报告记录定位中断 |
| 中 | 任务状态和 taskId 关联 | 复用服务层 Mission ID，补齐路测、测速和诊断之间的会话关联，再扩展主动测试 |
| 后续 | 室内楼层与图上坐标 | 独立模型/坐标系，不污染现有地图轨迹 |
| 后续 | KPI 分类注册 | 有多场景字段配置需求时再做，不当作统计算法移植 |

## 第二轮：当前项目具体可补齐的部分

2026-11-18 用户确认 SINR 已恢复后继续检查同一 APK；重新校验 SHA-256 与
本文顶部一致。本节是静态分析和实施建议，尚未实现下面的新功能，
也没有运行 T-Phone。SINR 修复源码提交为 `8ef3de6`，已推送至
`fix/lte-sinr-cellular-pro`。本次继续调查不修改已验证的 SINR 读取与单位策略。

### 先补路测连续记录及质量信息

当前自动打点的明确调用关系：

- `NetworkSurveyActivity.kt:onPause()` 调用 `mapTrack.detach()`。
- `ForegroundMapTrack.detach()` 注销位置和蜂窝监听并取消写入协程。
- `MapTrack` 的启用偏好仍保留；它不是一个独立后台路测任务。
- `MapPointRecord` / `MapPointDbHelper` 仅保存测量、坐标、卡槽和墙钟时间，
  没有位置来源、精度、定位时间或定位中断原因。

因此，按现有代码，离开界面时这条自动打点链会停止。
这不等于所有后台采集都会停止：`NetworkSurveyService` 的其他日志或连接
可以独立运行。单纯移除 `detach()` 也不能完成后台路测，因为写入仍属于
Activity 的 `lifecycleScope`，且服务的占用状态没有包含路测任务。

T-Phone 的 `LostGpsChecker` 提供定位失效/恢复事件，
`OnekeyTaskManager` 将测试过程放在任务生命周期中管理。
可借鉴这两个设计，接入本项目现有服务，而非再创建一套互不关联的采集器：

1. 将用户主动开始的路测记录交给服务持有，作为明确的活动任务，
   与默认前台自动打点分开；界面切换只解绑展示，停止按钮结束任务。
2. 按既有服务机制处理通知、权限和资源释放，异常退出后能够区分正常结束与中断。
3. 为新打点保存 `location_provider`、`accuracy_m`、位置采样时间和单调时钟年龄；
   历史数据的缺失字段留空，不补造 GPS 精度。
4. 定位过期时仍保留无线状态/中断事件，不在过期坐标上继续画点；
   恢复后断开轨迹连接，避免跨隧道或长时间失定位连出假线路。

验收应覆盖：切到导航、锁屏、返回界面、撤销定位权限、GPS 失效再恢复、
停止后不再写入，以及旧数据库升级不丢点。这些是待验证条件，不是本轮测试结果。

### 复用已有 Mission ID，贯通路测与测速

`NetworkSurveyService.java:onSurveyStarted()/onSurveyStopped()` 已管理采集会话；
`getMissionIdForRecords()` 提供当前 Mission ID。采集监控模型也有 `sessionId`，
不能再描述为项目完全没有会话能力。

但 `MapPointRecord`、`RoadSample`、`SpeedTestRecord` 未保存共同的会话标识；
`roadTracks()` 目前按卡槽及两分钟间隔分轨。短时间停止再开始可能仍归入同一条轨迹，
测速结果也无法按任务直接与打点、诊断对应。

建议为主动路测增加每轮唯一、可持久化的会话 ID；已有活动 Mission 时保存其关联，
没有时也允许独立路测。开始/停止/取消/失败状态应先入库，
异步回调携带启动时的会话 ID，不能在回调到来时套用新任务的 ID。
CSV 导出补充可选列，导入旧文件仍兼容；不能只改模型而漏掉实际写入/读取路径。

### 增加事件时间线，并明确无 Root 能确认到哪一层

本轮核对了以下 T-Phone 处理器：

- `airmessage/eventanalyzer/lte/handover/LteHandoverEvent.java`：
  按 RRC 消息、随机接入消息和状态区分尝试、完成及成功/失败。
- `airmessage/eventanalyzer/nr5g/handover/SAHandoverEvent.java`：
  记录开始时间，区分重配完成、随机接入成功和失败分支。
- `airmessage/eventanalyzer/lte/mm/LteReselectionEvent.java` 及
  `lte/rlf/DropEvent.java`：重选和重建过程另有状态。
- `g/m/l0.java:u()/v()` 将选定 NR 异常事件按 `TICK` 与位置/室内表关联；
  `replay/adapter/EventFilterAdapter` 提供事件筛选，当前枚举仅启用部分 NR 异常。

本项目并非没有事件：`root_diag.c:serving_update()` 已输出 `cell_change`，
`rrc_events()` 已输出重配、释放、重建、SCG 失败等单条事件，
`RootDiagScreens.kt` 也已有事件筛选。真正的差距是跨消息过程关联，
以及事件与路测位置/业务测试的联合回放。

建议分两层补齐：

- 无 Root 层记录“观察到的服务小区变化、制式变化、服务状态丢失/恢复、
  无线样本缺失、定位失效/恢复”，保存卡槽、前后身份、采样时刻和证据来源。
  小区变化本身不足以确认真实切换成功，更不能据此统计切换失败率。
- Root 层在已核实字段的 DIAG/RRC 解码之上增加过程状态机，
  将开始、协议完成、结果未知和明确失败分开。普通重配消息不能一律认作切换。
  超时要能独立触发，并区分“未收到结果”和“收到协议失败”。

时间关联使用同一会话和单调时钟容差；位置按最近有效点匹配并保留年龄，
不能只按秒级时间相等连接，更不能把没有位置的事件直接丢掉。

### KPI 阈值与统计是新的可借鉴点

不同于前文的 `onekey/kpis` 展示注册层，另一个包已有完整的分段配置：

- `radiokpi/e.java` / `f.java` 分别注册 LTE、NR 的 RSRP、RSRQ、SINR 等阈值。
- `radiokpi/d.java` 编解码区间及开闭关系，`i.java` 保存设置，`j.java` 保存区间计数/比例。
- `database/test/f.java:h()/o()` 构造 `CountRegion` / `KpiAvg` 查询，
  `g/m/l0.java:a()` 查询总数、各区间计数及平均值。
- `KPIStatisticsDialog` 展示每段样本数、百分比及部分指标平均值；
  `IndoorActivity` / `OnekeyIndoor` 有实际调用点，不只是未接入的模型。

本项目 `analyzeRoad()` 已统计平均 RSRP、固定 -110 dBm 弱覆盖比例和连续弱覆盖区间；
可以在现有报告上增加可配置的 RSRP/SINR 分布、有效样本数和缺失比例，
不必先重构整个信号页。阈值、区间边界、单位和配置版本应随报告导出。

统计分母需要先明确：当前前台打点存在去重，不是严格等间隔采样，
“点数占比”不能标成“时长占比”或“距离覆盖率”。
每项指标分别计算有效样本分母；缺失值单列，不能作为 0 dB 进入 SINR 分布。
百分比使用浮点除法并测试区间边界；不照抄反编译输出中的算式。
本轮未核实 T-Phone 的 P10/P50/P90 实现，如增加分位数，应作为本项目独立功能。

### 补业务质量，而不是重复开发已有测速

本项目 `SpeedTestScreen` / `SpeedTransport` 已有 HTTP、FTP、WebDAV、
并发、重复次数、超时、取消与历史记录。没有必要重新移植一整套下载测速。

T-Phone 的 `UdpPingExecutor` / `UdpPingService` 将 DNS 查询、请求序号、
发送计数、接收计数、乱序及最小/平均/最大时延分别记录，并携带测试计划 ID。
其中 UDP 请求自带计划 ID、序号和单调时钟值，需要兼容的回显端，
不是向任意网站发送 UDP 就能得到 Ping 结果。

可优先增加独立轻量探测和与无线事件的关联：
DNS 耗时、TCP 建连耗时、HTTP 首字节及请求结果分开命名，
按会话记录测试端点和实际承载；之后再扩展 ICMP 或自有 UDP 回显。
HTTP 请求成功率不标为 ICMP 丢包率，网络切换/任务取消也不能都算网络失败。

当前 `SpeedTransport` 未指定蜂窝网络或订阅，不能将测速值直接标作当前卡槽的速率。
如果增加选卡测速，需要同时处理套接字和 DNS 的实际网络归属；
无法绑定或网络消失时明确结束或记录变化，避免静默切到 Wi-Fi/另一张卡。
这也是本项目需要独立设计的部分，已检查的 T-Phone UDP 实现同样不能证明解决了选卡绑定。

### 本轮建议实施顺序

| 顺序 | 补齐内容 | 可交付的第一步 |
| --- | --- | --- |
| 1 | 连续路测、会话和定位质量 | 服务持有主动路测任务；保存会话、位置质量及中断原因，兼容历史数据库/CSV |
| 2 | 无 Root 事件与地图联动 | 小区/制式/服务/定位变化时间线，点击事件定位到有效采样附近 |
| 3 | 路测 KPI 报告 | RSRP/SINR 分段、缺失比例、自定义阈值，明确点数/时长/距离口径 |
| 4 | 业务质量关联 | 复用测速；新增分阶段轻量探测，导出共同会话 ID 和实际网络 |
| 5 | Root 专项增强 | 无输出诊断与有上限重订阅，再做有证据的切换/重建过程识别 |
| 后续 | 室内与多场景工作流 | 独立楼层坐标、手工标记、任务模板，建立在前述记录基础上 |

本节只补充分析与验收范围，没有新增应用功能、后台权限或自动网络测试，
也没有把 T-Phone 的私有服务、反编译代码或 native 库加入仓库。

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

## 第三轮：5G 多载波与 CA

2026-11-19 静态核对同一 APK 与当前项目。没有运行 T-Phone，也没有拿到
同一台手机上 2CC / 3CC 激活、退激活的对照抓包。本节记录接入依据与第一版实现。

### T-Phone 的数据与呈现

- `tphone/main/k4.java` 把 `0xB9A7` 交给 `g/x/b/u0.java`（`NrCaInfo`）。
  后者按 2.3、2.4、3.0、3.2 分支解析载波列表，带宽码表也分版本。
  `u0.c()` 按载波输出 PCI、SSB ARFCN、Band、带宽及独立的 DL / UL 状态，
  区分 `Configured`、`Activated` 和 `Deconfigured`，也有 SUL 特殊处理。
  主载波部分字段来自另一份当前服务小区快照，不是所有列都只靠一条日志填满。
- `g/x/b/p0.java:W()` 将 NR 测量/统计结构交给 `g/x/b/v0.java`，分别维护
  每载波的测量、吞吐、PDSCH、PUSCH 和发射功率；这些统计与 `B9A7` 的状态
  不应视为同一条消息。`DetailNr5gFragment` 能在普通详情和 NR CA 视图间切换，
  `MeasureNr5gFragment` 单独显示每载波测量。表头可参考 PCC / SCC1 / SCC2，
  但我们的数据模型不应固定三列。
- 这里能确认的是该 APK 消费这些数据，不代表本项目支持的每个 DIAG 通道都能收到
  同版本 `B9A7`，也不代表普通 Android 应用能通过 T-Phone 的私有服务读取它。

### 现状与接入

| 层次 | 现状 | 第一版 |
| --- | --- | --- |
| Android 普通采集 | `NetworkDetailsFragment` 用 `getCellBandwidths()` 生成通用带宽摘要；`CellInfoNr` 的连接状态已传到 UI，但只挑一条 NR 辅服务小区，余下记录可能进入邻区 | 保留同一订阅、同一批次中明确标为服务/辅服务的 NR 小区，去除重复记录和邻区表中的这些服务项。显示“系统上报”，不保证列出实际全部 CC |
| Root / DIAG | `root_diag.c:parse_nr_b825()` 已输出配置列表 `nr_cfg.cells`（CC、PCI、SSB/DL ARFCN、频段、带宽、SCS）；`parse_nr_b887()` 已输出按 CC 的下行调度统计 `nr_dl_sched` | 先修 B825 的 `cells[400]` 输出上限及对应的去重键，让 `cells` 和 `ncells` 一致；优先复用已核实布局，未知布局只留诊断。B887 能匹配且足够新时显示“近期有下行调度”，缺包不等于停用 |
| Root 状态增强 | 项目当前没有订阅或解析 B9A7，B825 配置和 B887 调度不能独立提供完整 UL/DL 激活状态 | 采集实机原始包核对版本、长度、索引、状态和 SUL；确认后再加入可选订阅与解码。只在协议状态明确时显示“已激活 / 已配置 / 未配置” |

关联时按当前会话、订阅/协议栈和主小区划分载波快照，再以 CC 索引与可核实的
PCI、频点交叉校验。不要跨日志盲用相同 CC 编号，也不要把 SSB 频点当成 DL
中心频点。切卡、切换服务小区、释放连接或采样超时后清掉旧辅载波和调度状态；
两条同频段载波不能仅按频段合并。SUL 不计入下行 CC 数或下行总带宽。

Android 的 `getCellBandwidths()` 不返回每个带宽对应的小区身份，不按数组位置
给 NR 辅小区分配带宽，也不把混有 LTE 的数组标为 NR 总带宽。
`PhysicalChannelConfigListener` 有精确电话状态权限或运营商权限要求，不作为普通采集的必需链路。

### 信号页布局和验收

在主小区参数后、邻区前放「NR 多载波」区块。按载波逐行列出角色、频段、带宽、
PCI、NR-ARFCN；展开再看有值的 RSRP/RSRQ/SINR、DL/UL 状态和调度指标。
区块标题可显示“NR 2CC · 120 MHz”，但只在同一快照的两条 NR 下行载波都
有可归属带宽时才显示总带宽；激活数量和配置数量分别统计。

SA 模式的主载波是 NR PCell。EN-DC 的 LTE 锚点单列，NR PSCell 计为第一条 NR
载波；只有 LTE 锚点和一个 NR PSCell 时不是 NR CA。无 Root 时若无法区分
PSCell 与其他 NR 辅载波，统一标为“NR 服务载波”，不猜角色。两路模式都不能把
邻区、同一载波的多束 SSB、终端能力列表或 5G 图标当作 CA 证据。

真机验证需要同一订阅下空闲、蜂窝下载、停止下载的连续样本，与 T-Phone 表格
按时间对照。至少覆盖 SA 2CC、NSA 单 NR、NSA 多 NR、同频段双载波、
切换/释放时清除旧值，以及 4+ CC 不截断。保留 `nr_cfg`、`nr_dl_sched` 和
可取得的原始 DIAG 记录；无 Root 设备仅能验证系统确实上报的载波。

标准与 API 口径：3GPP TS 37.340（MR-DC 的 PCell / PSCell / SCell）、
3GPP TS 38.321（NR SCell 激活与去激活）、Android `CellInfo#getCellConnectionStatus()`、
`ServiceState#getCellBandwidths()` 与 AOSP `TelephonyCallback.PhysicalChannelConfigListener`。

### 第一版行为

- 信号页在邻区前展示「NR 多载波」。无 Root 只列同一订阅、同一批次内 15 秒以内的
  NR 服务观测，以 PCI + NR-ARFCN 去重；NSA 角色不细分，LTE 不计入 CC。
  不分摊系统带宽数组。仅有一条 NR 时保留原来的 NSA 详情。
- Root 复用完整 B825 列表（上限 16 项），只采用现有非推测的 3.8 布局。
  仅当 CC 0 的 PCI 与近期 NR 主载波一致且频点匹配 SSB 或 DL ARFCN 时显示，SUL 不计入。
  全部显示载波均有已知带宽时才合计。已确认的配置在同一服务小区持续可见时保留；
  切换、释放、超过 10 秒无服务或主载波调度观测和采集器重启均不显示旧载波状态。
- B887 必须符合当前主小区、CC、PCI 以及相同的 SSB 或 DL ARFCN，且调度窗口不跨
  配置切换或释放。4 秒内匹配的记录注明「近期有下行调度」，不等于报告激活状态。
  尚未实现 B9A7 或独立 UL 激活状态。
- `src/test/host/test_nr_carriers.ps1` 覆盖 Android/DIAG 关联和过期边界；
  `src/test/cpp/test_nr_config_host.ps1` 使用合成 4/16 CC 数据验证 B825 完整输出与去重。
  这两套测试不等同于真实 modem 上的 CA 抓包验收。

## 第四轮：子帧、时隙和逐载波参数

对照 `DetailLteFragment`、`DetailNr5gFragment`、`MeasureNr5gFragment`、`tphone/views/z.java`、
`g/x/b/u0.java`、`g/x/b/v0.java` 及 APK 中文资源。以下区分已确认的界面、现有实现和后续候选：

| 参数 | T-Phone 静态证据 | 本项目与可补内容 |
| --- | --- | --- |
| LTE TDD 子帧 | LTE 详情资源 `lteinfo_subframe_scheduled`、`lteinfo_subframe_statistics_total`、`lteinfo_rbpersubframe_avg`、`lteinfo_tbsizepersubframe_avg` 是采样统计，未确认该 APK 有静态 D/S/U 配比页面 | 本轮读取 LTE SIB1 的 `subframeAssignment` 与基础 `specialSubframePatterns`。按 TS 36.211 显示 10 子帧 D/S/U 顺序及数量；特殊子帧不并入 DL 或 UL |
| NR TDD 时隙 | `RBs/Slot`、`TBSize/Slot`、`Grant Num` 是上下行调度表指标，不能推出公共配比 | 现有 NR SIB1 已解公共 `pattern1/2`；本轮补显示周期及 DL/UL/灵活符号数，按 reference SCS 校验。未等同动态 SFI 或专用时隙覆盖；仅收到 SIB1 后显示，NSA 不一定收到 |
| NR CA 与 SUL | `k4.java` 将 B9A7 分发到 `u0`，再交 `v0`；`u0.c()` 对 SCC 分别解 DL/UL 状态，主载波字段来自其他测量；SUL 行无 DL 状态 | 现有 B825 只代表配置、B887 只代表近期下行调度。本轮仅在 QMDL 录制时尝试订阅 B9A7，不解码状态。录制 1CC、2CC、下行/上行负载与退激活对照后再接状态 |
| 调度和物理层 | `z.java` 有 Grant 数、RB/TBSize 每槽、MCS 众数/最佳/均值、调制众数、BLER/iBLER/rBLER、层数、TxMode、RI/CQI、DL/UL BWP 索引 | 已有每 TB 的 RB/MCS、CRC BLER、部分 RI/CQI、发射功率和初始 BWP。后续优先补「按码字/首次与重传 BLER」和「当前 BWP 索引」；RB/slot 要用去重槽标识，不能把每 TB 均值改名 |
| 逐载波测量 | `MeasureNr5gFragment` 与 `v0` 区分逐 CC 测量、调度及功率 | 已有服务小区和部分按 CC 调度。可加逐 CC 的 RSRP/RSRQ/SINR，但只对齐同 CC、PCI、频点和时间窗的测量，不用邻区值填充 |
| 录制停止 | `PacketCaptureActivity` / `PacketCaptureService` 是独立抓包工具，不能当作本项目 Qualcomm DCI/QMDL 停止回执的实现依据 | 本项目 native 停录先落盘、关文件并回执，可选日志订阅的启停改由独立线程处理。应用保留可导出的非空文件，空录制不替换上一份记录 |

`v0.w()` 在锁下替换整份 CA 快照，`v0.c()` 读取；这条路径没有按秒过期。
`v0.l()` 从这份配置列 CC，`v0.m()` 找非主载波且无下行状态的 SUL，表头单列。
逐载波调度同时用单独的对象更新，所以忙闲改变统计不会直接抹掉配置。
这不证明 APK 的所有断链清理路径已经还原。

2026-11-19 样本检查：用户提供的 25 份历史 QMDL 含可验证 CRC 的 DIAG 帧；
两份 `wireless-*` 均为非空录制。部分 `diagcap_*` 中可见 B825 3.8 从单载波、
双载波到 idle 的真实变化；样本未包含 B9A7 或本次录制失败后的文件。

### 信号页逐载波读数

- Android 模式只使用对应服务小区自身的 SS-RSRP / SS-RSRQ / SS-SINR。
- Root 模式只用 B97F 的服务层记录，按 PCI 与 SSB ARFCN 匹配 B825，不从邻区条目取值。
- 两份双载波样本中，580 条 B8D8 3.2 辅载波 SSB SINR 同时匹配 B825 的 CC / PCI
  及近期 B97F 的 PCI / SSB ARFCN；不匹配的记录不使用。B8D8 不提供 ARFCN，
  因此必须有同一配置周期内、前 2 秒的对应载波测量。测量 5 秒过期，不影响载波配置。

### B9A7 可选实时状态

- 按包版本适配 2.3、2.4、3.0、3.2，不按手机型号猜布局。未知版本、长度
  不符或非法载波记录只保留布局诊断，不改变现有配置。
- Root 模式独立订阅 B9A7；订阅失败仍用 B825 配置与 B887 近期下行调度。
  停止 QMDL 不撤销实时订阅。B9A7 按 CC、PCI 和频点匹配 B825，独立标记
  上下行状态，不从 SUL 或占位项添加下行载波；状态失效不删除 B825 配置。
- Android 模式不读取 DIAG。同一订阅和主小区临时漏报时，只保留已观测的载波
  身份，最长 30 秒，不复用漏报载波的信号读数。
- JSONL 包含无小区标识的 `android_nr_carriers`、`root_nr_carriers`、
  `root_b9a7_state`，可区分订阅结果、包布局、匹配和显示状态。四种 B9A7
  布局使用合成包回归，历史 QMDL 中没有相应真机样本。
