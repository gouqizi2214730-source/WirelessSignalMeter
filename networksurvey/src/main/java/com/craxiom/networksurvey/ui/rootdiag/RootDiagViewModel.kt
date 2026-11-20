package com.craxiom.networksurvey.ui.rootdiag

import android.app.Application
import android.os.SystemClock
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.db.SurveyTaskStore
import com.craxiom.networksurvey.db.toJson
import com.craxiom.networksurvey.model.SurveyEvent
import com.craxiom.networksurvey.model.ownsDiagnostic
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.model.CellularViewModel
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel
import com.craxiom.networksurvey.util.AndroidLteSinrReader
import com.craxiom.networksurvey.util.LteSinrUtils
import com.craxiom.networksurvey.services.RootDiagCollectorService
import com.craxiom.networksurvey.ui.bandlock.BandLockProfile
import com.craxiom.networksurvey.ui.bandlock.LockSupportLevel
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal enum class RootDiagStatus {
    Checking, Unavailable, Denied, Starting, Unsupported, Running, Stopped,
}

internal data class DiagRecord(
    val id: Long, val receivedAt: Long, val data: JSONObject,
    val taskId: String? = if (data.isNull("task_id")) null else data.optString("task_id").takeIf(String::isNotBlank),
    val clockId: String? = if (data.isNull("clock_id")) null else data.optString("clock_id").takeIf(String::isNotBlank),
) {
    val type = data.optString("type")
    val source = data.optString("src")
    val time = data.optLong("t")
    val decodedTree = data.optString("decoded_tree")
}

internal data class RootSignalState(
    val status: RootDiagStatus = RootDiagStatus.Checking,
    val records: List<DiagRecord> = emptyList(),
    val errorCode: Int? = null,
    val errorDetails: List<String> = emptyList(),
    val cachedMeasureIds: Set<Long> = emptySet(),
    val failureReason: String? = null,
) {
    val useRootSignal: Boolean
        get() = (status == RootDiagStatus.Running || status == RootDiagStatus.Checking ||
            status == RootDiagStatus.Starting || status == RootDiagStatus.Stopped) &&
            records.any { record ->
                record.type == "serving" && record.data.optInt("pci", -1) >= 0 &&
                    record.data.optInt("arfcn", -1) >= 0
            }
}

internal enum class QmdlPhase { Idle, Starting, Recording, Stopping }
internal enum class QmdlResult { Saved, Limited, Interrupted, Empty, Error }
internal const val NR_RX_SAMPLE_TIMEOUT = 4_000L

internal data class QmdlState(
    val phase: QmdlPhase = QmdlPhase.Idle,
    val packets: Long = 0,
    val bytes: Long = 0,
    val hasRecording: Boolean = false,
    val result: QmdlResult? = null,
)

class RootDiagViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val signalState = MutableStateFlow(RootSignalState())
    internal val signal = signalState.asStateFlow()
    private val signalingState = MutableStateFlow<List<DiagRecord>>(emptyList())
    internal val signaling = signalingState.asStateFlow()
    private val qmdlDirectory by lazy { File(app.noBackupFilesDir, "qmdl") }
    private val activeQmdl get() = File(qmdlDirectory, "active.qmdl")
    private val latestQmdl get() = File(qmdlDirectory, "latest.qmdl")
    private val qmdlState = MutableStateFlow(QmdlState())
    internal val qmdl = qmdlState.asStateFlow()
    private val qmdlExport = MutableStateFlow<Int?>(null)
    internal val qmdlExportProgress = qmdlExport.asStateFlow()
    private val nrChart = CellularChartViewModel()
    private val lteChart = CellularChartViewModel()
    internal fun chartFor(rat: String) = if (rat == "NR") nrChart else lteChart
    // NAS/IMS configuration changes infrequently and the collector restarts on foreground entry.
    private val lastConfiguration = linkedMapOf<String, DiagRecord>()
    private val lastMeasurements = mutableMapOf<String, DiagRecord>()
    private val diagnosticRecords = ArrayDeque<JSONObject>()
    private val measurementSamples = ArrayDeque<JSONObject>()
    private val neighborProbeSamples = linkedMapOf<String, ArrayDeque<JSONObject>>()
    private val layoutNotices = linkedMapOf<String, JSONObject>()
    private val collectorRuns = ArrayDeque<JSONObject>()
    private var nextCollectorRun = 1L
    private var invalidLineCount = 0L
    private var lastCollectorStatus: JSONObject? = null
    private var lastSignalMode: JSONObject? = null
    private var lastAndroidNrBandwidth: JSONObject? = null
    private var overlayImsReg = "unknown"
    private var overlayImsSource = ""
    private var overlayImsAvailable: Boolean? = null
    private var overlayImsCall = "idle"
    private var overlayDropped = 0L
    private var lastEndStatus = RootDiagStatus.Checking
    private var lastEndError: Int? = null
    private var hadBeenRunning = false
    private var restartAttempt = 0
    private var simCount = 0
    private var selectedSubscriptionId: Int? = null
    private val imsProviders = linkedMapOf<String, ImsProviderStatus>()
    private var bandLockProfile = BandLockProfile(LockSupportLevel.Unsupported, "unknown", reason = "unchecked")
    private val capabilityState = MutableStateFlow(currentCapability())
    internal val capability = capabilityState.asStateFlow()

    internal fun recordSignalMode(root: Boolean, androidProtocol: String?, simCount: Int) {
        lastSignalMode = JSONObject().put("type", "signal_mode").put("t", System.currentTimeMillis())
            .put("mode", if (root) "root_diag" else "android").put("sim_count", simCount)
            .put("android_rat", androidProtocol ?: JSONObject.NULL)
    }

    internal fun recordAndroidNrBandwidth(sample: CellularViewModel.NrBandwidthDiagnostic?) {
        lastAndroidNrBandwidth = sample?.let {
            JSONObject().put("type", "android_nr_bandwidth").put("t", System.currentTimeMillis())
                .put("reported_khz", JSONArray(it.reportedKhz()))
                .put("registered_rat", it.registeredRat()).put("display_rat", it.displayRat())
                .put("single_carrier", it.singleCarrier()).put("cell_matches", it.cellMatches())
                .put("displayed_khz", it.displayedKhz())
        }
    }

    init {
        viewModelScope.launch {
            val hasRecording = withContext(Dispatchers.IO) { activeQmdl.length() > 0 || latestQmdl.length() > 0 }
            if (hasRecording) qmdlState.value = qmdlState.value.copy(hasRecording = true)
        }
        viewModelScope.launch {
            RootDiagCollectorService.stopRequested.collect { requested ->
                if (requested) {
                    RootDiagCollectorService.stopRequested.value = false
                    pause()
                }
            }
        }
    }

    internal fun startQmdl() {
        if (signalState.value.status != RootDiagStatus.Running ||
            qmdlState.value.phase != QmdlPhase.Idle || qmdlExport.value != null) return
        qmdlState.value = qmdlState.value.copy(phase = QmdlPhase.Starting, packets = 0, bytes = 0, result = null)
    }

    internal fun stopQmdl() {
        if (qmdlState.value.phase == QmdlPhase.Recording) {
            qmdlState.value = qmdlState.value.copy(phase = QmdlPhase.Stopping)
        }
    }

    private fun retainQmdl(): Boolean = when {
        activeQmdl.length() > 0 -> activeQmdl.renameTo(latestQmdl)
        activeQmdl.exists() -> activeQmdl.delete()
        else -> true
    }

    private suspend fun finishQmdl(result: QmdlResult) {
        val saved = withContext(Dispatchers.IO) {
            val hasNewData = activeQmdl.length() > 0
            val retained = retainQmdl()
            val outcome = when {
                !retained -> QmdlResult.Error
                !hasNewData && result == QmdlResult.Saved -> QmdlResult.Empty
                else -> result
            }
            outcome to (activeQmdl.length() > 0 || latestQmdl.length() > 0)
        }
        qmdlState.value = qmdlState.value.copy(phase = QmdlPhase.Idle, result = saved.first, hasRecording = saved.second)
    }

    internal fun exportQmdl(uri: Uri) {
        if (qmdlExport.value != null || qmdlState.value.phase != QmdlPhase.Idle) return
        qmdlExport.value = 0
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = if (activeQmdl.length() > 0) activeQmdl else latestQmdl
                    val total = file.length()
                    if (total <= 0) throw IOException("No QMDL data")
                    file.inputStream().use { input ->
                        val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                        output.use {
                            val buffer = ByteArray(256 * 1024)
                            var written = 0L
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                written += count
                                // Leave completion pending until the destination provider closes the stream.
                                qmdlExport.value = (100 * written / total).toInt().coerceIn(0, 99)
                            }
                        }
                    }
                }
                Toast.makeText(app, R.string.diag_qmdl_exported, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(app, R.string.diag_qmdl_export_failed, Toast.LENGTH_SHORT).show()
            } finally {
                qmdlExport.value = null
            }
        }
    }

    internal suspend fun exportDiagnosticLog(uri: Uri, includeMeasurementSamples: Boolean) {
        val snapshot = signalState.value
        val records = diagnosticRecords.toList()
        val layouts = layoutNotices.values.toList()
        val samples = if (includeMeasurementSamples) measurementSamples.toList() else emptyList()
        val probeSamples = if (includeMeasurementSamples) neighborProbeSamples.values.flatten() else emptyList()
        val collectorStatus = lastCollectorStatus
        val runs = collectorRuns.map { JSONObject(it.toString()) }
        val signalMode = lastSignalMode
        val androidNrBandwidth = lastAndroidNrBandwidth
        val androidLteSinr = AndroidLteSinrReader.snapshot()
        val neighborTables = neighborTableDiagnostics(snapshot.records)
        val invalidLines = invalidLineCount
        withContext(Dispatchers.IO) {
            val tasks = SurveyTaskStore.get(app)
            val taskIds = (snapshot.records.map { it.data } + records + samples + probeSamples)
                .mapNotNull { if (it.isNull("task_id")) null else it.optString("task_id").takeIf(String::isNotBlank) }.toMutableSet()
            snapshot.records.mapNotNullTo(taskIds) { it.taskId }
            androidLteSinr.mapNotNullTo(taskIds) { it.taskId() }
            tasks.active.value?.id?.let(taskIds::add)
            val header = JSONObject()
                .put("type", "export")
                .put("schema", 4)
                .put("active_task_id", tasks.active.value?.id ?: JSONObject.NULL)
                .put("tasks", JSONArray(taskIds.map { id -> tasks.taskMetadata(id).apply {
                    put("mission_count", optJSONArray("mission_ids")?.length() ?: 0)
                    remove("mission_ids")
                } }))
                .put("task_association", "captured_at_sample; missing task_id remains unassigned")
                .put("time_ms", System.currentTimeMillis())
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("app_version_code", BuildConfig.VERSION_CODE)
                .put("android", Build.VERSION.SDK_INT)
                .put("model", Build.MODEL)
                .put("manufacturer", Build.MANUFACTURER)
                .put("hardware", Build.HARDWARE)
                .put("soc_manufacturer", Build.SOC_MANUFACTURER)
                .put("soc_model", Build.SOC_MODEL)
                .put("baseband", Build.getRadioVersion().orEmpty())
                .put("collector_state", snapshot.status.name)
                .put("error_code", snapshot.errorCode ?: JSONObject.NULL)
                .put("invalid_json_lines", invalidLines)
                .put("measurement_samples_included", includeMeasurementSamples)
                .put("neighbor_probe_samples", probeSamples.size)
                .put("android_lte_sinr_samples", androidLteSinr.size)
                .put("android_lte_sinr_policy", AndroidLteSinrReader.POLICY)
                .put("qmdl_contains_raw_signaling", true)
                .put("qmdl_privacy", "Raw QMDL includes complete RRC, NAS and possibly SIP payloads with identifiers. Do not share it.")
                .put("measurement_samples_redacted", includeMeasurementSamples)
            val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(header.toString())
                for (taskId in taskIds) {
                    val events = tasks.events(taskId)
                    writer.appendLine(JSONObject().put("type", "task_event_window").put("task_id", taskId)
                        .put("limit", SurveyTaskStore.EVENT_LIMIT).put("limited", events.size > SurveyTaskStore.EVENT_LIMIT).toString())
                    for (event in events.takeLast(SurveyTaskStore.EVENT_LIMIT)) {
                        // Diagnostic exports retain the existing identity-redaction boundary.
                        writer.appendLine(event.toJson().apply { remove("detail") }.toString())
                    }
                }
                collectorStatus?.let { writer.appendLine(redactDiagnostic(it).toString()) }
                for (entry in runs) writer.appendLine(redactDiagnostic(entry).toString())
                signalMode?.let { writer.appendLine(it.toString()) }
                androidNrBandwidth?.let { writer.appendLine(it.toString()) }
                for (entry in androidLteSinr) writer.appendLine(entry.toDiagnosticJson().toString())
                for (entry in neighborTables) writer.appendLine(entry.toString())
                for (entry in layouts) writer.appendLine(redactDiagnostic(entry).toString())
                for (entry in snapshot.records) writer.appendLine(redactDiagnostic(entry.data)
                    .put("task_id", entry.taskId ?: JSONObject.NULL).put("clock_id", entry.clockId ?: JSONObject.NULL).toString())
                for (entry in records) writer.appendLine(redactDiagnostic(entry).toString())
                for (entry in samples) writer.appendLine(redactDiagnostic(entry).toString())
                for (entry in probeSamples) writer.appendLine(redactDiagnostic(entry).toString())
            }
        }
    }

    private fun recordDiagnostic(value: JSONObject) {
        val type = value.optString("type")
        when (type) {
            "status" -> lastCollectorStatus = value
            "unknown_layout", "layout_seen" -> {
                val key = type + ":" + value.optString("code") + ":" + value.optString("ver")
                if (key !in layoutNotices && layoutNotices.size == 128) layoutNotices.remove(layoutNotices.keys.first())
                layoutNotices[key] = value
            }
            "mr_sample" -> {
                if (measurementSamples.size == 128) measurementSamples.removeFirst()
                measurementSamples.addLast(value)
            }
            "neighbor_probe_sample" -> {
                val code = value.optString("code")
                if (code !in neighborProbeSampleCodes) return
                val samples = neighborProbeSamples.getOrPut(code) { ArrayDeque() }
                if (samples.size == MAX_NEIGHBOR_SAMPLES_PER_CODE) samples.removeFirst()
                samples.addLast(value)
            }
            "ims_provider" -> {
                if (diagnosticRecords.size == MAX_DIAGNOSTIC_MESSAGES) diagnosticRecords.removeFirst()
                diagnosticRecords.addLast(redactDiagnostic(value))
            }
            in signalingTypes -> {
                if (diagnosticRecords.size == MAX_DIAGNOSTIC_MESSAGES) diagnosticRecords.removeFirst()
                diagnosticRecords.addLast(if (value.has("decoded_tree")) redactDiagnostic(value) else value)
            }
        }
    }

    private var nextId = 0L
    private var firstVisibleMessageId = 0L
    private var capture: Job? = null
    private var authorized = false
    private var foreground = false
    private var attempted = false
    private var pausedByUser = false
    private var restartAfterStop = false

    internal fun enterForeground() {
        foreground = true
        if (pausedByUser || (attempted && !authorized)) return
        if (capture == null) start() else restartAfterStop = capture?.isCancelled == true
    }

    internal fun leaveForeground() {
        foreground = false
        if (keepsCollectingInBackground()) {
            RootDiagCollectorService.start(app)
            return
        }
        restartAfterStop = false
        capture?.cancel()
        RootDiagCollectorService.stop(app)
    }

    internal fun retry() {
        pausedByUser = false
        if (capture == null) start() else if (capture?.isCancelled == true) restartAfterStop = true
    }

    internal fun pause() {
        pausedByUser = true
        restartAfterStop = false
        capture?.cancel()
        RootDiagCollectorService.stop(app)
    }

    private fun keepsCollectingInBackground() =
        RootDiagPrefs.backgroundEnabled(app) && capture != null && !pausedByUser

    internal fun clearSignaling() {
        firstVisibleMessageId = nextId
        signalingState.value = emptyList()
    }

    private fun start() {
        if (capture != null) return
        if (!foreground && !RootDiagPrefs.backgroundEnabled(app)) return
        attempted = true
        signalState.value = RootSignalState(
            RootDiagStatus.Checking, lastMeasurements.values.toList() + lastConfiguration.values,
            cachedMeasureIds = lastMeasurements.values.mapTo(mutableSetOf()) { it.id },
        )
        capture = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                captureRecords()
            } finally {
                capture = null
                val keep = !pausedByUser && (foreground || RootDiagPrefs.backgroundEnabled(app))
                val retry = CollectorRestartPolicy.shouldRetry(
                    lastEndStatus, lastEndError, hadBeenRunning, restartAttempt,
                )
                if (keep && (restartAfterStop || retry)) {
                    restartAfterStop = false
                    if (retry) {
                        val wait = CollectorRestartPolicy.delayMs(restartAttempt)
                        restartAttempt++
                        delay(wait)
                    }
                    if (!pausedByUser && (foreground || RootDiagPrefs.backgroundEnabled(app))) start()
                    else syncCollectorService(lastEndStatus)
                } else {
                    syncCollectorService(lastEndStatus)
                }
            }
        }
        capture?.start()
        syncCollectorService(RootDiagStatus.Checking)
    }

    internal fun updateSimContext(count: Int, subscriptionId: Int?) {
        simCount = count
        selectedSubscriptionId = subscriptionId
        publishCapability()
        viewModelScope.launch(Dispatchers.IO) {
            imsProviders["android_telephony"] = readAndroidIms(app)
            withContext(Dispatchers.Main) { publishCapability() }
        }
    }

    internal fun updateBandLockProfile(profile: BandLockProfile) {
        bandLockProfile = profile
        publishCapability()
    }

    private fun syncCollectorService(status: RootDiagStatus) {
        val active = status == RootDiagStatus.Checking || status == RootDiagStatus.Starting ||
            status == RootDiagStatus.Running
        if (active && RootDiagPrefs.backgroundEnabled(app)) RootDiagCollectorService.start(app)
        else if (!active) RootDiagCollectorService.stop(app)
    }

    private fun currentCapability(): DeviceCapabilitySnapshot {
        val permission = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        return DeviceCapabilities.snapshot(
            sdk = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            soc = Build.SOC_MODEL,
            hardware = Build.HARDWARE,
            simCount = simCount,
            selectedSubscriptionId = selectedSubscriptionId,
            telephonyPermission = permission,
            collectorStatus = signalState.value.status,
            collectorError = signalState.value.errorCode,
            imsProviders = imsProviders.values.toList(),
            bandLock = bandLockProfile,
        )
    }

    private fun publishCapability() {
        capabilityState.value = currentCapability()
    }

    private fun publishOverlay(status: RootDiagStatus, records: List<DiagRecord>) {
        val serving = records.filter { it.type == "serving" }.maxByOrNull { it.receivedAt }?.data
        val registration = if (overlayImsAvailable == false) "unavailable" else overlayImsReg
        DiagOverlayState.publish(
            DiagOverlaySnapshot(
                collecting = status == RootDiagStatus.Running || status == RootDiagStatus.Starting ||
                    status == RootDiagStatus.Checking,
                status = status.name,
                rat = serving?.optString("rat").orEmpty(),
                pci = serving?.optInt("pci", -1)?.takeIf { it >= 0 },
                rsrp = serving?.optDouble("rsrp")?.takeIf { it.isFinite() },
                sinr = serving?.optDouble("sinr")?.takeIf { it.isFinite() },
                imsReg = registration,
                imsRegSource = if (overlayImsAvailable == false) "unavailable" else overlayImsSource,
                imsAvailable = overlayImsAvailable,
                imsCall = overlayImsCall,
                decodeDropped = overlayDropped,
            )
        )
    }

    private suspend fun captureRecords() {
        val tasks = try { withContext(Dispatchers.IO) { SurveyTaskStore.get(app) } }
        catch (error: android.database.SQLException) {
            timber.log.Timber.w(error, "Could not open diagnostic task store")
            null
        }
        val helper = File(app.applicationInfo.nativeLibraryDir, "libwireless_root_diag.so")
        val run = JSONObject().put("type", "collector_run").put("id", nextCollectorRun++)
            .put("started_ms", System.currentTimeMillis()).put("clock_id", SurveyTaskStore.CLOCK_ID)
        if (collectorRuns.size == MAX_COLLECTOR_RUNS) collectorRuns.removeFirst()
        collectorRuns.addLast(run)

        var process: Process? = null
        var status = RootDiagStatus.Checking
        var errorCode: Int? = null
        val metrics = mutableMapOf<String, DiagRecord>()
        val cached = lastMeasurements.toMutableMap()
        val measurementConfigs = mutableMapOf<String, RrcMeasurementConfig>()
        var readyAt: Long? = null
        val pending = ArrayDeque<DiagRecord>()
        val startupLog = ArrayDeque<String>()
        val line = ByteArrayOutputStream()
        var overlong = false
        var lastFlush = 0L
        var lastHeartbeat = SystemClock.elapsedRealtime()
        val lastChartRecords = mutableMapOf<String, Long>()
        val lastEventStates = mutableMapOf<String, String>()
        val recentRrc = mutableMapOf<String, JSONObject>()
        val runTaskIds = linkedSetOf<String>()
        val buffer = ByteArray(8192)
        var qmdlCommand: Char? = null
        var qmdlDeadline: Long? = null

        fun publish(now: Long) {
            metrics.entries.removeAll { (_, record) ->
                when (record.type) {
                    "nr_rach" -> false
                    "nr_ssb_rx" -> now - record.receivedAt > NR_RX_SAMPLE_TIMEOUT
                    else -> now - record.receivedAt > MEASUREMENT_TIMEOUT
                }
            }
            if (readyAt?.let { now - it > MEASUREMENT_TIMEOUT } == true) cached.clear()
            lastMeasurements.clear()
            lastMeasurements.putAll(cached)
            lastMeasurements.putAll(metrics)
            val errorDetails = if (status == RootDiagStatus.Unsupported) startupLog.toList() else emptyList()
            val cachedIds = (if (status == RootDiagStatus.Stopped) lastMeasurements else cached)
                .values.mapTo(mutableSetOf()) { it.id }
            lastEndStatus = status
            lastEndError = errorCode
            if (status == RootDiagStatus.Running) {
                hadBeenRunning = true
                restartAttempt = 0
            }
            signalState.value = RootSignalState(
                status, lastMeasurements.values.toList() + lastConfiguration.values,
                errorCode, errorDetails, cachedIds,
                failureReason = CollectorRestartPolicy.collectorReason(status, errorCode).takeIf {
                    status == RootDiagStatus.Unsupported || status == RootDiagStatus.Denied ||
                        status == RootDiagStatus.Unavailable
                },
            )
            publishOverlay(status, signalState.value.records)
            publishCapability()
            if (status == RootDiagStatus.Running) {
                for (rat in listOf("NR", "LTE")) {
                    val record = metrics["serving:$rat"] ?: continue
                    if (lastChartRecords.put(rat, record.id) != record.id) {
                        val protocol = if (rat == "NR") CellularProtocol.NR else CellularProtocol.LTE
                        chartFor(rat).recordSignal(protocol, record.data.optDouble("rsrp").takeIf { it.isFinite() }?.toInt())
                    }
                }
            }
            if (pending.isNotEmpty()) {
                while (pending.isNotEmpty() && pending.first().id < firstVisibleMessageId) pending.removeFirst()
                if (pending.isNotEmpty()) {
                    var detailChars = 0
                    signalingState.value = (pending.reversed() + signalingState.value).asSequence()
                        .take(MAX_MESSAGES)
                        .takeWhile { record ->
                            detailChars += record.decodedTree.length
                            detailChars <= MAX_DETAIL_CHARS
                        }.toList()
                }
                pending.clear()
            }
        }

        fun storeMeasurement(key: String, record: DiagRecord) {
            cached.remove(key)
            metrics[key] = record
        }

        fun removeMeasurements(prefix: String) {
            metrics.keys.removeAll { it.startsWith(prefix) }
            cached.keys.removeAll { it.startsWith(prefix) }
        }

        fun storeCellConfiguration(key: String, record: DiagRecord, data: JSONObject) {
            lastConfiguration.remove(key)
            lastConfiguration[key] = record.copy(data = data)
            val prefix = key.substringBefore(':') + ":"
            val keys = lastConfiguration.keys.filter { it.startsWith(prefix) }
            if (keys.size > MAX_BROADCAST_CELLS) lastConfiguration.remove(keys.first())
        }

        fun storeSchedulingSamples(record: DiagRecord) {
            val tech = if (record.type.startsWith("nr_")) "NR" else "LTE"
            val data = record.data
            val serving = metrics["serving:$tech"]?.data
            val channel = if (tech == "NR") "arfcn" else "earfcn"
            val pci = data.optInt("pci", serving?.optInt("pci", -1) ?: -1)
            val arfcn = data.optInt(channel, serving?.optInt("arfcn", -1) ?: -1)
            if (pci < 0 || arfcn < (if (tech == "NR") 1 else 0)) return
            fun keep(row: JSONObject, group: String) {
                val cc = row.optInt("cc", -1)
                if (cc !in 0..15) return
                if (record.type == "nr_dl_sched" && cc == 0 &&
                    (row.optInt("pci", pci) != pci || row.optInt("arfcn", arfcn) != arfcn)) return
                val sample = JSONObject(row.toString()).put("type", "schedule_sample")
                    .put("rat", tech).put("group", group).put("serving_pci", pci).put("serving_arfcn", arfcn)
                    .put("dt_ms", data.optLong("dt_ms", -1)).put("tentative", if (record.isEstimated()) 1 else 0)
                storeMeasurement("schedule_sample:$tech:$pci:$arfcn:$group:$cc", record.copy(data = sample))
            }
            if (record.type.endsWith("_csf")) {
                keep(data, record.type)
                return
            }
            val rows = data.optJSONArray(if (record.type == "nr_pdsch") "recs" else "cc") ?: return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                when {
                    record.type.endsWith("_ul_pwr") || record.type.startsWith("lte_pucch_") -> {
                        if (row.has("pusch_dbm")) keep(row, "${record.type}:pusch")
                        if (row.has("pucch_dbm")) keep(row, "${record.type}:pucch")
                    }
                    record.type == "lte_ul_tx" -> {
                        if (row.has("mcs_avg") || row.has("retx_pct")) keep(row, record.type)
                        if (row.has("pucch_dbm")) keep(row, "${record.type}:pucch")
                    }
                    record.type == "nr_pdsch" -> if (row.has("bler_pct") || row.has("retx_pct")) keep(row, record.type)
                    row.optInt("tb") > 0 -> keep(row, record.type)
                }
            }
        }

        suspend fun receive(value: JSONObject) {
            val now = SystemClock.elapsedRealtime()
            val receivedTime = System.currentTimeMillis()
            val type = value.optString("type")
            val task = try {
                tasks?.active?.value ?: if (type in signalingTypes || type == "serving") tasks?.observationTask() else null
            } catch (error: android.database.SQLException) {
                timber.log.Timber.w(error, "Could not associate diagnostic task")
                null
            }?.takeIf { it.ownsDiagnostic(value.optLong("t"), receivedTime, now, SurveyTaskStore.CLOCK_ID) }
            value.put("task_id", task?.id ?: JSONObject.NULL).put("received_time_ms", receivedTime)
                .put("received_elapsed_ms", now).put("clock_id", SurveyTaskStore.CLOCK_ID)
                .put("collector_run", run.optLong("id"))
            if (task != null && runTaskIds.add(task.id)) run.put("task_ids", JSONArray(runTaskIds.toList()))
            val messageRat = value.optString("rat")
            if (type == "rrc") {
                value.put("message_id", nextId)
                recentRrc[messageRat] = value
            }
            if (type == "event") {
                val parent = recentRrc[messageRat]?.takeIf {
                    it.optString("msg") == value.optString("msg") &&
                        it.optString("task_id") == value.optString("task_id") &&
                        now - it.optLong("received_elapsed_ms") in 0..1_000
                }
                if (parent != null) {
                    for (key in listOf("pci", "arfcn", "src", "rrc_transaction_id", "cause", "tentative", "guess", "decode_incomplete")) {
                        if (!value.has(key) && parent.has(key)) value.put(key, parent.get(key))
                    }
                    value.put("related_message_id", parent.optLong("message_id"))
                }
            }
            val stateKey = "$type:${value.optString("rat")}"
            val state = listOf("state", "reg", "call", "status").joinToString(":") { value.optString(it) }
            val isStateChange = type in setOf("nas_mm", "ims_reg", "ims_call", "ims_state") &&
                state.replace(":", "").isNotBlank() && lastEventStates.put(stateKey, state) != state
            val completion = type == "rrc" && value.optString("msg") in setOf(
                "rrcSetupComplete", "rrcConnectionSetupComplete", "rrcResumeComplete", "rrcConnectionResumeComplete",
                "rrcReconfigurationComplete", "rrcConnectionReconfigurationComplete",
                "rrcReestablishmentComplete", "rrcConnectionReestablishmentComplete", "securityModeFailure",
            )
            if (task != null && (type in setOf("event", "cell_change", "nas_release") || isStateChange || completion)) {
                val rat = value.optString("rat").takeIf { it in setOf("NR", "LTE") }
                // A private DIAG stream has no reliable Android subscription ID.
                val estimated = value.optInt("tentative") == 1 || value.optInt("guess") == 1
                val sourceTime = value.optLong("t")
                val sourceAge = receivedTime - sourceTime
                val fresh = sourceTime > 0 && sourceAge in 0..10_000
                val pci = value.optInt("to_pci", value.optInt("pci", -1)).takeIf { !estimated && fresh && it in 0..1007 }
                val arfcn = value.optInt("to_arfcn", value.optInt("arfcn", -1)).takeIf { !estimated && fresh && it >= 0 }
                val title = if (type == "cell_change") "服务小区变化（DIAG）"
                    else (if (messageRat == "IMS") value.optString("event") else value.optString("msg"))
                        .ifBlank { value.optString("event").ifBlank { "$type $state" } } +
                        if (estimated) "（推测）" else ""
                val safeValue = redactDiagnostic(value)
                val details = listOf("msg", "cause", "why", "from_pci", "from_arfcn", "to_pci",
                    "to_arfcn", "rrc_transaction_id", "related_message_id", "collector_run", "src", "tentative", "guess", "decode_incomplete")
                    .filter { safeValue.has(it) }.joinToString(" · ") { "$it=${safeValue.opt(it)}" }
                try {
                    withContext(Dispatchers.IO) {
                        tasks?.append(SurveyEvent(java.util.UUID.randomUUID().toString(), task.id,
                            if (fresh) sourceTime else receivedTime, if (fresh) now - sourceAge else now,
                            SurveyTaskStore.CLOCK_ID, "root_diag", type,
                            title, details, rat = rat, pci = pci, arfcn = arfcn,
                            sourceTimeMs = sourceTime.takeIf { it > 0 },
                            timeBasis = if (fresh) "source_age_adjusted" else "received"))
                    }
                } catch (error: android.database.SQLException) {
                    timber.log.Timber.w(error, "Could not save DIAG event")
                }
            }
            if (value.optString("type") == "neighbor_probe_sample") value.put("collector_run", run.optLong("id"))
            recordDiagnostic(value)
            when (value.optString("type")) {
                "status" -> {
                    val state = value.optString("state")
                    run.put("last_status", value)
                    if (state == "running") run.put("last_running_status", value)
                    val next = when (state) {
                        "authorized" -> RootDiagStatus.Starting
                        "running" -> RootDiagStatus.Running
                        "start_error" -> RootDiagStatus.Unsupported
                        "error" -> RootDiagStatus.Unsupported
                        "denied" -> RootDiagStatus.Denied
                        else -> return
                    }
                    if (next == RootDiagStatus.Starting) authorized = true
                    if (next == RootDiagStatus.Denied) authorized = false
                    if (next == RootDiagStatus.Running && readyAt == null) readyAt = now
                    status = next
                    errorCode = value.optInt("rc").takeIf { it != 0 }
                    lastHeartbeat = now
                    overlayDropped = value.optLong("decode_dropped")
                }
                "ims_provider" -> {
                    val name = value.optString("name")
                    val statusName = value.optString("status")
                    val available = if (value.has("available")) value.optBoolean("available") else null
                    if (name.isNotBlank()) {
                        imsProviders[name] = ImsProviderStatus(name, statusName, available, value.optString("detail"))
                        publishCapability()
                    }
                    if (name == "samsung_logcat") {
                        overlayImsAvailable = when {
                            available == false || statusName == "unavailable" -> false
                            available == true || statusName == "available" -> true
                            else -> overlayImsAvailable
                        }
                        if (overlayImsAvailable == false) {
                            overlayImsReg = "unavailable"
                            overlayImsSource = "samsung_logcat"
                        } else if (overlayImsAvailable == true && overlayImsSource.isBlank()) {
                            overlayImsSource = "samsung_logcat"
                        }
                    }
                    if (name == "qualcomm_diag" && (available == true || statusName == "available")) {
                        if (overlayImsAvailable != true) overlayImsSource = "qualcomm_diag"
                    }
                }
                "ims_state", "ims_reg", "ims_call" -> {
                    if (type == "ims_state" || type == "ims_reg") {
                        val registration = value.optString("reg").ifBlank { value.optString("result") }
                        if (overlayImsAvailable != false && registration.isNotBlank()) {
                            overlayImsReg = registration
                            overlayImsSource = when {
                                value.optInt("reg_inferred") == 1 -> "inferred"
                                value.optString("src").isNotBlank() -> value.optString("src")
                                else -> overlayImsSource.ifBlank { type }
                            }
                        }
                    }
                    value.optString("call").takeIf { it.isNotBlank() }?.let { overlayImsCall = it }
                }
                "qmdl" -> {
                    if (qmdlCommand == null) return
                    val current = qmdlState.value
                    qmdlState.value = current.copy(
                        packets = value.optLong("packets"), bytes = value.optLong("bytes"),
                        phase = if (current.phase == QmdlPhase.Stopping) QmdlPhase.Stopping else QmdlPhase.Recording,
                    )
                    val result = when (value.optString("state")) {
                        "stopped" -> QmdlResult.Saved
                        "limit" -> QmdlResult.Limited
                        "error" -> QmdlResult.Error
                        else -> null
                    }
                    if (qmdlCommand == 'Q' || result != null) qmdlDeadline = null
                    if (result != null) {
                        finishQmdl(result)
                        qmdlCommand = null
                    }
                }
                "mr_sample", "neighbor_probe_sample", "unknown_layout", "layout_seen" -> Unit
                else -> {
                    val record = DiagRecord(nextId++, now, value)
                    val rat = value.optString("rat")
                    when (record.type) {
                        "nas_release" -> {
                            val session = value.optString("psi", value.optString("ebi"))
                            lastConfiguration.remove("nas_pdu:$rat:$session")
                            lastConfiguration.remove("nas_qos:$rat:$session")
                            if (rat == "LTE") {
                                lastConfiguration.values.removeAll { previous ->
                                    previous.type == "nas_qos" && previous.data.optString("rat") == "LTE" &&
                                        previous.data.optString("linked_ebi") == session
                                }
                            }
                        }
                        "nas_pdu", "nas_qos" -> {
                            val session = value.optString("psi", value.optString("ebi"))
                            val key = "${record.type}:$rat:$session"
                            val previous = lastConfiguration[key]
                            if (record.type == "nas_pdu" && value.optBoolean("modified") && previous != null) {
                                val updated = JSONObject(value.toString())
                                if (updated.optString("dnn").isBlank() && previous.data.optString("dnn").isNotBlank()) {
                                    updated.put("dnn", previous.data.optString("dnn"))
                                    updated.put("ims", previous.data.optInt("ims"))
                                }
                                lastConfiguration[key] = record.copy(data = updated)
                            } else {
                                if (record.type == "nas_pdu" && !value.optBoolean("modified")) {
                                    lastConfiguration.remove("nas_qos:$rat:$session")
                                }
                                lastConfiguration[key] = record
                            }
                        }
                        "nas_mm" -> {
                            if (value.optString("state") == "Deregistered") {
                                lastConfiguration.values.removeAll { previous ->
                                    previous.type in setOf("nas_pdu", "nas_qos") && previous.data.optString("rat") == rat
                                }
                            }
                            lastConfiguration["nas_mm:$rat"] = record
                        }
                        in configurationTypes -> lastConfiguration[record.type] = record
                        "cell_change" -> if (rat == "NR") {
                            removeMeasurements("nr_rach:")
                            removeMeasurements("nr_ssb_rx:")
                        }
                        "nr_rach" -> if (record.source == "0xB88A" && value.has("ta")) {
                            val pci = value.optInt("pci", -1)
                            val arfcn = value.optInt("arfcn", -1)
                            if (pci in 0..1007 && arfcn in 1..3_279_165) {
                                storeMeasurement("nr_rach:serving", record)
                            }
                        }
                        "lte_cell", "lte_mib" -> {
                            val pci = value.optInt("pci", -1)
                            val earfcn = value.optInt("earfcn", -1)
                            if (pci in 0..503 && earfcn in 0..262_143) {
                                val key = "${record.type}:$earfcn:$pci"
                                val previous = lastConfiguration[key]?.data
                                val cid = value.optLong("cid")
                                val sameCell = cid <= 0 || (previous?.optLong("cid") ?: 0L) <= 0 ||
                                    previous?.optLong("cid") == cid
                                val config = JSONObject(if (sameCell) previous?.toString() ?: "{}" else "{}")
                                for (field in value.keys()) config.put(field, value.get(field))
                                storeCellConfiguration(key, record, config)
                            }
                        }
                        in signalTypes -> {
                            val channel = when (record.type) {
                                "serving" -> rat
                                "lte_meas" -> "${value.optString("src")}:${value.optInt("earfcn", -1)}:" +
                                    "${value.optInt("pci", -1)}:${value.optInt("cc", -1)}"
                                "nr_meas" -> "${value.optInt("arfcn", -1)}:${value.optInt("layer", -1)}"
                                "nr_snr" -> "${value.optString("rs")}:${record.source}:" +
                                    "${value.optInt("pci", -1)}:${value.optInt("arfcn", -1)}"
                                "layer_throughput" -> "$rat:${value.optString("layer")}:${value.optString("dir")}"
                                "lte_phr", "lte_bsr" -> "${value.optInt("pci", -1)}:${value.optInt("arfcn", -1)}"
                                else -> ""
                            }
                            storeMeasurement("${record.type}:$channel", record)
                            if (record.type == "nr_snr" && !record.isEstimated() &&
                                value.optString("rs") == "SSB" && value.optInt("carrier", -1) == 0) {
                                val pci = value.optInt("pci", -1)
                                val arfcn = value.optInt("arfcn", -1)
                                if (pci in 0..1007 && arfcn in 1..3_279_165) {
                                    for (rx in 0..3) {
                                        val sinr = value.optDouble("sinr_rx$rx")
                                        if (sinr !in -23.0..40.0) continue
                                        val measurement = JSONObject().put("type", "nr_ssb_rx")
                                            .put("src", record.source).put("t", record.time)
                                            .put("pci", pci).put("arfcn", arfcn).put("rx", rx).put("sinr", sinr)
                                        storeMeasurement("nr_ssb_rx:$pci:$arfcn:${record.source}:$rx", record.copy(data = measurement))
                                    }
                                }
                            }
                            if (record.type in schedulingTypes) storeSchedulingSamples(record)
                            if (record.type == "nr_ul_sched" && value.has("mac_new_tb_mbps")) {
                                val sample = JSONObject(value.toString()).put("type", "nr_mac_ul_window")
                                storeMeasurement("nr_mac_ul_window", record.copy(data = sample))
                            }
                            if (record.type == "nr_cfg" && !record.isEstimated()) {
                                val cells = value.optJSONArray("cells")
                                for (i in 0 until (cells?.length() ?: 0)) {
                                    val cell = cells?.optJSONObject(i) ?: continue
                                    val pci = cell.optInt("pci", -1)
                                    val arfcn = cell.optInt("ssb_arfcn", -1)
                                    if (pci !in 0..1007 || arfcn !in 1..3_279_165) continue
                                    val type = "nr_carrier_config"
                                    storeCellConfiguration("$type:$pci:$arfcn", record,
                                        JSONObject(cell.toString()).put("type", type).put("arfcn", arfcn))
                                }
                            }
                        }
                        "nr_neighbor_sinr" -> {
                            val channel = value.optInt("arfcn", -1)
                            val cells = value.optJSONArray("cells")
                            if (channel in 1..3_279_165 && cells != null) {
                                for (entry in 0 until cells.length()) {
                                    val cell = cells.optJSONObject(entry) ?: continue
                                    val pci = cell.optInt("pci", -1)
                                    val sinr = cell.optDouble("sinr")
                                    if (pci !in 0..1007 || sinr !in -23.0..40.0) continue
                                    val key = "nr_neighbor_sinr:$channel:$pci"
                                    val previous = metrics[key]
                                    if (previous?.id == record.id && previous.data.optDouble("sinr") >= sinr) continue
                                    val reading = JSONObject().put("type", "nr_neighbor_sinr").put("src", "B96A")
                                        .put("arfcn", channel).put("pci", pci).put("ssb", cell.optInt("ssb"))
                                        .put("sinr", sinr).put("t", record.time)
                                    storeMeasurement(key, record.copy(data = reading))
                                }
                            }
                        }
                    }
                    if (record.type == "rrc" && (rat == "NR" || rat == "LTE") && !record.isEstimated()) {
                        val sinr = value.optInt("pcell_sinr_index", -1)
                        if (rat == "LTE" && sinr in 1..126) {
                            val reading = JSONObject().put("type", "lte_rrc_sinr")
                                .put("pci", value.optInt("pci", -1)).put("arfcn", value.optInt("arfcn", -1))
                                .put("sinr_index", sinr).put("t", record.time)
                            storeMeasurement("lte_rrc_sinr", record.copy(data = reading))
                        }
                        val config = measurementConfigs.getOrPut(rat) { RrcMeasurementConfig() }
                        val message = value.optString("display_msg").ifBlank { value.optString("msg") }
                        if (message in measurementContextReset && !value.optBoolean("rrc_suspended")) {
                            config.clear()
                            lastConfiguration.remove("plmn_selection:$rat")
                            if (rat == "LTE") {
                                metrics.remove("lte_rrc_sinr")
                                cached.remove("lte_rrc_sinr")
                            }
                            removeMeasurements("rrc_meas:$rat:")
                        }
                        val pci = value.optInt("pci", -1)
                        val arfcn = value.optInt("arfcn", -1)
                        val rrcState = when (message) {
                            "rrcSetupComplete", "rrcResumeComplete", "rrcReestablishmentComplete",
                            "rrcReconfigurationComplete", "rrcConnectionSetupComplete", "rrcConnectionResumeComplete",
                            "rrcConnectionReestablishmentComplete", "rrcConnectionReconfigurationComplete" -> "Connected"
                            "measurementReport" -> if (!value.optBoolean("decode_incomplete", true)) "Connected" else null
                            "rrcRelease", "rrcConnectionRelease" -> when {
                                value.optBoolean("rrc_suspended") -> if (rat == "NR") "Inactive" else "Suspended"
                                !value.optBoolean("decode_incomplete", true) -> "Idle"
                                else -> null
                            }
                            else -> null
                        }
                        if (rrcState != null && pci in 0..(if (rat == "NR") 1007 else 503) &&
                            arfcn in (if (rat == "NR") 1..3_279_165 else 0..262_143)) {
                            val reading = JSONObject().put("type", "rrc_state").put("rat", rat)
                                .put("state", rrcState).put("pci", pci).put("arfcn", arfcn).put("t", record.time)
                            storeMeasurement("rrc_state:$rat", record.copy(data = reading))
                        }
                        val broadcastPlmns = value.optJSONArray("broadcast_plmns")
                        val selectedPlmnIndex = value.optInt("selected_plmn_index", -1)
                        if ((broadcastPlmns != null || selectedPlmnIndex in 1..12) && pci in 0..(if (rat == "NR") 1007 else 503) &&
                            arfcn in (if (rat == "NR") 1..3_279_165 else 0..262_143)) {
                            val selectionKey = "plmn_selection:$rat"
                            val identity = JSONObject().put("rat", rat).put("pci", pci).put("arfcn", arfcn)
                            broadcastPlmns?.let { list ->
                                val key = "plmn_broadcast:$rat:$pci:$arfcn"
                                val previous = lastConfiguration.remove(key)?.data?.optJSONArray("plmns")
                                if (previous != null && previous.toString() != list.toString()) {
                                    val selection = lastConfiguration[selectionKey]?.data
                                    if (selection?.optInt("pci", -1) == pci && selection.optInt("arfcn", -1) == arfcn)
                                        lastConfiguration.remove(selectionKey)
                                }
                                val data = JSONObject(identity.toString()).put("type", "plmn_broadcast").put("plmns", list)
                                lastConfiguration[key] = record.copy(data = data)
                                val keys = lastConfiguration.keys.filter { it.startsWith("plmn_broadcast:$rat:") }
                                if (keys.size > MAX_BROADCAST_CELLS) lastConfiguration.remove(keys.first())
                            }
                            if (selectedPlmnIndex in 1..12) {
                                val data = identity.put("type", "plmn_selection").put("index", selectedPlmnIndex)
                                lastConfiguration[selectionKey] = record.copy(data = data)
                            }
                        }
                        value.optJSONObject("meas_config")?.let { update ->
                            config.update(update).forEach { id ->
                                removeMeasurements("rrc_meas:$rat:$id:")
                            }
                        }
                        val results = value.optJSONObject("measResults")
                        val measurementId = results?.optInt("measId", -1)
                        if (results != null && measurementId != null && measurementId in 1..64) {
                            val key = "rrc_meas:$rat:$measurementId:"
                            fun storeNeighborSinr(neighborRat: String, measurement: RrcNeighborSinr) {
                                val reference = measurement.reference?.label
                                val servingPci = value.optInt("pci", -1)
                                val servingChannel = value.optInt("arfcn", -1)
                                val sample = JSONObject().put("type", "rrc_neighbor_sinr").put("src", "RRC")
                                    .put("rat", neighborRat).put("arfcn", measurement.channel).put("pci", measurement.pci)
                                    .put("serving_rat", rat).put("serving_pci", servingPci).put("serving_arfcn", servingChannel)
                                    .put("reference", reference).put("sinr_index", measurement.index)
                                    .put("beam", measurement.beam).put("t", record.time)
                                // Once resolved, the sample belongs to this cell pair. Reusing a measId
                                // for a different object does not change the frequency of this reading.
                                val sampleKey = "rrc_meas:$rat:sinr:$servingChannel:$servingPci:" +
                                    "$neighborRat:$reference:${measurement.channel}:${measurement.pci}"
                                storeMeasurement(sampleKey, record.copy(data = sample))
                            }
                            if (rat == "NR") {
                                results.nrServingObjectNeighborSinr(value.optInt("pci", -1), value.optInt("arfcn", -1))
                                    ?.let { storeNeighborSinr("NR", it) }
                            }
                            val carrier = config.carrierFor(measurementId)
                            if (carrier != null) {
                                val reading = JSONObject()
                                    .put("type", "rrc_neighbor").put("rat", rat)
                                    .put("measResults", results)
                                    .put("measurement_carrier", carrier).put("t", record.time)
                                storeMeasurement("${key}latest", record.copy(data = reading))
                                val neighborRat = carrier.optString("rat")
                                for (measurement in results.rrcNeighborSinr(carrier)) {
                                    storeNeighborSinr(neighborRat, measurement)
                                }
                            }
                        }
                    }
                    if (record.type == "rrc" && rat == "NR" && !record.isEstimated()) {
                        value.optJSONObject("frequency_config")?.let { config ->
                            val targetPci = config.optInt("pci", -1)
                            val targetArfcn = config.optInt("arfcn", -1)
                            if (targetPci in 0..1007 && targetArfcn in 1..3_279_165) {
                                val type = "nr_frequency_config"
                                storeCellConfiguration("$type:$targetPci:$targetArfcn", record,
                                    JSONObject(config.toString()).put("type", type))
                            }
                        }
                        val pci = value.optInt("pci", -1)
                        val arfcn = value.optInt("arfcn", -1)
                        if (pci in 0..1007 && arfcn in 1..3_279_165) {
                            for ((field, type) in nrBroadcastTypes) {
                                val config = value.optJSONObject(field) ?: continue
                                val key = "$type:$pci:$arfcn"
                                val data = JSONObject(config.toString())
                                    .put("type", type).put("pci", pci).put("arfcn", arfcn)
                                storeCellConfiguration(key, record, data)
                            }
                        }
                    }
                    if (record.type == "rrc" && (rat == "LTE" || rat == "NR") && !record.isEstimated()) {
                        val access = value.optJSONObject("access_config")
                        val pci = value.optInt("pci", -1)
                        val arfcn = value.optInt("arfcn", -1)
                        val validCell = if (rat == "NR") pci in 0..1007 && arfcn in 1..3_279_165
                            else pci in 0..503 && arfcn in 0..262_143
                        if (access != null && validCell) {
                            val type = if (rat == "NR") "nr_access_config" else "lte_access_config"
                            val key = "$type:$pci:$arfcn"
                            // LTE broadcasts these parameters in separate SIBs.
                            val data = JSONObject(lastConfiguration[key]?.data?.toString() ?: "{}")
                            for (field in access.keys()) data.put(field, access.get(field))
                            storeCellConfiguration(key, record, data.put("type", type).put("pci", pci).put("arfcn", arfcn))
                        }
                    }
                    if (record.type in signalingTypes) {
                        if (pending.size == MAX_MESSAGES) pending.removeFirst()
                        pending.addLast(record)
                    }
                }
            }
        }

        try {
            if (!withContext(Dispatchers.IO) { helper.isFile }) {
                status = RootDiagStatus.Unsupported
                signalState.value = RootSignalState(status)
                return
            }
            val running = withContext(Dispatchers.IO) {
                ProcessBuilder("su", "-c", collectorCommand(helper))
                    .redirectErrorStream(true)
                    .start()
                    .also { process = it }
            }
            val stream = running.inputStream
            do {
                when {
                    status == RootDiagStatus.Running && qmdlState.value.phase == QmdlPhase.Starting && qmdlCommand == null -> {
                        val prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                (qmdlDirectory.isDirectory || qmdlDirectory.mkdirs()) &&
                                    retainQmdl() && activeQmdl.createNewFile()
                            }.getOrDefault(false)
                        }
                        if (prepared) {
                            qmdlCommand = 'Q'
                            qmdlDeadline = SystemClock.elapsedRealtime() + QMDL_ACK_TIMEOUT
                            withContext(Dispatchers.IO) {
                                running.outputStream.write('Q'.code)
                                running.outputStream.flush()
                            }
                        } else finishQmdl(QmdlResult.Error)
                    }
                    qmdlState.value.phase == QmdlPhase.Stopping && qmdlCommand == 'Q' -> {
                        qmdlCommand = 'S'
                        qmdlDeadline = SystemClock.elapsedRealtime() + QMDL_ACK_TIMEOUT
                        withContext(Dispatchers.IO) {
                            running.outputStream.write('S'.code)
                            running.outputStream.flush()
                        }
                    }
                }
                val size = withContext(Dispatchers.IO) {
                    val available = stream.available()
                    if (available > 0) stream.read(buffer, 0, minOf(available, buffer.size)) else 0
                }
                for (index in 0 until size) {
                    val byte = buffer[index].toInt()
                    if (byte == '\n'.code) {
                        if (!overlong && line.size() > 0) {
                            val text = line.toString(Charsets.UTF_8.name())
                            if (text.startsWith(DIAG_LOG_PREFIX)) {
                                if (startupLog.size == 5) startupLog.removeFirst()
                                startupLog.addLast(text.removePrefix(DIAG_LOG_PREFIX).take(512))
                            } else if (text.startsWith("{")) {
                                try {
                                    receive(JSONObject(text))
                                } catch (_: org.json.JSONException) {
                                    invalidLineCount++
                                }
                            }
                        }
                        if (overlong) invalidLineCount++
                        line.reset()
                        overlong = false
                    } else if (line.size() < MAX_LINE_BYTES) {
                        line.write(byte)
                    } else overlong = true
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastFlush >= 400) {
                    publish(now)
                    lastFlush = now
                }
                if (!running.isAlive && size == 0) break
                if (qmdlDeadline?.let { now >= it } == true) {
                    restartAfterStop = (foreground || RootDiagPrefs.backgroundEnabled(app)) && !pausedByUser
                    break
                }
                val timeout = if (status == RootDiagStatus.Checking) 25_000 else 16_000
                if (now - lastHeartbeat >= timeout) break
                if (size < buffer.size) delay(40) else yield()
            } while (true)
            status = when (status) {
                RootDiagStatus.Checking -> RootDiagStatus.Denied
                RootDiagStatus.Starting, RootDiagStatus.Running -> RootDiagStatus.Unsupported
                else -> status
            }
            publish(SystemClock.elapsedRealtime())
        } catch (_: IOException) {
            status = if (process == null) RootDiagStatus.Unavailable else RootDiagStatus.Unsupported
            publish(SystemClock.elapsedRealtime())
        } catch (cancelled: CancellationException) {
            status = RootDiagStatus.Stopped
            publish(SystemClock.elapsedRealtime())
            throw cancelled
        } finally {
            run.put("ended_ms", System.currentTimeMillis()).put("end_state", status.name)
            withContext(NonCancellable + Dispatchers.IO) {
                process?.let {
                    runCatching { it.outputStream.close() }
                    // Close the read end as well so a writer blocked on a full pipe can exit.
                    runCatching { it.inputStream.close() }
                    if (!it.waitFor(4, TimeUnit.SECONDS)) {
                        it.destroy()
                        if (!it.waitFor(500, TimeUnit.MILLISECONDS)) {
                            it.destroyForcibly()
                            it.waitFor(500, TimeUnit.MILLISECONDS)
                        }
                    }
                }
            }
            if (qmdlState.value.phase != QmdlPhase.Idle) {
                withContext(NonCancellable) {
                    finishQmdl(QmdlResult.Interrupted)
                }
            }
        }
    }

    private fun collectorCommand(helper: File): String {
        // Match DiagCap's executable location so Android uses the same linker configuration.
        // The fresh 0700 directory is owned by root and the child keeps the app's stdin pipe.
        val quotedPath = "'" + helper.absolutePath.replace("'", "'\\''") + "'"
        val quotedQmdl = "'" + activeQmdl.absolutePath.replace("'", "'\\''") + "'"
        return """
            PATH=/system/bin
            export PATH
            umask 077
            [ "${'$'}(id -u)" = 0 ] || exit 1
            printf '%s\n' '{"type":"status","state":"authorized"}'
            start_error() {
                printf 'wireless-root-diag: %s\n' "${'$'}2" >&2
                printf '{"type":"status","state":"start_error","rc":%s}\n' "${'$'}1"
                exit "${'$'}1"
            }
            work=${'$'}(mktemp -d /data/local/tmp/wireless-root-diag.XXXXXXXX) ||
                start_error 20 'Unable to create the diagnostic working directory'
            trap 'rm -f -- "${'$'}work/collector"; rmdir -- "${'$'}work"' EXIT
            cp -- $quotedPath "${'$'}work/collector" || start_error 21 'Unable to copy the installed diagnostic helper'
            chmod 700 "${'$'}work/collector" || start_error 22 'Unable to make the diagnostic helper executable'
            cd "${'$'}work" || start_error 23 'Unable to enter the diagnostic working directory'
            ./collector $quotedQmdl
            result=${'$'}?
            if [ "${'$'}result" -eq 126 ] || [ "${'$'}result" -eq 127 ]; then
                start_error 24 'Unable to execute the diagnostic helper'
            fi
            exit "${'$'}result"
        """.trimIndent()
    }

    private companion object {
        const val MAX_MESSAGES = 4_000
        const val MEASUREMENT_TIMEOUT = 30_000L
        const val QMDL_ACK_TIMEOUT = 8_000L
        const val MAX_DIAGNOSTIC_MESSAGES = 400
        const val MAX_COLLECTOR_RUNS = 8
        const val MAX_NEIGHBOR_SAMPLES_PER_CODE = 6
        val neighborProbeSampleCodes = setOf("0xB119", "0xB123", "0xB96A", "0xB96D", "0xB96E", "0xB96F", "0xB992")
        const val MAX_LINE_BYTES = 540_672
        const val MAX_DETAIL_CHARS = 12_000_000
        const val MAX_BROADCAST_CELLS = 12
        val measurementContextReset = setOf(
            "rrcSetup", "rrcRelease", "rrcConnectionSetup", "rrcConnectionRelease",
        )
        const val DIAG_LOG_PREFIX = "wireless-root-diag: "
        val configurationTypes = setOf(
            "ims_state", "ims_call", "ims_reg", "ims_session", "ims_reg_log", "sip",
        )
        val nrBroadcastTypes = mapOf("mib" to "nr_mib_config", "sib1_config" to "nr_sib1_config")
        val schedulingTypes = setOf("nr_dl_sched", "nr_ul_sched", "nr_csf", "lte_csf", "nr_ul_pwr", "nr_pdsch",
            "lte_dl_sched", "lte_ul_sched", "lte_ul_pwr", "lte_ul_tx", "lte_pucch_power", "lte_pucch_tx")
        val signalTypes = setOf(
            "serving", "nr_cell", "lte_meas", "nr_meas", "nr_mib", "nr_snr", "lte_phr", "lte_bsr", "lte_csf",
            "nr_cfg", "nr_pdsch", "nr_csf", "nr_dl_sched", "nr_ul_sched", "nr_ul_pwr", "layer_throughput",
            "lte_dl_sched", "lte_ul_sched", "lte_ul_pwr", "lte_ul_tx", "lte_pucch_power", "lte_pucch_tx",
            "ims_media",
        )
        val signalingTypes = setOf(
            "rrc", "nas", "nas_pdu", "nas_qos", "nas_mm", "nas_release", "sip", "event", "nr_rach",
            "ims_call", "ims_reg", "ims_session", "ims_reg_log", "ims_state", "cell_change",
        )
    }
}

private fun AndroidLteSinrReader.Sample.toDiagnosticJson() = JSONObject().apply {
    fun sinr(value: Int): Any = if (LteSinrUtils.isReported(value)) value else JSONObject.NULL
    put("type", "android_lte_sinr")
    put("task_id", taskId() ?: JSONObject.NULL)
    put("clock_id", clockId() ?: JSONObject.NULL)
    put("t", timeMs())
    put("elapsed_ms", elapsedMs())
    put("subscription_id", subscriptionId())
    put("registered", registered())
    put("connection_status", connectionStatus())
    put("eligible_lte_cells", eligibleCells())
    put("cell_timestamp_ms", cellTimestampMs())
    put("cell_age_ms", elapsedMs() - cellTimestampMs())
    put("cell_rssnr_raw", cellRssnr())
    put("cell_sinr_db", sinr(cellSinr()))
    put("snapshot_timestamp_ms", snapshotTimestampMs() ?: JSONObject.NULL)
    put("snapshot_age_ms", snapshotTimestampMs()?.let { elapsedMs() - it } ?: JSONObject.NULL)
    put("snapshot_rssnr_raw", JSONArray(snapshotRssnr()))
    put("snapshot_sinr_db", JSONArray(snapshotSinr().map(::sinr)))
    put("snapshot_decision", snapshotDecision())
    put("snapshot_error", snapshotError() ?: JSONObject.NULL)
    put("selected_sinr_db", sinr(selectedSinr()))
    put("selected_source", selectedSource())
}

private val excludedDiagnosticKeys = setOf(
    "ncgi", "nci", "nci_hex", "cid", "ci", "tac", "call_id", "raw", "head", "access",
    "reason_hdr", "reason", "dnn", "apn", "imsi", "imei", "tmsi", "ue_identity", "uper_hex", "decoded_tree",
    "from", "to", "contact", "via", "uri", "ip", "impu", "impi", "msisdn", "phone", "user", "p_asserted",
)

private fun redactDiagnostic(data: JSONObject): JSONObject = JSONObject().apply {
    for (key in data.keys()) {
        if (key in excludedDiagnosticKeys ||
            (key == "msg" && data.optString("type") == "event" && data.optString("rat") == "IMS")
        ) continue
        val item = data.get(key)
        put(key, when (item) {
            is JSONObject -> redactDiagnostic(item)
            is JSONArray -> JSONArray().apply {
                for (i in 0 until item.length()) {
                    val part = item.get(i)
                    put(if (part is JSONObject) redactDiagnostic(part) else part)
                }
            }
            else -> item
        })
    }
}
