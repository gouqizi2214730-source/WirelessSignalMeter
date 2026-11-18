package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LteSinrUtilsTest
{
    private static final int UNAVAILABLE = Integer.MAX_VALUE;

    @Test
    public void validCellMeasurementWinsOverVendorValue()
    {
        assertEquals(8, LteSinrUtils.read(8, new VendorStrength()));
    }

    @Test
    public void zeroAndNegativeMeasurementsRemainInDb()
    {
        assertEquals(0, LteSinrUtils.read(0, new VendorStrength()));
        assertEquals(-7, LteSinrUtils.read(-7, new VendorStrength()));
        assertEquals(18, LteSinrUtils.read(18, null));
    }

    @Test
    public void privateVendorAccessorFillsUnavailableSinr()
    {
        assertEquals(14, LteSinrUtils.read(UNAVAILABLE, new VendorStrength()));
    }

    @Test
    public void inheritedNonPublicAccessorIsFound()
    {
        assertEquals(-3, LteSinrUtils.read(UNAVAILABLE, new InheritedStrength()));
    }

    @Test
    public void invalidAccessorFallsThroughToNextVendorGetter()
    {
        assertEquals(0, LteSinrUtils.read(UNAVAILABLE, new AlternateStrength()));
    }

    @Test
    public void throwingAccessorDoesNotPreventOtherVendorGetters()
    {
        assertEquals(12, LteSinrUtils.read(UNAVAILABLE, new ThrowingStrength()));
    }

    @Test
    public void cqiIsNeverUsedAsSinr()
    {
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new CqiOnlyStrength()));
    }

    @Test
    public void missingOrUnavailableAccessorsStayUnavailable()
    {
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, null));
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new Object()));
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new UnavailableStrength()));
    }

    @Test
    public void implausibleValuesAreNotRescaledOrTruncated()
    {
        assertEquals(UNAVAILABLE, LteSinrUtils.read(180, new FractionalStrength()));
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new OverflowStrength()));
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new WrongTypeStrength()));
    }

    @Test
    public void lteDbRangeIncludesBothEndpoints()
    {
        assertTrue(LteSinrUtils.isValid(-20));
        assertTrue(LteSinrUtils.isValid(30));
        assertFalse(LteSinrUtils.isValid(-21));
        assertFalse(LteSinrUtils.isValid(31));
        assertFalse(LteSinrUtils.isValid(UNAVAILABLE));
    }

    @Test
    public void legacySnapshotRequiresMatchingValidLteRsrp()
    {
        assertEquals(17, LteSinrUtils.readLegacy(new LegacyStrength(-95, 17), -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(new LegacyStrength(-94, 17), -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(new LegacyStrength(UNAVAILABLE, 17), UNAVAILABLE));
    }

    @Test
    public void legacySnapshotPreservesZeroAndRejectsInvalidValues()
    {
        assertEquals(0, LteSinrUtils.readLegacy(new LegacyStrength(-95, 0), -95));
        assertEquals(-8, LteSinrUtils.readLegacy(new LegacyStrength(-95, -8), -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(new LegacyStrength(-95, UNAVAILABLE), -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(new LegacyStrength(-95, 170), -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(null, -95));
        assertEquals(UNAVAILABLE, LteSinrUtils.readLegacy(new Object(), -95));
    }

    private static class VendorStrength
    {
        private int getCellSinr() { return 14; }
    }

    private static class ParentStrength
    {
        private int getSinr() { return -3; }
    }

    private static class InheritedStrength extends ParentStrength
    {
    }

    private static class AlternateStrength
    {
        private int getCellSinr() { return UNAVAILABLE; }
        private int getSnr() { return 0; }
    }

    private static class ThrowingStrength
    {
        private int getCellSinr() { throw new SecurityException("Vendor accessor denied"); }
        private int getSinr() { return 12; }
    }

    private static class CqiOnlyStrength
    {
        public int getCqi() { return 15; }
    }

    private static class UnavailableStrength
    {
        private int getCellSinr() { return UNAVAILABLE; }
        private int getSnr() { return -99; }
        private int getSinr() { return 99; }
    }

    private static class FractionalStrength
    {
        private double getSinr() { return 18.5; }
    }

    private static class OverflowStrength
    {
        private long getCellSinr() { return 0x1_0000_0000L; }
    }

    private static class WrongTypeStrength
    {
        private String getSnr() { return "18"; }
    }

    private static class LegacyStrength
    {
        private final int rsrp;
        private final int rssnr;

        private LegacyStrength(int rsrp, int rssnr)
        {
            this.rsrp = rsrp;
            this.rssnr = rssnr;
        }

        private int getLteRsrp() { return rsrp; }
        private int getLteRssnr() { return rssnr; }
    }
}
