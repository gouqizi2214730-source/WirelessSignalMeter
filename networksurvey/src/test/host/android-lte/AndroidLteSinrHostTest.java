import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.SignalStrength;
import com.craxiom.networksurvey.util.AndroidLteSinrReader;
import java.util.List;

public class AndroidLteSinrHostTest
{
    private static final int NA = Integer.MAX_VALUE;
    private static int checks;

    private static void check(boolean ok, String message)
    {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static SignalStrength snapshot(int value)
    {
        return new SignalStrength(List.of(new CellSignalStrengthLte(value)));
    }

    private static int read(CellInfoLte cell, SignalStrength snapshot, int eligible)
    {
        return AndroidLteSinrReader.read(cell, snapshot, 7, eligible, 21_000);
    }

    private static AndroidLteSinrReader.Sample last()
    {
        var samples = AndroidLteSinrReader.snapshot();
        return samples.get(samples.size() - 1);
    }

    public static void main(String[] args)
    {
        AndroidLteSinrReader.clear();
        CellInfoLte missing = new CellInfoLte(NA);
        check(read(missing, snapshot(17), 1) == 17, "subscription snapshot supplements missing CellInfo");
        check(last().snapshotDecision().equals("accepted") &&
                last().snapshotRssnr().equals(List.of(17)) &&
                last().cellRssnr() == NA, "record both raw sources and decision");
        check(read(missing, snapshot(0), 1) == 0, "zero is valid");
        check(read(missing, snapshot(-9), 1) == -9, "negative dB is valid");
        check(read(new CellInfoLte(0), snapshot(17), 1) == 17, "CellInfo zero cannot mask subscription RSSNR");
        check(read(new CellInfoLte(1), snapshot(17), 1) == 17, "CellInfo one cannot mask subscription RSSNR");
        check(read(new CellInfoLte(8), snapshot(17), 1) == 17, "subscription RSSNR always has priority");
        check(read(new CellInfoLte(8), snapshot(0), 1) == 0, "real subscription zero overrides CellInfo");
        check(read(missing, snapshot(180), 1) == 180, "match upstream raw value without guessing units");
        check(last().snapshotRssnr().equals(List.of(180)) &&
                last().selectedSinr() == 180, "retain out-of-range public raw measurement unchanged");
        check(read(missing, snapshot(Integer.MIN_VALUE), 1) == NA, "MIN_VALUE is unavailable");
        check(read(missing, snapshot(NA), 1) == NA &&
                last().snapshotDecision().equals("snapshot_no_sinr"), "MAX_VALUE is unavailable");
        check(read(missing, null, 1) == NA && last().snapshotDecision().equals("no_snapshot"), "no snapshot");
        check(read(new CellInfoLte(8), null, 1) == 8 &&
                last().selectedSource().equals("cell_info"), "CellInfo remains an optional compatibility fallback");

        SignalStrength stale = snapshot(17);
        stale.timestamp = 5_000;
        check(read(missing, stale, 1) == 17 &&
                last().snapshotTimestampMs() == 5_000, "upstream does not gate direct reads by snapshot age");
        SignalStrength distant = snapshot(17);
        distant.timestamp = 13_000;
        check(read(missing, distant, 1) == 17, "asynchronous modem timestamps do not discard RSSNR");
        CellInfoLte untimed = new CellInfoLte(NA);
        untimed.timestamp = 0;
        check(read(untimed, snapshot(17), 1) == 17, "missing CellInfo timestamp does not discard RSSNR");
        untimed.timestamp = 25_000;
        check(read(untimed, snapshot(17), 1) == 17, "future CellInfo timestamp is diagnostic only");
        check(read(missing, snapshot(17), 2) == 17 &&
                last().eligibleCells() == 2, "LTE cell count is diagnostic only, like upstream");
        SignalStrength multiple = new SignalStrength(List.of(
                new CellSignalStrengthLte(7), new CellSignalStrengthLte(17)));
        check(read(missing, multiple, 1) == 7 &&
                last().snapshotRssnr().equals(List.of(7, 17)), "first LTE measurement wins, like upstream");
        SignalStrength missingFirst = new SignalStrength(List.of(
                new CellSignalStrengthLte(NA), new CellSignalStrengthLte(17)));
        check(read(missing, missingFirst, 1) == NA, "do not replace a missing first LTE entry with a later one");
        check(read(new CellInfoLte(8), new SignalStrength(List.of()), 1) == 8,
                "empty LTE snapshot preserves CellInfo compatibility fallback");
        SignalStrength vendor = new SignalStrength(List.of(new CellSignalStrengthLte(NA)))
        {
            private int getLteRssnr() { return 14; }
        };
        check(read(missing, vendor, 1) == 14 &&
                last().selectedSource().equals("signal_strength_compat"), "vendor compatibility follows missing public RSSNR");
        SignalStrength publicAndVendor = new SignalStrength(List.of(new CellSignalStrengthLte(17)))
        {
            private int getLteRssnr() { return 14; }
        };
        check(read(missing, publicAndVendor, 1) == 17 &&
                last().selectedSource().equals("signal_strength"), "vendor value cannot override public RSSNR");

        CellInfoLte secondary = new CellInfoLte(NA);
        secondary.connectionStatus = CellInfo.CONNECTION_SECONDARY_SERVING;
        check(read(secondary, snapshot(17), 1) == NA &&
                last().snapshotDecision().equals("not_primary"), "CA secondary cannot borrow primary");
        CellInfoLte neighbor = new CellInfoLte(NA);
        neighbor.registered = false;
        int previousCount = AndroidLteSinrReader.snapshot().size();
        check(read(neighbor, snapshot(17), 1) == NA &&
                AndroidLteSinrReader.snapshot().size() == previousCount, "neighbor cannot borrow or flood diagnostics");
        CellInfoLte measuredNeighbor = new CellInfoLte(6);
        measuredNeighbor.registered = false;
        check(read(measuredNeighbor, snapshot(17), 1) == 6, "neighbor's own value is not overwritten");

        SignalStrength badTimestamp = snapshot(17);
        badTimestamp.timestampFailure = new SecurityException("vendor timestamp");
        check(read(new CellInfoLte(8), badTimestamp, 1) == 17 &&
                last().selectedSource().equals("signal_strength"), "diagnostic timestamp failure preserves public RSSNR");

        SignalStrength failing = snapshot(17);
        failing.failure = new SecurityException("vendor error");
        check(read(new CellInfoLte(8), failing, 1) == 8, "snapshot exception preserves valid CellInfo");
        check(last().snapshotError().equals("SecurityException"), "export exception class");
        check(read(missing, failing, 1) == NA, "snapshot exception preserves unavailable state");
        failing.failure = null;
        failing.linkageFailure = new NoSuchMethodError("vendor method");
        check(read(new CellInfoLte(0), failing, 1) == 0, "linkage error preserves valid zero");
        failing.linkageFailure = null;
        failing.fatal = new OutOfMemoryError("fatal");
        try
        {
            read(missing, failing, 1);
            throw new AssertionError("fatal VM error swallowed");
        } catch (OutOfMemoryError expected)
        {
            check(expected == failing.fatal, "fatal VM error propagates");
        }

        AndroidLteSinrReader.read(missing, snapshot(17), 7, 1, 21_000, "task-a", "clock");
        var capturedTask = last();
        AndroidLteSinrReader.read(missing, snapshot(18), 7, 1, 22_000, "task-b", "clock");
        check(capturedTask.taskId().equals("task-a") && last().taskId().equals("task-b"),
                "diagnostics retain task captured at sample time");
        check(capturedTask.selectedSinr() == 17 && last().selectedSinr() == 18 &&
                last().clockId().equals("clock"), "task metadata does not alter SINR selection");
        for (int i = 0; i < 140; i++)
            AndroidLteSinrReader.read(missing, snapshot(17), i % 2, 1, 21_000);
        List<AndroidLteSinrReader.Sample> captured = AndroidLteSinrReader.snapshot();
        check(captured.size() == 128 && captured.get(126).subscriptionId() == 0 &&
                captured.get(127).subscriptionId() == 1, "bounded diagnostics retain separate subscriptions");
        AndroidLteSinrReader.clear();
        check(captured.size() == 128 && AndroidLteSinrReader.snapshot().isEmpty(), "export snapshot survives clear");
        System.out.println("PASS: " + checks + " Android LTE SINR host checks");
    }
}
