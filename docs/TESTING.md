# 无线无忧测试指南

## 信号、信令和地图回归

1. 在无 Root 或未授权 DIAG 的设备上，分别在 LTE 和 NR 下查看服务小区。检查运营商、注册 PLMN、频点与双工模式。系统未提供 5G 带宽且没有该小区的保存值时，带宽行应隐藏。
2. 在兼容设备开启 Root DIAG，检查信令列表和详情。手动滚动历史记录应停止自动跟随。开始、停止并导出一份 QMDL，确认文件可读取。
3. 导入含经纬度的 CSV 工参，按基站 ID 搜索。打开基站地图并授权定位，检查服务小区蓝线和已匹配实测邻区的橙线。切换小区或关闭连线图层时，旧连线应消失。
4. 导出一次常规 JSONL，确认文件可读取。带宽缺失时可检查 `android_nr_bandwidth` 记录中的 `reported_khz` 和 `cell_matches`。

## LTE SINR 兼容性回归

LTE SINR 先读 `CellSignalStrengthLte.getRssnr()`，缺失时依次尝试同一对象的
`getCellSinr()` / `getSnr()` / `getSinr()`，包含非 public 和继承的方法。
只有注册服务小区缺失 SINR 时才考虑当前订阅的 `SignalStrength`；要求单个 LTE
样本且 RSRP 一致。旧接口 `getLteRssnr()` 同时校验 `getLteRsrp()`，允许 LTE
样本列表为空，但不允许存在多个 LTE 样本或已知 RSRP 不匹配。

有效值仍为 -20 至 30 dB，包含 0；不除以 10，不使用 CQI，不猜测厂商自定义单位。
接口不存在、受非 SDK 限制、抛出异常或全部返回无效值时保留缺失状态。
现有有效 CellInfo 值不会被信号快照覆盖，未注册的小区不会借用服务小区快照。

1. 在原来能显示 SINR 的设备上检查 LTE 数值继续更新，核对 0 和负数。
2. 在原来 LTE RSRP/RSRQ 正常、仅 SINR 缺失的设备上，与同机其它工具对照。
   分别验证标准接口和厂商接口上报的场景；固件未提供或系统拒绝反射时仍可能缺失。
3. 双卡切换、LTE CA 和邻区场景下确认没有把主服务小区的 SINR 复制到其它小区。
4. 核对页面、地图、邻区和导出中的 LTE SINR，缺失时不得出现 MAX_VALUE 或 CQI。

`LteSinrUtilsTest` 为纯 JVM 测试，覆盖主值优先、私有/继承方法、异常回退、缺失 API、
数值边界、CQI 排除和旧快照 RSRP 匹配。2026-11-18 使用缓存 JDK 与 JUnit 直接
编译运行，12 项通过；复现脚本为 Git 忽略的 `.temp/test-lte-sinr.ps1`。
ADB 当前没有连接设备，尚未进行上述真机验证。

## 路测回放回归

1. 从工具页进入路测回放，加载本机打点。相同运营商的双卡应分为不同路线，超过两分钟的采样间隔应分段。
2. 导入旧版中文地图 CSV，以及包含 `deviceTime`、`ssRsrp`、`ssSinr`、`narfcn` 的 NR CSV。检查频点、PCI 0 和 SINR 0，导出后重新导入应保留数据。
3. 播放、暂停和拖动时间轴，切换 RSRP / SINR，选择弱覆盖路段后检查定位。缺失信号应显示为灰色，长时间断点和异常 GPS 跳点不应连线。
4. 弱覆盖按 RSRP 低于 -110 dBm 统计，连续路段至少包含三个采样点且持续五秒。恰好 -110 dBm 不算弱覆盖。
5. 暂停自动打点后加载路线，返回基站地图核对开关状态。升级数据库后旧坐标应保留，旧数据的 SIM 卡槽为未知。

自动测试：

```powershell
.\gradlew.bat :networksurvey:testCdrDebugUnitTest --tests '*RoadCoverageTest' --offline
```

`RoadCoverageTest` 包含旧 CSV 往返、原生 NR 列名、双卡分轨、缺失采样、GPS 跳点、阈值边界、无信号列和超过 50,000 行的文件检查。

## 上游电源管理测试

锁网协议、限制、本地验证结果和真机验收步骤见 [锁网工具](LOCKBAND.md)。

以下为继承自 Network Survey 的 Doze 和 Wake Lock 测试步骤，需在连接了 Android 设备的环境中执行。

## Power Management Tests

These tests verify that Network Survey continues to function properly when Android's power management features (like Doze mode) are active.

### Test #1: Doze Mode Behavior

This test verifies that the app continues logging data even when the device enters Doze mode.

#### Prerequisites

- Device connected via USB with ADB debugging enabled
- App running with at least one survey active (cellular, wifi, etc.)

#### Steps

1. **Start monitoring logs in a terminal:**
   ```bash
   adb logcat | grep -E "Wake lock|Device status|Doze"
   ```

2. **Force device into Doze mode:**
   ```bash
   adb shell dumpsys deviceidle force-idle
   ```
   
   You should see output like:
   ```
   Now forced in to idle mode
   ```

3. **Verify Doze mode is active:**
   ```bash
   adb shell dumpsys deviceidle get deep
   ```
   
   Should return: `IDLE`

4. **Monitor for 5-10 minutes**
   - Check if device status messages continue being generated
   - Look for any "Wake lock" related messages in logcat
   - Verify CSV files continue to receive entries

5. **Exit Doze mode:**
   ```bash
   adb shell dumpsys deviceidle unforce
   ```

6. **Reset device idle state (optional):**
   ```bash
   adb shell dumpsys deviceidle reset
   ```

#### Expected Results

- Device status messages should continue at regular intervals
- Wake lock should remain active during Doze
- No gaps in CSV logging

---

### Test #2: Wake Lock Monitoring

This test verifies proper wake lock management - acquisition when surveys start and release when they stop.

#### Steps

1. **Check initial wake lock state (no surveys running):**
   ```bash
   adb shell dumpsys power | grep -A 5 "NetworkSurvey"
   ```
   
   Should show no wake locks held.

2. **Start a survey (any type)**

3. **Check wake lock is acquired:**
   ```bash
   adb shell dumpsys power | grep -A 5 "NetworkSurvey"
   ```
   
   Should show something like:
   ```
   (com.craxiom.networksurvey.dev) - ACQ NetworkSurvey:WakeLock (partial)
   ```

4. **Start additional surveys** (e.g., if cellular is running, start wifi)
   - Run the wake lock check again
   - Should still show only ONE wake lock (not multiple)

5. **Stop one survey** (but leave others running)
   - Check wake lock is still held:
   ```bash
   adb shell dumpsys power | grep -A 5 "NetworkSurvey"
   ```

6. **Stop ALL surveys**
   - Check wake lock is released:
   ```bash
   adb shell dumpsys power | grep -A 5 "NetworkSurvey"
   ```
   - Should show no wake locks

#### Additional Wake Lock Monitoring

**Check battery stats for wake lock usage:**
```bash
adb shell dumpsys batterystats | grep -A 10 "NetworkSurvey"
```

**Monitor wake lock acquisition/release in real-time:**
```bash
adb logcat | grep -E "Acquiring wake lock|Releasing wake lock"
```

**Check if app is in battery optimization whitelist:**
```bash
adb shell dumpsys deviceidle whitelist | grep networksurvey
```

#### Expected Results

- Wake lock acquired when ANY survey starts
- Wake lock maintained while ANY survey is active
- Wake lock released ONLY when ALL surveys stop
- Only one wake lock instance at a time

---

## Future Test Sections

_This section will be expanded with additional test categories:_

- [ ] Survey Functionality Tests
- [ ] Data Export/Import Tests
- [ ] Network Connectivity Tests
- [ ] UI/UX Tests
- [ ] Performance Tests
- [ ] Compatibility Tests
