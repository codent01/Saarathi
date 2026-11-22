# Verifies Phase 2 features on the connected phone: embedding retrieval, reader search, Daily Wisdom.
$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$w = Join-Path $env:TEMP "saarathi_phone"; New-Item -ItemType Directory -Force $w | Out-Null
function Dump() { & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null; & $adb pull /sdcard/ui.xml "$w\ui.xml" | Out-Null; [xml](Get-Content "$w\ui.xml" -Encoding UTF8) }
function Nodes($x) { $x.SelectNodes("//node") }
function Center($bounds) { $m = [regex]::Match([string]$bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); return @([int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
function TapText($text) { $x = Dump; $n = Nodes $x | Where-Object { $_.text -eq $text -or $_.'content-desc' -eq $text } | Select-Object -First 1; if (-not $n) { "  (no node '$text')"; return $false }; $c = Center $n.bounds; & $adb shell input tap $c[0] $c[1]; Start-Sleep 2; return $true }
function Texts() { $x = Dump; Nodes $x | Where-Object { $_.text -ne '' } | ForEach-Object { $_.text.Substring(0, [Math]::Min(110, $_.text.Length)) } }
function Logs($pattern) { & $adb logcat -d -v time > "$w\logcat.txt"; Get-Content "$w\logcat.txt" | Select-String -Pattern $pattern | ForEach-Object { $_.Line.Substring(0, [Math]::Min(230, $_.Line.Length)) } | Select-Object -Last 8 }

& $adb shell svc power stayon usb; & $adb shell input keyevent KEYCODE_WAKEUP; & $adb shell wm dismiss-keyguard
& $adb shell pm grant com.saarathi.app android.permission.POST_NOTIFICATIONS
& $adb shell am force-stop com.saarathi.app; & $adb logcat -c
& $adb shell am start -n com.saarathi.app/.MainActivity | Out-Null; Start-Sleep 12
TapText "Don't Show Again" | Out-Null
$t0 = Get-Date
do { Start-Sleep 3; $ok = (Logs "primed|embedding model ready").Count -ge 2 } while (-not $ok -and ((Get-Date) - $t0).TotalSeconds -lt 120)
"=== startup ==="; Logs "LlamaEngine|Embedder|ScriptureDb"

"=== 1. embedding retrieval ==="
& $adb logcat -c
powershell -ExecutionPolicy Bypass -File "$PSScriptRoot\phone_test.ps1" -NoRestart -WaitSeconds 150 -Message "Everyone keeps comparing me to my cousin and it makes me feel small" | Select-String -Pattern "sent at|Orchestrator|prompt tokens"

"=== 2. reader search ==="
TapText "Menu" | Out-Null; TapText "Scripture library" | Out-Null
$x = Dump; $edit = Nodes $x | Where-Object { $_.class -match 'EditText' } | Select-Object -First 1
$c = Center $edit.bounds; & $adb shell input tap $c[0] $c[1]; Start-Sleep 1; & $adb shell input text "anger"; Start-Sleep 4
Texts | Select-Object -First 12
& $adb shell input keyevent KEYCODE_BACK; Start-Sleep 1; & $adb shell input keyevent KEYCODE_BACK; Start-Sleep 2

"=== 3. daily wisdom screen ==="
TapText "Menu" | Out-Null; TapText "Today's verse" | Out-Null
Texts | Select-Object -First 10
& $adb shell input keyevent KEYCODE_BACK; Start-Sleep 2

"=== 4. test notification ==="
TapText "Menu" | Out-Null; TapText "Settings" | Out-Null
$x = Dump; $btn = Nodes $x | Where-Object { $_.text -eq 'Send test notification' } | Select-Object -First 1
if (-not $btn) { & $adb shell input swipe 540 2000 540 400 600; Start-Sleep 1; & $adb shell input swipe 540 2000 540 400 600; Start-Sleep 1 }
TapText "Send test notification" | Out-Null; Start-Sleep 5
& $adb shell "dumpsys notification --noredact" | Select-String -Pattern "saarathi|Bhagavad Gita" | Select-Object -First 6
& $adb shell cmd notification list 2>$null | Select-String saarathi | Select-Object -First 3
