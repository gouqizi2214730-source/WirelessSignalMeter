package com.craxiom.networksurvey.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * 更新下载器：DownloadManager 下载 APK → 广播自动 FileProvider 安装。
 * 下载完成后写入 Download/DIRECTORY_DOWNLOADS/wireless_signal_meter/update.apk。
 */
object UpdateDownloader {

    private val pendingDownloads = mutableMapOf<Long, String>()

    /** 触发下载（下载进度走系统通知栏）。 */
    fun download(context: Context, url: String) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("无线无忧 更新下载")
                .setDescription("正在下载新版本安装包…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "wireless_signal_meter/update.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val id = dm.enqueue(req)
            pendingDownloads[id] = url
        } catch (_: Exception) {
        }
    }

    /** 处理 DOWNLOAD_COMPLETE 广播：成功则拉起安装。 */
    fun handleDownloadComplete(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        pendingDownloads.remove(id) ?: return
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    install(context, Uri.parse(uri))
                }
            }
            cursor?.close()
        } catch (_: Exception) {
        }
    }

    private fun install(context: Context, uri: Uri) {
        val file = uri.path?.let { File(it) } ?: return
        if (!file.exists()) return
        val apkUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 下载完成广播接收器（Application 内注册，系统广播需 RECEIVER_EXPORTED）。 */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                handleDownloadComplete(context, intent)
            }
        }
    }

    fun register(context: Context) {
        runCatching {
            ContextCompat.registerReceiver(
                context,
                Receiver(),
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
}
