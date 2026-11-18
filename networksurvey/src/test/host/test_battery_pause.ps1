param([string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr')

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = Join-Path $repo '.temp/battery-pause-host'
$json = Join-Path $JavaHome '../plugins/grazie/lib/org.json-json.jar'
foreach ($directory in @('android/os', 'timber/log')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $target $directory) | Out-Null
}

# Only Android Parcel storage and the logging boundary are replaced.
$parcel = @'
package android.os;
public class Parcel {
    private final java.util.List<Byte> bytes = new java.util.ArrayList<>();
    private int position;
    public void writeByte(byte value) { bytes.add(value); }
    public byte readByte() { return bytes.get(position++); }
}
'@
$parcelable = @'
package android.os;
public interface Parcelable {
    int describeContents();
    void writeToParcel(Parcel dest, int flags);
    interface Creator<T> {
        T createFromParcel(Parcel in);
        T[] newArray(int size);
    }
}
'@
$timber = @'
package timber.log;
public class Timber {
    public static void e(Throwable error, String message) {}
}
'@
$harness = @'
import android.os.Parcel;
import com.craxiom.networksurvey.model.BatteryPauseState;
import java.lang.reflect.Method;
import org.json.JSONObject;

public class BatteryPauseHostTest {
    private static final String[] NAMES = {"CellularScanningActive", "WifiScanningActive",
        "BluetoothScanningActive", "GnssScanningActive", "CdrScanningActive",
        "CellularLoggingEnabled", "WifiLoggingEnabled", "BluetoothLoggingEnabled",
        "GnssLoggingEnabled", "CdrLoggingEnabled", "DeviceStatusActive"};
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static boolean get(BatteryPauseState state, String name) throws Exception {
        return (Boolean) BatteryPauseState.class.getMethod("was" + name).invoke(state);
    }
    public static void main(String[] args) throws Exception {
        for (String name : NAMES) {
            BatteryPauseState state = new BatteryPauseState();
            check(!state.hasActiveOperations(), "default");
            Method setter = BatteryPauseState.class.getMethod("setWas" + name, boolean.class);
            setter.invoke(state, true);
            check(state.hasActiveOperations(), name + " active");
            BatteryPauseState fromJson = BatteryPauseState.fromJson(state.toJson());
            Parcel parcel = new Parcel();
            state.writeToParcel(parcel, 0);
            BatteryPauseState fromParcel = BatteryPauseState.CREATOR.createFromParcel(parcel);
            for (String field : NAMES) {
                boolean expected = field.equals(name);
                check(get(fromJson, field) == expected, name + " JSON " + field);
                check(get(fromParcel, field) == expected, name + " Parcel " + field);
            }
            state.clear();
            check(!state.hasActiveOperations(), name + " clear");
            check(!get(state, name), name + " field clear");
        }
        JSONObject old = new JSONObject();
        old.put("cdr_logging", true);
        old.put("device_status", true);
        old.put("mqtt_connection", true);
        old.put("grpc_connection", true);
        BatteryPauseState upgraded = BatteryPauseState.fromJson(old.toString());
        check(upgraded.wasCdrLoggingEnabled() && upgraded.wasDeviceStatusActive(), "old local state");
        check(!new JSONObject(upgraded.toJson()).has("mqtt_connection"), "obsolete state ignored");
        check(!BatteryPauseState.fromJson(null).hasActiveOperations(), "missing JSON");
        check(!BatteryPauseState.fromJson("{bad").hasActiveOperations(), "invalid JSON");
        System.out.println("PASS: all 11 local battery pause flags; JSON/Parcel round trips, clear, legacy JSON");
        System.out.println("Host Parcel substitute; not Android instrumentation");
    }
}
'@
foreach ($entry in @{
    'android/os/Parcel.java' = $parcel
    'android/os/Parcelable.java' = $parcelable
    'timber/log/Timber.java' = $timber
    'BatteryPauseHostTest.java' = $harness
}.GetEnumerator()) {
    [IO.File]::WriteAllText((Join-Path $target $entry.Key), $entry.Value)
}
$sources = @(Get-ChildItem -LiteralPath $target -Recurse -Filter '*.java' -File |
    Select-Object -ExpandProperty FullName)
$sources += Join-Path $repo 'networksurvey/src/main/java/com/craxiom/networksurvey/model/BatteryPauseState.java'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $json -d $target $sources
if ($LASTEXITCODE) { throw 'Battery pause host compilation failed' }
& "$JavaHome/bin/java.exe" -cp "$target$([IO.Path]::PathSeparator)$json" BatteryPauseHostTest
if ($LASTEXITCODE) { throw 'Battery pause host checks failed' }
