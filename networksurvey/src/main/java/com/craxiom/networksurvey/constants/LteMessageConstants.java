package com.craxiom.networksurvey.constants;

import com.craxiom.messaging.LteBandwidth;

/**
 * The constants associated with the LTE table in the GeoPackage file.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 0.0.5
 */
public class LteMessageConstants extends CellularMessageConstants
{
    private LteMessageConstants()
    {
    }

    public static final String LTE_RECORD_MESSAGE_TYPE = "LteRecord";
    public static final String LTE_RECORDS_TABLE_NAME = "LTE_MESSAGE";

    public static final String MCC_COLUMN = "MCC";
    public static final String MNC_COLUMN = "MNC";
    public static final String PLMN_COLUMN = "plmn";
    public static final String TAC_COLUMN = "TAC";
    public static final String CI_COLUMN = "ECI";
    public static final String EARFCN_COLUMN = "DL_EARFCN";
    public static final String PCI_COLUMN = "Phys_Cell_ID";
    public static final String RSRP_COLUMN = "RSRP";
    public static final String RSRQ_COLUMN = "RSRQ";
    public static final String TA_COLUMN = "TA";
    public static final String BANDWIDTH_COLUMN = "DL_Bandwidth";
    public static final String CONNECTION_STATUS_COLUMN = "connectionStatus";
    public static final String CELL_BANDWIDTHS_KHZ_COLUMN = "cellBandwidthsKhz";

    /**
     * Given a Protocol Buffer defined LTE Bandwidth, return a user friendly string representation.
     *
     * @param lteBandwidth The LTE Bandwidth enum to convert.
     * @return The user friendly LTE Bandwidth, or an empty String if it is unknown/could not be converted.
     */
    public static String getLteBandwidth(LteBandwidth lteBandwidth)
    {
        final int khz = getLteBandwidthKhz(lteBandwidth);
        if (khz == 0) return "";
        return khz == 1_400 ? "1.4" : String.valueOf(khz / 1_000);
    }

    public static int getLteBandwidthKhz(LteBandwidth lteBandwidth)
    {
        return switch (lteBandwidth)
        {
            case MHZ_1_4 -> 1_400;
            case MHZ_3 -> 3_000;
            case MHZ_5 -> 5_000;
            case MHZ_10 -> 10_000;
            case MHZ_15 -> 15_000;
            case MHZ_20 -> 20_000;
            default -> 0;
        };
    }
}
