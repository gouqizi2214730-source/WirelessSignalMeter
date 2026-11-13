package com.craxiom.networksurvey.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.util.AppLogger
import com.craxiom.networksurvey.util.LogUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 上传日志对话框：填写问题描述 → 收集本地日志（filesDir/logs/app.log）→ 直传服务器 /api/log。
 * 照搬 WirelessSignalMeter 旧版「日志与反馈 → 上传日志」交互。
 */
@Composable
fun UploadLogDialog(context: Context, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var logSizeKb by remember { mutableStateOf(AppLogger.logSize() / 1024) }
    var logPreview by remember { mutableStateOf<String?>(null) }

    // 打开对话框时立即刷新日志大小与内容预览（IO 读取 + 主线程更新）
    LaunchedEffect(Unit) {
        val (size, preview) = withContext(Dispatchers.IO) {
            // AppLogger.readLog / logSize 内部已处理 null/线程安全
            AppLogger.logSize() to AppLogger.readLog()
        }
        logSizeKb = size / 1024
        logPreview = preview
    }

    AlertDialog(
        onDismissRequest = { if (!uploading) onDismiss() },
        title = { Text("上传日志") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildString {
                        append("本地日志 ")
                        if (logSizeKb == 0L) append("（0 KB）")
                        else if (logSizeKb < 1024) append("${logSizeKb} KB")
                        else append("%.1f MB".format(logSizeKb / 1024.0))
                        append("，将随设备信息一并上传，用于排查问题。")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 日志内容预览：让用户确认日志确实写到本地（有内容），可信度更高
                logPreview?.let { preview ->
                    Text(
                        text = if (preview.isBlank()) "（日志文件为空）"
                        else "日志预览（最近内容）：\n" + preview.takeLast(600),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("请描述遇到的问题（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                result?.let { r ->
                    Text(
                        text = r,
                        fontSize = 13.sp,
                        color = if (r.startsWith("上传成功") || r.startsWith("日志上传成功")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !uploading,
                onClick = {
                    uploading = true
                    result = "正在上传…"
                    LogUploader.upload(context, description) { ok, msg ->
                        uploading = false
                        result = msg
                    }
                }
            ) {
                Text(if (uploading) "上传中…" else "上传")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uploading) {
                Text("关闭")
            }
        }
    )
}
