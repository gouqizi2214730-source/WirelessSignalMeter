package com.craxiom.networksurvey.ui.cellular.baidumap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.craxiom.networksurvey.model.SurveyEvent
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

    @Test
    fun separatesExplicitTasksAndClockDomainsWithoutWaitingForGap() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1, clockId = "boot1")
        val tracks = roadTracks(listOf(p, p.copy(time = p.time + 1, taskId = "b"),
            p.copy(time = p.time + 2, clockId = "boot2")))
        assertEquals(3, tracks.size)
    }

    @Test
    fun roundTripsTaskSubscriptionAndLocationQuality() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(
            taskId = "task,quoted", missionId = "mission", subscriptionId = 12, clockId = "clock",
            elapsedMs = 2_000, locationAgeMs = 123, locationAccuracyM = 4.5, locationProvider = "gps")
        val text = StringWriter().apply { writeRoadCsv(this, listOf(p)) }.toString()
        assertEquals(listOf(p), readRoadCsv(text.reader()))
    }

    @Test
    fun linksUsingMonotonicTimeAcrossWallClockAdjustment() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1,
            clockId = "clock", elapsedMs = 10_000, locationAgeMs = 10, locationAccuracyM = 5.0)
        val event = event(p).copy(time = p.time + 3_600_000, elapsedMs = 10_500)
        val result = linkRoadEvents(listOf(RoadTrack(listOf(p))), listOf(event)).single()
        assertEquals(0, result.trackIndex)
        assertEquals(0, result.sampleIndex)
    }

    @Test
    fun refusesCrossTaskCrossSimCrossBootAndExpiredPositions() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1,
            clockId = "clock", elapsedMs = 10_000, locationAgeMs = 10, locationAccuracyM = 5.0)
        val events = listOf(event(p).copy(taskId = "b"), event(p).copy(subscriptionId = 2),
            event(p).copy(clockId = "another"), event(p).copy(elapsedMs = 20_001),
            event(p).copy(pci = 99))
        assertTrue(linkRoadEvents(listOf(RoadTrack(listOf(p))), events).all { it.sampleIndex == null })
        for (bad in listOf(p.copy(locationAgeMs = 10_001), p.copy(locationAccuracyM = 101.0))) {
            assertNull(linkRoadEvents(listOf(RoadTrack(listOf(bad))), listOf(event(p))).single().sampleIndex)
        }
    }

    @Test
    fun unknownDiagSubscriptionRequiresUniqueMatchingCellSource() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1,
            clockId = "clock", elapsedMs = 10_000)
        val root = event(p).copy(subscriptionId = null, simSlot = null, source = "root_diag")
        val one = RoadTrack(listOf(p))
        val other = RoadTrack(listOf(p.copy(simSlot = 1, subscriptionId = 2)))
        assertEquals(0, linkRoadEvents(listOf(one), listOf(root)).single().sampleIndex)
        assertNull(linkRoadEvents(listOf(one, other), listOf(root)).single().sampleIndex)
        assertNull(linkRoadEvents(listOf(one), listOf(root.copy(pci = null))).single().sampleIndex)
        assertNull(linkRoadEvents(listOf(one), listOf(root.copy(arfcn = null))).single().sampleIndex)
    }

    @Test
    fun speedUsesDevicePositionWithoutClaimingNetworkSimAndUnlocatedEventsRemain() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1)
        val speed = event(p).copy(subscriptionId = null, simSlot = null, rat = null, pci = null, arfcn = null,
            source = "speedtest")
        val links = linkRoadEvents(listOf(RoadTrack(listOf(p))), listOf(speed))
        assertEquals(speed, links.single().event)
        assertEquals(0, links.single().sampleIndex)
        assertTrue(links.single().devicePositionOnly)
        assertNull(links.single().event.simSlot)
        assertEquals(speed, linkRoadEvents(emptyList(), listOf(speed)).single().event)
        val otherSim = p.copy(subscriptionId = 2, simSlot = 1)
        assertTrue(linkRoadEvents(listOf(RoadTrack(listOf(p)), RoadTrack(listOf(otherSim))), listOf(speed))
            .single().devicePositionOnly)
        assertNull(linkRoadEvents(listOf(RoadTrack(listOf(p)),
            RoadTrack(listOf(otherSim.copy(latitude = 35.0)))), listOf(speed)).single().sampleIndex)
    }

    @Test
    fun indexedMatcherHandlesFullReplayWindow() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1,
            clockId = "clock", elapsedMs = 10_000)
        val points = List(ROAD_SAMPLE_LIMIT) { i -> p.copy(time = p.time + i * 2_000,
            elapsedMs = p.elapsedMs!! + i * 2_000) }
        val events = points.filterIndexed { i, _ -> i % 5 == 0 }.mapIndexed { i, sample -> event(sample).copy(id = "$i") }
        val links = linkRoadEvents(listOf(RoadTrack(points)), events)
        assertEquals(10_000, links.size)
        assertTrue(links.withIndex().all { (i, link) -> link.sampleIndex == i * 5 })
    }

    @Test
    fun matchesNearestCompatibleCellAndDoesNotInventIdentityForLegacyRows() {
        val p = point(1_795_000_000_000L, 0, -100.0).copy(taskId = "a", subscriptionId = 1,
            clockId = "clock", elapsedMs = 10_000)
        val points = listOf(p, p.copy(time = p.time + 2_000, elapsedMs = 12_000, pci = 88))
        val e = event(p).copy(elapsedMs = 12_000)
        assertEquals(0, linkRoadEvents(listOf(RoadTrack(points)), listOf(e)).single().sampleIndex)
        assertNull(linkRoadEvents(listOf(RoadTrack(listOf(p.copy(taskId = null)))), listOf(e)).single().sampleIndex)
    }

    private fun event(p: RoadSample) = SurveyEvent("event", p.taskId!!, p.time,
        p.elapsedMs ?: 0, p.clockId.orEmpty(), "android", "cell_change", "observed",
        subscriptionId = p.subscriptionId, simSlot = p.simSlot, rat = p.rat, pci = p.pci, arfcn = p.arfcn)

    private fun point(time: Long, simSlot: Int, rsrp: Double?) = RoadSample(
        time, 30.0, 101.0, rsrp, null, null, "NR", 27, 627264, "460-11", simSlot,
    )
}
