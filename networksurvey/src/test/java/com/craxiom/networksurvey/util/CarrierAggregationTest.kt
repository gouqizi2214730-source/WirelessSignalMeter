package com.craxiom.networksurvey.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierAggregationTest {
    private val lte = CarrierAggregation.Component("LTE", 10, 1750, 3, 20_000)
    private val nr = CarrierAggregation.Component("NR", 20, 627264, 78, 0)
    private val fallback = CarrierAggregation.fromServiceState(intArrayOf(20_000, 100_000))
    private val pcc = CarrierAggregation.B193Sample(3, 0, 10, 1750, true, 1_000)
    private val scc = CarrierAggregation.B193Sample(3, 1, 12, 1550, true, 1_010)

    @Test fun bandwidthsDoNotProveCaOrEndc() {
        assertEquals(CarrierAggregation.Kind.MULTI_BANDWIDTH, fallback.kind)
        assertEquals(120_000, fallback.aggregateKhz)
        assertFalse(CarrierAggregation.fromServiceState(intArrayOf(20_000, Int.MAX_VALUE, 0)).visible)
    }

    @Test fun lteSecondariesRequireDistinctValidIdentity() {
        val invalid = lte.copy(pci = -1)
        assertEquals(fallback, CarrierAggregation.mergeWithCellInfo(fallback, lte, listOf(lte, invalid), emptyList()))
        val secondary = lte.copy(arfcn = 1550)
        val result = CarrierAggregation.mergeWithCellInfo(fallback, lte, listOf(secondary, secondary), emptyList())
        assertEquals(CarrierAggregation.Kind.LTE_CA, result.kind)
        assertEquals(listOf(lte, secondary), result.components)
    }

    @Test fun nrOnLteAnchorIsEndcNotNrCa() {
        assertEquals(CarrierAggregation.Kind.EN_DC,
            CarrierAggregation.mergeWithCellInfo(fallback, lte, emptyList(), listOf(nr)).kind)
        assertEquals(CarrierAggregation.Kind.NR_CA,
            CarrierAggregation.mergeWithCellInfo(fallback, nr, emptyList(), listOf(nr.copy(arfcn = 634990))).kind)
    }

    @Test fun b193RequiresMatchingPccAndExplicitSccFromSameReport() {
        fun select(vararg rows: CarrierAggregation.B193Sample) =
            CarrierAggregation.b193Secondaries(rows.toList(), 10, 1750, 1_100)
        assertEquals(listOf(scc), select(pcc, scc))
        assertTrue(select(pcc, scc.copy(report = 2)).isEmpty())
        assertTrue(select(pcc, scc.copy(cc = 0)).isEmpty())
        assertTrue(select(pcc, scc.copy(serving = false)).isEmpty())
        assertTrue(select(pcc.copy(pci = 11), scc).isEmpty())
        assertTrue(select(pcc, scc, pcc.copy(report = 4)).isEmpty())
        assertTrue(CarrierAggregation.b193Secondaries(listOf(pcc, scc), 10, 1750, 3_100).isEmpty())
    }
}
