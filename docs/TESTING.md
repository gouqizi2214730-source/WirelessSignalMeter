# 无线无忧测试指南

## 信号、信令和地图回归

1. 在无 Root 或未授权 DIAG 的设备上，分别在 LTE 和 NR 下查看服务小区。检查运营商、注册 PLMN、频点与双工模式。系统未提供 5G 带宽且没有该小区的保存值时，带宽行应隐藏。
2. 在兼容设备开启 Root DIAG，检查信令列表和详情。手动滚动历史记录应停止自动跟随。开始、停止并导出一份 QMDL，确认文件可读取。
3. 导入含经纬度的 CSV 工参，按基站 ID 搜索。打开基站地图并授权定位，检查服务小区蓝线和已匹配实测邻区的橙线。切换小区或关闭连线图层时，旧连线应消失。
4. 导出一次常规 JSONL，确认文件可读取。带宽缺失时可检查 `android_nr_bandwidth` 记录中的 `reported_khz` 和 `cell_matches`。

## 上游电源管理测试

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
