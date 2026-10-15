package com.indianservers.samsungsmarttvremote.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.indianservers.samsungsmarttvremote.data.storage.FavoritesManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.RemoteButton
import com.indianservers.samsungsmarttvremote.domain.*

@Composable internal fun AppsPanel(vm: RemoteViewModel,connected: Boolean) {
    val apps by vm.advanced.apps.collectAsStateWithLifecycle()
    val busy by vm.advanced.busy.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val caps by vm.advanced.capabilities.state.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    SectionTitle("TV APPLICATIONS")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick=vm.advanced::refreshApps,enabled=connected && !busy,modifier=Modifier.heightIn(min=52.dp)) { Text(if(busy) "Working…" else "Refresh apps") }
        OutlinedButton(onClick={add=true},modifier=Modifier.heightIn(min=52.dp)) { Text("Add shortcut") }
    }
    if(apps.isEmpty()) {
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Your TV's apps, in one place",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text("Refresh to request installed apps. No installed applications have been reported yet.",fontSize=14.sp)
            Text("YouTube · Netflix · Prime Video · Disney+ · Samsung TV Plus · Internet Browser",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Availability and IDs vary by model and region. Custom shortcuts require your TV-specific app identifier.",fontSize=13.sp)
        } }
    }
    apps.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        row.forEach { app ->
            val favorite=prefs.favorites.any { it.appId==app.id }
            val tint by animateColorAsState(if(favorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="app favorite")
            Card(Modifier.weight(1f),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=tint),elevation=CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Box(Modifier.size(42.dp).background(Color(0xFF2563EB),CircleShape),contentAlignment=Alignment.Center) { Text(app.name.take(1).uppercase(),color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold,fontSize=22.sp) }
                        TextButton(onClick={vm.favorite(app)},modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics { contentDescription=if(favorite) "Unfavorite ${app.name}" else "Favorite ${app.name}" }) { Text(if(favorite) "★" else "☆",fontSize=23.sp) }
                    }
                    Text(app.name,fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Text(when(app.origin) { AppOrigin.Installed->"Reported by this TV";AppOrigin.Cached->"Cached • unverified now";AppOrigin.Custom->"Custom • unverified" },fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick={vm.advanced.launch(app)},enabled=connected && !busy && app.actionType!="UNKNOWN" && caps[Feature.AppLaunching].support!=Support.Unsupported,contentPadding=PaddingValues(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(if(app.actionType=="UNKNOWN") "Unverified type" else "Try launch",fontSize=13.sp) }
                    if(app.origin==AppOrigin.Custom) TextButton(onClick={vm.removeCustom(app.id)}) { Text("Remove",fontSize=12.sp) }
                }
            }
        }
        if(row.size==1) Spacer(Modifier.weight(1f))
    } }
    Text("Launch requests use the TV's remote channel. A request acknowledgment does not prove the app opened.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(add) CustomAppDialog(onDismiss={add=false},onSave={name,id,type->vm.customApp(name,id,type);add=false})
}
@Composable private fun CustomAppDialog(onDismiss: ()->Unit,onSave: (String,String,String)->Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("DEEP_LINK") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("TV-specific app shortcut")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Enter an identifier verified for your TV. This does not mark the app as installed.",fontSize=13.sp)
        OutlinedTextField(value=name,onValueChange={if(it.length<=120) name=it},label={Text("App name")},singleLine=true)
        OutlinedTextField(value=id,onValueChange={if(it.length<=128) id=it},label={Text("Application ID")},singleLine=true)
        Text("Launch action type")
        listOf("DEEP_LINK","NATIVE_LAUNCH").forEach { action -> FilterChip(selected=type==action,onClick={type=action},label={Text(action)},modifier=Modifier.heightIn(min=48.dp)) }
    }},confirmButton={TextButton(onClick={onSave(name,id,type)},enabled=name.isNotBlank() && id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { Text("Save shortcut") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancel") }})
}

@Composable internal fun FavoriteLaunch(favorite: FavoriteShortcut,vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val apps by vm.advanced.apps.collectAsStateWithLifecycle()
    val busy by vm.advanced.busy.collectAsStateWithLifecycle()
    val unsupported by vm.connection.unsupported.collectAsStateWithLifecycle()
    val caps by vm.advanced.capabilities.state.collectAsStateWithLifecycle()
    val app=apps.firstOrNull { it.id==favorite.appId }
    when {
        favorite.key!=null -> RemoteButton(favorite.title,favorite.key,connected && favorite.key !in unsupported,send)
        app!=null -> OutlinedButton(onClick={vm.advanced.launch(app)},enabled=connected && !busy && app.actionType!="UNKNOWN" && caps[Feature.AppLaunching].support!=Support.Unsupported,modifier=Modifier.heightIn(min=48.dp)) { Text("Try ${favorite.title}") }
        else -> Text("${favorite.title} • unavailable; refresh the TV app list",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun FavoritesPanel(vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var order by remember { mutableStateOf(prefs.favorites) }
    val heights=remember { mutableStateMapOf<String,Int>() }
    LaunchedEffect(prefs.favorites) { if(dragged==null) order=prefs.favorites }
    val density=LocalDensity.current.density
    SectionTitle("YOUR FAVORITES")
    Text("Hold and drag a shortcut to reorder. The arrow buttons offer the same action for accessibility. The first three pins appear on Remote.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(prefs.favorites.isEmpty()) Text("Star a TV app or pin an action below to build your quick-access panel.")
    order.forEach { favorite -> key(favorite.id) {
        val tint by animateColorAsState(if(dragged==favorite.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="favorite drag")
        Card(Modifier.fillMaxWidth().semantics { contentDescription="Reorder ${favorite.title}" }.onSizeChanged { heights[favorite.id]=it.height }.zIndex(if(dragged==favorite.id) 1f else 0f).graphicsLayer { translationY=if(dragged==favorite.id) dragOffset else 0f }.pointerInput(favorite.id,density) {
            detectDragGesturesAfterLongPress(onDragStart={dragged=favorite.id;dragOffset=0f},onDragCancel={dragged=null;dragOffset=0f;order=prefs.favorites},onDragEnd={val reordered=order;dragged=null;dragOffset=0f;vm.updatePreferences { it.copy(favorites=reordered) }}) { change,amount ->
                change.consume(); dragOffset+=amount.y
                val threshold=(heights[favorite.id] ?: (130*density).toInt())+16*density
                if(kotlin.math.abs(dragOffset)>=threshold) {
                    val direction=if(dragOffset>0) 1 else -1
                    val updated=FavoritesManager.move(order,favorite.id,direction)
                    if(updated!=order) { order=updated;dragOffset-=threshold*direction }
                }
            }
        },colors=CardDefaults.cardColors(containerColor=tint),shape=RoundedCornerShape(20.dp),elevation=CardDefaults.cardElevation(if(dragged==favorite.id) 5.dp else 1.dp)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                FavoriteLaunch(favorite,vm,connected,send)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={vm.moveFavorite(favorite.id,-1)},modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Move ${favorite.title} up" }) { Text("↑ Up") }
                    TextButton(onClick={vm.moveFavorite(favorite.id,1)},modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Move ${favorite.title} down" }) { Text("↓ Down") }
                    TextButton(onClick={vm.removeFavorite(favorite.id)},modifier=Modifier.heightIn(min=48.dp)) { Text("Unpin") }
                }
            }
        }
    } }
    SectionTitle("PIN REMOTE ACTIONS")
    listOf(RemoteKey.HOME,RemoteKey.RETURN,RemoteKey.MUTE,RemoteKey.SOURCE,RemoteKey.PLAY,RemoteKey.PAUSE,RemoteKey.CH_LIST).chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { action ->
        val selected=prefs.favorites.any { it.key==action }
        FilterChip(selected=selected,onClick={vm.pin(action)},label={Text(action.code.removePrefix("KEY_").replace('_',' '))},modifier=Modifier.heightIn(min=48.dp))
    } } }
}


