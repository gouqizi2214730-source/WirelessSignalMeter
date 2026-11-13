package com.craxiom.networksurvey.ui.bluetooth.model

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.networksurvey.fragments.model.BluetoothViewModel

/**
 * 蓝牙列表快照桥梁。
 *
 * Java 侧用 [androidx.recyclerview.widget.SortedList]（SortedSet）保存扫描结果，
 * 它不是响应式的，Compose 无法直接订阅。这里在版本号变化时安全地取一份不可变快照，
 * 避免在重组期间集合被后台线程修改。
 */
object BluetoothListBridge {

    /**
     * 读取当前的蓝牙设备列表快照。
     *
     * @param viewModel 蓝牙页 ViewModel
     * @param version   列表版本号；仅用于触发重组，取值时会重新读集合
     */
    fun snapshot(viewModel: BluetoothViewModel, version: Long?): List<BluetoothRecord> {
        val set = viewModel.bluetoothList
        val result = ArrayList<BluetoothRecord>(set.size())
        // 与 Fragment 写入处使用同一把锁，避免并发修改
        synchronized(set) {
            for (i in 0 until set.size()) {
                set.get(i)?.let { result.add(it) }
            }
        }
        return result
    }
}
