. "$PSScriptRoot/capture-production-screens.ps1" -FunctionsOnly
Tap-Text 'Connected TVs'
Tap-Text 'Discover another TV'
Capture '10-permission-denied'
$ui=Read-Ui
if(-not @($ui.SelectNodes('//node') | Where-Object text -ceq 'Local network permission is required.').Count) { throw 'Expected denied state not shown' }
& $adb -s $Serial shell pm grant com.indianservers.samsungsmarttvremote android.permission.ACCESS_LOCAL_NETWORK
& $adb -s $Serial shell am force-stop com.indianservers.samsungsmarttvremote
& $adb -s $Serial shell am start -W -n com.indianservers.samsungsmarttvremote/.MainActivity
Tap-Text 'Discover another TV'
Capture '11-permission-granted'
$ui=Read-Ui
if(@($ui.SelectNodes('//node') | Where-Object text -ceq 'Allow local network').Count) { throw 'Granted permission still presented as denied' }
& $adb -s $Serial shell pm revoke com.indianservers.samsungsmarttvremote android.permission.ACCESS_LOCAL_NETWORK
& $adb -s $Serial shell am start -W -n com.indianservers.samsungsmarttvremote/.MainActivity
Tap-Text 'Discover another TV'
Capture '12-permission-revoked'
$ui=Read-Ui
if(-not @($ui.SelectNodes('//node') | Where-Object text -ceq 'Local network permission is required.').Count) { throw 'Revoked permission was not reflected' }
Tap-Text 'Settings'
Tap-Text 'Help'
Tap-Text 'TV not found'
Capture '13-help-center'
Tap-Text 'Connected TVs'
Tap-Text 'Connection diagnostics'
Capture '14-diagnostics'
Tap-Text 'Export sanitized diagnostics'
$null=Read-Ui
& $adb -s $Serial shell dumpsys window | Select-String 'mCurrentFocus'
& $adb -s $Serial shell input keyevent KEYCODE_BACK
'Permission denied, granted and revoked states verified using Android package-manager permission transitions; sanitized share flow opened; no share recipient selected.'

