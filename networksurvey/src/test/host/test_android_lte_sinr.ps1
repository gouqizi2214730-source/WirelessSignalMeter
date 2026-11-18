$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$taskOutput = Join-Path $taskRoot '.temp/android-lte-sinr-host'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskJavaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else {
    'C:/Program Files/Android/Android Studio/jbr/bin'
}
$taskSources = @(
    (Join-Path $taskRoot 'networksurvey/src/main/java/com/craxiom/networksurvey/util/LteSinrUtils.java'),
    (Join-Path $taskRoot 'networksurvey/src/main/java/com/craxiom/networksurvey/util/AndroidLteSinrReader.java')
)
$taskSources += Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'android-lte') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName }
& "$taskJavaBin/javac.exe" --release 17 -d $taskOutput @taskSources
if ($LASTEXITCODE) { throw 'Android LTE host test compilation failed' }
& "$taskJavaBin/java.exe" -cp $taskOutput AndroidLteSinrHostTest
if ($LASTEXITCODE) { throw 'Android LTE host test failed' }
