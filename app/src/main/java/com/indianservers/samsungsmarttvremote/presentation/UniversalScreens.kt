package com.indianservers.samsungsmarttvremote.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.RemoteGlyph

@Composable internal fun SettingsRow(title: String,subtitle: String,key: RemoteKey=RemoteKey.MENU,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            RemoteGlyph(key,MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›",fontSize=24.sp)
        }
    }
}
@Composable internal fun BrandSettings(vm:RemoteViewModel,onDiscover:()->Unit) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    Text("TV brands",style=MaterialTheme.typography.headlineSmall)
    Text("Choose a brand for your next discovery. Your currently connected TV stays selected until you switch devices.")
    OutlinedTextField(value=search,onValueChange={search=it},label={Text("Search ${TvBrands.all.size} brands")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Text("A brand name is not a compatibility guarantee. This app checks model-reported services; Samsung menu control and compatible local-media control are separate.",style=MaterialTheme.typography.bodySmall)
    TvBrands.all.filter { it.name.contains(search,true) || it.aliases.any { alias->alias.contains(search,true) } }.forEach { brand ->
        OutlinedCard(Modifier.fillMaxWidth().clickable { vm.brand(brand.name) }) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=appearance.brand==brand.name,onClick={vm.brand(brand.name)})
                Column(Modifier.weight(1f)) { Text(brand.name,fontWeight=FontWeight.Bold);Text(brand.note,style=MaterialTheme.typography.bodySmall) }
            }
            if(appearance.brand==brand.name) TextButton(onClick=onDiscover,modifier=Modifier.padding(horizontal=12.dp)) { Text("Find ${brand.name} TVs") }
        }
    }
}
@Composable internal fun ConnectionBanner(vm:RemoteViewModel,onPermission:()->Unit,onDiagnostics:()->Unit) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val diagnostic by vm.connection.diagnostics.collectAsStateWithLifecycle()
    if(state.tv==null || state.status==Status.Connected || state.status==Status.Discovering) return
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(state.status==Status.Failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(state.status.label(),fontWeight=FontWeight.Bold)
            Text(state.message,style=MaterialTheme.typography.bodySmall)
            if(state.status in setOf(Status.Connecting,Status.Reconnecting,Status.AwaitingAuthorization)) {
                val stages=listOf("Connect","Verify certificate","Approve on TV","Ready")
                val stage=if(state.status==Status.AwaitingAuthorization) 2 else 0
                Text(stages.mapIndexed { index,s -> if(index==stage) "[$s]" else s }.joinToString(" → "),fontSize=12.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else Row {
                TextButton(onClick={if(diagnostic.failure==FailureKind.Permission) onPermission() else vm.connection.reconnect()}) { Text(if(diagnostic.failure==FailureKind.Permission) "Open permissions" else if(state.message.contains("certificate",true)) "Review certificate" else "Retry connection") }
                TextButton(onClick=onDiagnostics) { Text("Details") }
            }
        }
    }
}
@Composable internal fun MediaSliders(vm:RemoteViewModel) {
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val media by vm.connection.media.collectAsStateWithLifecycle()
    var volumeDrag by remember(state.tv?.id) { mutableStateOf<Float?>(null) }
    var seekDrag by remember(state.tv?.id,media.track) { mutableStateOf<Float?>(null) }
    val ready=state.status==Status.Connected
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Volume & playback",fontWeight=FontWeight.Bold)
        Text(if(volumeDrag!=null) "Set volume: ${volumeDrag!!.toInt()}" else "Volume: ${media.volume?.toString() ?: "not reported"}",style=MaterialTheme.typography.bodyMedium)
        Slider(value=(volumeDrag ?: media.volume?.toFloat() ?: media.minimum.toFloat()).coerceIn(media.minimum.toFloat(),media.maximum.toFloat()),
            onValueChange={volumeDrag=it},onValueChangeFinished={volumeDrag?.let { vm.connection.setVolume(it.toInt()) };volumeDrag=null},
            enabled=ready && media.canVolume && !media.busy,valueRange=media.minimum.toFloat()..media.maximum.toFloat(),
            modifier=Modifier.semantics { contentDescription="TV-reported volume slider" })
        val duration=media.duration?.takeIf { it>0 } ?: 1
        val position=(seekDrag?.toLong() ?: media.position ?: 0).coerceAtLeast(0)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(media.position==null) "Playback position unavailable" else LocalMediaPolicy.time(position),style=MaterialTheme.typography.bodySmall)
            Text(media.duration?.let(LocalMediaPolicy::time) ?: "--:--",style=MaterialTheme.typography.bodySmall)
        }
        Slider(value=if(media.duration!=null && media.duration!!>0) position.coerceAtMost(duration).toFloat() else 0f,onValueChange={seekDrag=it},onValueChangeFinished={seekDrag?.let { vm.connection.seekTo(it.toLong()) };seekDrag=null},
            valueRange=0f..duration.toFloat(),enabled=ready && media.canSeek && !media.busy,modifier=Modifier.semantics { contentDescription="Playback seek position" })
        if(media.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(media.message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!media.canVolume || !media.canSeek) Text("Sliders need TV-reported ranges and a seekable local-media timeline. Samsung +/- and media keys remain independent.",style=MaterialTheme.typography.bodySmall)
        Row {
            if(state.tv?.transport==null && state.tv?.rendering==null) TextButton(onClick=vm::checkMedia,enabled=ready) { Text("Check media capabilities") }
            else TextButton(onClick=vm.connection::refreshMedia,enabled=ready && !media.busy) { Text("Refresh media") }
        }
    } }
}
@Composable internal fun QuickTvPicker(vm:RemoteViewModel,onDismiss:()->Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.connection.state.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=onDismiss,title={Text("Switch television")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(profiles.isEmpty()) Text("No saved TVs yet. Open Devices to discover one.")
        profiles.forEach { profile -> SettingsRow(profile.friendlyName,"${profile.room} • ${profile.tv.brand}${if(state.tv?.id==profile.tv.id) " • selected" else ""}") { if(state.tv?.id!=profile.tv.id || state.status!=Status.Connected) vm.connectProfile(profile);onDismiss() } }
    }},confirmButton={TextButton(onClick=onDismiss) { Text("Done") }})
}
@Composable internal fun WidgetPreview(advanced: Boolean) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(advanced) "Advanced Remote" else "Compact Remote",fontWeight=FontWeight.Bold)
            Text("Living Room TV • example preview",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) { listOf(RemoteKey.POWER,RemoteKey.VOLDOWN,RemoteKey.VOLUP,RemoteKey.MUTE).forEach { RemoteGlyph(it,MaterialTheme.colorScheme.primary) } }
            if(advanced) Text("↑  ←  OK  →  ↓       Back    Home")
            Text("Tap → connect in app → confirm Send",style=MaterialTheme.typography.bodySmall)
        }
    }
}
