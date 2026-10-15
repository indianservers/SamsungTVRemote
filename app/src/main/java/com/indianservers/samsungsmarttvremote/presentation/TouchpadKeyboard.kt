package com.indianservers.samsungsmarttvremote.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indianservers.samsungsmarttvremote.RemoteButton
import com.indianservers.samsungsmarttvremote.domain.*
import kotlinx.coroutines.*

@Composable internal fun TouchpadPanel(vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val interpreter=remember { GestureInterpreter() }
    val currentSend by rememberUpdatedState(send)
    val currentSensitivity by rememberUpdatedState(prefs.sensitivity)
    val density=LocalDensity.current.density
    val scope=rememberCoroutineScope()
    var position by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    var trail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val smoothPosition by animateOffsetAsState(position,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="finger position")
    val feedbackAlpha by animateFloatAsState(if(touching) 1f else 0f,animationSpec=com.indianservers.samsungsmarttvremote.ui.theme.remoteTween(),label="touch trail")
    var pendingTap by remember { mutableStateOf<Job?>(null) }
    var lastTapAt by remember { mutableLongStateOf(-1000) }
    DisposableEffect(connected) { onDispose { pendingTap?.cancel(); touching=false } }
    SectionTitle("DIRECTIONAL TOUCHPAD")
    Text("Swipe to move • Tap or double-tap to select once\nHold and release for Back",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(Modifier.fillMaxWidth().height(290.dp),shape=RoundedCornerShape(30.dp),color=MaterialTheme.colorScheme.onPrimary,shadowElevation=4.dp) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription="Directional television touchpad"
                onClick(label="Select") { if(connected) currentSend(RemoteKey.ENTER); connected }
                customActions=listOf(RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT,RemoteKey.RETURN).map { key ->
                    CustomAccessibilityAction(key.name.lowercase()) { if(connected) currentSend(key); connected }
                }
            }
            .pointerInput(connected,density) {
                try {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        pendingTap?.cancel()
                        down.consume()
                        val origin=down.position
                        position=origin; touching=connected; trail=listOf(origin)
                        var end=origin
                        var endTime=down.uptimeMillis
                        var canceled=!connected
                        var maxMovement=0f
                        do {
                            val event=awaitPointerEvent()
                            if(event.changes.size!=1) canceled=true
                            val change=event.changes.firstOrNull { it.id==down.id }
                            if(change==null) { canceled=true; break }
                            if(change.isConsumed) canceled=true
                            end=change.position; endTime=change.uptimeMillis
                            maxMovement=maxOf(maxMovement,(end-origin).getDistance())
                            position=end; trail=(trail+end).takeLast(14)
                            event.changes.forEach { it.consume() }
                            val pressed=event.changes.any { it.pressed }
                        } while(pressed)
                        touching=false
                        if(!canceled) {
                            val delta=(end-origin)/density
                            val swipe=interpreter.swipe(delta.x,delta.y,currentSensitivity)
                            if(swipe!=null) { lastTapAt=-1000; currentSend(swipe) }
                            else if(maxMovement/density>18f) { lastTapAt=-1000 }
                            else if(endTime-down.uptimeMillis>=500) { lastTapAt=-1000; interpreter.action(TouchAction.LongPress)?.let(currentSend) }
                            else if(endTime-lastTapAt in 0..300) { lastTapAt=-1000; interpreter.action(TouchAction.DoubleTap)?.let(currentSend) }
                            else {
                                lastTapAt=endTime
                                pendingTap=scope.launch { delay(300); interpreter.action(TouchAction.Tap)?.let(currentSend) }
                            }
                        } else { lastTapAt=-1000; interpreter.action(TouchAction.Cancel) }
                    }
                } finally { pendingTap?.cancel(); touching=false }
            }) {
            Canvas(Modifier.matchParentSize()) {
                val grid=18.dp.toPx()
                var x=grid
                while(x<size.width) { var y=grid; while(y<size.height) { drawCircle(Color(0xFFBCCDE8),1.dp.toPx(),Offset(x,y));y+=grid };x+=grid }
                trail.zipWithNext().forEachIndexed { index,(a,b) -> drawLine(Color(0xFF60A5FA).copy(alpha=feedbackAlpha*.6f*(index+1)/trail.size),a,b,5.dp.toPx()) }
                drawCircle(Color(0xFF2563EB).copy(alpha=feedbackAlpha*.12f),30.dp.toPx(),smoothPosition)
                drawCircle(Color(0xFF2563EB).copy(alpha=feedbackAlpha),7.dp.toPx(),smoothPosition)
            }
            Text(if(connected) "Glide to navigate" else "Connect a television to navigate",Modifier.align(Alignment.Center),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
        }
    }
    var sensitivity by remember(prefs.sensitivity) { mutableFloatStateOf(prefs.sensitivity) }
    Text("Sensitivity: ${"%.1f".format(sensitivity)}×",fontSize=13.sp)
    Slider(value=sensitivity,onValueChange={sensitivity=it},onValueChangeFinished={vm.updatePreferences { it.copy(sensitivity=sensitivity) }},valueRange=.5f..2f,modifier=Modifier.semantics { contentDescription="Touchpad sensitivity" })
    OutlinedButton(onClick={},enabled=false) { Text("Pointer mode • unverified") }
    Text("No pointer-capable model has been validated. Directional mode remains available; gestures send bounded D-pad clicks.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { RemoteButton("Back",RemoteKey.RETURN,connected,send);RemoteButton("Home",RemoteKey.HOME,connected,send) }
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { RemoteButton("Volume down",RemoteKey.VOLDOWN,connected,send);RemoteButton("Mute",RemoteKey.MUTE,connected,send);RemoteButton("Volume up",RemoteKey.VOLUP,connected,send) }
}

@Composable internal fun KeyboardPanel(vm: RemoteViewModel,connected: Boolean,send: (RemoteKey)->Unit) {
    val connectionState by vm.connection.state.collectAsStateWithLifecycle()
    val focused by vm.advanced.focused.collectAsStateWithLifecycle()
    val busy by vm.advanced.busy.collectAsStateWithLifecycle()
    val caps by vm.advanced.capabilities.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") } // Deliberately not saveable or persisted.
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) { text="";vm.advanced.cancelSensitiveAction() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);text="";vm.advanced.cancelSensitiveAction() }
    }
    val canSend=connected && focused && caps[Feature.TextInput].support==Support.Supported && !busy
    val submit: ()->Unit={if(canSend && text.isNotBlank()) { vm.advanced.sendText(text);text="" } }
    SectionTitle("PHONE KEYBOARD")
    if(connectionState.tv?.port==8001) Text("This TV uses an unencrypted local connection. Avoid sensitive text on shared networks.",fontSize=13.sp,color=MaterialTheme.colorScheme.error)
    Text(if(canSend) "TV keyboard is focused. Type below." else "Open a text field on the TV. Send becomes available when the television advertises remote keyboard focus.",fontSize=14.sp)
    OutlinedTextField(value=text,onValueChange={if(it.length<=1024) text=it},label={Text("Text for your television")},minLines=3,maxLines=6,modifier=Modifier.fillMaxWidth(),
        supportingText={Text("${text.toByteArray(Charsets.UTF_8).size}/1,024 UTF-8 bytes • not saved")},
        keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Text,imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={submit()}))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick=submit,enabled=canSend && text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size<=1024,modifier=Modifier.heightIn(min=52.dp)) { Text(if(busy) "Sending…" else "Send to TV") }
        OutlinedButton(onClick={text=""},enabled=text.isNotEmpty(),modifier=Modifier.heightIn(min=52.dp)) { Text("Clear locally") }
    }
    OutlinedButton(onClick={if(text.isNotEmpty()) text=text.dropLast(if(text.last().isLowSurrogate() && text.length>1 && text[text.lastIndex-1].isHighSurrogate()) 2 else 1)},enabled=text.isNotEmpty(),modifier=Modifier.heightIn(min=52.dp)) { Text("Backspace locally") }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={},enabled=false,modifier=Modifier.heightIn(min=52.dp)) { Text("TV backspace") }
        RemoteButton("Enter",RemoteKey.ENTER,connected,send)
    }
    Text("Remote deletion is unverified on this TV; use its on-screen keyboard. Text submission does not confirm insertion. Some TV apps ignore remote text even when keyboard focus is advertised.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

