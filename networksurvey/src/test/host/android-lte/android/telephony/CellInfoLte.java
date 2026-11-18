package android.telephony;

public class CellInfoLte extends CellInfo
{
    public boolean registered = true;
    public int connectionStatus = CONNECTION_PRIMARY_SERVING;
    public long timestamp = 19_000;
    private final CellSignalStrengthLte strength;

    public CellInfoLte(int rssnr)
    {
        strength = new CellSignalStrengthLte(rssnr);
    }

    public CellSignalStrengthLte getCellSignalStrength() { return strength; }
    public int getCellConnectionStatus() { return connectionStatus; }
    public boolean isRegistered() { return registered; }
    public long getTimestampMillis() { return timestamp; }
}
