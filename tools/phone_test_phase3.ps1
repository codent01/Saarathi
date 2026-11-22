# Verifies Phase 3 features on the connected phone: journal + reflect, Karma Steps, Decision Compass, Gita journey.
# Usage: powershell -ExecutionPolicy Bypass -File tools\phone_test_phase3.ps1 [-Steps journal,karma,compass,journey] [-NoRestart]
# The app must already be set up on the phone (onboarding done, Gemma 1B chosen) as in phone_test.ps1.
param([string[]]$Steps = @("journal", "karma", "compass", "journey"), [switch]$NoRestart, [int]$ReplyWait = 300)
$Steps = @($Steps | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() })  # "-Steps karma,compass" arrives as one string through -File
$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$w = Join-Path $env:TEMP "saarathi_phone"; New-Item -ItemType Directory -Force $w | Out-Null
function Dump() { & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null; & $adb pull /sdcard/ui.xml "$w\ui.xml" 2>&1 | Out-Null; [xml](Get-Content "$w\ui.xml" -Encoding UTF8) }
function Nodes($x) { $x.SelectNodes("//node") }
function TapXY($b) { $m = [regex]::Match([string]$b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); & $adb shell input tap (([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2) (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2) }
function FindText($text) { $x = Dump; Nodes $x | Where-Object { $_.text -like $text -or $_.'content-desc' -like $text } | Select-Object -First 1 }
function TapText($text) { $n = FindText $text; if (-not $n) { "  (no node '$text')"; return $false }; TapXY $n.bounds; Start-Sleep 3; return $true }
function Texts() { $x = Dump; Nodes $x | Where-Object { $_.text -ne '' } | ForEach-Object { $_.text.Substring(0, [Math]::Min(110, $_.text.Length)) } }
function Logs($pattern, $last = 8) { & $adb logcat -d -v time > "$w\logcat.txt"; Get-Content "$w\logcat.txt" | Select-String -Pattern $pattern | ForEach-Object { $_.Line.Substring(0, [Math]::Min(230, $_.Line.Length)) } | Select-Object -Last $last }
function Edits() { $x = Dump; @(Nodes $x | Where-Object { $_.class -match 'EditText' }) }
# Taps the n-th text field, types (spaces as %s), then closes the keyboard so later taps land on buttons.
function TypeInto($index, $text) { $e = @(Edits); if ($e.Count -le $index) { "  (no EditText #$index)"; return }; TapXY $e[$index].bounds; Start-Sleep 1; & $adb shell input text ($text -replace ' ', '%s'); Start-Sleep 1; & $adb shell input keyevent KEYCODE_BACK; Start-Sleep 1 }
function Back() { & $adb shell input keyevent KEYCODE_BACK; Start-Sleep 2 }
function Front() { $top = & $adb shell "dumpsys activity activities | grep topResumedActivity"; if ($top -notmatch 'saarathi') { "  (app not in front, bringing it back)"; & $adb shell am start -n com.saarathi.app/.MainActivity | Out-Null; Start-Sleep 4 } }
function Menu($item) { Front; TapText "Menu" | Out-Null; TapText $item | Out-Null }
function Send() { & $adb logcat -c; TapText "Send" | Out-Null }
function WaitReply($seconds) {
    $t0 = Get-Date
    do { Start-Sleep 10; & $adb shell input keyevent KEYCODE_WAKEUP | Out-Null; $done = (Logs "Orchestrator.*reply intent" 1).Count -gt 0; $el = [int]((Get-Date) - $t0).TotalSeconds } while (-not $done -and $el -lt $seconds)
    "  reply finished after ${el}s"; Logs "Orchestrator|FATAL EXCEPTION" 2
}
function LongTexts($min = 100) { $x = Dump; Nodes $x | Where-Object { $_.text.Length -gt $min } | ForEach-Object { $_.text } }

& $adb shell svc power stayon usb; & $adb shell input keyevent KEYCODE_WAKEUP; & $adb shell wm dismiss-keyguard; & $adb shell cmd statusbar collapse
if (-not $NoRestart) {
    & $adb shell am force-stop com.saarathi.app; & $adb logcat -c
    & $adb shell am start -n com.saarathi.app/.MainActivity | Out-Null; Start-Sleep 12
    TapText "Don't Show Again" | Out-Null
    $t0 = Get-Date
    do { Start-Sleep 3; $ok = (Logs "primed" 1).Count -ge 1 } while (-not $ok -and ((Get-Date) - $t0).TotalSeconds -lt 120)
    "=== startup ==="; Logs "LlamaEngine|FATAL EXCEPTION|Migration|Room" 4
}

if ($Steps -contains "journal") {
    "=== 1. journal ==="
    Menu "My Kurukshetra journal"
    TapText "New entry" | Out-Null
    TypeInto 0 "Job offer"
    TypeInto 1 "I got an offer from a startup in Pune. More money, more risk. My father wants me to stay in the bank job. I keep going back and forth and cannot sleep."
    TypeInto 2 "work, family"
    TapText "Reflect with Krishna" | Out-Null
    "  chat after reflect: " + ((Texts) -match 'JOURNAL|characters read|journal' -join ' | ')
    Send; WaitReply $ReplyWait
    "  reply: " + ((LongTexts 150 | Select-Object -Last 1) -replace "`n", ' ').Substring(0, 200)
    Menu "My Kurukshetra journal"
    "  journal list: " + ((Texts) -match 'Job offer|reflected' -join ' | ')
    Back
}

if ($Steps -contains "karma") {
    "=== 2. karma steps ==="
    Menu "New conversation"
    TypeInto 0 "Krishna, I keep overthinking at night. Give me exactly three small steps for tomorrow as a numbered list, 1. 2. 3., one short line each."
    Send; WaitReply $ReplyWait
    & $adb shell input swipe 540 800 540 1800 400; Start-Sleep 2
    $save = FindText 'Save as Karma Steps*'
    if (-not $save) { & $adb shell input swipe 540 1800 540 800 400; Start-Sleep 2; $save = FindText 'Save as Karma Steps*' }
    if ($save) { "  found: $($save.text)"; TapXY $save.bounds; Start-Sleep 3; "  after save: " + ((Texts) -match 'Karma' -join ' | ') }
    else { "  (model did not write a list this time; reply:)"; LongTexts 60 | Select-Object -Last 1 }
    Menu "New conversation"
    "  empty chat: " + ((Texts) -match 'Karma|done today' -join ' | ')
    $x = Dump; $cb = Nodes $x | Where-Object { $_.class -match 'CheckBox' } | Select-Object -First 1
    if ($cb) { TapXY $cb.bounds; Start-Sleep 2; "  after ticking one: " + ((Texts) -match 'done today' -join ' ') }
    Menu "Karma Steps"; "  karma screen: " + ((Texts) -match '^(Open|Done) . \d' -join ' | '); Back
}

if ($Steps -contains "compass") {
    "=== 3. decision compass ==="
    Menu "Decision Compass"
    $answers = @(
        "Whether to take the startup offer in Pune or stay in my bank job",
        "The startup work excites me and pays more. The bank is safe and my father is proud of it",
        "I am afraid of failing in front of my family. I assume the startup will fold within a year",
        "My parents depend on me partly. I promised my father I would be stable",
        "Startup: faster growth, could fail, far from home. Bank: slow, safe, I feel dull",
        "Honesty with my family. Not living out of fear. Doing my own duty, not my fathers",
        "I notice most of my fear is about my fathers face, not the job itself"
    )
    for ($i = 0; $i -lt 7; $i++) {
        "  " + ((Texts) -match '^Step ' -join '')
        TypeInto 0 $answers[$i]
        if ($i -lt 6) { TapText "Next" | Out-Null } else { & $adb logcat -c; TapText "Begin with Krishna" | Out-Null; Start-Sleep 3 }
    }
    "  chat: " + ((Texts) -match 'Arjuna Mode|Decision Compass' -join ' | ')
    WaitReply $ReplyWait
    & $adb shell input swipe 540 800 540 1800 400; Start-Sleep 2
    $reply = LongTexts 150 | Where-Object { $_ -notlike 'I am using the Decision Compass*' } | Select-Object -First 1
    if ($reply) { "  reply: " + ($reply -replace "`n", ' ').Substring(0, [Math]::Min(240, $reply.Length)) }
    TapText "Conversations" | Out-Null; "  history: " + ((Texts) -match 'Compass|Arjuna' -join ' | '); Back
}

if ($Steps -contains "journey") {
    "=== 4. gita journey ==="
    Menu "Gita journey"
    "  overview: " + ((Texts) -match 'verses read' -join ' | ')
    TapText "Arjuna's Despair" | Out-Null
    for ($i = 0; $i -lt 8; $i++) { & $adb shell input swipe 540 1900 540 600 300; Start-Sleep 1 }
    Back
    "  after scrolling chapter 1: " + ((Texts) -match 'verses read' -join ' | ')
    TapText "Arjuna's Despair" | Out-Null
    for ($i = 0; $i -lt 45; $i++) { & $adb shell input swipe 540 2000 540 200 150; Start-Sleep 0.4 }
    Start-Sleep 2
    "  reader bottom: " + ((Texts) -match 'reflection|Mark chapter|Write in journal' -join ' | ')
    TapText "Mark chapter as read" | Out-Null
    "  after mark: " + ((Texts) -match 'complete|Read again' -join ' | ')
    TapText "Write in journal" | Out-Null
    "  journal draft: " + ((@(Edits) | ForEach-Object { $_.text }) -join ' / ')
    Back; Back
    "  overview: " + ((Texts) -match 'verses read|Complete' -join ' | ')
    Back
}
"=== memory ==="; & $adb shell "dumpsys meminfo com.saarathi.app | grep -E 'TOTAL PSS|TOTAL RSS'"
Logs "FATAL EXCEPTION|Process com.saarathi.app.*died" 4
