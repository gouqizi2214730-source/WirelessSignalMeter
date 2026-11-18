package com.craxiom.networksurvey.ui.cellular.baidumap

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.telephony.SubscriptionManager
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.db.MapPointDbHelper
import com.craxiom.networksurvey.db.SurveyTaskStore
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.MapPointRecord
import com.craxiom.networksurvey.model.ObservedCell
import com.craxiom.networksurvey.model.ObservedCellEvents
import com.craxiom.networksurvey.services.NetworkSurveyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

internal object MapTrack {
    private const val ENABLED_KEY = "map_automatic_tracking"
    private val lock = Mutex()
    private val generation = AtomicLong()
    private val revisionState = MutableStateFlow(0L)
    val revision = revisionState.asStateFlow()
    val currentGeneration: Long get() = generation.get()

    fun isEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
        generation.incrementAndGet()
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            MapPointDbHelper.getInstance(context).deleteAll()
            generation.incrementAndGet()
            revisionState.update { it + 1 }
        }
    }

    suspend fun save(context: Context, point: MapPointRecord, sampleGeneration: Long): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!isEnabled(context) || generation.get() != sampleGeneration) return@withLock false
                val saved = MapPointDbHelper.getInstance(context).insert(point) >= 0
                if (saved) revisionState.update { it + 1 }
                saved
            }
        }
}

internal class ForegroundMapTrack(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private var connection: NetworkSurveyService? = null
    private var listener: ICellularSurveyRecordListener? = null
    private var writer: Job? = null
    private var gpsListener: LocationListener? = null
    @Volatile private var foregroundFix: Location? = null
    @Volatile private var tasks: SurveyTaskStore? = null
    private data class Capture(val cell: ObservedCell, val point: MapPointRecord?, val generation: Long, val mission: String?)

    fun attach(service: NetworkSurveyService) {
        if (connection === service) return
        detach()
        connection = service
        val manager = app.getSystemService(LocationManager::class.java)
        val gps = LocationListener { location ->
            val previous = foregroundFix
            if (location.isRecent() && (previous == null || !previous.isRecent() ||
                    !previous.hasAccuracy() || location.accuracy <= previous.accuracy * 2)
            ) foregroundFix = location
        }
        gpsListener = gps
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)) {
            try {
                manager?.getLastKnownLocation(provider)?.let(gps::onLocationChanged)
                manager?.requestLocationUpdates(provider, 2_000L, 0f, gps, Looper.getMainLooper())
            } catch (_: SecurityException) {
                // Location is optional until permission is granted.
            } catch (_: IllegalArgumentException) {
                // The device may lack this provider.
            }
        }
        val samples = Channel<Capture>(64)
        writer = scope.launch {
            val store = try { withContext(Dispatchers.IO) { SurveyTaskStore.get(app) } }
            catch (error: android.database.SQLException) {
                Timber.w(error, "Could not open survey tasks")
                return@launch
            }
            tasks = store
            val lastSaved = mutableMapOf<String, MapPointRecord>()
            var cellEvents = ObservedCellEvents()
            var savedGeneration: Long? = null
            for ((cell, point, generation, mission) in samples) {
                if (savedGeneration != generation) {
                    lastSaved.clear()
                    cellEvents = ObservedCellEvents()
                    savedGeneration = generation
                }
                try {
                    withContext(Dispatchers.IO) {
                        store.bindMission(cell.taskId, mission)
                        cellEvents.accept(cell).forEach(store::append)
                    }
                } catch (error: android.database.SQLException) {
                    Timber.w(error, "Could not save survey event")
                }
                if (point == null) continue
                val source = "${point.taskId}:${point.subscriptionId}:${point.simSlot}:${point.plmnDisplay}"
                val previous = lastSaved[source]
                if (previous != null && previous.cellId == point.cellId &&
                    previous.netType == point.netType && previous.rsrp == point.rsrp &&
                    previous.sinr == point.sinr && previous.rsrq == point.rsrq &&
                    point.timestamp - previous.timestamp in 0L..14_999L
                ) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(previous.latitude, previous.longitude, point.latitude, point.longitude, distance)
                    if (distance[0] < 3f) continue
                }
                try {
                    if (MapTrack.save(app, point, generation)) {
                        lastSaved[source] = point
                    }
                } catch (error: android.database.SQLException) {
                    Timber.w(error, "Could not save map point")
                }
            }
        }.also { job -> job.invokeOnCompletion { samples.close() } }
        val recordListener = object : ICellularSurveyRecordListener {
            override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
                val generation = MapTrack.currentGeneration
                if (!MapTrack.isEnabled(app)) return
                val station = cellularGroup.mapServingCell()?.toStationInfo() ?: return
                val task = try { tasks?.observationTask() } catch (error: android.database.SQLException) {
                    Timber.w(error, "Could not start survey task")
                    null
                } ?: return
                val now = SystemClock.elapsedRealtimeNanos()
                val stamp = System.currentTimeMillis()
                val slot = SubscriptionManager.getSlotIndex(subscriptionId).takeIf { it >= 0 }
                val rat = if (station.networkType in setOf("NR", "5G")) "NR" else "LTE"
                val cell = ObservedCell(task.id, subscriptionId, slot, stamp, now / 1_000_000,
                    SurveyTaskStore.CLOCK_ID, rat, station.cellId.takeIf { it > 0 },
                    station.pci.takeIf { it in 0..1007 }, station.earfcn.takeIf { it >= 0 },
                    "${station.mcc}-${station.mnc}".takeIf { station.mcc > 0 })
                val location = listOfNotNull(foregroundFix, service.primaryLocationListener?.latestLocation)
                    .filter { now - it.elapsedRealtimeNanos in 0..10_000_000_000L &&
                        BaiduMapArea.isValid(it.latitude, it.longitude) }
                    .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
                val mission = service.missionIdForRecords.takeIf { service.isMissionSessionActive }
                val point = if (location == null || station.rsrp !in -160..-20 && station.sinr !in -40..50) null
                else MapPointRecord().apply {
                    taskId = task.id
                    missionId = mission
                    this.subscriptionId = subscriptionId
                    timestamp = stamp
                    elapsedRealtimeMs = cell.elapsedMs
                    clockId = cell.clockId
                    locationAgeMs = (now - location.elapsedRealtimeNanos) / 1_000_000
                    locationAccuracyM = if (location.hasAccuracy()) location.accuracy else -1f
                    locationProvider = location.provider
                    latitude = location.latitude
                    longitude = location.longitude
                    rsrp = station.rsrp
                    sinr = station.sinr
                    rsrq = station.rsrq
                    gnbId = station.gnbId
                    cellId = station.cellId
                    sectorId = station.sectorId
                    pci = station.pci
                    earfcn = station.earfcn
                    mcc = station.mcc
                    mnc = station.mnc
                    tac = station.tac
                    simSlot = slot ?: -1
                    netType = station.networkType
                    remark = "自动打点"
                }
                samples.trySend(Capture(cell, point, generation, mission))
            }
        }
        listener = recordListener
        service.registerCellularSurveyRecordListener(recordListener)
        service.runSingleCellularScan()
    }

    fun detach() {
        gpsListener?.let { app.getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        gpsListener = null
        foregroundFix = null
        writer?.cancel()
        writer = null
        listener?.let { connection?.unregisterCellularSurveyRecordListener(it) }
        listener = null
        connection = null
    }
}
