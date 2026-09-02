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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.util.AppLogger
import com.craxiom.networksurvey.util.LogUploader

/**
 * 上传日志对话框：填写问题描述 → 收集本地日志（filesDir/logs/app.log）→ 直传服务器 /api/log。
 * 照搬 WirelessSignalMeter 旧版「日志与反馈 → 上传日志」交互。
 */
@Composable
fun UploadLogDialog(context: Context, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val logSizeKb = remember { AppLogger.logSize() / 1024 }

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
                    text = "本地日志约 ${logSizeKb} KB，将随设备信息一并上传，用于排查问题。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
