# Drives one chat turn on a connected phone over adb and reports timings.
# Usage: powershell -ExecutionPolicy Bypass -File tools\phone_test.ps1 -Message "Krishna, I feel lost." [-WaitSeconds 240]
param([string]$Message = "Krishna, I am working very hard but nothing is going right. I feel like giving up.", [int]$WaitSeconds = 240, [switch]$NoRestart)
$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$w = Join-Path $env:TEMP "saarathi_phone"
New-Item -ItemType Directory -Force $w | Out-Null

function Dump() {
    & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    & $adb pull /sdcard/ui.xml "$w\ui.xml" | Out-Null
    [xml](Get-Content "$w\ui.xml" -Encoding UTF8)
}
function Nodes($x) { $x.SelectNodes("//node") }
function Center($bounds) { $m = [regex]::Match([string]$bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2; return @([int]$cx, [int]$cy) }

& $adb shell input keyevent KEYCODE_WAKEUP; & $adb shell wm dismiss-keyguard; & $adb shell cmd statusbar collapse
if (-not $NoRestart) {
    & $adb shell am force-stop com.saarathi.app
    & $adb logcat -c
    & $adb shell am start -n com.saarathi.app/.MainActivity | Out-Null
    $t0 = Get-Date
    do { Start-Sleep 2; $loaded = (& $adb logcat -d -s LlamaEngine:I | Select-String "loaded ").Count -gt 0 } while (-not $loaded -and ((Get-Date) - $t0).TotalSeconds -lt 90)
    "model load: $([int]((Get-Date) - $t0).TotalSeconds)s (loaded=$loaded)"
    Start-Sleep 2
}
$x = Dump
$warn = Nodes $x | Where-Object { $_.text -eq "Don't Show Again" } | Select-Object -First 1
if ($warn) { $c = Center $warn.bounds; & $adb shell input tap $c[0] $c[1]; Start-Sleep 2; $x = Dump }
$edit = Nodes $x | Where-Object { $_.class -match 'EditText' } | Select-Object -First 1
if (-not $edit) { "no composer on screen"; Nodes $x | Where-Object { $_.text -ne '' } | ForEach-Object { $_.text.Substring(0,[Math]::Min(80,$_.text.Length)) }; exit 1 }
$c = Center $edit.bounds; & $adb shell input tap $c[0] $c[1]; Start-Sleep 1
& $adb shell input text ($Message -replace ' ', '%s'); Start-Sleep 1
$x = Dump
$send = Nodes $x | Where-Object { $_.'content-desc' -eq 'Send' } | Select-Object -First 1
$c = Center $send.bounds; & $adb logcat -c; & $adb shell input tap $c[0] $c[1]
$sent = Get-Date
"sent at $($sent.ToString('HH:mm:ss'))"
$lastLen = -1
do {
    Start-Sleep 15
    & $adb shell input keyevent KEYCODE_WAKEUP | Out-Null
    $x = Dump
    $stop = Nodes $x | Where-Object { $_.'content-desc' -eq 'Stop' }
    $texts = Nodes $x | Where-Object { $_.text -ne '' } | ForEach-Object { $_.text }
    $reply = $texts | Where-Object { $_ -ne $Message -and $_.Length -gt 40 -and $_ -notmatch 'Speak your heart|Every (mind|Arjuna)|AI interpretation' } | Select-Object -Last 1
    $elapsed = [int]((Get-Date) - $sent).TotalSeconds
    $len = if ($reply) { $reply.Length } else { 0 }
    if ($len -ne $lastLen) { "t=${elapsed}s streaming=$([bool]$stop) replyChars=$len"; $lastLen = $len }
} while ($stop -and $elapsed -lt $WaitSeconds)
"--- reply ---"; $reply
"--- engine log ---"
& $adb logcat -d -v time > "$w\logcat.txt"; Get-Content "$w\logcat.txt" | Select-String -Pattern "LlamaEngine|SaarathiLlama|Orchestrator|AndroidRuntime" | ForEach-Object { $_.Line.Substring(0,[Math]::Min(230,$_.Line.Length)) } | Select-Object -Last 14
& $adb shell "dumpsys meminfo com.saarathi.app | grep -E 'TOTAL PSS|TOTAL RSS'"


