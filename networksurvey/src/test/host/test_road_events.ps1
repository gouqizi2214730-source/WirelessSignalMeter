param(
    [string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$GradleCache = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1"
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
Set-Location -LiteralPath $repo
function Find-Jar([string]$module, [string]$version) {
    $found = Get-ChildItem -LiteralPath (Join-Path $GradleCache "$module/$version") -Recurse -File -Filter '*.jar' |
        Where-Object Name -NotMatch 'sources|javadoc' | Select-Object -First 1 -ExpandProperty FullName
    if (!$found) { throw "Missing cached JAR: $module $version" }
    return $found
}
$stdlib = Find-Jar 'org.jetbrains.kotlin/kotlin-stdlib' '2.4.10'
$annotations = Find-Jar 'org.jetbrains/annotations' '26.0.2-1'
$compiler = @(
    (Find-Jar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' '2.4.10'), $stdlib, $annotations,
    (Find-Jar 'org.jetbrains.kotlin/kotlin-daemon-embeddable' '2.4.10'),
    (Find-Jar 'org.jetbrains.kotlin/kotlin-reflect' '1.6.10'),
    (Find-Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm' '1.10.2')
) -join [IO.Path]::PathSeparator
$target = Join-Path $repo '.temp/road-event-tests'
New-Item -ItemType Directory -Path $target -Force | Out-Null
$classpath = @($target, $stdlib, $annotations, (Join-Path $JavaHome '../lib/junit4.jar'),
    (Find-Jar 'org.apache.commons/commons-csv' '1.14.1'),
    (Find-Jar 'commons-io/commons-io' '2.21.0'),
    (Find-Jar 'commons-codec/commons-codec' '1.19.0')) -join [IO.Path]::PathSeparator
$source = Join-Path $repo 'networksurvey/src/main/java/com/craxiom/networksurvey'
$tests = Join-Path $repo 'networksurvey/src/test/java/com/craxiom/networksurvey'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $target "$source/model/MapPointRecord.java"
if ($LASTEXITCODE) { throw 'Java compilation failed' }
& "$JavaHome/bin/java.exe" -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $classpath -d $target "$source/model/SurveyEvent.kt" "$source/ui/cellular/baidumap/RoadCoverage.kt" "$tests/model/ObservedCellEventsTest.kt" "$tests/ui/cellular/baidumap/RoadCoverageTest.kt"
if ($LASTEXITCODE) { throw 'Kotlin compilation failed' }
& "$JavaHome/bin/javac.exe" -cp $classpath -d $target "$PSScriptRoot/RunHostTests.java"
if ($LASTEXITCODE) { throw 'Runner compilation failed' }
& "$JavaHome/bin/java.exe" -cp $classpath RunHostTests com.craxiom.networksurvey.ui.cellular.baidumap.RoadCoverageTest com.craxiom.networksurvey.model.ObservedCellEventsTest
if ($LASTEXITCODE) { throw 'Road/event tests failed' }
