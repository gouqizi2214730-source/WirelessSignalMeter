package com.craxiom.networksurvey.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierAggregationTest {
    @Test
    fun serviceState_singleOrEmpty_isNotCa() {
        assertEquals(CarrierAggregation.Kind.NONE, CarrierAggregation.fromServiceState(null, TelephonyStateUtils.NrMode.NONE).kind)
        assertEquals(CarrierAggregation.Kind.NONE, CarrierAggregation.fromServiceState(intArrayOf(), TelephonyStateUtils.NrMode.STANDALONE).kind)
        assertEquals(CarrierAggregation.Kind.NONE, CarrierAggregation.fromServiceState(intArrayOf(20_000), TelephonyStateUtils.NrMode.NONE).kind)
        assertEquals(CarrierAggregation.Kind.NONE, CarrierAggregation.fromServiceState(intArrayOf(0, Integer.MAX_VALUE, 20_000), TelephonyStateUtils.NrMode.NONE).kind)
    }

    @Test
    fun serviceState_nsaTwoBandwidths_isEndcNotClaimedAsCa() {
        val snapshot = CarrierAggregation.fromServiceState(intArrayOf(20_000, 100_000), TelephonyStateUtils.NrMode.NON_STANDALONE)
        assertEquals(CarrierAggregation.Kind.EN_DC, snapshot.kind)
        assertEquals(CarrierAggregation.Evidence.SERVICE_STATE_BANDWIDTHS, snapshot.evidence)
        assertFalse(snapshot.claimCarrierAggregation)
        assertTrue(snapshot.visible)
        assertEquals(120_000, snapshot.aggregateKhz)
        assertFalse(CarrierAggregation.englishSummary(snapshot).contains("Carrier Aggregation"))
        assertTrue(CarrierAggregation.englishSummary(snapshot).contains("EN-DC"))
    }

    @Test
    fun serviceState_lteOrSa_doesNotClaimActivation() {
        val lte = CarrierAggregation.fromServiceState(intArrayOf(15_000, 20_000), TelephonyStateUtils.NrMode.NONE)
        assertEquals(CarrierAggregation.Kind.LTE_CA, lte.kind)
        assertFalse(lte.claimCarrierAggregation)
        assertTrue(CarrierAggregation.englishSummary(lte).contains("not activation"))

        val nr = CarrierAggregation.fromServiceState(intArrayOf(40_000, 100_000), TelephonyStateUtils.NrMode.STANDALONE)
        assertEquals(CarrierAggregation.Kind.NR_CA, nr.kind)
        assertFalse(nr.claimCarrierAggregation)
    }

    @Test
    fun cellInfoSecondary_lte_preferredOverUntypedBandwidths() {
        val service = CarrierAggregation.fromServiceState(intArrayOf(20_000, 15_000), TelephonyStateUtils.NrMode.NONE)
        val scc = CarrierAggregation.Component(
            rat = CarrierAggregation.LTE,
            role = CarrierAggregation.ROLE_SCC,
            pci = 54,
            arfcn = 1550,
            band = 3,
            bandwidthKhz = 15_000,
            evidence = CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING,
        )
        val pcc = CarrierAggregation.Component(
            rat = CarrierAggregation.LTE, role = CarrierAggregation.ROLE_PCC,
            pci = 54, arfcn = 1750, band = 3, bandwidthKhz = 20_000,
            evidence = CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING,
        )
        val merged = CarrierAggregation.mergeWithCellInfo(service, pcc, listOf(scc), emptyList())
        assertEquals(CarrierAggregation.Kind.LTE_CA, merged.kind)
        assertEquals(CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING, merged.evidence)
        assertTrue(merged.claimCarrierAggregation)
        assertTrue(CarrierAggregation.chipLabel(scc).contains("B3"))
        assertTrue(CarrierAggregation.chipLabel(scc).contains("PCI 54"))
    }

    @Test
    fun cellInfoSecondaryNrOnLteAnchor_isEndc() {
        val service = CarrierAggregation.fromServiceState(intArrayOf(20_000, 100_000), TelephonyStateUtils.NrMode.NON_STANDALONE)
        val scc = CarrierAggregation.Component(
            rat = CarrierAggregation.NR,
            role = CarrierAggregation.ROLE_SCC,
            pci = 73,
            arfcn = 428910,
            band = 1,
            bandwidthKhz = 40_000,
            evidence = CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING,
        )
        val pcc = CarrierAggregation.Component(
            rat = CarrierAggregation.LTE, role = CarrierAggregation.ROLE_PCC,
            pci = 54, arfcn = 1750, band = 3, bandwidthKhz = 20_000,
            evidence = CarrierAggregation.Evidence.CELLINFO_SECONDARY_SERVING,
        )
        val merged = CarrierAggregation.mergeWithCellInfo(service, pcc, emptyList(), listOf(scc))
        assertEquals(CarrierAggregation.Kind.EN_DC, merged.kind)
        assertFalse(merged.claimCarrierAggregation)
        assertEquals(2, merged.components.size)
        assertTrue(CarrierAggregation.englishSummary(merged).startsWith("EN-DC"))
    }

    @Test
    fun b825_twoConnectedCells_isConfiguredNrCa() {
        val config = CarrierAggregation.DiagNrConfig(
            state = "connected",
            ncells = 2,
            cells = listOf(
                CarrierAggregation.DiagNrCell(0, 73, 1, 426000, 428910, 40),
                CarrierAggregation.DiagNrCell(1, 500, 78, 623334, 627264, 100),
            ),
            receivedAt = 1_000,
        )
        val snapshot = CarrierAggregation.fromDiagNrConfig(config, nowMs = 2_000)
        assertTrue(snapshot.visible)
        assertEquals(CarrierAggregation.Kind.NR_CA, snapshot.kind)
        assertEquals(CarrierAggregation.Evidence.DIAG_B825_CONFIGURED, snapshot.evidence)
        assertFalse(snapshot.claimCarrierAggregation)
        assertEquals(140_000, snapshot.aggregateKhz)
        assertEquals(CarrierAggregation.ROLE_PCC, snapshot.components[0].role)
        assertEquals(CarrierAggregation.ROLE_SCC, snapshot.components[1].role)
    }

    @Test
    fun b825_idleOrStaleOrSingleCell_isNotCa() {
        val two = listOf(
            CarrierAggregation.DiagNrCell(0, 73, 1, 426000, 428910, 40),
            CarrierAggregation.DiagNrCell(1, 500, 78, 623334, 627264, 100),
        )
        assertFalse(CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig("idle", 2, two, 1_000), 2_000).visible)
        assertFalse(CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig("connected", 0, emptyList(), 1_000), 2_000).visible)
        assertFalse(CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig("connected", 1, listOf(two[0]), 1_000), 2_000).visible)
        assertFalse(CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig("connected", 2, two, 1_000), nowMs = 20_000).visible)
        // later empty connected list must clear a previous two-cell config
        assertFalse(CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig("connected", 0, emptyList(), 5_000), 5_100).visible)
    }

    @Test
    fun b193_handoverBoundary_isNotLteCa() {
        val samples = listOf(
            serving(1750, 54, 0),
            serving(1750, 54, 100),
            serving(1750, 54, 200),
            serving(1550, 54, 250),
            serving(1550, 54, 800),
            serving(1550, 54, 1_200),
        )
        val snapshot = CarrierAggregation.fromDiagLteServing(samples, nowMs = 1_200)
        assertFalse(snapshot.visible)
        assertEquals(CarrierAggregation.Kind.NONE, snapshot.kind)
    }

    @Test
    fun b193_overlappingServingEarfcns_isMeasuredLteCa() {
        val samples = (0 until 8).flatMap { i ->
            val t = i * 500L
            listOf(serving(1750, 54, t), serving(1550, 54, t + 10))
        }
        val snapshot = CarrierAggregation.fromDiagLteServing(samples, nowMs = 3_600)
        assertTrue(snapshot.visible)
        assertEquals(CarrierAggregation.Kind.LTE_CA, snapshot.kind)
        assertEquals(CarrierAggregation.Evidence.DIAG_B193_MEASURED_SERVING, snapshot.evidence)
        assertFalse(snapshot.claimCarrierAggregation)
        assertEquals(2, snapshot.components.size)
        assertTrue(CarrierAggregation.englishSummary(snapshot).contains("B193"))
    }

    @Test
    fun b193_neighborsAreIgnored() {
        val samples = (0 until 5).flatMap { i ->
            val t = i * 500L
            listOf(
                serving(1750, 54, t),
                CarrierAggregation.DiagLteServingSample(1550, 55, isServing = false, receivedAt = t),
            )
        }
        assertFalse(CarrierAggregation.fromDiagLteServing(samples, nowMs = 2_500).visible)
    }

    @Test
    fun preferDiag_overAndroidWhenDiagVisible() {
        val diag = CarrierAggregation.fromDiagNrConfig(
            CarrierAggregation.DiagNrConfig(
                "connected", 2,
                listOf(
                    CarrierAggregation.DiagNrCell(0, 73, 1, 426000, 428910, 40),
                    CarrierAggregation.DiagNrCell(1, 500, 78, 623334, 627264, 100),
                ),
                1_000,
            ),
            2_000,
        )
        val android = CarrierAggregation.fromServiceState(intArrayOf(20_000, 100_000), TelephonyStateUtils.NrMode.NON_STANDALONE)
        val preferred = CarrierAggregation.preferDiag(diag, android)
        assertEquals(CarrierAggregation.Evidence.DIAG_B825_CONFIGURED, preferred.evidence)
        val fallback = CarrierAggregation.preferDiag(CarrierAggregation.Snapshot(CarrierAggregation.Kind.NONE, CarrierAggregation.Evidence.NONE), android)
        assertEquals(CarrierAggregation.Kind.EN_DC, fallback.kind)
    }

    private fun serving(earfcn: Long, pci: Int, t: Long) =
        CarrierAggregation.DiagLteServingSample(earfcn, pci, isServing = true, receivedAt = t)
}
