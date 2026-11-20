package com.craxiom.networksurvey.ui.rootdiag

import com.craxiom.networksurvey.model.AndroidNrCarriers
import com.craxiom.networksurvey.model.NrCarrier
import com.craxiom.networksurvey.model.NrCarrier.CaState
import com.craxiom.networksurvey.model.NrCarrier.Identity
import com.craxiom.networksurvey.model.NrCarrier.Role
import com.craxiom.networksurvey.model.NrCarrier.Signal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NrCarrierTest {
    private val primary = Identity(0, 635_000)
    private val secondary = Identity(10, 636_000)

    @Test fun androidSaDeduplicatesKeepsSameBandAndDropsInvalidIdentity() {
        val main = NrCarrier(primary, Role.PRIMARY, -1, "n78", 0, false)
        val aux = main.copyFor(secondary, Role.SECONDARY)
        val input = listOf(aux, main.copyFor(primary, Role.SECONDARY), main, aux,
            main.copyFor(Identity(1008, 636_000), Role.SECONDARY),
            main.copyFor(Identity(10, Int.MAX_VALUE), Role.SECONDARY))
        assertEquals(listOf(main, aux), NrCarrier.androidSnapshot(input))
        assertEquals(0, NrCarrier.totalBandwidthMhz(NrCarrier.androidSnapshot(input)))
        assertEquals(Role.SECONDARY, NrCarrier.androidRole(true, false, false, true))
        assertNull(NrCarrier.androidRole(true, false, true, false))
    }

    @Test fun androidNsaCountsOnlyReportedNrAndRejectsStaleObservations() {
        val role = NrCarrier.androidRole(false, false, false, true)
        assertEquals(Role.SERVING, role)
        assertNull(NrCarrier.androidRole(false, false, false, false))
        val oneNr = NrCarrier(primary, role, -1, "n78", 0, false)
        val anotherNr = oneNr.copyFor(secondary, role)
        assertEquals(1, NrCarrier.androidSnapshot(listOf(oneNr, oneNr)).size)
        assertEquals(2, NrCarrier.androidSnapshot(listOf(oneNr, anotherNr)).size)
        assertTrue(NrCarrier.isFreshAndroid(5_000, 20_000))
        assertFalse(NrCarrier.isFreshAndroid(4_999, 20_000))
        assertFalse(NrCarrier.isFreshAndroid(-1, 20_000))
        assertFalse(NrCarrier.isFreshAndroid(20_001, 20_000))
    }

    @Test fun diagRequiresMatchingPrimaryAndCompleteVerifiedConfiguration() {
        val state = RootNrCarriers()
        state.accept(config(), 1_000)
        assertTrue(state.snapshot(1_000).isEmpty())
        state.accept(serving(Identity(1, 635_000)), 1_100)
        assertTrue(state.snapshot(1_100).isEmpty())
        state.accept(serving(), 1_200)
        val rows = state.snapshot(1_200)
        assertEquals(listOf(primary, secondary), rows.map { it.identity() })
        assertEquals(180, NrCarrier.totalBandwidthMhz(rows))
        state.accept(config().put("tentative", 1), 1_300)
        assertEquals(2, state.snapshot(1_300).size)
        state.accept(config(t = 102_000).put("ncells", 4), 1_400)
        assertTrue(state.snapshot(1_400).isEmpty())
        assertTrue(RootNrCarriers().snapshot(1_400).isEmpty())
    }

    @Test fun diagFiltersSulAndDoesNotInventTotalWhenBandwidthIsMissing() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(rows = arrayOf(row(0, primary, 100), row(1, secondary, 0),
            row(2, Identity(11, 170_000), 20).put("band", 80))), 1_100)
        val carriers = state.snapshot(1_100)
        assertEquals(2, carriers.size)
        assertEquals(0, NrCarrier.totalBandwidthMhz(carriers))
        state.accept(config(rows = arrayOf(row(0, primary, 100), row(1, primary, 80))), 1_200)
        assertTrue(state.snapshot(1_200).isEmpty())
    }

    @Test fun diagClearsOnReleaseHandoverExpiryAndIdle() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        state.accept(JSONObject().put("type", "event").put("rat", "LTE").put("event", "rrc_release")
            .put("t", 102_000), 1_200)
        state.accept(config(t = 101_900), 1_300)
        assertTrue(state.snapshot(1_300).isEmpty())
        state.accept(config(t = 102_100), 1_400)
        assertEquals(2, state.snapshot(1_400).size)
        state.accept(JSONObject().put("type", "cell_change").put("rat", "NR").put("t", 103_000), 1_500)
        assertTrue(state.snapshot(1_500).isEmpty())
        state.accept(config(t = 104_000), 1_600)
        assertEquals(2, state.snapshot(11_001).count { it.lastObservation() })
        state.accept(serving().put("t", 105_000), 11_001)
        assertEquals(2, state.snapshot(11_001).count { !it.lastObservation() })
        assertTrue(state.snapshot(41_002).isEmpty())
        state.accept(serving().put("t", 105_900), 41_002)
        assertTrue(state.snapshot(41_002).isEmpty())
        state.accept(config(t = 106_000), 41_100)
        assertEquals(2, state.snapshot(41_100).size)
        state.accept(config(t = 107_000).put("state", "idle"), 41_200)
        assertTrue(state.snapshot(41_200).isEmpty())
    }

    @Test fun diagRetainsEventDrivenConfigWhileSameServingCellIsLive() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        for (now in 5_000L..90_000L step 5_000) {
            state.accept(serving(), now)
            assertEquals(2, state.snapshot(now).size)
        }
        state.accept(serving().put("cid", 456).put("t", 200_000), 92_000)
        assertTrue(state.snapshot(92_000).isEmpty())
    }

    @Test fun diagSchedulingRequiresSamePrimaryCcPciAndFrequencyAndExpires() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        val t = 102_000L
        state.accept(schedule(t, 1, secondary).put("pci", 4), 1_900)
        state.accept(schedule(t, 2, secondary), 1_910)
        state.accept(schedule(t, 1, Identity(11, 636_000)), 1_920)
        state.accept(schedule(t, 1, Identity(10, 636_002)), 1_930)
        state.accept(schedule(t, 1, secondary).put("tentative", 1), 1_940)
        state.accept(schedule(t - 1, 1, secondary), 1_950)
        assertTrue(state.snapshot(1_950).none { it.recentDownlink() })
        state.accept(schedule(t, 1, Identity(10, 636_001)), 2_000)
        assertEquals(listOf(1), state.snapshot(2_000).filter { it.recentDownlink() }.map { it.index() })
        state.accept(serving(), 4_800)
        assertTrue(state.snapshot(6_001).none { it.recentDownlink() })
    }

    @Test fun diagKeepsConfiguredCarriersAcrossPrimaryFrequencyAliasesButNotCellChanges() {
        val state = RootNrCarriers()
        val dl = Identity(0, 635_001)
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        state.accept(cellChange(dl, 102_000), 5_000)
        state.accept(serving(dl), 5_100)
        assertEquals(listOf(primary, secondary), state.snapshot(5_100).map { it.identity() })
        state.accept(cellChange(Identity(0, 635_100), 115_000), 6_000)
        assertTrue(state.snapshot(6_000).isEmpty())
    }

    @Test fun diagFreshPrimaryTrafficKeepsConfigurationWhenServingMetricsPause() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        val dl = Identity(0, 635_001)
        state.accept(schedule(110_000, 0, dl).put("arfcn", dl.arfcn()), 10_500)
        state.accept(serving().put("seen_age_ms", 9_800), 10_800)
        assertEquals(2, state.snapshot(18_000).size)
        assertTrue(state.snapshot(18_000).none { it.recentDownlink() })
        state.accept(schedule(120_000, 1, secondary), 20_000)
        val held = state.snapshot(20_501)
        assertEquals(2, held.size)
        assertTrue(held.all { it.lastObservation() && it.signal() == Signal.EMPTY && !it.recentDownlink() })
        assertTrue(state.snapshot(40_501).isEmpty())
        state.accept(config(t = 121_000, rows = arrayOf(row(0, primary, 100))), 21_000)
        state.accept(serving(), 21_100)
        assertEquals(1, state.snapshot(21_100).size)
    }

    @Test fun diagDoesNotDiscardNewConfigReceivedBeforeServingCellChange() {
        val state = RootNrCarriers()
        val old = Identity(10, 634_000)
        state.accept(serving(old), 1_000)
        state.accept(config(), 1_100)
        state.accept(cellChange(primary, 102_000), 1_200)
        state.accept(serving(), 1_300)
        assertEquals(2, state.snapshot(1_300).size)
    }

    @Test fun androidSnapshotPreservesEachCarriersSeparateMeasurements() {
        val main = NrCarrier(primary, Role.PRIMARY, -1, "n78", 0, false, Signal.ss(-80.0, -12.0, 18.0))
        val aux = NrCarrier(secondary, Role.SECONDARY, -1, "n78", 0, false,
            Signal.ss(-99.0, Int.MAX_VALUE.toDouble(), 0.0))
        val rows = NrCarrier.androidSnapshot(listOf(aux, main))
        assertEquals(Signal.ss(-80.0, -12.0, 18.0), rows[0].signal())
        assertEquals(Signal(-99.0, null, 0.0), rows[1].signal())
    }

    @Test fun diagMatchesServingLayersByPciAndSsbNotTheirArrayPosition() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        state.accept(power(secondary, 101_200).put("serving", 0), 1_200)
        state.accept(power(secondary, 101_300).put("guess", 1), 1_300)
        state.accept(power(Identity(11, secondary.arfcn()), 101_400), 1_400)
        assertEquals(Signal.EMPTY, state.snapshot(1_400)[1].signal())
        state.accept(power(primary, 101_500).put("rsrp", -80.0), 1_500)
        state.accept(power(secondary, 101_600).put("layer", 7), 1_600)
        assertEquals(-80.0, state.snapshot(1_600)[0].signal().rsrp() ?: Double.NaN, 0.001)
        assertEquals(Signal(-97.0, -14.0, null), state.snapshot(1_600)[1].signal())
        assertEquals(Signal.EMPTY, state.snapshot(6_601)[1].signal())
    }

    @Test fun diagSccSinrRequiresVerifiedCcAndRecentMeasurementEvenWithSamePci() {
        val state = RootNrCarriers()
        val aux = Identity(primary.pci(), secondary.arfcn())
        val rows = arrayOf(row(0, primary, 100), row(1, aux, 80))
        state.accept(serving(), 1_000)
        state.accept(config(rows = rows), 1_100)
        state.accept(power(primary, 101_200), 1_200)
        state.accept(snr(1, aux, 101_300), 1_300)
        assertNull(state.snapshot(1_300)[1].signal().sinr())
        state.accept(power(aux, 101_400), 1_400)
        state.accept(snr(1, aux, 101_450).put("ver", "3.1"), 1_450)
        state.accept(snr(1, secondary, 101_500), 1_500)
        state.accept(snr(1, aux, 101_550).put("rs", "TRS"), 1_550)
        assertNull(state.snapshot(1_550)[1].signal().sinr())
        state.accept(snr(1, aux, 101_600), 1_600)
        assertEquals(11.4, state.snapshot(1_600)[1].signal().sinr() ?: Double.NaN, 0.001)
        assertNull(state.snapshot(1_600)[0].signal().sinr())
        state.accept(snr(1, aux, 104_000), 4_000)
        assertNull(state.snapshot(6_601)[1].signal().sinr())
        state.accept(config(t = 105_000, rows = arrayOf(rows[0], row(1, secondary, 80))), 6_700)
        assertEquals(Signal.EMPTY, state.snapshot(6_700)[1].signal())
    }

    @Test fun androidHoldsMissingCarriersWithoutRepeatingMeasurementsAndExpiresWithoutCallbacks() {
        val state = AndroidNrCarriers()
        val anchor = AndroidNrCarriers.Anchor(true, "00101", 123, primary.pci(), primary.arfcn())
        val pcc = NrCarrier(primary, Role.PRIMARY, -1, "n78", 100, false, Signal.ss(-80.0, -12.0, 18.0), 1000, false)
        val scc = NrCarrier(secondary, Role.SECONDARY, -1, "n78", 80, false, Signal.ss(-99.0, -14.0, 5.0), 1000, false)
        assertEquals(listOf(pcc, scc), state.update(anchor, listOf(pcc, scc), 1_000))
        val held = state.update(anchor, listOf(pcc), 2_000)
        assertEquals(listOf(pcc, scc.asLastObservation()), held)
        assertEquals(Signal.EMPTY, held[1].signal())
        assertTrue(AndroidNrCarriers.displayAt(held, 16_001).all { it.lastObservation() })
        assertTrue(AndroidNrCarriers.displayAt(held, 31_001).isEmpty())
        val otherAnchor = AndroidNrCarriers.Anchor(true, "00101", 124, primary.pci(), primary.arfcn())
        assertEquals(listOf(pcc), state.update(otherAnchor, listOf(pcc), 3_000))
        state.update(anchor, listOf(pcc, scc), 4_000)
        val newNr = scc.copyFor(Identity(11, 639_000), Role.SECONDARY)
        assertEquals(listOf(newNr), state.update(anchor, listOf(newNr), 5_000))
        assertTrue(state.update(anchor, emptyList(), 5_001).isEmpty())
    }

    @Test fun diagB9a7AddsIndependentStatesAndRejectsMismatchedOrDelayedReports() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        val active = caRow(1, secondary, 2, 1)
        val pcc = caRow(0, primary, 3, 0)
        val sul = caRow(2, Identity(65535, 0), 0, 2).put("band", 80)
        assertEquals("matched", state.acceptCaState(ca(101_200, pcc, active, sul), 1_200))
        val rows = state.snapshot(1_200)
        assertEquals(listOf(CaState.UNKNOWN, CaState.ACTIVATED), rows.map { it.downlinkState() })
        assertEquals(listOf(CaState.DECONFIGURED, CaState.CONFIGURED), rows.map { it.uplinkState() })
        assertTrue(rows.none { it.recentDownlink() })
        assertEquals("identity_mismatch", state.acceptCaState(ca(101_300,
            caRow(1, Identity(secondary.pci(), secondary.arfcn() + 2), 2, 0)), 1_300))
        assertEquals("invalid", state.acceptCaState(ca(101_400, active, active), 1_400))
        assertEquals("stale", state.acceptCaState(ca(101_199, active), 1_500))
        assertEquals("stale", state.acceptCaState(ca(101_600, active).put("received_time_ms", 107_000), 1_600))
        assertEquals(CaState.ACTIVATED, state.snapshot(1_600)[1].downlinkState())
        state.accept(serving(), 16_201)
        assertEquals(CaState.UNKNOWN, state.snapshot(16_201)[1].downlinkState())
        assertEquals("matched", state.acceptCaState(ca(117_000, active), 16_300))
        assertEquals("empty", state.acceptCaState(ca(117_100), 16_400))
        assertEquals(CaState.UNKNOWN, state.snapshot(16_400)[1].downlinkState())
    }

    @Test fun diagB9a7StateClearsAcrossConfigurationChangesAndRelease() {
        val state = RootNrCarriers()
        state.accept(serving(), 1_000)
        state.accept(config(), 1_100)
        val active = caRow(1, secondary, 2, 1)
        assertEquals("matched", state.acceptCaState(ca(101_200, active), 1_200))
        state.accept(config(t = 101_300, rows = arrayOf(row(0, primary, 100), row(1, secondary, 60))), 1_300)
        assertEquals(CaState.UNKNOWN, state.snapshot(1_300)[1].downlinkState())
        assertEquals("stale", state.acceptCaState(ca(101_200, active), 1_400))
        assertEquals("matched", state.acceptCaState(ca(101_500, active), 1_500))
        state.accept(JSONObject().put("type", "event").put("rat", "NR").put("event", "rrc_release")
            .put("t", 101_600), 1_600)
        assertTrue(state.snapshot(1_600).isEmpty())
        assertEquals("no_configuration", state.acceptCaState(ca(101_700, active), 1_700))
    }

    private fun ca(t: Long, vararg rows: JSONObject) = JSONObject().put("type", "nr_ca_state")
        .put("src", "B9A7").put("tentative", 0).put("t", t).put("cc", JSONArray(rows.toList()))

    private fun caRow(cc: Int, id: Identity, dl: Int, ul: Int) = row(cc, id, 0).put("dl", dl).put("ul", ul)

    private fun power(id: Identity, t: Long) =
        JSONObject().put("type", "nr_meas").put("src", "B97F").put("t", t)
            .put("pci", id.pci()).put("arfcn", id.arfcn()).put("rsrp", -97.0).put("rsrq", -14.0)

    private fun snr(cc: Int, id: Identity, t: Long) =
        JSONObject().put("type", "nr_snr").put("src", "B8D8").put("ver", "3.2").put("rs", "SSB")
            .put("tentative", 0).put("t", t).put("pci", id.pci()).put("carrier", cc).put("sinr", 11.4)

    private fun NrCarrier.copyFor(id: Identity, role: Role) = NrCarrier(id, role, index(), band(), bandwidthMhz(), false)

    private fun serving(id: Identity = primary) =
        JSONObject().put("type", "serving").put("rat", "NR").put("seen_age_ms", 0)
            .put("pci", id.pci()).put("arfcn", id.arfcn()).put("t", 100_000).put("cid", 123)

    private fun cellChange(id: Identity, t: Long) =
        JSONObject().put("type", "cell_change").put("rat", "NR").put("t", t)
            .put("to_pci", id.pci()).put("to_arfcn", id.arfcn())

    private fun config(t: Long = 101_000, rows: Array<JSONObject> = arrayOf(row(0, primary, 100), row(1, secondary, 80))) =
        JSONObject().put("type", "nr_cfg").put("src", "B825").put("tentative", 0).put("state", "connected")
            .put("t", t).put("pci", primary.pci()).put("ssb_arfcn", primary.arfcn())
            .put("ncells", rows.size).put("cells", JSONArray(rows.toList()))

    private fun row(cc: Int, id: Identity, bandwidth: Int) =
        JSONObject().put("cc", cc).put("pci", id.pci()).put("ssb_arfcn", id.arfcn())
            .put("dl_arfcn", id.arfcn() + 1).put("band", 78).put("bw_mhz", bandwidth)

    private fun schedule(t: Long, cc: Int, id: Identity) =
        JSONObject().put("type", "nr_dl_sched").put("src", "B887").put("tentative", 0).put("t", t)
            .put("dt_ms", 1_000).put("pci", primary.pci()).put("arfcn", primary.arfcn()).put("cc",
                JSONArray().put(JSONObject().put("cc", cc).put("pci", id.pci()).put("arfcn", id.arfcn()).put("tb", 12)))
}
