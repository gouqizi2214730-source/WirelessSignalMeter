package com.craxiom.networksurvey.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 进程级协程作用域：与 Application 同生命周期，永不取消。
 *
 * 用途：工参导入等「重活完成后需回主线程写 Compose state」的操作，若用
 * rememberCoroutineScope()，当用户在操作进行中离开/重建该 Compose 组合时，
 * 该 scope 会被取消，随后往 mutableStateOf 写入会抛出
 * "The coroutine scope left the composition"。
 *
 * 改用本作用域后，无论组合是否仍存活，协程都不会被取消，
 * 状态更新一律安全执行（写一个已离屏的 state 只是浪费，不会崩溃）。
 */
object AppScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
