param([string]$SourceRevision)

$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$taskRelativeSource = 'networksurvey/src/main/java/com/craxiom/networksurvey/util/LteSinrUtils.java'
$taskOutput = Join-Path $taskRoot '.temp/lte-regex-icu'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskSource = Join-Path $taskRoot $taskRelativeSource
if ($SourceRevision) {
    $taskOldSource = (& git -C $taskRoot show "${SourceRevision}:$taskRelativeSource") -join "`n"
    if ($LASTEXITCODE) { throw 'Cannot read source revision' }
    $taskSource = Join-Path $taskOutput 'LteSinrUtils.java'
    [IO.File]::WriteAllText($taskSource, $taskOldSource, [Text.UTF8Encoding]::new($false))
}
$taskJavaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else {
    'C:/Program Files/Android/Android Studio/jbr/bin'
}
& "$taskJavaBin/javac.exe" --release 17 -d $taskOutput $taskSource (Join-Path $PSScriptRoot 'LteRegexPatterns.java')
if ($LASTEXITCODE) { throw 'Regex export compilation failed' }
$taskPatterns = @(& "$taskJavaBin/java.exe" -cp $taskOutput LteRegexPatterns)
if ($LASTEXITCODE -or !$taskPatterns.Count) { throw 'Cannot read production patterns' }

# Windows exposes ICU4C's regex compiler, the engine family used by Android.
if (!(Test-Path -LiteralPath (Join-Path $env:SystemRoot 'System32/icu.dll'))) {
    throw 'This host regression requires Windows ICU (System32/icu.dll).'
}
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class LteIcuRegexTest
{
    [DllImport("icu.dll", CallingConvention = CallingConvention.Cdecl, ExactSpelling = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr uregex_open(string pattern, int length, uint flags, IntPtr parseError, ref int status);
    [DllImport("icu.dll", CallingConvention = CallingConvention.Cdecl, ExactSpelling = true)]
    private static extern void uregex_close(IntPtr regex);
    [DllImport("icu.dll", CallingConvention = CallingConvention.Cdecl, ExactSpelling = true)]
    private static extern IntPtr u_errorName(int status);
    [DllImport("icu.dll", CallingConvention = CallingConvention.Cdecl, ExactSpelling = true)]
    private static extern void u_getVersion([Out] byte[] version);

    public static string Version()
    {
        byte[] version = new byte[4];
        u_getVersion(version);
        return String.Join(".", version);
    }

    public static void Validate(string name, string pattern, uint flags)
    {
        // UParseError: two int32 fields followed by two 16-UChar contexts.
        IntPtr error = Marshal.AllocHGlobal(72);
        IntPtr regex = IntPtr.Zero;
        try
        {
            int status = 0;
            regex = uregex_open(pattern, pattern.Length, flags, error, ref status);
            if (status > 0 || regex == IntPtr.Zero)
                throw new Exception(name + ": " + Marshal.PtrToStringAnsi(u_errorName(status))
                    + " at index " + Marshal.ReadInt32(error, 4));
        }
        finally
        {
            if (regex != IntPtr.Zero) uregex_close(regex);
            Marshal.FreeHGlobal(error);
        }
    }
}
'@
Write-Output "ICU version: $([LteIcuRegexTest]::Version())"
foreach ($taskPattern in $taskPatterns) {
    $taskFields = $taskPattern -split "`t", 3
    if ($taskFields.Count -ne 3) { throw 'Invalid production pattern export' }
    $taskText = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($taskFields[2]))
    $taskFlags = [uint32]$taskFields[1]
    if (($taskFlags -band 2) -ne $taskFlags) { throw 'Map new Java Pattern flags to ICU before testing' }
    [LteIcuRegexTest]::Validate($taskFields[0], $taskText, $taskFlags)
    Write-Output "PASS ICU compilation: $($taskFields[0])"
}
