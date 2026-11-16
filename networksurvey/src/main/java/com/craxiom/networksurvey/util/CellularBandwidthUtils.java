package com.craxiom.networksurvey.util;

import android.telephony.ServiceState;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Helpers for working with the per-carrier cell bandwidth array reported by
 * {@link ServiceState#getCellBandwidths()} (values are in kHz). Used by the carrier aggregation
 * display on the cellular details screen.
 */
public final class CellularBandwidthUtils
{
    private CellularBandwidthUtils()
    {
    }

    /**
     * Filters a cell bandwidth array down to plausible entries. Some devices report 0 or the
     * framework "unavailable" sentinel for carriers they cannot measure; counting or summing those
     * would produce nonsense like "2 carriers, 0 MHz".
     *
     * @param cellBandwidthsKhz The bandwidths in kHz; may be null/empty.
     * @return The entries greater than 0 and less than Integer.MAX_VALUE, never null.
     */
    @NonNull
    public static int[] validBandwidthsKhz(int[] cellBandwidthsKhz)
    {
        if (cellBandwidthsKhz == null) return new int[0];
        int count = 0;
        for (int khz : cellBandwidthsKhz)
        {
            if (isValid(khz)) count++;
        }
        final int[] valid = new int[count];
        int i = 0;
        for (int khz : cellBandwidthsKhz)
        {
            if (isValid(khz)) valid[i++] = khz;
        }
        return valid;
    }

    /**
     * Sums the per-carrier bandwidths, ignoring invalid entries.
     *
     * @param cellBandwidthsKhz The bandwidths in kHz; may be null/empty.
     * @return The aggregate bandwidth in kHz, or 0 if there is nothing to sum.
     */
    public static int aggregateBandwidthKhz(int[] cellBandwidthsKhz)
    {
        int totalKhz = 0;
        for (int khz : validBandwidthsKhz(cellBandwidthsKhz)) totalKhz += khz;
        return totalKhz;
    }

    /**
     * Bandwidth of a single serving carrier when the scan also identifies only one active carrier.
     */
    public static int singleCarrierBandwidthKhz(int[] bandwidthsKhz)
    {
        return bandwidthsKhz != null && bandwidthsKhz.length == 1 && isValid(bandwidthsKhz[0])
                ? bandwidthsKhz[0] : 0;
    }

    /**
     * A confirmed NR cell identity takes precedence over the channel reported by ServiceState.
     * Some devices report different channel references for ServiceState and CellInfo.
     */
    public static boolean matchesPrimaryNrCell(long registeredNci, long scanNci,
                                                int reportedChannel, int scanChannel)
    {
        if (registeredNci >= 0 && scanNci >= 0) return registeredNci == scanNci;
        return scanChannel >= 0 && (reportedChannel < 0 || reportedChannel == scanChannel);
    }

    /**
     * A two-carrier snapshot can identify the secondary bandwidth once the anchor is known.
     * ServiceState does not guarantee that the anchor is the first entry.
     */
    public static int secondaryBandwidthKhz(int[] bandwidthsKhz, int anchorBandwidthKhz)
    {
        if (bandwidthsKhz == null || bandwidthsKhz.length != 2
                || !isValid(bandwidthsKhz[0]) || !isValid(bandwidthsKhz[1])) return 0;
        if (bandwidthsKhz[0] == anchorBandwidthKhz) return bandwidthsKhz[1];
        return bandwidthsKhz[1] == anchorBandwidthKhz ? bandwidthsKhz[0] : 0;
    }

    /**
     * Formats a kHz bandwidth as an MHz display value: "20" for 20000, "1.4" for 1400 (LTE's
     * 1.4 MHz carriers must not truncate to "1").
     *
     * @param khz The bandwidth in kHz.
     * @return The MHz value without a unit suffix.
     */
    @NonNull
    public static String formatBandwidthMhz(int khz)
    {
        if (khz % 1_000 == 0) return String.valueOf(khz / 1_000);
        return String.format(Locale.getDefault(), "%.1f", khz / 1_000.0);
    }

    private static boolean isValid(int khz)
    {
        return khz > 0 && khz < Integer.MAX_VALUE;
    }
}
