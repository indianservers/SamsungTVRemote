param([string]$Serial='emulator-5554',[switch]$ResumeAfterOnboarding,[switch]$FunctionsOnly)
$adb='C:\Users\saisa\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$workspace=Split-Path $PSScriptRoot -Parent
$screenDir=Join-Path $workspace 'release/screenshots'
[System.IO.Directory]::CreateDirectory($screenDir) | Out-Null
function Read-Ui {
    & $adb -s $Serial shell uiautomator dump /sdcard/smart-remote-ui.xml | Out-Null
    & $adb -s $Serial pull /sdcard/smart-remote-ui.xml (Join-Path $workspace 'qa/production-ui.xml') 2>$null | Out-Null
    [xml]$document=Get-Content (Join-Path $workspace 'qa/production-ui.xml')
    return $document
}
function Tap-Text([string]$text) {
    for($attempt=0;$attempt -lt 8;$attempt++) {
        $document=Read-Ui
        $matches=@($document.SelectNodes('//node') | Where-Object { $_.text -ceq $text -and $_.package -eq 'com.indianservers.samsungsmarttvremote' })
        if($matches.Count -eq 1) {
            $bounds=[regex]::Matches($matches[0].bounds,'\d+') | ForEach-Object { [int]$_.Value }
            & $adb -s $Serial shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))
            return
        }
        $scroll=$document.SelectNodes('//node') | Where-Object { $_.scrollable -eq 'true' -and $_.package -eq 'com.indianservers.samsungsmarttvremote' } | Select-Object -First 1
        if($null -eq $scroll) { throw "Visible app control not found: $text" }
        $b=[regex]::Matches($scroll.bounds,'\d+') | ForEach-Object { [int]$_.Value }
        $x=[int](($b[0]+$b[2])/2)
        & $adb -s $Serial shell input swipe $x ($b[3]-100) $x ($b[1]+100) 350
    }
    throw "App control not found after bounded scroll: $text"
}
function Capture([string]$name) {
    $null=Read-Ui
    & $adb -s $Serial shell screencap -p /sdcard/smart-remote-capture.png
    & $adb -s $Serial pull /sdcard/smart-remote-capture.png (Join-Path $screenDir "$name.png") 2>$null | Out-Null
}
if($FunctionsOnly) { return }
if(-not $ResumeAfterOnboarding) {
Capture '01-onboarding'
Tap-Text 'Continue'
Capture '02-same-wifi'
Tap-Text 'Continue'
Capture '03-pairing-guide'
Tap-Text 'Continue'
Capture '04-start-controlling'
}
Tap-Text 'Start controlling'
Capture '05-devices-empty'
Tap-Text 'Settings'
Capture '06-settings'
Tap-Text 'Themes and Appearance'
Capture '07-snow-theme'
Tap-Text 'Midnight Dark'
Capture '08-midnight-theme'
Tap-Text 'Royal Blue'
Capture '09-royal-theme'
Tap-Text 'Snow White'



