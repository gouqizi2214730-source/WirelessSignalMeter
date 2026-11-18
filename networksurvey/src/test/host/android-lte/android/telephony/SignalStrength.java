package android.telephony;

import java.util.List;

public class SignalStrength
{
    public long timestamp = 20_000;
    public RuntimeException failure;
    public LinkageError linkageFailure;
    public Error fatal;
    public RuntimeException timestampFailure;
    private final List<CellSignalStrengthLte> strengths;

    public SignalStrength(List<CellSignalStrengthLte> strengths) { this.strengths = strengths; }
    public long getTimestampMillis()
    {
        if (timestampFailure != null) throw timestampFailure;
        return timestamp;
    }

    public <T> List<T> getCellSignalStrengths(Class<T> type)
    {
        if (failure != null) throw failure;
        if (linkageFailure != null) throw linkageFailure;
        if (fatal != null) throw fatal;
        return strengths.stream().map(type::cast).toList();
    }
}
