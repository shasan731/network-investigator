package com.shasan731.networkinvestigator.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NetworkInvestigatorTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF72D7C6), secondary = androidx.compose.ui.graphics.Color(0xFFB7C9FF))
        else -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF006B5F), secondary = androidx.compose.ui.graphics.Color(0xFF3F5F90))
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

