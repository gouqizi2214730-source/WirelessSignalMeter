package com.craxiom.networksurvey.ui.rootdiag

import com.craxiom.networksurvey.ui.bandlock.BandLockProfile
import com.craxiom.networksurvey.ui.bandlock.LockSupportLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImsStatusTest {
    private val lock = BandLockProfile(LockSupportLevel.Unsupported, "unknown", reason = "no_diag_channel")

    private val screenshotProviders = listOf(
        ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied"),
        ImsProviderStatus("qualcomm_diag", "subscribed", null, ""),
        ImsProviderStatus("mediatek", "unsupported", false, "no_backend"),
        ImsProviderStatus("samsung_logcat", "available", true, "sipmsg"),
    )

    private fun summarize(
        providers: List<ImsProviderStatus> = screenshotProviders,
        evidence: ImsEvidence? = null,
        manufacturer: String = "samsung",
        soc: String = "SM8750",
        hardware: String = "sun",
        simCount: Int = 1,
    ) = ImsStatus.summarize(providers, evidence, manufacturer, soc, hardware, simCount)

    @Test
    fun screenshotPermissionDeniedDoesNotOverrideSamsungReady() {
        val status = summarize()
        assertEquals(ImsRegistrationUi.Unknown, status.registration)
        assertEquals("ims_unknown_samsung_ready", status.headlineKey)
        assertFalse(status.registered)
        assertEquals(listOf("mediatek"), status.hiddenProviders)
        assertTrue(status.visibleSourceKeys.any { it.startsWith("samsung_logcat:ims_pipeline_ready") })
        assertTrue(status.visibleSourceKeys.any { it.startsWith("android_telephony:ims_pipeline_privileged") })
        assertTrue(status.visibleSourceKeys.any { it.startsWith("qualcomm_diag:ims_pipeline_subscribed") })
        assertFalse(status.visibleSourceKeys.any { it.startsWith("mediatek") })
        assertFalse(status.headlineKey.contains("subscribed"))
        assertFalse(status.headlineKey.contains("permission"))
    }

    @Test
    fun snapshotForUserPhoneDoesNotTreatReadyAsRegistered() {
        val snap = DeviceCapabilities.snapshot(
            36, arrayOf("arm64-v8a"), "samsung", "SM-S9370", "SM8750", "sun",
            1, 1, true, RootDiagStatus.Running, 0, screenshotProviders, lock,
        )
        assertEquals("ims_unknown_samsung_ready", snap.imsStatus.headlineKey)
        assertEquals(ImsRegistrationUi.Unknown, snap.imsStatus.registration)
        val samsung = snap.imsSources.first { it.id == "samsung_logcat" }
        assertEquals(CapabilityState.Available, samsung.state)
        assertFalse(samsung.reason == "ok")
    }

    @Test
    fun samsungReadyIsNotRegisteredWithoutEvidence() {
        val status = summarize(evidence = null)
        assertEquals(ImsRegistrationUi.Unknown, status.registration)
        assertFalse(status.registered)
    }

    @Test
    fun samsungRegistrationEvidenceIsTrustedOverAndroidDenial() {
        val status = summarize(
            evidence = ImsEvidence("registered", inferred = false, source = "register_ap", call = "idle"),
        )
        assertEquals(ImsRegistrationUi.Registered, status.registration)
        assertEquals("ims_registered", status.headlineKey)
        assertEquals("register_ap", status.evidenceSource)
        assertTrue(status.registered)
    }

    @Test
    fun inferredRegistrationIsLabeled() {
        val status = summarize(evidence = ImsEvidence("registered", inferred = true, source = "inferred"))
        assertEquals(ImsRegistrationUi.RegisteredInferred, status.registration)
        assertEquals("ims_registered_inferred", status.headlineKey)
    }

    @Test
    fun mediatekHiddenOnSamsungAndShownOnMtk() {
        assertTrue("mediatek" in summarize(manufacturer = "samsung", soc = "SM8750").hiddenProviders)
        val mtk = summarize(
            providers = listOf(ImsProviderStatus("mediatek", "unsupported", false, "no_backend")),
            manufacturer = "Xiaomi",
            soc = "MT6893",
            hardware = "mt6893",
        )
        assertTrue(mtk.hiddenProviders.isEmpty())
        assertTrue(mtk.visibleSourceKeys.any { it.startsWith("mediatek:ims_pipeline_unsupported") })
    }

    @Test
    fun subscribedQualcommIsNotRegistered() {
        val status = summarize(
            providers = listOf(
                ImsProviderStatus("qualcomm_diag", "subscribed", null, ""),
                ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied"),
            ),
            manufacturer = "Xiaomi",
            soc = "SM8450",
            hardware = "taro",
        )
        assertEquals(ImsRegistrationUi.Unknown, status.registration)
        assertEquals("ims_unknown_waiting", status.headlineKey)
        assertFalse(status.registered)
    }

    @Test
    fun noSourcesYieldsUnavailable() {
        val status = summarize(providers = emptyList(), manufacturer = "unknown", soc = "", hardware = "")
        assertEquals(ImsRegistrationUi.Unavailable, status.registration)
        assertEquals("ims_unavailable_no_source", status.headlineKey)
    }

    @Test
    fun onlyPrivilegedAndroidDenialIsUnavailableNotUnregistered() {
        val status = summarize(
            providers = listOf(ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied")),
            manufacturer = "Google",
            soc = "goldfish",
            hardware = "ranchu",
        )
        assertEquals(ImsRegistrationUi.Unavailable, status.registration)
        assertEquals("ims_unavailable_privileged", status.headlineKey)
    }

    @Test
    fun reconnectingSamsungStaysUnknown() {
        val status = summarize(
            providers = listOf(
                ImsProviderStatus("samsung_logcat", "reconnecting", null, "logcat_exited"),
                ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied"),
            ),
        )
        assertEquals(ImsRegistrationUi.Unknown, status.registration)
        assertEquals("ims_unknown_reconnecting", status.headlineKey)
    }

    @Test
    fun laterEvidenceIsKeptIfSamsungLaterUnavailable() {
        val status = summarize(
            providers = listOf(
                ImsProviderStatus("samsung_logcat", "unavailable", false, "no_samsung_ims_tags"),
                ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied"),
            ),
            evidence = ImsEvidence("registered", source = "register_ap"),
        )
        assertEquals(ImsRegistrationUi.Registered, status.registration)
        assertEquals("ims_registered", status.headlineKey)
    }

    @Test
    fun dualSimNoteGoesToTechnicalNotHeadline() {
        val status = summarize(simCount = 2)
        assertEquals("ims_unknown_samsung_ready", status.headlineKey)
        assertTrue(status.technical.contains("simCount=2 unmapped"))
    }

    @Test
    fun androidRegisteredIsUsedOnlyWhenApiIsReadable() {
        val readable = summarize(
            providers = listOf(
                ImsProviderStatus("android_telephony", "available", true, "registered"),
                ImsProviderStatus("samsung_logcat", "available", true, "sipmsg"),
            ),
        )
        assertEquals(ImsRegistrationUi.Registered, readable.registration)
        assertEquals("android_telephony", readable.evidenceSource)

        val denied = summarize(
            providers = listOf(
                ImsProviderStatus("android_telephony", "unavailable", false, "permission_denied"),
                ImsProviderStatus("samsung_logcat", "available", true, "sipmsg"),
            ),
        )
        assertEquals(ImsRegistrationUi.Unknown, denied.registration)
        assertEquals("samsung_logcat", denied.evidenceSource)
    }
}

class OverlayCollectionTest {
    @Test
    fun createdButNotStartedIsNotCollecting() {
        assertEquals(
            OverlayCollection.Mode.Listening,
            OverlayCollection.mode(taskRunning = false, diagListening = true),
        )
        assertEquals(
            OverlayCollection.Mode.Standby,
            OverlayCollection.mode(taskRunning = false, diagListening = false),
        )
    }

    @Test
    fun onlyStartedTaskShowsCollecting() {
        assertEquals(
            OverlayCollection.Mode.TaskCollecting,
            OverlayCollection.mode(taskRunning = true, diagListening = true),
        )
        assertEquals(
            OverlayCollection.Mode.TaskCollecting,
            OverlayCollection.mode(taskRunning = true, diagListening = false),
        )
    }

    @Test
    fun stoppedOrFailedTaskReturnsToListeningOrStandby() {
        assertEquals(
            OverlayCollection.Mode.Listening,
            OverlayCollection.mode(taskRunning = false, diagListening = true),
        )
        assertEquals(
            OverlayCollection.Mode.Standby,
            OverlayCollection.mode(taskRunning = false, diagListening = false),
        )
    }

    @Test
    fun diagStartingOrCheckingCountsAsListeningNotCollecting() {
        assertTrue(OverlayCollection.diagListening(RootDiagStatus.Checking))
        assertTrue(OverlayCollection.diagListening(RootDiagStatus.Starting))
        assertTrue(OverlayCollection.diagListening(RootDiagStatus.Running))
        assertFalse(OverlayCollection.diagListening(RootDiagStatus.Stopped))
        assertFalse(OverlayCollection.diagListening(RootDiagStatus.Denied))
        assertEquals(
            OverlayCollection.Mode.Listening,
            OverlayCollection.mode(false, OverlayCollection.diagListening(RootDiagStatus.Running)),
        )
    }
}
