package com.indianservers.samsungsmarttvremote.domain

import kotlin.math.abs
import kotlin.math.hypot

enum class TouchAction { Tap, DoubleTap, LongPress, Cancel }
class GestureInterpreter {
    fun swipe(dx: Float,dy: Float,sensitivity: Float): RemoteKey? {
        val threshold=54f/sensitivity.coerceIn(.5f,2f)
        if(hypot(dx,dy)<threshold || abs(abs(dx)-abs(dy))<hypot(dx,dy)*.18f) return null
        return if(abs(dx)>abs(dy)) { if(dx>0) RemoteKey.RIGHT else RemoteKey.LEFT } else { if(dy>0) RemoteKey.DOWN else RemoteKey.UP }
    }
    fun action(action: TouchAction): RemoteKey? = when(action) {
        TouchAction.Tap -> RemoteKey.ENTER
        TouchAction.DoubleTap -> RemoteKey.ENTER // One selection, never a speculative double-command.
        TouchAction.LongPress -> RemoteKey.RETURN
        TouchAction.Cancel -> null
    }
}
