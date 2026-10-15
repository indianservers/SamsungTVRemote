package com.indianservers.samsungsmarttvremote.ui.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

@Composable fun <T> remoteTween(durationMillis:Int=200):TweenSpec<T> = tween((durationMillis*LocalAppearance.current.motion).toInt())
