package com.craxiom.networksurvey.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.rootdiag.DiagOverlayState
import com.craxiom.networksurvey.ui.rootdiag.RootDiagPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service that keeps DIAG collection alive after the UI is stopped.
 * It never force-starts overlay permission settings and only manages its own views.
 */
class RootDiagCollectorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayJob: Job? = null
    private var window: WindowManager? = null
    private var overlay: View? = null
    private var label: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Application.createNotificationChannel(this)
        startInForeground()
        runningState.value = true
        maybeShowOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested.value = true
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_OVERLAY -> maybeShowOverlay()
        }
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        runningState.value = false
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to start the DIAG foreground service")
            foregroundFailed.value = true
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, NetworkSurveyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RootDiagCollectorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logging_thick_icon)
            .setContentTitle(getString(R.string.diag_bg_notification_title))
            .setContentText(getString(R.string.diag_bg_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.diag_bg_stop), stop)
            .build()
    }

    private fun maybeShowOverlay() {
        if (!RootDiagPrefs.overlayWanted(this) || !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }
        if (overlay != null) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        window = manager
        val text = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(8, 4, 8, 4)
        }
        label = text
        val close = TextView(this).apply {
            setTextColor(0xFFFFC107.toInt())
            setText("×")
            textSize = 16f
            setPadding(12, 4, 8, 4)
            setOnClickListener {
                RootDiagPrefs.setOverlayWanted(this@RootDiagCollectorService, false)
                hideOverlay()
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1B1B1B.toInt())
            setPadding(4, 2, 4, 2)
            addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
            elevation = 8f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }
        var lastX = 0
        var lastY = 0
        var startX = 0
        var startY = 0
        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = params.x
                    lastY = params.y
                    startX = event.rawX.toInt()
                    startY = event.rawY.toInt()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = lastX + event.rawX.toInt() - startX
                    params.y = lastY + event.rawY.toInt() - startY
                    runCatching { manager.updateViewLayout(card, params) }
                    true
                }
                else -> false
            }
        }
        try {
            manager.addView(card, params)
            overlay = card
            overlayJob = scope.launch {
                DiagOverlayState.snapshot.collect { snap -> label?.text = formatOverlay(snap) }
            }
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to add the DIAG overlay")
            hideOverlay()
        }
    }

    private fun formatOverlay(snap: com.craxiom.networksurvey.ui.rootdiag.DiagOverlaySnapshot): String {
        val parts = mutableListOf<String>()
        parts += if (snap.collecting) getString(R.string.diag_overlay_collecting) else snap.status.ifBlank { "—" }
        if (RootDiagPrefs.showRf(this)) {
            val rf = buildString {
                if (snap.rat.isNotBlank()) append(snap.rat)
                snap.pci?.let { append(" PCI ").append(it) }
                snap.rsrp?.let { append(" ").append("%.0f".format(it)).append(" dBm") }
                snap.sinr?.let { append(" / ").append("%.1f".format(it)).append(" dB") }
            }
            if (rf.isNotBlank()) parts += rf
        }
        if (RootDiagPrefs.showIms(this)) {
            val ims = when (snap.imsAvailable) {
                false -> getString(R.string.diag_ims_source_unavailable)
                else -> buildString {
                    append(snap.imsReg)
                    if (snap.imsRegSource.isNotBlank()) append(" (").append(snap.imsRegSource).append(")")
                    if (snap.imsCall.isNotBlank() && snap.imsCall != "idle") append(" · ").append(snap.imsCall)
                }
            }
            parts += ims
        }
        if (RootDiagPrefs.showQueue(this) && snap.decodeDropped > 0) {
            parts += getString(R.string.diag_overlay_dropped, snap.decodeDropped)
        }
        return parts.joinToString("\n")
    }

    private fun hideOverlay() {
        overlayJob?.cancel()
        overlayJob = null
        overlay?.let { view ->
            runCatching { window?.removeView(view) }
        }
        overlay = null
        label = null
    }

    companion object {
        const val ACTION_STOP = "com.craxiom.networksurvey.action.STOP_ROOT_DIAG"
        const val ACTION_REFRESH_OVERLAY = "com.craxiom.networksurvey.action.REFRESH_OVERLAY"
        const val NOTIFICATION_ID = 42
        private val runningState = MutableStateFlow(false)
        val running = runningState.asStateFlow()
        val stopRequested = MutableStateFlow(false)
        val foregroundFailed = MutableStateFlow(false)

        fun start(context: Context) {
            stopRequested.value = false
            foregroundFailed.value = false
            val intent = Intent(context, RootDiagCollectorService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RootDiagCollectorService::class.java))
        }

        fun refreshOverlay(context: Context) {
            if (running.value) {
                context.startService(Intent(context, RootDiagCollectorService::class.java).setAction(ACTION_REFRESH_OVERLAY))
            }
        }
    }
}
