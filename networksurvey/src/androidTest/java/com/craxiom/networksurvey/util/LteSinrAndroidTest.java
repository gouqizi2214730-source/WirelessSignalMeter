package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs with Android's ICU-backed Pattern implementation, rather than the host JDK regex engine.
 */
@RunWith(AndroidJUnit4.class)
public class LteSinrAndroidTest
{
    @Test
    public void lteUtilityInitializesOnAndroid()
    {
        assertTrue(LteSinrUtils.isValid(0));
        assertEquals(7, LteSinrUtils.read(7, null));
    }

    @Test
    public void snapshotTextHandlesClosingBraceAndKeepsNrSeparate()
    {
        assertEquals(13, LteSinrUtils.readSnapshot(text(
                "SignalStrength:{mLte=CellSignalStrengthLte: rsrp=-95 rssnr=13}")));
        assertEquals(0, LteSinrUtils.readSnapshot(text(
                "SignalStrength:{mLte=CellSignalStrengthLte: rssnr=0}  ")));
        assertEquals(Integer.MAX_VALUE, LteSinrUtils.readSnapshot(text(
                "SignalStrength:{mLte=CellSignalStrengthLte: rssnr=2147483647 "
                        + "mNr=CellSignalStrengthNr: sinr=25}")));
    }

    private static Object text(String value)
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return value;
            }
        };
    }
}
