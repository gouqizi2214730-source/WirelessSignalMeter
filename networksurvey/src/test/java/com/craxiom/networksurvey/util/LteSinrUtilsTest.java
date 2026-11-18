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
    public void snapshotLegacyAccessorPreservesDbValues()
    {
        assertEquals(17, LteSinrUtils.readSnapshot(new LegacyStrength(17)));
        assertEquals(0, LteSinrUtils.readSnapshot(new LegacyStrength(0)));
        assertEquals(-8, LteSinrUtils.readSnapshot(new LegacyStrength(-8)));
    }

    @Test
    public void snapshotRejectsUnavailableAndAmbiguousLegacyUnits()
    {
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new LegacyStrength(UNAVAILABLE)));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new LegacyStrength(170)));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new FlatLegacyStrength()));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(null));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new Object()));
    }

    @Test
    public void privateAndInheritedLteFieldsAreRead()
    {
        assertEquals(19, LteSinrUtils.read(UNAVAILABLE, new FieldStrength()));
        assertEquals(-5, LteSinrUtils.read(UNAVAILABLE, new InheritedFieldStrength()));
        assertEquals(4, LteSinrUtils.read(4, new FieldStrength()));
    }

    @Test
    public void invalidFieldFallsThroughToText()
    {
        assertEquals(11, LteSinrUtils.read(UNAVAILABLE, new InvalidFieldStrength()));
    }

    @Test
    public void cellTextSupportsAndroidAndVendorLabels()
    {
        assertEquals(16, LteSinrUtils.read(UNAVAILABLE, new TextStrength(
                "CellSignalStrengthLte: rssi=-69 rsrp=-96 rssnr=16 cqi=12")));
        assertEquals(0, LteSinrUtils.read(UNAVAILABLE, new TextStrength("{mRssnr=0, cqi=8}")));
        assertEquals(-9, LteSinrUtils.read(UNAVAILABLE, new TextStrength("CellSignalStrengthLte: sinr=-9")));
    }

    @Test
    public void textNeverUsesCqiNrOrPartialNumbers()
    {
        for (String text : new String[]{"cqi=15", "ssSinr=20 csiSinr=25", "nrSinr=12",
                "rssnr=2147483647", "rssnr=180", "rssnr=18.5", "rssnr=18dB",
                "rssnr=9223372036854775808"})
        {
            assertEquals(text, UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new TextStrength(text)));
        }
    }

    @Test
    public void malformedTextDoesNotBreakCollection()
    {
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new TextStrength(null)));
        assertEquals(UNAVAILABLE, LteSinrUtils.read(UNAVAILABLE, new ThrowingTextStrength()));
    }

    @Test
    public void nestedModernLteSnapshotIsRead()
    {
        assertEquals(19, LteSinrUtils.readSnapshot(new NestedStrength()));
    }

    @Test
    public void snapshotTextIsRestrictedToLteBlock()
    {
        assertEquals(13, LteSinrUtils.readSnapshot(new TextStrength(
                "SignalStrength:{mLte=CellSignalStrengthLte: rsrp=-95 rssnr=13 "
                        + "mNr=CellSignalStrengthNr: ssSinr=25 primary=CellSignalStrengthLte}")));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new TextStrength(
                "SignalStrength:{mLte=CellSignalStrengthLte: rssnr=2147483647 "
                        + "mNr=CellSignalStrengthNr: sinr=25}")));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new TextStrength(
                "SignalStrength:{mLte=CellSignalStrengthLte: rssnr=2147483647,"
                        + "mNr=CellSignalStrengthNr: sinr=25}")));
        assertEquals(UNAVAILABLE, LteSinrUtils.readSnapshot(new TextStrength("lterssnr=180 sinr=18")));
    }

    @Test
    public void freshSnapshotDoesNotRequireIdenticalRsrp()
    {
        assertTrue(LteSinrUtils.canUseSnapshot(true, 1, 19_000, 20_000, 21_000));
        assertTrue(LteSinrUtils.canUseSnapshot(true, 1, 15_000, 20_000, 21_000));
    }

    @Test
    public void snapshotCannotFillNeighborsOrAmbiguousServingCells()
    {
        assertFalse(LteSinrUtils.canUseSnapshot(false, 1, 19_000, 20_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 0, 19_000, 20_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 2, 19_000, 20_000, 21_000));
    }

    @Test
    public void snapshotAgeAndTimeGapAreBounded()
    {
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 19_000, 10_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 10_000, 19_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 14_999, 20_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 0, 20_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 19_000, 0, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 22_000, 20_000, 21_000));
        assertFalse(LteSinrUtils.canUseSnapshot(true, 1, 19_000, 22_000, 21_000));
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
        private final int rssnr;

        private LegacyStrength(int rssnr)
        {
            this.rssnr = rssnr;
        }

        private int getLteRssnr() { return rssnr; }
    }

    private static class FieldStrength
    {
        private final int mRssnr = 19;
    }

    private static class ParentFieldStrength
    {
        private final int mSinr = -5;
    }

    private static class InheritedFieldStrength extends ParentFieldStrength
    {
    }

    private static class InvalidFieldStrength
    {
        private final double mRssnr = 11.5;
        @Override public String toString() { return "rssnr=11"; }
    }

    private static class TextStrength
    {
        private final String text;
        private TextStrength(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }

    private static class ThrowingTextStrength
    {
        @Override public String toString() { throw new IllegalStateException("unavailable"); }
    }

    private static class NestedStrength
    {
        private final Object mLte = new FieldStrength();
    }

    private static class FlatLegacyStrength
    {
        private final int mLteRssnr = 18;
    }
}
