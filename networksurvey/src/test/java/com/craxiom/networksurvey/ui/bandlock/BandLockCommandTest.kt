package com.craxiom.networksurvey.ui.bandlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandLockCommandTest {
    @Test
    fun normalizesAndDeduplicatesBandSelections() {
        val cmd = BandLockCommand.apply(LockRadio.Lte, LockTarget.Bands, "b3, 1;3 3", "", "", 30)
        assertEquals(listOf("lte-band", "1,3"), cmd.arguments)
        /* Bands 1..32 go out instantly through the CM frame; no modem restart. */
        assertTrue(!cmd.restartsModem)
        /* Mixed selections above 32 still ride the EFS+restart route. */
        val high = BandLockCommand.apply(LockRadio.Lte, LockTarget.Bands, "B41, 1;3 3", "", "", 30)
        assertEquals(listOf("lte-band", "1,3,41"), high.arguments)
        assertTrue(high.restartsModem)
    }

    @Test
    fun keepsModemRestartForHighLteBandsNrAndCellLocks() {
        assertTrue(BandLockCommand.apply(LockRadio.Lte, LockTarget.Bands, "40,41", "", "", 30).restartsModem)
        assertTrue(BandLockCommand.apply(LockRadio.Nr, LockTarget.Bands, "78", "", "", 30).restartsModem)
        assertTrue(BandLockCommand.apply(LockRadio.Lte, LockTarget.Cell, "", "100", "3", 30).restartsModem)
        assertTrue(BandLockCommand.apply(LockRadio.Lte, LockTarget.Frequency, "", "100", "", 30).restartsModem)
    }

    @Test
    fun preservesZeroLteChannelAndPciAndEncodesNrScs() {
        assertEquals(listOf("lte-cell", "0:0"),
            BandLockCommand.apply(LockRadio.Lte, LockTarget.Cell, "", "0", "0", 30).arguments)
        assertEquals(listOf("nr-cell", "78", "627264", "1007", "30"),
            BandLockCommand.apply(LockRadio.Nr, LockTarget.Cell, "78", "627264", "1007", 30).arguments)
        assertEquals(listOf("nr-freq", "3279165", "120"),
            BandLockCommand.apply(LockRadio.Nr, LockTarget.Frequency, "", "3279165", "", 120).arguments)
    }

    @Test
    fun rejectsEmptySelectionsInjectionAndOutOfRangeValues() {
        fun invalid(radio: LockRadio, target: LockTarget, band: String, frequency: String, pci: String, scs: Int = 30) {
            assertTrue(runCatching { BandLockCommand.apply(radio, target, band, frequency, pci, scs) }.isFailure)
        }
        invalid(LockRadio.Lte, LockTarget.Bands, "", "", "")
        invalid(LockRadio.Lte, LockTarget.Bands, "65", "", "")
        invalid(LockRadio.Nr, LockTarget.Bands, "513", "", "")
        invalid(LockRadio.Nr, LockTarget.Bands, "78;reboot", "", "")
        invalid(LockRadio.Lte, LockTarget.Cell, "", "262144", "0")
        invalid(LockRadio.Lte, LockTarget.Cell, "", "100", "504")
        invalid(LockRadio.Nr, LockTarget.Cell, "78", "627264", "1008")
        invalid(LockRadio.Nr, LockTarget.Cell, "78", "0", "0")
        invalid(LockRadio.Nr, LockTarget.Cell, "78", "627264", "0", 240)
        invalid(LockRadio.Nr, LockTarget.Cell, "78", "627264", "-1")
        invalid(LockRadio.Nr, LockTarget.Cell, "78", "999999999999999999", "0")
    }

    @Test
    fun usesSeparateRestoreCommandsAndFixedModeNames() {
        assertEquals(listOf("nr-band-restore"), BandLockCommand.restore(LockRadio.Nr, LockTarget.Bands).arguments)
        assertEquals(listOf("lte-freq-clear"), BandLockCommand.restore(LockRadio.Lte, LockTarget.Frequency).arguments)
        assertEquals(listOf("nr-cell-clear"), BandLockCommand.restore(LockRadio.Nr, LockTarget.Cell).arguments)
        assertEquals(listOf("mode", "auto"), BandLockCommand.mode(LockNetworkMode.Auto).arguments)
    }
}
