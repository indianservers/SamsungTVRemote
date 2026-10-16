package com.indianservers.samsungsmarttvremote.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.domain.*

@Composable internal fun CastingScreen(vm:RemoteViewModel,onDevices:()->Unit) {
    val context=LocalContext.current
    val remote by vm.connection.state.collectAsStateWithLifecycle()
    val media by vm.connection.media.collectAsStateWithLifecycle()
    val casting by vm.casting.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Video (MP4)") }
    var formats by remember { mutableStateOf(false) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm.casting::selectFile) }
    val ready=remote.status==Status.Connected
    val canCast=ready && media.actions.containsAll(setOf("SetAVTransportURI","Play"))
    Text("Mirror & cast",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    PrimaryTabRow(selectedTabIndex=tab) {
        Tab(selected=tab==0,onClick={tab=0},text={Text("Screen Mirror")})
        Tab(selected=tab==1,onClick={tab=1},text={Text("Media Casting")})
    }
    if(tab==0) {
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Mirror your phone screen",style=MaterialTheme.typography.titleLarge)
            Text("Use your phone’s native Cast, Screen Cast or Smart View controls to share your display. Phone and TV must support the same mirroring protocol.")
            Text("Screen sharing can expose notifications, passwords and other private content to everyone watching the TV. Hide sensitive content first, choose a TV you trust and approve sharing in Android. Stop sharing in the system controls when finished.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={
                try { context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS));message="Choose your TV and approve screen sharing in the phone’s system controls." }
                catch(_:ActivityNotFoundException) { message="This phone does not expose Android Cast settings. Open Quick Settings and look for Smart View, Screen Cast or Wireless Display." }
                catch(_:SecurityException) { message="Android did not allow this settings screen. Open the phone’s mirroring tile in Quick Settings." }
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Open phone mirroring controls") }
            Text("Start and stop mirroring in those system controls. This app cannot verify that mirroring started and does not capture your screen itself.",style=MaterialTheme.typography.bodySmall)
            Text("Samsung TVs commonly need a compatible Smart View/Miracast sender. Google Cast-only phones and receivers may not match. A working remote connection does not establish mirroring compatibility.",style=MaterialTheme.typography.bodySmall)
            Text("Protected video may appear black; app audio and latency depend on the phone and receiver.",style=MaterialTheme.typography.bodySmall)
            if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.primary)
        } }
    } else {
        Text("Receiver: ${remote.tv?.name ?: "No TV selected"}",fontWeight=FontWeight.Bold)
        Text("Cast a selected photo, video, audio file or direct media URL to a compatible UPnP/DLNA renderer. This is separate from full-screen mirroring.",style=MaterialTheme.typography.bodySmall)
        if(!ready) Button(onClick=onDevices) { Text("Connect a television") }
        else if(!canCast) {
            Text("The selected TV has not advertised SetAVTransportURI and Play. Media casting stays disabled until those capabilities are available.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=vm::checkMedia) { Text("Check media capabilities") }
        }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Cast a file",style=MaterialTheme.typography.titleMedium)
            Text("Choose only media you own or have permission to share. Selection grants access to that file; transmission begins only when you tap Cast selected file. Transfer uses unencrypted local HTTP: use a trusted network. Stop casting, backgrounding or disconnecting ends local sharing.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={picker.launch(arrayOf("video/*","audio/*","image/*"))},enabled=!casting.busy) { Text("Choose photo, video or audio") }
            casting.file?.let { file ->
                Text(file.name,fontWeight=FontWeight.SemiBold)
                Text("${file.mime} • ${file.bytes/1024} KB",style=MaterialTheme.typography.bodySmall)
                Button(onClick=vm.casting::castFile,enabled=canCast && !casting.busy) { Text("Cast selected file") }
                TextButton(onClick=vm.casting::clearFile,enabled=!casting.busy) { Text("Clear selected file") }
            }
            Text("Only the selected file is shared over an unencrypted, temporary phone URL restricted to this TV. Keep this app open: leaving it or disconnecting stops local sharing.",style=MaterialTheme.typography.bodySmall)
        } }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Cast a media link",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(value=url,onValueChange={url=it},label={Text("Direct media URL")},supportingText={Text("Direct MP4/WebM, audio or image URL; not a web page or DRM stream.")},isError=url.isNotBlank() && !CastMediaPolicy.validUrl(url),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),singleLine=true,modifier=Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick={formats=true}) { Text(format+" ▾") }
                DropdownMenu(expanded=formats,onDismissRequest={formats=false}) { CastMediaPolicy.types.keys.forEach { name -> DropdownMenuItem(text={Text(name)},onClick={format=name;formats=false}) } }
            }
            Text("Cast sends the complete link to the selected TV, including any access token in its query. The TV contacts the media host, which may receive the TV's IP address. Avoid private links and share only authorized content. Playback may continue after you leave this app.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={vm.casting.castUrl(url.trim(),CastMediaPolicy.types.getValue(format))},enabled=canCast && !casting.busy && CastMediaPolicy.validUrl(url.trim())) { Text("Cast this URL") }
            Text("The TV fetches this URL directly. Its internet access, format and codec support determine playback. A receiver may continue a web stream after you leave this app.",style=MaterialTheme.typography.bodySmall)
        } }
        if(casting.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(casting.sharing) Text("Local file sharing is active for the selected TV",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        Text(casting.notice,style=MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick=vm.casting::stopPlayback,enabled=casting.sharing || ready && "Stop" in media.actions) { Text("Stop casting / playback") }
        if(ready) MediaSliders(vm)
    }
}
