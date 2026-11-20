package com.craxiom.networksurvey.ui.rootdiag

import com.craxiom.networksurvey.ui.bandlock.BandLockProfile
import com.craxiom.networksurvey.ui.bandlock.LockSupportLevel

internal enum class CapabilityState { Available, Degraded, Unavailable, Unsupported }

internal data class CapabilityItem(
    val id: String,
    val state: CapabilityState,
    val reason: String,
    val detail: String = "",
    val source: String = "",
)

internal data class ImsProviderStatus(
    val name: String,
    val status: String = "",
    val available: Boolean? = null,
    val detail: String = "",
)

internal data class DeviceCapabilitySnapshot(
    val sdk: Int,
    val abiSupported: Boolean,
    val primaryAbi: String,
    val manufacturer: String,
    val model: String,
    val soc: String,
    val hardware: String,
    val simCount: Int,
    val selectedSubscriptionId: Int? = null,
    val telephony: CapabilityItem,
    val root: CapabilityItem,
    val qualcommDiag: CapabilityItem,
    val imsSources: List<CapabilityItem>,
    val bandLock: CapabilityItem,
) {
    val basicSignalAvailable get() = telephony.state != CapabilityState.Unavailable
}

internal object DeviceCapabilities {
    fun abiSupported(supportedAbis: Array<String>): Boolean =
        supportedAbis.any { it == "arm64-v8a" }

    /**
     * DIAG measurements have no reliable subscription id, so the per-SIM
     * Android view stays primary whenever more than one SIM is active.
     * Collection itself is not gated by this policy.
     */
    fun displayUsesDiag(
        simCount: Int,
        protocol: String?,
        diagLive: Boolean,
    ): Boolean {
        if (simCount > 1) return false
        return when (protocol) {
            "GSM", "UMTS", "CDMA" -> false
            "LTE", "NR" -> diagLive
            else -> diagLive
        }
    }

    fun snapshot(
        sdk: Int,
        supportedAbis: Array<String>,
        manufacturer: String,
        model: String,
        soc: String,
        hardware: String,
        simCount: Int,
        selectedSubscriptionId: Int?,
        telephonyPermission: Boolean,
        collectorStatus: RootDiagStatus,
        collectorError: Int?,
        imsProviders: List<ImsProviderStatus>,
        bandLock: BandLockProfile,
    ): DeviceCapabilitySnapshot {
        val abi = supportedAbis.firstOrNull().orEmpty()
        val arm64 = abiSupported(supportedAbis)
        val root = when (collectorStatus) {
            RootDiagStatus.Denied -> CapabilityItem("root", CapabilityState.Unavailable, "root_denied")
            RootDiagStatus.Unavailable -> CapabilityItem("root", CapabilityState.Unavailable, "no_su")
            RootDiagStatus.Checking, RootDiagStatus.Starting ->
                CapabilityItem("root", CapabilityState.Degraded, "checking")
            RootDiagStatus.Running, RootDiagStatus.Stopped, RootDiagStatus.Unsupported ->
                CapabilityItem("root", CapabilityState.Available, "ok")
        }
        val diagReason = CollectorRestartPolicy.collectorReason(collectorStatus, collectorError)
        val diag = when {
            !arm64 -> CapabilityItem("qualcomm_diag", CapabilityState.Unsupported, "abi_unsupported", abi)
            collectorStatus == RootDiagStatus.Running ->
                CapabilityItem("qualcomm_diag", CapabilityState.Available, "ok")
            collectorStatus == RootDiagStatus.Checking || collectorStatus == RootDiagStatus.Starting ->
                CapabilityItem("qualcomm_diag", CapabilityState.Degraded, "checking")
            collectorStatus == RootDiagStatus.Denied || collectorStatus == RootDiagStatus.Unavailable ->
                CapabilityItem("qualcomm_diag", CapabilityState.Unavailable, diagReason)
            diagReason == "no_libdiag" || diagReason == "missing_libdiag_symbols" ->
                CapabilityItem("qualcomm_diag", CapabilityState.Unsupported, diagReason)
            else -> CapabilityItem("qualcomm_diag", CapabilityState.Unavailable, diagReason)
        }
        val telephony = if (telephonyPermission) CapabilityItem("telephony", CapabilityState.Available, "ok")
        else CapabilityItem("telephony", CapabilityState.Unavailable, "permission_denied")
        val lock = when (bandLock.level) {
            LockSupportLevel.Verified -> CapabilityItem("band_lock", CapabilityState.Available, "verified", bandLock.channel, bandLock.adapter)
            LockSupportLevel.ProbeOnly -> CapabilityItem("band_lock", CapabilityState.Degraded, bandLock.reason.ifBlank { "unverified_model" }, bandLock.channel, bandLock.adapter)
            LockSupportLevel.Unsupported -> CapabilityItem("band_lock", CapabilityState.Unsupported, bandLock.reason.ifBlank { "unsupported" }, bandLock.channel, bandLock.adapter)
        }
        return DeviceCapabilitySnapshot(
            sdk = sdk,
            abiSupported = arm64,
            primaryAbi = abi,
            manufacturer = manufacturer,
            model = model,
            soc = soc,
            hardware = hardware,
            simCount = simCount,
            selectedSubscriptionId = selectedSubscriptionId,
            telephony = telephony,
            root = root,
            qualcommDiag = diag,
            imsSources = imsItems(imsProviders, sdk),
            bandLock = lock,
        )
    }

    fun imsItems(providers: List<ImsProviderStatus>, sdk: Int): List<CapabilityItem> {
        val mapped = providers.map { provider ->
            val state = when {
                provider.status == "unsupported" || provider.available == false && provider.status == "unsupported" ->
                    CapabilityState.Unsupported
                provider.available == true || provider.status == "available" -> CapabilityState.Available
                provider.status == "starting" || provider.status == "subscribed" || provider.status == "reconnecting" ->
                    CapabilityState.Degraded
                provider.available == false || provider.status == "unavailable" -> CapabilityState.Unavailable
                else -> CapabilityState.Degraded
            }
            val reason = when {
                state == CapabilityState.Available -> "ok"
                provider.detail.isNotBlank() -> provider.detail
                provider.status.isNotBlank() -> provider.status
                else -> "unknown"
            }
            CapabilityItem(provider.name, state, reason, provider.detail, provider.name)
        }
        val hasAndroid = mapped.any { it.id == "android_telephony" }
        return if (hasAndroid) mapped else mapped + androidImsFallback(sdk)
    }

    fun androidImsFallback(sdk: Int, registered: Boolean? = null, error: String? = null): CapabilityItem {
        return when {
            sdk < 30 -> CapabilityItem("android_telephony", CapabilityState.Unavailable, "api_too_low")
            error != null -> CapabilityItem("android_telephony", CapabilityState.Unavailable, error)
            registered == true -> CapabilityItem("android_telephony", CapabilityState.Available, "registered", source = "android_telephony")
            registered == false -> CapabilityItem("android_telephony", CapabilityState.Available, "not_registered", source = "android_telephony")
            else -> CapabilityItem("android_telephony", CapabilityState.Degraded, "unknown", source = "android_telephony")
        }
    }
}
