package com.ex57.capital.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val base = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            glow,
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(base)
                .padding(0.dp),
            content = content
        )
    }
}
