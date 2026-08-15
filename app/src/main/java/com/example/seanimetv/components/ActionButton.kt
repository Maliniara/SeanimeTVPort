package com.example.seanimetv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seanimetv.ui.theme.ExpressiveMotion
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimePrimary

@Composable
internal fun ActionButton(label: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Expressive: springy focus scale on every button (not just primary).
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "btnScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) {
                    this@drawWithContent.drawContent()
                }
            }
            // Expressive: fully-rounded pill buttons.
            .clip(ExpressiveShapes.full)
            .background(
                when {
                    primary -> SeanimePrimary
                    isFocused -> SeanimePrimary
                    else -> Color.White.copy(alpha = 0.13f)
                }
            )
            .border(
                width = if (primary && isFocused) 2.dp else 0.dp,
                color = Color.White.copy(alpha = 0.55f),
                shape = ExpressiveShapes.full
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(
                horizontal = if (primary) 22.dp else 18.dp,
                vertical = if (primary) 11.dp else 9.dp
            )
    ) {
        Text(
            text = label,
            fontSize = if (primary) 14.sp else 13.sp,
            fontWeight = if (isFocused || primary) FontWeight.Bold else FontWeight.Medium,
            color = Color.White
        )
    }
}
