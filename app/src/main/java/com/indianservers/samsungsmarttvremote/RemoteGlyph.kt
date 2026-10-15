package com.indianservers.samsungsmarttvremote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.indianservers.samsungsmarttvremote.domain.RemoteKey

fun RemoteKey.accessibleLabel() = when(this) {
    RemoteKey.POWER -> "Power off television"
    RemoteKey.VOLUP -> "Volume up"
    RemoteKey.VOLDOWN -> "Volume down"
    RemoteKey.CHUP -> "Channel up"
    RemoteKey.CHDOWN -> "Channel down"
    RemoteKey.ENTER -> "OK, select"
    RemoteKey.RETURN -> "Back"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable fun RemoteGlyph(key: RemoteKey, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val scale=size.width/24f
        fun point(x: Float,y: Float)=Offset(x*scale,y*scale)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,point(x,y),point(x2,y2),2*scale,StrokeCap.Round)
        when(key) {
            RemoteKey.UP,RemoteKey.CHUP -> {line(6f,15f,12f,9f);line(12f,9f,18f,15f)}
            RemoteKey.DOWN,RemoteKey.CHDOWN -> {line(6f,9f,12f,15f);line(12f,15f,18f,9f)}
            RemoteKey.LEFT -> {line(15f,6f,9f,12f);line(9f,12f,15f,18f)}
            RemoteKey.RIGHT -> {line(9f,6f,15f,12f);line(15f,12f,9f,18f)}
            RemoteKey.VOLUP -> {line(5f,12f,19f,12f);line(12f,5f,12f,19f)}
            RemoteKey.VOLDOWN -> line(5f,12f,19f,12f)
            RemoteKey.POWER -> {drawArc(color,-45f,270f,false,point(4f,4f),Size(16*scale,16*scale),style=Stroke(2*scale,cap=StrokeCap.Round));line(12f,2f,12f,11f)}
            RemoteKey.RETURN -> {line(4f,11f,10f,5f);line(4f,11f,10f,17f);line(4f,11f,19f,11f);line(19f,11f,19f,19f)}
            RemoteKey.HOME -> {line(3f,11f,12f,3f);line(12f,3f,21f,11f);line(6f,9f,6f,21f);line(6f,21f,18f,21f);line(18f,21f,18f,9f)}
            RemoteKey.MENU -> {line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f)}
            RemoteKey.PAUSE -> {line(8f,5f,8f,19f);line(16f,5f,16f,19f)}
            RemoteKey.PLAY -> {val p=Path().apply { moveTo(7*scale,4*scale);lineTo(20*scale,12*scale);lineTo(7*scale,20*scale);close() };drawPath(p,color,style=Stroke(2*scale))}
            RemoteKey.SOURCE -> {drawRoundRect(color,point(3f,4f),Size(18*scale,16*scale),style=Stroke(2*scale));line(8f,12f,16f,12f);line(13f,9f,16f,12f);line(13f,15f,16f,12f)}
            RemoteKey.MUTE -> {line(3f,9f,3f,15f);line(3f,9f,7f,9f);line(3f,15f,7f,15f);line(7f,9f,12f,5f);line(7f,15f,12f,19f);line(12f,5f,12f,19f);line(17f,9f,22f,15f);line(22f,9f,17f,15f)}
            RemoteKey.ENTER -> {line(5f,12f,10f,17f);line(10f,17f,20f,6f)}
            else -> { drawRoundRect(color,point(4f,4f),Size(16*scale,16*scale),style=Stroke(2*scale)) }
        }
    }
}

