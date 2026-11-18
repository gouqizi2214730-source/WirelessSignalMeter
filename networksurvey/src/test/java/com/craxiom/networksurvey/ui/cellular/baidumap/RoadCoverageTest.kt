package com.craxiom.networksurvey.ui.cellular.baidumap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class RoadCoverageTest {
    @Test
    fun readsOldMapExportsAndKeepsZeroSinrAndPci() {
        val previousExport = """
            经度,纬度,RSRP(dBm),SINR(dB),RSRQ(dB),基站ID,小区ID,PCI,频点,PLMN,TAC,网络制式,备注,时间
            100.5,30.5,-115.5,0,-11,22,33,0,627264,460-1,12,NR,"含逗号,备注",2026-11-15 23:18:52
            100.6,30.6,,,,22,33,0,627264,460-1,12,NR,无数据,2026-11-15 23:18:54
        """.trimIndent()
        val rows = readRoadCsv(("\uFEFF" + previousExport).reader())
        assertEquals(2, rows.size)
        assertEquals(0, rows.first().pci)
        assertEquals(0.0, rows.first().sinr!!, 0.0)
        assertEquals("460-01", rows.first().plmn)
        assertNull(rows.last().rsrp)
        val exported = StringWriter().apply { writeRoadCsv(this, rows) }
        assertEquals(rows, readRoadCsv(exported.toString().reader()))
    }

    @Test
    fun separatesSameOperatorSimTracksAndDoesNotBridgeMissingSamples() {
        val time = 1_795_000_000_000L
        val samples = listOf(
            point(time, 0, -115.0),
            point(time + 1_000, 1, -95.0),
            point(time + 3_000, 0, -114.0),
            point(time + 6_000, 0, -113.0),
            point(time + 9_000, 0, null),
            point(time + 12_000, 0, -114.0),
            point(time + 50_000, 0, -118.0),
            point(time + 53_000, 0, -117.0),
            point(time + 56_000, 0, -116.0),
            point(time + 300_000, 0, -114.0),
        )
        val tracks = roadTracks(samples.reversed())
        assertEquals(3, tracks.size)
        val firstSimRoute = tracks.single { it.simSlot == 0 && it.startTime == time }
        val report = analyzeRoad(firstSimRoute.samples)
        assertEquals(7, report.samplesWithRsrp)
        assertEquals(7, report.weakSamples)
        assertEquals(2, report.weakSections.size)
        assertEquals(0, report.weakSections.first().firstIndex)
        assertEquals(5, report.weakSections.last().firstIndex)
    }

    @Test
    fun readsNativeNrHeadersAndSkipsNonServingAndInvalidCoordinates() {
        val csv = """
            deviceTime,latitude,longitude,ssRsrp,ssRsrq,ssSinr,narfcn,pci,mcc,mnc,servingCell
            2026-11-15T15:18:52Z,30.5,100.5,-115.5,-11,0,627264,0,460,1,true
            1794755933,30.5,100.5,-90,-10,20,627264,2,460,1,false
            1794755934,91,100.5,-90,-10,20,627264,2,460,1,true
            1794755935,NaN,100.5,-90,-10,20,627264,2,460,1,true
        """.trimIndent()
        val rows = readRoadCsv(csv.reader())
        assertEquals(1, rows.size)
        assertEquals(627264, rows.single().arfcn)
        assertEquals("NR", rows.single().rat)
        assertEquals("460-01", rows.single().plmn)
        assertEquals(0.0, rows.single().sinr!!, 0.0)
        assertEquals(rows, readRoadCsv(StringWriter().apply { writeRoadCsv(this, rows) }.toString().reader()))
    }

    @Test
    fun handlesEmptyRoutesAndDoesNotCountGpsJumpsOrThresholdAsWeak() {
        assertTrue(roadTracks(emptyList()).isEmpty())
        assertNull(analyzeRoad(emptyList()).averageRsrp)
        val time = 1_795_000_000_000L
        val first = point(time, 0, -110.0)
        val jumped = first.copy(time = time + 1_000, latitude = 35.0)
        assertNull(roadDistance(first, jumped))
        assertNull(roadDistance(first, first.copy(time = time - 1)))
        val report = analyzeRoad(listOf(first, jumped))
        assertEquals(0, report.weakSamples)
        assertEquals(0.0, report.distanceMeters, 0.0)
        assertTrue(report.weakSections.isEmpty())
    }

    @Test(expected = RoadCsvFormatException::class)
    fun rejectsCsvWithoutSignalColumns() {
        readRoadCsv("latitude,longitude,timestamp\n30,100,1795000000000".reader())
    }

    @Test(expected = RoadCsvFormatException::class)
    fun rejectsOversizedCsvInsteadOfSilentlyTruncating() {
        val csv = buildString {
            append("latitude,longitude,timestamp,rsrp\n")
            repeat(ROAD_SAMPLE_LIMIT + 1) { append("30,100,1795000000000,-115\n") }
        }
        readRoadCsv(csv.reader())
    }

    private fun point(time: Long, simSlot: Int, rsrp: Double?) = RoadSample(
        time, 30.0, 101.0, rsrp, null, null, "NR", 27, 627264, "460-11", simSlot,
    )
}
