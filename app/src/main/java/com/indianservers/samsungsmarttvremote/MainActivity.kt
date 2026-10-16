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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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
    val wifiAvailable by vm.wifiAvailable.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val unsupported by vm.connection.unsupported.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("devices") }
    var manual by rememberSaveable { mutableStateOf("") }
    val certificateReview by vm.connection.certificateReview.collectAsStateWithLifecycle()
    var resetCertificate by remember { mutableStateOf(false) }
    var quickTv by remember { mutableStateOf(false) }
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
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            topBar={if(ready && appearance.onboardingDone) Surface(tonalElevation=1.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(screen !in setOf("devices","remote","settings")) TextButton(onClick={screen=if(state.tv==null) "devices" else "remote"}) { Text("‹ Back") }
                    Column(Modifier.weight(1f)) { Text(profiles.firstOrNull { it.tv.id==state.tv?.id }?.friendlyName ?: "Universal TV Remote",fontWeight=FontWeight.Bold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text(state.tv?.let { "${it.brand} • ${state.status.label()}" } ?: "Choose your television",style=MaterialTheme.typography.bodySmall) }
                    TextButton(onClick={quickTv=true}) { Text("Switch TV") }
                    if(screen=="remote") RemoteButton("Power off",RemoteKey.POWER,state.status==Status.Connected && RemoteKey.POWER !in unsupported,send,compact=true)
                }
            }},
            bottomBar={if(ready && appearance.onboardingDone) NavigationBar {
                listOf(Triple("Remote","remote",RemoteKey.ENTER),Triple("Devices","devices",RemoteKey.SOURCE),Triple("Cast","cast",RemoteKey.PLAY),Triple("Settings","settings",RemoteKey.MENU)).forEach { (label,destination,icon) ->
                    NavigationBarItem(modifier=Modifier.semantics { contentDescription="Open $label" },selected=screen==destination,onClick={screen=destination},icon={RemoteGlyph(icon,LocalContentColor.current)},label={Text(label)})
                }
            }}
        ) { padding ->
            Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max=760.dp).padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {

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
                    ConnectionBanner(vm,onPermission={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))},onDiagnostics={screen="diagnostics"})
                    when(screen) {
                        "devices" -> DeviceDashboard(vm,onDiscover={screen="find"},onRemote={screen="remote"},onDiagnostics={screen="diagnostics"})
                        "find" -> {
                            Text("Find your ${appearance.brand} TV",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            TextButton(onClick={screen="brands"}) { Text("Change TV brand") }
                            Text("Connect your phone and TV to the same Wi-Fi network.")
                            if(!permission) {
                                Text("Local network permission is required.")
                                Button(onClick={if(LocalNetworkPermission.required(Build.VERSION.SDK_INT,context.applicationInfo.targetSdkVersion)) launcher.launch(LocalNetworkPermission.NAME)}) { Text("Allow local network") }
                                TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}) { Text("Open app permissions") }
                            } else {
                                if(!wifiAvailable) {
                                    Text("Wi-Fi is unavailable",fontWeight=FontWeight.Bold)
                                    Text("Connect your phone to the TV’s local network before scanning.",style=MaterialTheme.typography.bodySmall)
                                    TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))}) { Text("Open Wi-Fi settings") }
                                }
                                if(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Searching your network…") }
                                TextButton(onClick=vm::refresh,enabled=!scanning) { Text("Rescan televisions") }
                                if(devices.isEmpty() && !scanning) { Text("No compatible ${appearance.brand} TVs found",fontWeight=FontWeight.Bold);Text("Check the same Wi-Fi, TV network-control settings and selected brand. You can also enter its private IP.",style=MaterialTheme.typography.bodySmall) }
                                devices.forEach { tv -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(tv.name,fontWeight=FontWeight.Bold);Text("${tv.model} • ${tv.ip}")
                                    Text(if(tv.platform==TvPlatform.Dlna) "Local media only • no menu navigation • unencrypted" else "Samsung remote",style=MaterialTheme.typography.bodySmall)
                                    if(tv.port==8001) Text("Unencrypted port 8001. Use only on a trusted network.",fontSize=12.sp)
                                    Button(onClick={vm.connectDiscovered(tv)}) { Text("Connect") }
                                } } }
                                OutlinedTextField(value=manual,onValueChange={manual=it},label={Text("TV IP address")},singleLine=true,isError=manual.isNotBlank() && !SamsungProtocol.localIp(manual.trim()),supportingText={Text(if(manual.isNotBlank() && !SamsungProtocol.localIp(manual.trim())) "Use a private IPv4 address, for example 192.168.1.20" else "Find this under the TV’s network settings")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(SamsungProtocol.localIp(manual.trim())) vm.manual(manual)}),modifier=Modifier.fillMaxWidth())
                                OutlinedButton(onClick={vm.manual(manual)},enabled=SamsungProtocol.localIp(manual.trim()) && !error.startsWith("Checking")) { Text(if(error.startsWith("Checking")) "Checking TV…" else "Find TV by IP") }
                            }
                            TextButton(onClick={screen="devices"}) { Text("Saved televisions") }
                        }
                        "settings" -> {
                            Text("Settings",style=MaterialTheme.typography.headlineLarge)
                            Text("Connection",color=Cobalt,fontWeight=FontWeight.Bold)
                            SettingsRow("Connected TVs","Manage rooms, names and saved televisions",RemoteKey.SOURCE) {screen="devices"}
                            SettingsRow("Screen Mirror & Casting","Mirror controls, files and direct media links",RemoteKey.PLAY) {screen="cast"}
                            SettingsRow("TV brands","${appearance.brand} • ${TvBrands.all.size} brand choices",RemoteKey.SOURCE) {screen="brands"}
                            SettingsRow("Connection Diagnostics","Status, recovery and certificate details") {screen="diagnostics"}
                            Text("Personalize",color=Cobalt,fontWeight=FontWeight.Bold)
                            SettingsRow("Remote Preferences","Default tab and control groups") {screen="preferences"}
                            SettingsRow("Themes and Appearance","Colors, size, animation and haptics") {screen="appearance"}
                            SettingsRow("Widgets","Preview and add home-screen controls") {screen="widgets"}
                            Text("Support & privacy",color=Cobalt,fontWeight=FontWeight.Bold)
                            SettingsRow("Help","Troubleshooting and model limitations") {screen="help"}
                            SettingsRow("Privacy","Local storage and data reset") {screen="privacy"}
                            SettingsRow("About","Version and independent-app information") {screen="about"}
                            if(state.tv?.platform==TvPlatform.Samsung && state.tv?.port==8002) OutlinedButton(onClick={resetCertificate=true}) { Text("Reset TV Certificate Trust") }
                            OutlinedButton(onClick=vm.connection::disconnect) { Text("Disconnect") }
                        }
                        "preferences" -> if(state.tv!=null) CustomizationSettings(vm) else Text("Select a saved television to customize its remote preferences.")
                        "cast" -> CastingScreen(vm,onDevices={screen="devices"})
                        "brands" -> BrandSettings(vm,onDiscover={screen="find";if(permission) vm.refresh()})
                        "appearance" -> AppearanceSettings(vm)
                        "diagnostics" -> DiagnosticsScreen(vm,permission,onRescan={screen="find";if(permission) vm.refresh()},onHelp={screen="help"})
                        "help" -> HelpCenter()
                        "privacy" -> PrivacyScreen(vm)
                        "widgets" -> WidgetSettings()
                        "about" -> AboutAndDisclaimers()
                        else -> ConnectedRemote(vm,send,onChangeTv={screen="devices"},onSettings={screen="settings"},compactHeader=true)
                    }
                    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)

                }
            }
        }
        if(quickTv) QuickTvPicker(vm,onDismiss={quickTv=false})
        certificateReview?.let { review -> CertificateReviewDialog(review,vm.connection::approveCertificate,vm.connection::cancelCertificateReview) }
        if(resetCertificate) AlertDialog(onDismissRequest={resetCertificate=false},title={Text("Reset TV Certificate Trust?")},text={Text("Remove the approved certificate for ${state.tv?.name ?: "this TV"} and disconnect. The next connection uses normal TLS verification and may require a new fingerprint review. Samsung pairing credentials are separate and remain saved.")},confirmButton={TextButton(onClick={resetCertificate=false;vm.connection.resetCertificate()}) { Text("Reset trust") }},dismissButton={TextButton(onClick={resetCertificate=false}) { Text("Cancel") }})
    }
}
@Composable internal fun RemoteButton(label:String,key:RemoteKey,enabled:Boolean,send:(RemoteKey)->Unit,compact:Boolean=false) {
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
        if(!compact && key !in setOf(RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT,RemoteKey.VOLUP,RemoteKey.VOLDOWN,RemoteKey.CHUP,RemoteKey.CHDOWN)) {
            Spacer(Modifier.width(6.dp)); Text(label.filter { it.isLetter() || it==' ' }.trim(),fontWeight=FontWeight.SemiBold)
        }
    }
}
internal val LocalUnsupported=compositionLocalOf<Set<RemoteKey>> { emptySet() }



internal val LocalRepeat=compositionLocalOf<((RemoteKey,()->Boolean)->Unit)?> { null }









