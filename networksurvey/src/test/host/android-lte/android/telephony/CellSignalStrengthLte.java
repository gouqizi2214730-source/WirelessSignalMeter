package android.telephony;

public class CellSignalStrengthLte
{
    private final int rssnr;

    public CellSignalStrengthLte(int rssnr)
    {
        this.rssnr = rssnr;
    }

    public int getRssnr()
    {
        return rssnr;
    }
}
