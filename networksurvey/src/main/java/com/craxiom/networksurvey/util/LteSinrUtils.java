package com.craxiom.networksurvey.util;

import java.lang.reflect.Method;

/**
 * Reads LTE SINR in dB without replacing a valid CellInfo measurement with a signal snapshot.
 * Vendor accessors are optional: missing or restricted methods leave the measurement unavailable.
 */
public final class LteSinrUtils
{
    private static final int UNAVAILABLE = Integer.MAX_VALUE;
    private static final String[] VENDOR_GETTERS = {"getCellSinr", "getSnr", "getSinr"};

    private LteSinrUtils()
    {
    }

    public static boolean isValid(int sinr)
    {
        return sinr >= -20 && sinr <= 30;
    }

    public static int read(int rssnr, Object lteSignalStrength)
    {
        if (isValid(rssnr)) return rssnr;

        for (String getter : VENDOR_GETTERS)
        {
            int sinr = readInt(lteSignalStrength, getter);
            if (isValid(sinr)) return sinr;
        }
        return UNAVAILABLE;
    }

    /**
     * The legacy snapshot has no PCI, so require its LTE RSRP to agree with the serving cell.
     * The caller must also restrict this path to a registered cell and at most one LTE sample.
     */
    public static int readLegacy(Object signalStrength, int servingRsrp)
    {
        if (servingRsrp < -140 || servingRsrp > -43
                || readInt(signalStrength, "getLteRsrp") != servingRsrp)
        {
            return UNAVAILABLE;
        }
        int sinr = readInt(signalStrength, "getLteRssnr");
        return isValid(sinr) ? sinr : UNAVAILABLE;
    }

    private static int readInt(Object source, String getter)
    {
        if (source == null) return UNAVAILABLE;

        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Method method = type.getDeclaredMethod(getter);
                method.setAccessible(true);
                Object result = method.invoke(source);
                if (result instanceof Number)
                {
                    double value = ((Number) result).doubleValue();
                    // Reject fractional values and overflows rather than guessing vendor units.
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value))
                    {
                        return (int) value;
                    }
                }
                return UNAVAILABLE;
            } catch (NoSuchMethodException ignored)
            {
                // Some vendor accessors are inherited from a hidden superclass.
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
            {
                return UNAVAILABLE;
            }
        }
        return UNAVAILABLE;
    }
}
