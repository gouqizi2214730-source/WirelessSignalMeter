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
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.MapPointRecord
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
        val samples = Channel<Pair<MapPointRecord, Long>>(64)
        writer = scope.launch {
            val lastSaved = mutableMapOf<String, MapPointRecord>()
            var savedGeneration: Long? = null
            for ((point, generation) in samples) {
                if (savedGeneration != generation) {
                    lastSaved.clear()
                    savedGeneration = generation
                }
                val source = "${point.simSlot}:${point.plmnDisplay}"
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
                val now = SystemClock.elapsedRealtimeNanos()
                val location = listOfNotNull(foregroundFix, service.primaryLocationListener?.latestLocation)
                    .filter { now - it.elapsedRealtimeNanos in 0..10_000_000_000L &&
                        BaiduMapArea.isValid(it.latitude, it.longitude) }
                    .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE } ?: return
                val station = cellularGroup.mapServingCell()?.toStationInfo() ?: return
                if (station.rsrp !in -160..-20 && station.sinr !in -40..50) return
                samples.trySend(MapPointRecord().apply {
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
                    simSlot = SubscriptionManager.getSlotIndex(subscriptionId)
                    netType = station.networkType
                    remark = "自动打点"
                } to generation)
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
