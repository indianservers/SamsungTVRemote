package com.indianservers.samsungsmarttvremote.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.*
import com.indianservers.samsungsmarttvremote.domain.*

@Composable internal fun ConnectedRemote(vm: RemoteViewModel,send: (RemoteKey)->Unit,onChangeTv: ()->Unit,onSettings: ()->Unit,compactHeader: Boolean=false) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val unsupported by vm.connection.unsupported.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val caps by vm.advanced.capabilities.state.collectAsStateWithLifecycle()
    val notice by vm.advanced.notice.collectAsStateWithLifecycle()
    val appearance=com.indianservers.samsungsmarttvremote.ui.theme.LocalAppearance.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val connected=state.status==Status.Connected
    var tab by rememberSaveable(state.tv?.id) { mutableStateOf(LandingTab.Remote) }
    var customized by rememberSaveable(state.tv?.id) { mutableStateOf(false) }
    var panel by rememberSaveable(state.tv?.id) { mutableStateOf("") }
    var pickTv by remember { mutableStateOf(false) }
    var panelsMenu by remember { mutableStateOf(false) }
    LaunchedEffect(preferences.landing) { if(!customized) tab=preferences.landing }
    DisposableEffect(tab,panel) { onDispose { if(tab==LandingTab.Keyboard) vm.advanced.cancelSensitiveAction() } }
    CompositionLocalProvider(LocalUnsupported provides unsupported,LocalRepeat provides { key,guard -> vm.connection.send(key,guard) }) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(!compactHeader) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextButton(onClick={pickTv=true},contentPadding=PaddingValues(0.dp)) { Text((profiles.firstOrNull { it.tv.id==state.tv?.id }?.friendlyName ?: state.tv?.name ?: "Choose a TV")+" ▾",fontSize=21.sp,fontWeight=FontWeight.Bold,maxLines=2) }
                    val color by animateColorAsState(if(connected) Color(0xFF158360) else Color(0xFF9A6A21),animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="connection")
                    Text("${if(connected) "✓" else "○"} ${state.status.label()}",color=color,fontSize=13.sp)
                }
                RemoteButton("Power off",RemoteKey.POWER,connected,send,compact=true)
            }

            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                PrimaryTabRow(selectedTabIndex=tab.ordinal,modifier=Modifier.weight(1f)) {
                    LandingTab.entries.forEach { item -> Tab(selected=tab==item && panel.isEmpty(),onClick={customized=true;tab=item;panel=""},modifier=Modifier.heightIn(min=48.dp)) { Text(item.name,fontSize=12.sp,maxLines=2,softWrap=true) } }
                }
                Box {
                    IconButton(onClick={panelsMenu=true},modifier=Modifier.semantics { contentDescription="Remote panels" }) { Text("⋯",fontSize=24.sp) }
                    DropdownMenu(expanded=panelsMenu,onDismissRequest={panelsMenu=false}) {
                        listOf("Sources","Favorites","More").forEach { name -> DropdownMenuItem(text={Text((if(panel==name) "✓ " else "")+name)},onClick={panel=name;panelsMenu=false}) }
                    }
                }
            }
            if(panel.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically) { AssistChip(onClick={panel=""},label={Text("${panel} • Back to ${tab.name}")}) }
            if(state.tv?.platform==TvPlatform.Dlna) Text("Local media connection • menu navigation, keyboard and apps are unavailable on this adapter.",style=MaterialTheme.typography.bodySmall)
            if(state.tv==null) Button(onClick=onChangeTv) { Text("Choose a television") }
            Crossfade(targetState=panel.ifEmpty { tab.name },animationSpec=androidx.compose.animation.core.tween((200*appearance.motion).toInt()),label="remote navigation") { page ->
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    when(page) {
                        "Sources" -> SourcesPanel(connected,caps,send)
                        "Favorites" -> FavoritesPanel(vm,connected,send)
                        "More" -> AdvancedControls(vm,connected,send)
                        "Touchpad" -> if(state.tv?.platform!=TvPlatform.Dlna) TouchpadPanel(vm,connected,send) else Text("Touchpad needs a menu-control adapter for this model.")
                        "Keyboard" -> if(state.tv?.platform!=TvPlatform.Dlna) KeyboardPanel(vm,connected,send) else Text("Keyboard input is unavailable through local-media services.")
                        "Apps" -> if(state.tv?.platform!=TvPlatform.Dlna) AppsPanel(vm,connected) else Text("App listing and launching need a platform-specific adapter.")
                        else -> {
                            if(state.tv?.platform==TvPlatform.Dlna) {
                                MediaSliders(vm)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { RemoteButton("Play",RemoteKey.PLAY,connected,send);RemoteButton("Pause",RemoteKey.PAUSE,connected,send);RemoteButton("Stop",RemoteKey.STOP,connected,send) }
                            } else Phase1Controls(vm,connected,send)
                            val pins=preferences.favorites.take(3)
                            if(pins.isNotEmpty()) { SectionTitle("QUICK ACCESS"); pins.forEach { FavoriteLaunch(it,vm,connected,send) } }
                        }
                    }
                }
            }
            if(pickTv) QuickTvPicker(vm,onDismiss={pickTv=false})
            if(!connected && state.tv!=null) OutlinedButton(onClick=vm.connection::reconnect) { Text("Reconnect television") }
            if(notice.isNotBlank()) Text(notice,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Phase1Controls(vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val unsupported by vm.connection.unsupported.collectAsStateWithLifecycle()
    val height=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val appearance=com.indianservers.samsungsmarttvremote.ui.theme.LocalAppearance.current
    val pad=((if(height<740) 184 else 220)*appearance.buttonSize).dp
    if(appearance.mediaFirst) MediaSliders(vm)
    Box(Modifier.widthIn(max=pad).fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(Color(0xFF4384F8),Color(0xFF1D4ED8))),CircleShape).padding(8.dp)) {
        Box(Modifier.align(Alignment.TopCenter)) { RemoteButton("Up",RemoteKey.UP,connected,send,compact=true) }
        Box(Modifier.align(Alignment.BottomCenter)) { RemoteButton("Down",RemoteKey.DOWN,connected,send,compact=true) }
        Box(Modifier.align(Alignment.CenterStart)) { RemoteButton("Left",RemoteKey.LEFT,connected,send,compact=true) }
        Box(Modifier.align(Alignment.CenterEnd)) { RemoteButton("Right",RemoteKey.RIGHT,connected,send,compact=true) }
        Box(Modifier.align(Alignment.Center)) { Button(onClick={send(RemoteKey.ENTER)},enabled=connected && RemoteKey.ENTER !in unsupported,shape=CircleShape,contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=Cobalt),modifier=Modifier.size(52.dp)) { Text("OK",fontSize=20.sp) } }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) { RemoteButton("Back",RemoteKey.RETURN,connected,send,compact=true); RemoteButton("Home",RemoteKey.HOME,connected,send,compact=true);RemoteButton("Menu",RemoteKey.MENU,connected,send,compact=true) }
    Row(horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) { RemoteButton("Volume down",RemoteKey.VOLDOWN,connected,send,compact=true);RemoteButton("Mute",RemoteKey.MUTE,connected,send,compact=true);RemoteButton("Volume up",RemoteKey.VOLUP,connected,send,compact=true) }
    var extra by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick={extra=!extra}) { Text(if(extra) "Hide playback & channel controls" else "Volume slider, playback & channels") }
    if(extra) {
        if(!appearance.mediaFirst) MediaSliders(vm)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { RemoteButton("Input",RemoteKey.SOURCE,connected,send);RemoteButton("Play",RemoteKey.PLAY,connected,send,compact=true);RemoteButton("Pause",RemoteKey.PAUSE,connected,send,compact=true) }
        if("channels" !in prefs.hiddenGroups) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { RemoteButton("Channel down",RemoteKey.CHDOWN,connected,send);RemoteButton("Channel up",RemoteKey.CHUP,connected,send) }
    }
}

@Composable internal fun SectionTitle(title: String) { Text(title,color=Cobalt,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp) }

@Composable private fun SourcesPanel(connected: Boolean,caps: TVCapabilities,send: (RemoteKey)->Unit) {
    SectionTitle("INPUT SOURCES")
    Text("Open the TV's source menu, then choose the input using the directional pad. No universal HDMI navigation sequence is assumed.",fontSize=14.sp)
    RemoteButton("Open source menu",RemoteKey.SOURCE,connected,send)
    /* Direct input candidates are hidden until a model adapter verifies them. */
    if(caps[Feature.DirectInput].support==Support.Supported) InputSources.candidates.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { row.forEach { source ->
        OutlinedButton(onClick={InputSources.command(source,caps)?.let(send)},enabled=connected && InputSources.command(source,caps)!=null,modifier=Modifier.weight(1f).heightIn(min=52.dp)) { Text(source.name) }
    } } }
    Text("Direct HDMI selection is unavailable for this adapter. Use the source menu and directional controls.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    DirectionRows(connected,send)
    Row { RemoteButton("Back",RemoteKey.RETURN,connected,send); RemoteButton("Home",RemoteKey.HOME,connected,send) }
}
@Composable internal fun DirectionRows(connected: Boolean,send: (RemoteKey)->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        RemoteButton("Up",RemoteKey.UP,connected,send)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { RemoteButton("Left",RemoteKey.LEFT,connected,send); RemoteButton("Select",RemoteKey.ENTER,connected,send); RemoteButton("Right",RemoteKey.RIGHT,connected,send) }
        RemoteButton("Down",RemoteKey.DOWN,connected,send)
    }
}
@Composable private fun AdvancedControls(vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    SectionTitle("ADVANCED CONTROLS")
    Text("Candidate Samsung keys • behavior varies by model. A reported TV error disables the most recent key for this session.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if("numeric" !in prefs.hiddenGroups) {
        (1..9).chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { digit ->
            val key=RemoteKey.valueOf("DIGIT_$digit")
            Button(onClick={send(key)},enabled=connected && key !in LocalUnsupported.current,modifier=Modifier.weight(1f).height(58.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.filledTonalButtonColors()) { Text(digit.toString(),fontSize=22.sp) }
        } } }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={send(RemoteKey.DIGIT_0)},enabled=connected && RemoteKey.DIGIT_0 !in LocalUnsupported.current,modifier=Modifier.heightIn(min=52.dp)) { Text("0",fontSize=22.sp) }
            RemoteButton("Enter",RemoteKey.ENTER,connected,send)
        }
        RemoteButton("Previous channel",RemoteKey.PRECH,connected,send)
        RemoteButton("Channel list",RemoteKey.CH_LIST,connected,send)
    }
    if("media" !in prefs.hiddenGroups) {
        SectionTitle("MEDIA")
        listOf(listOf(RemoteKey.PLAY,RemoteKey.PAUSE),listOf(RemoteKey.STOP,RemoteKey.REWIND),listOf(RemoteKey.FF,RemoteKey.RETURN)).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { row.forEach { key -> RemoteButton(when(key) { RemoteKey.FF->"Forward";RemoteKey.RETURN->"Back";else->key.name.lowercase().replaceFirstChar { it.uppercase() } },key,connected,send) } }
        }
        RemoteButton("Home",RemoteKey.HOME,connected,send)
        Text("Rewind and fast-forward are seek keys; previous/next-track support is not assumed.",fontSize=13.sp)
    }
    TextButton(onClick={vm.updatePreferences { it.copy(hiddenGroups=emptySet()) }}) { Text("Show all control groups") }
}

@Composable internal fun CustomizationSettings(vm: RemoteViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val caps by vm.advanced.capabilities.state.collectAsStateWithLifecycle()
    SectionTitle("MAKE IT YOURS")
    Text("Default landing tab")
    LandingTab.entries.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { tab -> FilterChip(selected=prefs.landing==tab,onClick={vm.updatePreferences { it.copy(landing=tab) }},label={Text(tab.name)},modifier=Modifier.heightIn(min=48.dp)) } } }
    listOf("numeric","media","channels").forEach { group -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Show $group controls")
        Switch(checked=group !in prefs.hiddenGroups,onCheckedChange={shown->vm.updatePreferences { it.copy(hiddenGroups=if(shown) it.hiddenGroups-group else it.hiddenGroups+group) }})
    } }
    SectionTitle("TV CAPABILITIES")
    Feature.entries.forEach { feature -> Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
        Text("${feature.name}: ${caps[feature].support}",fontWeight=FontWeight.SemiBold)
        Text(caps[feature].evidence,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}




