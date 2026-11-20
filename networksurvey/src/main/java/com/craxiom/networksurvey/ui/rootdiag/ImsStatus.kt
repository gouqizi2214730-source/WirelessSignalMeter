package com.craxiom.networksurvey.ui.rootdiag

/**
 * User-facing IMS status. Pipeline readiness (logcat running, DIAG codes subscribed)
 * is not the same as a registration or call observation. A privileged Android IMS API
 * denial must not override a working Samsung logcat source, and a ready source must
 * not be shown as registered.
 */
internal enum class ImsRegistrationUi {
    Unknown,
    Registered,
    RegisteredInferred,
    Unregistered,
    Failed,
    Unavailable,
}

internal data class ImsEvidence(
    val registration: String? = null,
    val inferred: Boolean = false,
    val source: String? = null,
    val call: String? = null,
    val why: String? = null,
)

internal data class ImsStatusSummary(
    val registration: ImsRegistrationUi,
    val headlineKey: String,
    val evidenceSource: String? = null,
    val visibleSourceKeys: List<String> = emptyList(),
    val hiddenProviders: List<String> = emptyList(),
    val technical: String = "",
    val call: String? = null,
) {
    val registered: Boolean
        get() = registration == ImsRegistrationUi.Registered ||
            registration == ImsRegistrationUi.RegisteredInferred
}

internal object ImsStatus {
    fun relevantProvider(name: String, manufacturer: String, soc: String, hardware: String): Boolean {
        val hay = "$manufacturer $soc $hardware".lowercase()
        return when (name) {
            "mediatek" -> "mediatek" in hay || Regex("\\bmt[0-9]").containsMatchIn(hay)
            "exynos" -> "exynos" in hay
            "kirin" -> "kirin" in hay || "hi36" in hay || "hi37" in hay
            "samsung_logcat" -> "samsung" in hay
            "qualcomm_diag", "android_telephony" -> true
            else -> true
        }
    }

    fun pipelineReason(provider: ImsProviderStatus): String {
        val status = provider.status
        val detail = provider.detail
        return when {
            status == "unsupported" || detail == "no_backend" -> "ims_pipeline_unsupported"
            status == "subscribed" && provider.available != true -> "ims_pipeline_subscribed"
            status == "starting" || status == "reconnecting" || detail == "logcat_exited" ->
                if (detail == "logcat_exited") "ims_pipeline_reconnecting" else "ims_pipeline_starting"
            provider.available == true || status == "available" -> "ims_pipeline_ready"
            detail == "permission_denied" -> "ims_pipeline_privileged"
            detail == "api_too_low" -> "api_too_low"
            detail == "no_subscription" -> "no_subscription"
            detail == "ims_unavailable" -> "ims_unavailable"
            detail == "no_samsung_ims_tags" -> "no_samsung_ims_tags"
            status == "unavailable" || provider.available == false -> detail.ifBlank { "ims_pipeline_unavailable" }
            else -> status.ifBlank { detail.ifBlank { "unknown" } }
        }
    }

    fun summarize(
        providers: List<ImsProviderStatus>,
        evidence: ImsEvidence? = null,
        manufacturer: String = "",
        soc: String = "",
        hardware: String = "",
        simCount: Int = 1,
    ): ImsStatusSummary {
        val hidden = providers.map { it.name }.filterNot { relevantProvider(it, manufacturer, soc, hardware) }
        val visible = providers.filter { relevantProvider(it.name, manufacturer, soc, hardware) }
        val notes = visible.map { it.name to pipelineReason(it) }
        val samsung = visible.firstOrNull { it.name == "samsung_logcat" }
        val qualcomm = visible.firstOrNull { it.name == "qualcomm_diag" }
        val android = visible.firstOrNull { it.name == "android_telephony" }
        val samsungReady = samsung != null && pipelineReason(samsung) == "ims_pipeline_ready"
        val qualcommPackets = qualcomm != null && pipelineReason(qualcomm) == "ims_pipeline_ready"
        val anyReady = notes.any { it.second == "ims_pipeline_ready" || it.second == "ims_pipeline_starting" ||
            it.second == "ims_pipeline_reconnecting" || it.second == "ims_pipeline_subscribed" }

        val evidenceReg = normalizeRegistration(evidence?.registration)
        val androidUsable = android != null && pipelineReason(android) !in setOf(
            "ims_pipeline_privileged", "api_too_low", "ims_unavailable",
        )
        val androidReg = if (androidUsable) when (android?.detail) {
            "registered" -> ImsRegistrationUi.Registered
            "not_registered" -> ImsRegistrationUi.Unregistered
            "registering" -> ImsRegistrationUi.Unknown
            else -> null
        } else null

        val registration: ImsRegistrationUi
        val source: String?
        val headline: String
        when {
            evidenceReg != null -> {
                registration = if (evidence?.inferred == true && evidenceReg == ImsRegistrationUi.Registered) {
                    ImsRegistrationUi.RegisteredInferred
                } else evidenceReg
                source = evidence?.source?.takeIf { it.isNotBlank() }
                headline = headlineFor(registration, notes)
            }
            androidReg != null -> {
                registration = androidReg
                source = "android_telephony"
                headline = headlineFor(registration, notes)
            }
            samsungReady || qualcommPackets || notes.any { it.second == "ims_pipeline_starting" || it.second == "ims_pipeline_reconnecting" || it.second == "ims_pipeline_subscribed" } -> {
                registration = ImsRegistrationUi.Unknown
                source = when {
                    samsungReady -> "samsung_logcat"
                    notes.any { it.second == "ims_pipeline_reconnecting" } -> "samsung_logcat"
                    qualcommPackets || notes.any { it.second == "ims_pipeline_subscribed" } -> "qualcomm_diag"
                    else -> visible.firstOrNull()?.name
                }
                headline = when {
                    notes.any { it.second == "ims_pipeline_reconnecting" } -> "ims_unknown_reconnecting"
                    notes.any { it.first == "samsung_logcat" && it.second == "ims_pipeline_ready" } -> "ims_unknown_samsung_ready"
                    else -> "ims_unknown_waiting"
                }
            }
            else -> {
                registration = ImsRegistrationUi.Unavailable
                source = null
                headline = if (android != null && pipelineReason(android) == "ims_pipeline_privileged" && !anyReady) {
                    "ims_unavailable_privileged"
                } else "ims_unavailable_no_source"
            }
        }

        val technical = buildList {
            notes.forEach { (name, reason) -> add("$name=$reason/${visible.first { it.name == name }.status}") }
            hidden.forEach { add("$it=hidden") }
            evidence?.registration?.let { add("evidence=$it inferred=${evidence.inferred} src=${evidence.source}") }
            if (simCount > 1) add("simCount=$simCount unmapped")
        }.joinToString("; ")

        return ImsStatusSummary(
            registration = registration,
            headlineKey = headline,
            evidenceSource = source,
            visibleSourceKeys = notes.map { "${it.first}:${it.second}" },
            hiddenProviders = hidden,
            technical = technical,
            call = evidence?.call?.takeIf { it.isNotBlank() && it != "idle" },
        )
    }

    private fun normalizeRegistration(value: String?): ImsRegistrationUi? = when (value?.lowercase()) {
        "registered" -> ImsRegistrationUi.Registered
        "deregistered" -> ImsRegistrationUi.Unregistered
        "failed" -> ImsRegistrationUi.Failed
        "unknown", "", null -> null
        else -> null
    }

    private fun headlineFor(
        registration: ImsRegistrationUi,
        notes: List<Pair<String, String>>,
    ): String = when (registration) {
        ImsRegistrationUi.Registered -> "ims_registered"
        ImsRegistrationUi.RegisteredInferred -> "ims_registered_inferred"
        ImsRegistrationUi.Unregistered -> "ims_deregistered"
        ImsRegistrationUi.Failed -> "ims_failed"
        ImsRegistrationUi.Unavailable -> "ims_unavailable_no_source"
        ImsRegistrationUi.Unknown -> when {
            notes.any { it.second == "ims_pipeline_reconnecting" } -> "ims_unknown_reconnecting"
            notes.any { it.first == "samsung_logcat" && it.second == "ims_pipeline_ready" } -> "ims_unknown_samsung_ready"
            else -> "ims_unknown_waiting"
        }
    }
}

internal object OverlayCollection {
    enum class Mode { Standby, Listening, TaskCollecting }

    fun mode(taskRunning: Boolean, diagListening: Boolean): Mode = when {
        taskRunning -> Mode.TaskCollecting
        diagListening -> Mode.Listening
        else -> Mode.Standby
    }

    fun diagListening(status: RootDiagStatus): Boolean =
        status == RootDiagStatus.Checking || status == RootDiagStatus.Starting ||
            status == RootDiagStatus.Running
}
