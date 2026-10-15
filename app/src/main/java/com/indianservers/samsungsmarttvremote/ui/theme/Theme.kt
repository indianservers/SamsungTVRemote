package com.indianservers.samsungsmarttvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SnowBlue = lightColorScheme(
    primary=Color(0xFF2563EB), onPrimary=Color.White,
    primaryContainer=Color(0xFFDBEAFE), onPrimaryContainer=Color(0xFF1E3A8A),
    secondary=Color(0xFF2563EB), onSecondary=Color.White,
    secondaryContainer=Color(0xFFE8F1FF), onSecondaryContainer=Color(0xFF163D86),
    tertiary=Color(0xFF60A5FA), tertiaryContainer=Color(0xFFE0F2FE), onTertiaryContainer=Color(0xFF0C4A6E),
    background=Color(0xFFF8FBFF), onBackground=Color(0xFF0F172A),
    surface=Color.White, onSurface=Color(0xFF0F172A),
    surfaceVariant=Color(0xFFF1F5F9), onSurfaceVariant=Color(0xFF64748B),
    surfaceTint=Color(0xFF2563EB), outline=Color(0xFF94A3B8), outlineVariant=Color(0xFFDBE5F2),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFF1F7FF),
    surfaceContainer=Color(0xFFF8FBFF), surfaceContainerHigh=Color(0xFFE8F1FF), surfaceContainerHighest=Color(0xFFDBEAFE)
)

val LocalAppearance=androidx.compose.runtime.compositionLocalOf { com.indianservers.samsungsmarttvremote.domain.Appearance() }

@Composable fun SamsungSmartTVRemoveTheme(
    appearance: com.indianservers.samsungsmarttvremote.domain.Appearance=com.indianservers.samsungsmarttvremote.domain.Appearance(),
    content: @Composable ()->Unit
) {
    val theme=appearance.theme
    val dark=theme==com.indianservers.samsungsmarttvremote.domain.RemoteTheme.MidnightDark || theme==com.indianservers.samsungsmarttvremote.domain.RemoteTheme.RoyalBlue || theme==com.indianservers.samsungsmarttvremote.domain.RemoteTheme.System && androidx.compose.foundation.isSystemInDarkTheme()
    val royal=theme==com.indianservers.samsungsmarttvremote.domain.RemoteTheme.RoyalBlue
    val scheme=if(dark) androidx.compose.material3.darkColorScheme(
        primary=Color(0xFF60A5FA),onPrimary=Color(0xFF07182E),primaryContainer=Color(0xFF173C72),onPrimaryContainer=Color(0xFFDBEAFE),
        background=if(royal) Color(0xFF071C45) else Color(0xFF080F1E),onBackground=Color(0xFFF1F5F9),
        surface=if(royal) Color(0xFF122F60) else Color(0xFF152238),onSurface=Color(0xFFF1F5F9),
        secondaryContainer=if(royal) Color(0xFFDBEAFE) else Color(0xFF233D60),onSecondaryContainer=if(royal) Color(0xFF102958) else Color(0xFFE4EFFF),
        surfaceVariant=Color(0xFF1D3452),onSurfaceVariant=Color(0xFFCBD5E1),outline=Color(0xFF8AA6C7),outlineVariant=Color(0xFF405878),
        surfaceContainer=Color(0xFF152238),surfaceContainerLow=Color(0xFF152238),surfaceContainerHigh=Color(0xFF233D60)
    ) else SnowBlue
    val view=androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme=scheme,typography=Typography,content=content)
    }
}
