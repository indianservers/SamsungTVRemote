package com.indianservers.samsungsmarttvremote.presentation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.RemoteGlyph
import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.widget.*
import java.io.File
import kotlinx.coroutines.*

@Composable internal fun Onboarding(onDone: ()->Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val headings=listOf("Control Your Samsung TV","Connect to the Same Wi-Fi","Pair Your Television","Start Controlling")
    val details=listOf("One thoughtful remote for compatible Samsung Smart TVs. Independent app; not endorsed by Samsung.","Your phone and TV need a reachable local network. Guest Wi-Fi isolation and VPNs can prevent discovery.","Select your TV, then approve Smart TV Remote using the TV's existing remote. Authorization is never bypassed.","Navigate, adjust volume and use the touchpad. Keyboard and app shortcuts are enabled only with appropriate TV support.")
    Spacer(Modifier.height(20.dp))
    Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.primary) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.indianservers.samsungsmarttvremote.R.drawable.ic_remote_foreground),contentDescription="Original television and remote illustration",modifier=Modifier.size(180.dp)) }
    Text("${step+1} OF 4",color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
    Crossfade(step,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="onboarding") { page -> Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(headings[page],style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(details[page],style=MaterialTheme.typography.bodyLarge)
    } }
    LinearProgressIndicator(progress={ (step+1)/4f },modifier=Modifier.fillMaxWidth())
    Button(onClick={if(step==3) onDone() else step++},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(step==3) "Start controlling" else "Continue") }
    TextButton(onClick=onDone,modifier=Modifier.heightIn(min=48.dp)) { Text("Skip introduction") }
}

@Composable internal fun DeviceDashboard(vm:RemoteViewModel,onDiscover:()->Unit,onRemote:()->Unit,onDiagnostics:()->Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.connection.state.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<DeviceProfile?>(null) }
    var deleting by remember { mutableStateOf<DeviceProfile?>(null) }
    Text("Your televisions",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
    Text("Saved independently. One active remote at a time.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(profiles.isEmpty()) Text("No paired TVs yet. Discover a television and approve pairing on its screen.")
    profiles.forEach { profile ->
        val selected=state.tv?.id==profile.tv.id
        val border by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="selected TV")
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),border=BorderStroke(if(selected) 2.dp else 1.dp,border),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                RemoteGlyph(RemoteKey.SOURCE,MaterialTheme.colorScheme.primary)
                Text(profile.friendlyName,fontWeight=FontWeight.Bold,fontSize=22.sp)
                Text("${profile.room} • ${profile.tv.model}")
                Text("${profile.tv.ip}:${profile.tv.port}",fontSize=13.sp)
                Text(if(selected) "Selected • ${state.status}" else "Saved • availability not checked",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(profile.tv.port==8001) Text("Unencrypted local connection; use a trusted network.",fontSize=12.sp)
                Button(onClick={vm.connectProfile(profile);onRemote()},modifier=Modifier.heightIn(min=52.dp)) { Text(if(selected) "Reconnect this TV" else "Switch to this TV") }
                Row { TextButton(onClick={edit=profile},modifier=Modifier.heightIn(min=48.dp)) { Text("Device settings") }; TextButton(onClick={deleting=profile},modifier=Modifier.heightIn(min=48.dp)) { Text("Remove") } }
            }
        }
    }
    Button(onClick=onDiscover,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Discover another TV") }
    TextButton(onClick=onDiagnostics) { Text("Connection diagnostics") }
    edit?.let { profile ->
        var name by remember(profile.tv.id) { mutableStateOf(profile.friendlyName) }
        var room by remember(profile.tv.id) { mutableStateOf(profile.room) }
        AlertDialog(onDismissRequest={edit=null},title={Text("Device details")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${profile.tv.model}\n${profile.tv.ip}:${profile.tv.port}",fontSize=13.sp)
            OutlinedTextField(value=name,onValueChange={if(it.length<=60) name=it},label={Text("Friendly name")},singleLine=true)
            OutlinedTextField(value=room,onValueChange={if(it.length<=40) room=it},label={Text("Room (Living Room, Bedroom, Office or custom)")},singleLine=true)
        }},confirmButton={TextButton(onClick={vm.rename(profile.tv.id,name,room);edit=null},enabled=name.isNotBlank() && room.isNotBlank()) { Text("Save device") }},dismissButton={TextButton(onClick={edit=null}) { Text("Cancel") }})
    }
    deleting?.let { p -> AlertDialog(onDismissRequest={deleting=null},title={Text("Remove ${p.friendlyName}?")},text={Text("Only this TV's credentials, favorites and widget assignments will be removed. Other televisions remain saved.")},confirmButton={TextButton(onClick={vm.removeProfile(p.tv.id);deleting=null}) { Text("Remove TV") }},dismissButton={TextButton(onClick={deleting=null}) { Text("Cancel") }}) }
}

@Composable internal fun AppearanceSettings(vm:RemoteViewModel) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    Text("Themes and appearance",style=MaterialTheme.typography.headlineSmall)
    RemoteTheme.entries.forEach { theme -> FilterChip(selected=appearance.theme==theme,onClick={vm.appearance { it.copy(theme=theme) }},label={Text(when(theme) { RemoteTheme.SnowWhite->"Snow White";RemoteTheme.MidnightDark->"Midnight Dark";RemoteTheme.RoyalBlue->"Royal Blue";RemoteTheme.Minimal->"Minimal Remote";RemoteTheme.System->"Follow system" })},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) }
    Text("Button size")
    var size by remember(appearance.buttonSize) { mutableFloatStateOf(appearance.buttonSize) }
    Slider(value=size,onValueChange={size=it},onValueChangeFinished={vm.appearance { it.copy(buttonSize=size) }},valueRange=1f..1.2f,steps=1)
    Text("Animation intensity • 0 removes decorative movement")
    var motion by remember(appearance.motion) { mutableFloatStateOf(appearance.motion) }
    Slider(value=motion,onValueChange={motion=it},onValueChangeFinished={vm.appearance { it.copy(motion=motion) }},valueRange=0f..1f,steps=1)
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("Haptic feedback",Modifier.weight(1f));Switch(checked=haptics,onCheckedChange=vm::haptics) }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("Media controls above D-pad",Modifier.weight(1f));Switch(checked=appearance.mediaFirst,onCheckedChange={first->vm.appearance { it.copy(mediaFirst=first) }}) }
    Text("System reduced-motion preferences also apply. Gesture sensitivity, default tab, favorite pins and hidden groups are under Remote Preferences.",fontSize=13.sp)
}

@Composable internal fun DiagnosticsScreen(vm:RemoteViewModel,permission:Boolean,onRescan:()->Unit,onHelp:()->Unit) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val diagnostic by vm.connection.diagnostics.collectAsStateWithLifecycle()
    val network by vm.wifiAvailable.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var exportError by remember { mutableStateOf("") }
    Text("Connection diagnostics",style=MaterialTheme.typography.headlineSmall)
    val lines=listOf("Wi-Fi network available" to network.toString(),"Local-network permission" to if(permission) "Allowed / not required" else "Required", "Selected TV" to (state.tv?.name ?: "None"),"Local IP" to (state.tv?.ip ?: "None"),"Endpoint" to (state.tv?.let { "${if(it.port==8002) "wss" else "ws"}://${it.ip}:${it.port}/api/v2/channels/samsung.remote.control" } ?: "None"),"Authorization/socket state" to state.status.name,"Failure category" to diagnostic.failure.name,"Last error" to diagnostic.lastError,"Last transport submission" to (diagnostic.lastTransmissionMillis?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "None"))
    lines.forEach { (title,value) -> Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) { Text(title,fontWeight=FontWeight.SemiBold);Text(value,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
    Text("A transport submission is not proof that the TV executed the action.",fontSize=12.sp)
    Button(onClick=vm.connection::reconnect,enabled=permission && state.tv!=null) { Text("Retry connection") }
    OutlinedButton(onClick=onRescan) { Text("Rescan TVs") }
    OutlinedButton(onClick=vm::reauthorize,enabled=permission && state.tv!=null) { Text("Reauthorize TV") }
    TextButton(onClick=vm::forget,enabled=state.tv!=null) { Text("Forget this device") }
    if(!permission) TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) { Text("Open Android app permissions") }
    OutlinedButton(onClick={scope.launch {
        try {
            val uri=withContext(Dispatchers.IO) {
                val dir=File(context.cacheDir,"diagnostics").apply { mkdirs() }
                val file=File(dir,"smart-remote-diagnostics.txt").apply { writeText(DiagnosticReport.create(state,diagnostic,network,permission)) }
                FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            }
            val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).apply { clipData=ClipData.newRawUri("Sanitized diagnostics",uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(share,"Share sanitized diagnostics"))
        } catch(_:Exception) { exportError="Unable to open the Android share sheet." }
    }}) { Text("Export sanitized diagnostics") }
    if(exportError.isNotEmpty()) Text(exportError)
    TextButton(onClick=onHelp) { Text("Troubleshooting help") }
}

@Composable internal fun WidgetSettings() {
    val context=LocalContext.current
    var message by remember { mutableStateOf("") }
    Text("Home-screen widgets",style=MaterialTheme.typography.headlineSmall)
    Text("Pair a TV first. Long-press your launcher, choose Widgets, then Smart TV Remote. Each instance has its own saved TV; tap its title to reconfigure. Resize for larger targets.")
    Text("Commands open the app for an authorized live session and explicit Send confirmation. Widget status describes the last attempt, never a continuously monitored connection. No always-running service is used.")
    listOf("Compact Remote" to CompactRemoteWidget::class.java,"Advanced Remote" to AdvancedRemoteWidget::class.java).forEach { (name,provider) -> Button(onClick={
        val manager=AppWidgetManager.getInstance(context)
        message=if(manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context,provider),null,null)) "Widget request sent to your launcher." else "Use your launcher's Widgets picker to add this remote."
    },modifier=Modifier.heightIn(min=52.dp)) { Text("Add $name") } }
    if(message.isNotEmpty()) Text(message)
}

private val helpTopics=linkedMapOf(
    "TV not found" to "Connect phone and TV to the same reachable Wi-Fi. Disable guest/client isolation and check VPN routing. Rescan or enter the TV's private IPv4 address. Some firmware blocks the required device-information API.",
    "Unable to pair" to "Keep the TV powered on, select the correct device, and approve the prompt with its existing remote. Retry after checking the local-network permission. Do not approve an unfamiliar device.",
    "Pairing permission rejected" to "Open the TV's external device/connection settings and remove the previous denial if needed. Use Reauthorize TV in diagnostics, then approve its new prompt.",
    "Buttons not working" to "Some keys depend on model and foreground TV app. Check the TV screen and diagnostics. A socket submission does not confirm execution; reported command errors disable the most recent key conservatively.",
    "Text input unavailable" to "Open a TV text field and wait for the remote IME event. Some TV apps do not accept inserted text. TV backspace remains unverified; use its on-screen keyboard.",
    "App launching unavailable" to "Refresh the TV app list. Cached and custom shortcuts are unverified. IDs/action types vary by model and region. Acknowledgment does not prove that an app opened.",
    "How to switch TVs" to "Open Connected TVs and choose Switch. Each saved device has independent credentials and favorites; the old session and queued actions are canceled.",
    "How to disconnect" to "Open Settings, then Disconnect. The app closes its socket when backgrounded and reconnects the intended device on resume.",
    "How to reset saved devices" to "Remove one TV from Connected TVs to preserve others. Reset all saved data in Privacy removes local profiles, credentials and customization and restarts onboarding.",
    "Supported Samsung TV limitations" to "This independent app targets TVs exposing Samsung's modern WebSocket API. Compatibility varies by model/firmware. Pointer movement and direct HDMI selection remain unverified and disabled. Power-on, IR and Wake-on-LAN are not included. No Samsung endorsement is implied.")
@Composable internal fun HelpCenter() {
    Text("Help center",style=MaterialTheme.typography.headlineSmall)
    helpTopics.forEach { (title,body) -> var expanded by rememberSaveable(title) { mutableStateOf(false) }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(title) };if(expanded) Text(body,fontSize=14.sp) } }
    }
}
@Composable internal fun PrivacyScreen(vm:RemoteViewModel) {
    var reset by remember { mutableStateOf(false) }
    Text("Privacy and local data",style=MaterialTheme.typography.headlineSmall)
    Text("TV profiles, preferences and encrypted pairing tokens stay on this device. Typed text is held only for transmission to the selected TV and cleared on backgrounding. No advertising, analytics, account or backend is included. Diagnostics are shared only when you invoke Android's share sheet; exported reports exclude device identity, IPs, tokens and keyboard input.")
    Text("Your TV, Android keyboard and a chosen share destination have their own policies. Unencrypted port-8001 TVs require a trusted local network. The local self-signed certificate exception is explicit and scoped, not a universal security guarantee.")
    OutlinedButton(onClick={reset=true}) { Text("Reset all saved data") }
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Reset all local data?")},text={Text("All TV profiles, pairing credentials, widgets' assignments, favorites and preferences will be removed. You will see onboarding again.")},confirmButton={TextButton(onClick={vm.resetDevices();reset=false}) { Text("Reset local data") }},dismissButton={TextButton(onClick={reset=false}) { Text("Cancel") }})
}



@Composable internal fun CertificateReviewDialog(
    review: com.indianservers.samsungsmarttvremote.data.network.CertificateReview,
    onApprove: (com.indianservers.samsungsmarttvremote.data.network.CertificateReview)->Unit,
    onCancel: ()->Unit
) {
    AlertDialog(
            onDismissRequest=onCancel,
            title={Text(if(review.changed) "Security Warning: TV Certificate Changed" else "Verify Your Samsung TV")},
            text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Your TV uses a local certificate that Android cannot verify automatically.")
                Text("${review.tv.name}\nTV IP: ${review.tv.ip}\nVerification: ${review.reason.name}")
                if(review.changed) Text("The saved certificate or endpoint differs. A network impersonator, TV reset or firmware update could cause this. Verify the device before approving a replacement.",color=MaterialTheme.colorScheme.error)
                Text("Certificate SHA-256 fingerprint",fontWeight=FontWeight.Bold)
                androidx.compose.foundation.text.selection.SelectionContainer { Text(review.fingerprint,fontSize=12.sp) }
                Text(if(review.approvable) "Confirm this is your TV on your home network. Trust is saved only for this TV and endpoint; Samsung pairing approval is still required." else "This certificate cannot be approved. Check dates and TV firmware, or reset trust for an endpoint change.")
            }},
            confirmButton={if(review.approvable) TextButton(onClick={onApprove(review)}) { Text("Trust Certificate & Connect") }},
            dismissButton={TextButton(onClick=onCancel) { Text("Cancel") }}
        )
}
