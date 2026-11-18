package com.craxiom.networksurvey.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads LTE SINR in dB without replacing a valid CellInfo measurement with a signal snapshot.
 * Vendor accessors are optional: missing or restricted methods leave the measurement unavailable.
 */
public final class LteSinrUtils
{
    private static final int UNAVAILABLE = Integer.MAX_VALUE;
    private static final String[] VENDOR_GETTERS = {"getCellSinr", "getSnr", "getSinr"};
    private static final String[] LTE_FIELDS = {"mRssnr", "rssnr", "mCellSinr", "cellSinr",
            "mSinr", "sinr", "mSnr", "snr"};
    private static final Pattern LTE_VALUE = Pattern.compile(
            "(?:^|[\\s{,;:])(?:m?rssnr|m?cellSinr|m?sinr|m?snr)\\s*=\\s*([+-]?\\d+)(?=$|[\\s},;])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LTE_BLOCK = Pattern.compile(
            "\\bmLte\\s*=\\s*CellSignalStrengthLte\\s*:\\s*(.*?)(?=[\\s,;]+m(?:Cdma|Gsm|Wcdma|Tdscdma|Nr)\\s*=|[\\s,;]+primary\\s*=|}\\s*$|$)");
    private static final long MAX_SNAPSHOT_AGE_MS = 10_000;
    private static final long MAX_SAMPLE_GAP_MS = 5_000;

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
        for (String field : LTE_FIELDS)
        {
            int sinr = asInt(readField(lteSignalStrength, field));
            if (isValid(sinr)) return sinr;
        }
        return readText(safeToString(lteSignalStrength));
    }

    /**
     * A snapshot has no PCI. Borrow it only for a single LTE primary sampled nearby in time.
     * RSRP is deliberately not an identity check: separate modem indications can differ by a dB.
     */
    public static boolean canUseSnapshot(boolean primaryServing, int eligibleLteCells,
                                         long cellTimestampMs, long signalTimestampMs, long nowMs)
    {
        return primaryServing && eligibleLteCells == 1
                && isRecent(cellTimestampMs, nowMs) && isRecent(signalTimestampMs, nowMs)
                && Math.abs(cellTimestampMs - signalTimestampMs) <= MAX_SAMPLE_GAP_MS;
    }

    private static boolean isRecent(long timestampMs, long nowMs)
    {
        return timestampMs > 0 && timestampMs <= nowMs
                && nowMs - timestampMs <= MAX_SNAPSHOT_AGE_MS;
    }

    /**
     * The caller must enforce canUseSnapshot and reject snapshots containing multiple LTE samples.
     * Modern nested LTE values are in dB. Old flat mLteRssnr fields can be in tenths, so skip them.
     */
    public static int readSnapshot(Object signalStrength)
    {
        int sinr = readInt(signalStrength, "getLteRssnr");
        if (isValid(sinr)) return sinr;

        Object lte = readField(signalStrength, "mLte");
        if (lte != null)
        {
            sinr = read(readInt(lte, "getRssnr"), lte);
            if (isValid(sinr)) return sinr;
        }
        Matcher block = LTE_BLOCK.matcher(safeToString(signalStrength));
        return block.find() ? readText(block.group(1)) : UNAVAILABLE;
    }

    private static int readText(String text)
    {
        Matcher matcher = LTE_VALUE.matcher(text);
        while (matcher.find())
        {
            try
            {
                int sinr = Integer.parseInt(matcher.group(1));
                if (isValid(sinr)) return sinr;
            } catch (NumberFormatException ignored)
            {
            }
        }
        return UNAVAILABLE;
    }

    private static String safeToString(Object source)
    {
        if (source == null) return "";
        try
        {
            String text = source.toString();
            return text == null ? "" : text;
        } catch (RuntimeException | LinkageError ignored)
        {
            return "";
        }
    }

    private static Object readField(Object source, String name)
    {
        if (source == null) return null;
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException ignored)
            {
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
            {
                return null;
            }
        }
        return null;
    }

    private static int asInt(Object result)
    {
        if (result instanceof Number)
        {
            double value = ((Number) result).doubleValue();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value))
            {
                return (int) value;
            }
        }
        return UNAVAILABLE;
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
                return asInt(method.invoke(source));
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
