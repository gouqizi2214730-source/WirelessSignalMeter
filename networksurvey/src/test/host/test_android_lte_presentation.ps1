param([string]$SourceRevision)

$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$taskOutput = Join-Path $taskRoot '.temp/android-lte-presentation-host'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskJavaBin = 'C:/Program Files/Android/Android Studio/jbr/bin'
$taskCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'

function Find-TaskJar([string]$module, [string]$version = '') {
    $taskDirectory = Join-Path $taskCache $module
    if ($version) { $taskDirectory = Join-Path $taskDirectory $version }
    $taskJar = Get-ChildItem -LiteralPath $taskDirectory -Recurse -Filter '*.jar' -File |
        Where-Object Name -NotMatch 'sources|javadoc' |
        Select-Object -First 1 -ExpandProperty FullName
    if (!$taskJar) { throw "Missing cached JAR: $module $version" }
    return $taskJar
}

function Read-TaskSource([string]$relativePath) {
    if (!$SourceRevision) { return Get-Content -LiteralPath (Join-Path $taskRoot $relativePath) -Raw }
    $taskText = (& git -C $taskRoot show "${SourceRevision}:$relativePath") -join "`n"
    if ($LASTEXITCODE) { throw "Cannot read $relativePath at $SourceRevision" }
    return $taskText
}

function Select-TaskMethod([string]$text, [string]$start, [string]$end) {
    $taskStart = $text.IndexOf($start)
    $taskEnd = $text.IndexOf($end, [Math]::Max(0, $taskStart))
    if ($taskStart -lt 0 -or $taskEnd -le $taskStart) { throw "Cannot locate production method: $start" }
    return $text.Substring($taskStart, $taskEnd - $taskStart)
}

$taskScreen = Read-TaskSource 'networksurvey/src/main/java/com/craxiom/networksurvey/ui/rootdiag/RootSignalScreen.kt'
$taskMerge = Select-TaskMethod $taskScreen 'private fun LteRecordData.withDiag(' 'private fun NrRecordData.matches('
$taskNumberStart = $taskScreen.IndexOf('private fun JSONObject.number(key: String)')
if ($taskNumberStart -lt 0) { throw 'Cannot locate production numeric JSON reader' }
$taskNumberEnd = $taskScreen.IndexOf("`n", $taskNumberStart)
$taskNumber = $taskScreen.Substring($taskNumberStart, $taskNumberEnd - $taskNumberStart)
# The optional old-screen comparison still uses today's diagnostic serializer.
$taskViewModel = Get-Content -LiteralPath (Join-Path $taskRoot 'networksurvey/src/main/java/com/craxiom/networksurvey/ui/rootdiag/RootDiagViewModel.kt') -Raw
$taskDiagnostic = Select-TaskMethod $taskViewModel 'private fun AndroidLteSinrReader.Sample.toDiagnosticJson()' 'private val excludedDiagnosticKeys'

$taskHarness = @'
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrengthLte
import android.telephony.SignalStrength
import com.craxiom.messaging.LteRecordData
import com.google.protobuf.FloatValue
import com.craxiom.networksurvey.util.AndroidLteSinrReader
import com.craxiom.networksurvey.util.LteSinrUtils
import org.json.JSONObject
import org.json.JSONArray

// These unrelated band conversions are not exercised; JSON, protobuf and SINR code are real.
private object CellularUtils { fun downlinkEarfcnToBand(value: Int) = 0 }
private object LteMessageConstants { fun getLteBandwidth(value: Any) = "" }

/* MERGE */
/* NUMBER */
/* DIAGNOSTIC */

fun main() {
    var checks = 0
    fun verify(ok: Boolean, description: String) {
        checks++
        check(ok) { description }
    }
    for (value in listOf(17, 0, -9, 180)) {
        val selected = AndroidLteSinrReader.read(CellInfoLte(0),
            SignalStrength(listOf(CellSignalStrengthLte(value))), 7, 2, 21_000)
        val record = LteRecordData.newBuilder()
            .setSnr(FloatValue.of(selected.toFloat())).build()
        val displayed = record.withDiag(null)
        verify(displayed.number("sinr") == value.toDouble(), "measurement reaches displayed JSON: $value")
        verify(displayed.getString("sinr_src") == "Android RSSNR", "display source: $value")
        val exported = AndroidLteSinrReader.snapshot().last().toDiagnosticJson()
        verify(exported.number("selected_sinr_db") == value.toDouble(), "measurement reaches export: $value")
        verify(exported.getString("selected_source") == "signal_strength", "export source: $value")
        verify(exported.getJSONArray("snapshot_rssnr_raw").getInt(0) == value, "raw value survives export: $value")
    }
    val unavailable = AndroidLteSinrReader.read(CellInfoLte(Int.MAX_VALUE),
        SignalStrength(listOf(CellSignalStrengthLte(Int.MAX_VALUE))), 9, 1, 21_000)
    verify(!LteSinrUtils.isReported(unavailable), "missing value remains missing")
    verify(LteRecordData.getDefaultInstance().withDiag(null).number("sinr") == null,
        "missing protobuf field is not displayed as zero")
    val missingExport = AndroidLteSinrReader.snapshot().last().toDiagnosticJson()
    verify(missingExport.isNull("selected_sinr_db"), "missing measurement exports JSON null")
    verify(missingExport.getInt("subscription_id") == 9, "export keeps subscription association")
    verify(!missingExport.has("cid") && !missingExport.has("pci") && !missingExport.has("latitude"),
        "export does not add cell identity or location")
    val measured = LteRecordData.newBuilder().setSnr(FloatValue.of(17f)).build()
    verify(measured.withDiag(JSONObject().put("sinr", 11)).number("sinr") == 11.0,
        "existing DIAG measurement retains priority")
    for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
        verify(LteRecordData.newBuilder().setSnr(FloatValue.of(value)).build()
            .withDiag(null).number("sinr") == null, "non-finite values cannot reach displayed JSON")
    }
    println("PASS: $checks Android LTE record/display/export host checks")
}
'@
$taskHarness = $taskHarness.Replace('/* MERGE */', $taskMerge).
    Replace('/* NUMBER */', $taskNumber).Replace('/* DIAGNOSTIC */', $taskDiagnostic)
$taskSource = Join-Path $taskOutput 'AndroidLtePresentationHost.kt'
[IO.File]::WriteAllText($taskSource, $taskHarness, [Text.UTF8Encoding]::new($false))

$taskStdlib = Find-TaskJar 'org.jetbrains.kotlin/kotlin-stdlib' '2.4.10'
$taskAnnotations = Find-TaskJar 'org.jetbrains/annotations' '26.0.2-1'
$taskCompiler = @(
    (Find-TaskJar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' '2.4.10'),
    $taskStdlib, $taskAnnotations,
    (Find-TaskJar 'org.jetbrains.kotlin/kotlin-daemon-embeddable' '2.4.10'),
    (Find-TaskJar 'org.jetbrains.kotlin/kotlin-reflect' '1.6.10'),
    (Find-TaskJar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm' '1.10.2')
) -join [IO.Path]::PathSeparator
$taskClasspath = @(
    $taskOutput, $taskStdlib, $taskAnnotations,
    (Find-TaskJar 'com.craxiom/network-survey-messaging' '2.6.0'),
    (Find-TaskJar 'com.google.protobuf/protobuf-java' '4.35.1'),
    'C:/Program Files/Android/Android Studio/plugins/grazie/lib/org.json-json.jar'
) -join [IO.Path]::PathSeparator
$taskJavaSources = @(
    (Join-Path $taskRoot 'networksurvey/src/main/java/com/craxiom/networksurvey/util/LteSinrUtils.java'),
    (Join-Path $taskRoot 'networksurvey/src/main/java/com/craxiom/networksurvey/util/AndroidLteSinrReader.java')
)
$taskJavaSources += Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'android-lte/android') -Recurse -Filter '*.java' |
    ForEach-Object FullName
& "$taskJavaBin/javac.exe" --release 17 -d $taskOutput @taskJavaSources
if ($LASTEXITCODE) { throw 'Java LTE boundary compilation failed' }
& "$taskJavaBin/java.exe" -cp $taskCompiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $taskClasspath -d $taskOutput $taskSource
if ($LASTEXITCODE) { throw 'Kotlin presentation test compilation failed' }
& "$taskJavaBin/java.exe" -cp $taskClasspath AndroidLtePresentationHostKt
if ($LASTEXITCODE) { throw 'Android LTE presentation/export regression failed' }
