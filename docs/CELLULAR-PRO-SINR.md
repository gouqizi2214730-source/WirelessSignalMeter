# Cellular-Pro LTE SINR 调查

调查日期：2026-11-18。输入为用户提供的 Cellular-Pro 2.0.9.1（versionCode 99），
包名 `make.more.r2d2.play.cellular_pro`。
APK SHA-256：`AB59A59E9622BCCE2F6E1288C5209181C117388A0605389B710C19FEF8DCED3B`。

## 已确认的路径

- `A0B0.sj` 声明 `onSignalStrengthsChanged(SignalStrength)`；`SlotInfo` 保留独立的
  SignalStrength 和 CellInfo 数据源。
- `A0B0.ik` 的三个 LTE 辅助函数由 JNI 注册表第 652 项注册。
  解出方法索引后，`ik.b` 调用 `CellSignalStrengthLte.getRssnr()`，
  随后将整数装箱返回；该直接取值分支没有除以 10。
  `ik.a` 读取 RSRQ，`ik.c` 的 Parcel/反射代码处理 RSSI，不能误认为它在处理 SINR。
- `SlotInfo.c(SignalStrength, Cell)` 调用 `getDeclaredFields()`、
  `Field.setAccessible(true)`、`Field.get()`，以字段名小写后的
  `ltesignalstrength`、`ltersrp`、`ltersrq`、`lterssnr` 等关键字分派。
  `lterssnr` 分支进一步交给 `SlotInfo.G(Number, Cell)`。
  这证明它除了普通 getter，还包含厂商/旧系统字段取值路径。
- `SlotInfo.G` 的 SDK >= 30 分支把输入乘以 10 后存入自身字段，
  后续使用 -200..300 的边界，说明这个内部字段采用十分之一 dB。
  这与 `ik.b` 直接返回 getter 的 dB 整数并不矛盾；本项目最低 SDK 33，
  显示现代 getter 值时不应因为对方内部单位而再除以 10。
- APK 包含 HiddenApiBypass、华为 HiAgent 和独立高通 DIAG 组件。
  组件存在不能证明某一机型的 SINR 必须或一定能通过这些组件读取。

大部分核心方法运行在 native 虚拟机中，JADX 只能显示 native 声明。
本次通过 Ghidra、JNI 注册表、方法元数据及字节码交叉核对了上述调用；
未完整还原所有厂商分支和旧字段的单位转换策略。

## 无线无忧的改动

1. 保留 CellInfo 有效值优先，补充明确 LTE 字段读取。
2. 反射不可用时解析公开文本中的明确 LTE SINR 字段。完整信号文本限定在 LTE 段，
   防止把 NR SINR 或 CQI 当成 LTE SINR。这是本项目的兼容补充，
   不表示已经确认 Cellular-Pro 使用同样的文本解析实现。
3. 接收按订阅发送的信号强度回调，补齐停扫、换卡和销毁时的注销与缓存清理。
4. 用单个可归属的 LTE 主小区和采样时间限制快照回退，去掉 RSRP 必须完全相等的条件。
   已明确标记的 CA 辅小区只用自身测量；多个角色未知的注册小区不借用快照。
   SignalStrength 不携带 PCI，该回退仍存在异步采样的关联局限。
5. 现代值按 dB 使用。旧平铺 `mLteRssnr` 字段可能使用十分之一 dB，
   缺少机型及单位证据时不猜测缩放，不直接拿来显示。

未引入该 APK 的源码、native 库或全局隐藏 API 豁免。Root DIAG 仍为独立路径。
如果系统返回值、字段和公开文本均无测量值，普通采集仍显示缺失。

## 验证范围

22 项纯 JVM 测试通过；另补充了 LTE 连接小区数量的 Android 单测。
构建与设备验证状态见 `RESUME-2026-11-18.md` 和 `TESTING.md`。
没有连接到用户故障机型，不能据此宣布真机问题已经解决。
