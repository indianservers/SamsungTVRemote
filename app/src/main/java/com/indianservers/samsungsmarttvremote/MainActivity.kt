package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.presentation.*
import com.indianservers.samsungsmarttvremote.ui.theme.SamsungSmartTVRemoveTheme
import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.*
import com.indianservers.samsungsmarttvremote.data.discovery.*
import com.indianservers.samsungsmarttvremote.data.network.*

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainActivity: ComponentActivity() {
    private val widgetIntent=MutableStateFlow<android.content.Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);widgetIntent.value=intent;setContent { val incoming by widgetIntent.collectAsState();RemoteApp(widgetIntent=incoming) } }
    override fun onNewIntent(intent:android.content.Intent) { super.onNewIntent(intent);setIntent(intent);widgetIntent.value=intent }
}
internal val Cobalt: Color @Composable get()=MaterialTheme.colorScheme.primary
@Composable fun RemoteApp(vm: RemoteViewModel=viewModel(),widgetIntent:android.content.Intent?=null) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val ready by vm.settingsReady.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val unsupported by vm.connection.unsupported.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("devices") }
    var manual by rememberSaveable { mutableStateOf("") }
    val certificateReview by vm.connection.certificateReview.collectAsStateWithLifecycle()
    var resetCertificate by remember { mutableStateOf(false) }
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var pendingWidget by remember { mutableStateOf<Triple<WidgetConfig,RemoteKey,DeviceProfile>?>(null) }
    var widgetMessage by remember { mutableStateOf("") }
    fun granted()=!LocalNetworkPermission.required(Build.VERSION.SDK_INT,context.applicationInfo.targetSdkVersion) || context.checkSelfPermission(LocalNetworkPermission.NAME)==PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(granted()) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission=it;if(!it) vm.permissionLost() }
    LifecycleResumeEffect(permission,ready,appearance.onboardingDone) {
        permission=granted()
        if(permission && ready && appearance.onboardingDone) { vm.resume();vm.start() } else if(!permission) vm.permissionLost()
        onPauseOrDispose { vm.pause() }
    }
    LaunchedEffect(state.status) { if(state.status==Status.Connected && screen in setOf("find","devices")) screen="remote" }
    LaunchedEffect(widgetIntent,ready,appearance.onboardingDone,permission) {
        if(ready && appearance.onboardingDone && widgetIntent?.action=="com.indianservers.samsungsmarttvremote.WIDGET") {
            val resolved=com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.resolve(context,widgetIntent)
            if(resolved!=null) {
                val profile=LocalStore(context).profilesFlow.first().firstOrNull { it.tv.id==resolved.first.deviceId }
                if(profile!=null) { pendingWidget=Triple(resolved.first,resolved.second,profile);screen="remote" }
            } else {
                val id=widgetIntent.getIntExtra("widgetId",-1)
                if(com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.belongs(context,id)) {
                    val config=LocalStore(context).widget(id)
                    val profile=LocalStore(context).profilesFlow.first().firstOrNull { it.tv.id==config?.deviceId }
                    if(profile!=null && permission) { vm.connectProfile(profile);screen="remote" } else screen="devices"
                }
            }
            widgetIntent.action=null
        }
    }
    androidx.activity.compose.BackHandler(enabled=screen !in setOf("devices","remote") && appearance.onboardingDone) { screen=if(state.tv==null) "devices" else "remote" }
    val feedback=LocalHapticFeedback.current
    val send:(RemoteKey)->Unit={if(haptics) feedback.performHapticFeedback(HapticFeedbackType.LongPress);vm.connection.send(it)}
    SamsungSmartTVRemoveTheme(appearance) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(if(appearance.theme==RemoteTheme.Minimal) 12.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(if(appearance.theme==RemoteTheme.Minimal) 10.dp else 18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(appearance.theme!=RemoteTheme.Minimal) Text("◉  SMART TV REMOTE",fontSize=13.sp,letterSpacing=3.sp,color=Cobalt,fontWeight=FontWeight.Bold)
                if(!ready) CircularProgressIndicator()
                else if(!appearance.onboardingDone) Onboarding(vm::completeOnboarding)
                else {
                    pendingWidget?.let { (config,key,profile) -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Widget action: ${key.accessibleLabel()} • ${profile.friendlyName}",fontWeight=FontWeight.Bold)
                        Text("Connect the selected TV, then explicitly send this action. Opening a widget does not imply delivery.",fontSize=13.sp)
                        if(key in unsupported) Text("This control was rejected by this TV and is disabled for this session.")
                        if(state.tv?.id!=profile.tv.id || state.status!=Status.Connected) Button(onClick={vm.connectProfile(profile)},enabled=permission) { Text("Connect this TV") }
                        Button(onClick={scope.launch {
                            val result=vm.connection.transmitKey(key)
                            LocalStore(context).widget(config.copy(status=if(result.sent) "Last: submitted; TV execution unconfirmed" else "Last: not sent; open app to retry"))
                            com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.updateAll(context)
                            widgetMessage=result.message;pendingWidget=null
                        }},enabled=permission && state.status==Status.Connected && state.tv?.id==profile.tv.id && key !in unsupported) { Text("Send widget action") }
                        TextButton(onClick={pendingWidget=null}) { Text("Cancel widget action") }
                    } } }
                    if(widgetMessage.isNotEmpty()) Text(widgetMessage,fontSize=13.sp)
                    if(screen!="remote") Row { TextButton(onClick={screen="devices"}) { Text("Connected TVs") };TextButton(onClick={screen="settings"}) { Text("Settings") } }
                    when(screen) {
                        "devices" -> DeviceDashboard(vm,onDiscover={screen="find"},onRemote={screen="remote"},onDiagnostics={screen="diagnostics"})
                        "find" -> {
                            Text("Connect Your\nSamsung TV",fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold)
                            Text("Connect your phone and TV to the same Wi-Fi network.")
                            if(!permission) {
                                Text("Local network permission is required.")
                                Button(onClick={if(LocalNetworkPermission.required(Build.VERSION.SDK_INT,context.applicationInfo.targetSdkVersion)) launcher.launch(LocalNetworkPermission.NAME)}) { Text("Allow local network") }
                                TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}) { Text("Open app permissions") }
                            } else {
                                if(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Searching your network…") }
                                TextButton(onClick=vm::refresh) { Text("Rescan televisions") }
                                if(devices.isEmpty() && !scanning) Text("No Samsung TVs found. Enter a private TV IP or try again.")
                                devices.forEach { tv -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(tv.name,fontWeight=FontWeight.Bold);Text("${tv.model} • ${tv.ip}")
                                    if(tv.port==8001) Text("Unencrypted port 8001. Use only on a trusted network.",fontSize=12.sp)
                                    Button(onClick={vm.connectDiscovered(tv)}) { Text("Connect") }
                                } } }
                                OutlinedTextField(value=manual,onValueChange={manual=it},label={Text("TV IP address")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                OutlinedButton(onClick={vm.manual(manual)}) { Text("Find TV by IP") }
                            }
                            TextButton(onClick={screen="devices"}) { Text("Saved televisions") }
                        }
                        "settings" -> {
                            Text("Settings",style=MaterialTheme.typography.headlineLarge)
                            listOf("Connected TVs" to "devices","Remote Preferences" to "preferences","Themes and Appearance" to "appearance","Widgets" to "widgets","Connection Diagnostics" to "diagnostics","Help" to "help","Privacy" to "privacy","About" to "about").forEach { (title,destination) -> OutlinedButton(onClick={screen=destination},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(title) } }
                            if(state.tv?.port==8002) OutlinedButton(onClick={resetCertificate=true}) { Text("Reset TV Certificate Trust") }
                            OutlinedButton(onClick=vm.connection::disconnect) { Text("Disconnect") }
                        }
                        "preferences" -> if(state.tv!=null) CustomizationSettings(vm) else Text("Select a saved television to customize its remote preferences.")
                        "appearance" -> AppearanceSettings(vm)
                        "diagnostics" -> DiagnosticsScreen(vm,permission,onRescan={screen="find";if(permission) vm.refresh()},onHelp={screen="help"})
                        "help" -> HelpCenter()
                        "privacy" -> PrivacyScreen(vm)
                        "widgets" -> WidgetSettings()
                        "about" -> { Text("Smart TV Remote",style=MaterialTheme.typography.headlineLarge);Text("Version 3.0.1 • Independent Samsung-compatible remote. Not affiliated with or endorsed by Samsung. Compatibility depends on model and firmware.\nLocal operation; no account, advertising or analytics.\nRelease preparation build: hardware and Play review remain required.") }
                        else -> ConnectedRemote(vm,send,onChangeTv={screen="devices"},onSettings={screen="settings"})
                    }
                    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
                    if(state.tv!=null) {
                        Text(state.message,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)

                    }
                    if(screen !in setOf("devices","remote")) TextButton(onClick={screen=if(state.tv==null) "devices" else "remote"}) { Text("Back to remote") }
                }
            }
        }
        certificateReview?.let { review -> CertificateReviewDialog(review,vm.connection::approveCertificate,vm.connection::cancelCertificateReview) }
        if(resetCertificate) AlertDialog(onDismissRequest={resetCertificate=false},title={Text("Reset TV Certificate Trust?")},text={Text("Remove the approved certificate for ${state.tv?.name ?: "this TV"} and disconnect. The next connection uses normal TLS verification and may require a new fingerprint review. Samsung pairing credentials are separate and remain saved.")},confirmButton={TextButton(onClick={resetCertificate=false;vm.connection.resetCertificate()}) { Text("Reset trust") }},dismissButton={TextButton(onClick={resetCertificate=false}) { Text("Cancel") }})
    }
}
@Composable internal fun RemoteButton(label:String,key:RemoteKey,enabled:Boolean,send:(RemoteKey)->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance=com.indianservers.samsungsmarttvremote.ui.theme.LocalAppearance.current
    val pressScale by animateFloatAsState(if(pressed && appearance.motion>0f) .94f else 1f,animationSpec=androidx.compose.animation.core.tween((100*appearance.motion).toInt()),label="button press")
    val active=enabled && key !in LocalUnsupported.current
    var repeated by remember { mutableStateOf(false) }
    val currentSend by rememberUpdatedState(send)
    val repeatSender=LocalRepeat.current
    val alive=remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    SideEffect { alive.set(pressed && active) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    LaunchedEffect(pressed,active) {
        if(pressed && active) {
            repeated=false
            if(key in setOf(RemoteKey.VOLUP,RemoteKey.VOLDOWN,RemoteKey.CHUP,RemoteKey.CHDOWN,RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT)) {
                delay(350); repeated=true
                while(isActive) { repeatSender?.invoke(key) { alive.get() } ?: currentSend(key); delay(160) }
            }
        }
    }
    FilledTonalButton(onClick={if(!repeated) send(key)},interactionSource=interaction,enabled=active,contentPadding=PaddingValues(horizontal=14.dp,vertical=14.dp),modifier=Modifier.heightIn(min=(52*appearance.buttonSize).dp).graphicsLayer { scaleX=pressScale; scaleY=pressScale }.semantics { contentDescription=key.accessibleLabel() }) {
        RemoteGlyph(key,LocalContentColor.current)
        if(key !in setOf(RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT,RemoteKey.VOLUP,RemoteKey.VOLDOWN,RemoteKey.CHUP,RemoteKey.CHDOWN)) {
            Spacer(Modifier.width(6.dp)); Text(label.filter { it.isLetter() || it==' ' }.trim(),fontWeight=FontWeight.SemiBold)
        }
    }
}
internal val LocalUnsupported=compositionLocalOf<Set<RemoteKey>> { emptySet() }



internal val LocalRepeat=compositionLocalOf<((RemoteKey,()->Boolean)->Unit)?> { null }









