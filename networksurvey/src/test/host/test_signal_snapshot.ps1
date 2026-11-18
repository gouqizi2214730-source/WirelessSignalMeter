param([string]$SourceRevision)

$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$taskRelativeSource = 'networksurvey/src/main/java/com/craxiom/networksurvey/services/controller/CellularController.java'
if ($SourceRevision) {
    $taskSource = (& git -C $taskRoot show "${SourceRevision}:$taskRelativeSource") -join "`n"
    if ($LASTEXITCODE) { throw 'Cannot read source revision' }
} else {
    $taskSource = Get-Content -LiteralPath (Join-Path $taskRoot $taskRelativeSource) -Raw
}
$taskStart = $taskSource.IndexOf('    private SignalStrength getSignalStrengthForScan(')
$taskEnd = $taskSource.IndexOf('    private void registerScanCallbacks()', $taskStart)
if ($taskStart -lt 0 -or $taskEnd -le $taskStart) { throw 'Cannot locate production method' }
$taskMethod = $taskSource.Substring($taskStart, $taskEnd - $taskStart)
$taskOutput = Join-Path $taskRoot '.temp/signal-snapshot-host'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null

# Compile the production method unchanged, with fakes only at the Android/logging boundary.
$taskHarness = @'
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SignalSnapshotHostTest {
    private final AtomicBoolean signalStrengthReadFailed = new AtomicBoolean(false);
    private final Map<Integer, Object> displayInfoCallbackMap = new HashMap<>();

    static class SignalStrength {}
    static class Build {
        static class VERSION { static int SDK_INT = 33; }
        static class VERSION_CODES { static final int P = 28; }
    }
    static class TelephonyCallbackFactory {
        static SignalStrength cached;
        static Error error;
        static SignalStrength getSignalStrength(Object listener) {
            if (error != null) throw error;
            return cached;
        }
    }
    static class Manager {
        SignalStrength snapshot;
        RuntimeException exception;
        Error error;
        int reads;
        SignalStrength getSignalStrength() {
            reads++;
            if (exception != null) throw exception;
            if (error != null) throw error;
            return snapshot;
        }
    }
    static class TelephonyManagerWrapper {
        final Manager manager = new Manager();
        int getSubscriptionId() { return 7; }
        Manager getTelephonyManager() { return manager; }
    }
    static class AppLogger {
        static int warnings;
        static Throwable failure;
        static void w(String tag, String message, Throwable error) {
            warnings++;
            failure = error;
        }
    }

    /* PRODUCTION_METHOD */

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SignalSnapshotHostTest reader = new SignalSnapshotHostTest();
        TelephonyManagerWrapper wrapper = new TelephonyManagerWrapper();
        SignalStrength cached = new SignalStrength();
        TelephonyCallbackFactory.cached = cached;
        wrapper.manager.snapshot = new SignalStrength();
        check(reader.getSignalStrengthForScan(wrapper) == wrapper.manager.snapshot,
                "current subscription snapshot must win over callback cache");
        check(wrapper.manager.reads == 1, "read current snapshot even when callback is present");
        wrapper.manager.snapshot = null;
        check(reader.getSignalStrengthForScan(wrapper) == cached, "null direct read falls back to cache");
        TelephonyCallbackFactory.cached = null;
        check(reader.getSignalStrengthForScan(wrapper) == null, "missing snapshot");

        wrapper.manager.exception = new SecurityException("permission revoked");
        check(reader.getSignalStrengthForScan(wrapper) == null, "runtime failure must be optional");
        check(AppLogger.warnings == 1 && AppLogger.failure == wrapper.manager.exception, "persist failure");
        check(reader.getSignalStrengthForScan(wrapper) == null && AppLogger.warnings == 1, "deduplicate failure");

        TelephonyCallbackFactory.cached = cached;
        check(reader.getSignalStrengthForScan(wrapper) == cached, "direct failure preserves cached snapshot");
        TelephonyCallbackFactory.cached = null;
        wrapper.manager.exception = null;
        check(reader.getSignalStrengthForScan(wrapper) == null, "recover after failure");
        wrapper.manager.error = new NoSuchMethodError("vendor implementation");
        check(reader.getSignalStrengthForScan(wrapper) == null && AppLogger.warnings == 2, "linkage failure");
        wrapper.manager.error = null;
        reader.getSignalStrengthForScan(wrapper);

        TelephonyCallbackFactory.error = new NoClassDefFoundError("vendor cached snapshot");
        check(reader.getSignalStrengthForScan(wrapper) == null && AppLogger.warnings == 3, "cache linkage failure");
        wrapper.manager.snapshot = new SignalStrength();
        check(reader.getSignalStrengthForScan(wrapper) == wrapper.manager.snapshot,
                "broken cache must not block the current subscription read");
        wrapper.manager.snapshot = null;
        TelephonyCallbackFactory.error = null;

        wrapper.manager.error = new OutOfMemoryError("fatal error must propagate");
        try {
            reader.getSignalStrengthForScan(wrapper);
            throw new AssertionError("fatal VM error was swallowed");
        } catch (OutOfMemoryError expected) {
            check(expected == wrapper.manager.error, "original fatal error");
        }
        System.out.println("PASS: current snapshot priority, null/error cache fallback, missing, logging, recovery, linkage, fatal propagation");
    }
}
'@
$taskHarness = $taskHarness.Replace('    /* PRODUCTION_METHOD */', $taskMethod)
$taskJavaSource = Join-Path $taskOutput 'SignalSnapshotHostTest.java'
[IO.File]::WriteAllText($taskJavaSource, $taskHarness, [Text.UTF8Encoding]::new($false))
$taskJavaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else {
    'C:/Program Files/Android/Android Studio/jbr/bin'
}
& "$taskJavaBin/javac.exe" --release 17 -d $taskOutput $taskJavaSource
if ($LASTEXITCODE) { throw 'Snapshot host test compilation failed' }
& "$taskJavaBin/java.exe" -cp $taskOutput SignalSnapshotHostTest
if ($LASTEXITCODE) { throw 'Snapshot host regression failed' }
