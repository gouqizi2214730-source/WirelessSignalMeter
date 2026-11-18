# Crash Compatibility Investigation (2026-11-18)

The user subsequently confirmed that LTE SINR works in the Network Survey-aligned
test APK. See [ANDROID-NO-ROOT-NETWORK-SURVEY.md](ANDROID-NO-ROOT-NETWORK-SURVEY.md)
for the current implementation and artifact. The sections below retain the earlier
investigation and test results; this feedback does not establish a complete crash
or map compatibility regression across devices.

The report was clarified to "4G crashes, 5G works." The user subsequently
provided the v1.2.0 exception chain: CellInfo callback, LTE processing, static
initialization, PatternSyntaxException at index 111, and
ExceptionInInitializerError. The production LTE regex reproduces that exact
syntax error under ICU. ADB has no connected devices, so behavior after the fix
still needs phone verification. The earlier map/native findings alone do not
explain this exception chain.

## Confirmed LTE Regex Initialization Defect

LteSinrUtils.LTE_BLOCK ended its lookahead with the unescaped literal
`}\s*$`. Host JDK Pattern accepted it, but Windows ICU 72.1.0.4 rejects the
same exported production pattern with `U_REGEX_RULE_SYNTAX at index 111`.
The Java source now uses `\\}\\s*$`, producing the escaped regex `\}\s*$`.

SurveyRecordProcessor calls LteSinrUtils while generating LTE records, even
when the primary SINR is valid and text fallback would not otherwise be needed.
The first static method call initializes both Pattern fields. The initialization
exception is an Error and escapes the processor's existing `catch (Exception)`.
This accounts for the supplied stack; the LTE utility need not be initialized
in an NR-only processing path.

The optional SignalStrength read guard below does not repair this initializer,
and neither earlier test APK in this document contains the regex correction.
Use the separately named ICU-fix APK when checking this report.

Regression coverage:

- `networksurvey/src/test/host/test_lte_regex_icu.ps1` compiles the actual utility,
  exports its Pattern strings and validates both with Windows ICU. The `03ecd62`
  source fails at index 111; the corrected source passes both patterns.
  This is an ICU host check, not execution on the phone's Android build.
- The JVM suite now has 23 passing tests, including closing-brace text with
  positive, zero and negative SINR. Existing NR exclusion and freshness
  checks still pass. JDK tests alone cannot detect this syntax difference.
- `LteSinrAndroidTest` adds class-initialization and LTE/NR text cases for
  Android's real Pattern implementation. It has not been run because no
  Android device or emulator is available.

## Latest ICU Fix APK Verification

APK: `released_apks/WirelessSignalMeter-1.2.0-lte-icu-fix-20261118.apk`.

SHA-256: `E6A2D3F25199DB3516B0B038D260E47E5644E3C0F990E6447361A870D1516E1B`.

- Release build succeeded in 4m 21s, exit 0; build log and exit code are
  `.temp/lte-icu-fix-build.log` / `.temp/lte-icu-fix-build.exit`.
- The final APK's classes.dex contains the escaped LTE terminator; the
  previous unescaped terminator is absent from all its DEX files.
- Signature, ZIP alignment and all eight native ELF alignment checks passed.
  The certificate SHA-256 remains
  `d1ffe5c42e44aea11ed6d56758418272767e26b955781ab08f9025bf99e2de84`.
- Package `com.wireless.signalmeter.new`, version `1.2.0` / `120002`,
  arm64, minSdk 33. The APK includes the earlier compatibility/guard changes,
  and does not include unmerged PR #4.
- The corresponding R8 mapping is retained alongside the APK as
  `WirelessSignalMeter-1.2.0-lte-icu-fix-20261118.mapping.txt`.
  Mapping SHA-256:
  `98BDD1CB83832085F353B7BA9031D07C3A0018EC5AA88DAEC911C60F401B08FC`.
- Host ICU and the 23 JVM cases passed. No Android instrumentation or phone
  verification was run. Full lint remains excluded; vendor R8 stack-map
  warnings remain in the successful build.

## Optional Signal Snapshot Failure

The modern asynchronous CellInfo callback supplemented each scan with a cached
SignalStrength snapshot or TelephonyManager.getSignalStrength(). An exception
from this optional read could escape before SurveyRecordProcessor handled the
CellInfo. The older polling paths had outer exception handlers.

CellularController.getSignalStrengthForScan now catches RuntimeException and
LinkageError at that boundary, persists the first consecutive failure through
AppLogger, and returns null so the existing CellInfo scan can continue. A
successful read resets warning suppression. Fatal VM errors still propagate.
This boundary is shared by LTE and NR and is not proof of an LTE-specific
vendor failure on the reported device.

The host regression in `networksurvey/src/test/host/test_signal_snapshot.ps1`
compiles the production method unchanged with fake Android/logging boundaries.
The `03ecd62` version fails with an injected SecurityException; the working version
passes cache precedence, direct/missing reads, runtime/linkage failures,
logging, suppression, recovery, and fatal error propagation. This is evidence
of the exception escape and protection, not a reproduction of the phone crash.

The modern callback also records the latest scan's elapsed realtime,
subscription ID, LTE/NR counts and total CellInfo count in memory. AppLogger
adds that context to uncaught exceptions without collecting cell identities or
location. It describes the latest received scan, not necessarily the failing
thread's SIM or the phone's selected network mode.

## Native Libraries

The previous APK, `WirelessSignalMeter-1.2.0-all-prs-sinr-20261118.apk`, contains
four arm64 libraries with 4 KB ELF LOAD alignment:

- libBaiduMapSDK_base_v7_6_4.so
- libBaiduMapSDK_map_v7_6_4.so
- libc++_shared.so
- libtiny_magic.so

Its successful `zipalign -c -P 16 4` check did not establish ELF compatibility.
The new verification script rejects that APK. The other four libraries in
that APK have 16 KB compatible LOAD segments.

Baidu map/base 7.6.5.1 was inspected first. Its libtiny_magic.so still has
4 KB LOAD alignment, so that release was not used. Map/base 7.6.7 has 16 KB
LOAD alignment in all four arm64 libraries. Its matching common dependency is
1.0.42 (AAR). See `networksurvey/libs/README.md` for vendor artifact checksums.

```powershell
.\tools\check-apk-native-alignment.ps1 -ApkPath <assembled-apk>
```

The script checks every arm64/x86_64 native library, both LOAD alignment and
file-offset/virtual-address congruence. It fails if there are no libraries.
APK ZIP alignment and APK signature must be checked separately.

## Map Initialization and Lifetime

Previously Application initialized the SDK in a background thread, set privacy
agreement after initialize, and caught failures only in Timber. Three map
surfaces independently created TextureMapView without waiting for initialization.

All three now use BaiduMapView. It initializes on the UI thread before view
construction, sets the existing privacy flag first, and checks initialization
completion. Initialization, construction, and lifecycle Java/linker failures
are persisted through AppLogger and show a localized unavailable message.
The view is released once through AndroidView.onRelease; screen references are
cleared before destruction. Late tower map callbacks ignore released views.

This does not intercept native signals or guarantee that vendor rendering works
on every GPU. Entering a map for the first time can briefly wait for SDK loading.

## Diagnostics

AppLogger is installed before preference migration. It preserves the previous
uncaught exception handler, uses a thread-safe time formatter and serialized
file writes, and uses the standard stack formatter (including suppressed
exceptions and cause-cycle handling). On restart, available Android exit
metadata for the most recent crash/native crash/ANR/initialization failure is
written locally. Native tombstones are not collected.

The device-info helper now includes ABI and memory page size. However, the
current main UI has no reachable UploadLogDialog entry, so do not instruct
users to upload from Settings. The private app log remains under
`files/logs/app.log`; a normal release installation does not expose that file
through `run-as`. Use ADB logcat for device collection as described in
`TESTING.md`. No logs were uploaded during this investigation.

## Earlier LTE Guard APK Verification

APK: `released_apks/WirelessSignalMeter-1.2.0-lte-crash-guard-20261118.apk`.

SHA-256: `D453788415BCE0577C07D6AD020E47C9C6C99711ABCEF1A0F000570F223723F9`.

- Release build succeeded in 5m 14s, exit code 0; log and exit file:
  `.temp/lte-crash-guard-build.log` / `.temp/lte-crash-guard-build.exit`.
- All eight native library ELF checks, APK signature verification, and
  `zipalign -c -P 16 4` passed. The signing certificate matches the previous APK.
- Package/version remain `com.wireless.signalmeter.new`, `1.2.0`, `120002`;
  arm64, minSdk 33. This APK includes the earlier map/native compatibility work.
- The signal snapshot host regression and existing 22 LTE SINR JVM tests
  passed. The old method fails under injected SecurityException.
- No device is connected. Full lint, Android/Robolectric/UI tests and the
  reported phone's LTE/5G behavior remain unverified. The same lintVital
  exclusions and vendor R8 stack-map warnings apply as to the earlier build.

## Earlier Map/Native Verification Results

Final APK: `released_apks/WirelessSignalMeter-1.2.0-crash-compat-20261118.apk`.

SHA-256: `93B5ADCC42A18677FFCC0D8F92D41BF49144B99299245A2DFF5B708BB86EB08D`.

- Final Release build succeeded in 4m 48s, exit code 0.
- Build log: `.temp/crash-compat-final-build.log`; exit file with the same stem.
- The final APK passed all eight ELF library checks, signature verification,
  and `zipalign -c -P 16 4`. The signing certificate matches the previous APK.
- Package/version remain `com.wireless.signalmeter.new`, `1.2.0`, `120002`;
  arm64 only, minimum Android 13.
- Existing 22 LTE SINR, six road replay, and five band-lock parameter host
  regression tests passed. These are not map rendering or lifecycle tests.
- Full lint and Android/Robolectric/UI tests were not run. The existing
  lintVital task exclusions were retained for this build.
- Vendor common 1.0.42 still emits R8 stack-map warnings; the build completed
  with the existing vendor keep rules.

## Device Verification Still Required

- Request model, Android version, app APK/build, and exact crash trigger.
- Compare LTE cold launch, LTE/5G transitions, stop/start scanning, and dual-SIM
  changes. On LTE, exercise both idle and active traffic; note the page open
  at the time and whether Root DIAG is active.
- If SignalStrength fails, verify that CellInfo measurements keep updating;
  missing optional SINR must remain unavailable rather than crash or freeze.
- Test cold launch, immediate map entry, tower map, road replay, and Watchlist.
- Repeat map entry/exit, background/foreground, screen lock, and rotation.
- Test both 4 KB and 16 KB devices and check map authentication/tiles/overlays.
- If a crash remains, provide ADB logcat, model/Android version, APK/build and
  the steps that triggered it. The release UI currently has no log export or
  upload entry.
