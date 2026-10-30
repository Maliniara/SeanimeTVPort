package com.malina.seanimetvport.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimePrimary

/**
 * Row-level section header: 3dp primary accent bar + 16sp SemiBold label.
 * The accent bar marks the start of every content section across the app.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SeanimePrimary)
        )
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SeanimeOnSurface)
    }
}

/**
 * Screen-level title: taller accent bar + 20sp Bold label. One per screen,
 * at the top (Search, Schedule, Settings, Plugin Store…).
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SeanimePrimary)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SeanimeOnSurface)
    }
}
