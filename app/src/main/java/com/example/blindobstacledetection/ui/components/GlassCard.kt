package com.example.blindobstacledetection.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blindobstacledetection.ui.theme.GlassBorderDark
import com.example.blindobstacledetection.ui.theme.GlassBorderLight
import com.example.blindobstacledetection.ui.theme.GlassSurfaceDark
import com.example.blindobstacledetection.ui.theme.GlassSurfaceLight

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultBg = if (isDark) GlassSurfaceDark else GlassSurfaceLight
    val borderColor = if (isDark) GlassBorderDark else GlassBorderLight
    val finalBg = backgroundColor ?: defaultBg

    Surface(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = finalBg,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
