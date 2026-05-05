package com.tv2cast.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.tv2cast.app.data.model.Video
import com.tv2cast.app.ui.theme.*

/**
 * TV-optimized video card with focus support for D-pad navigation
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = BgCard,
            focusedContainerColor = BgCardHover,
            pressedContainerColor = BgCardHover
        ),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Accent), shape = shape),
            border = Border(border = BorderStroke(2.dp, Color(0xFF1A1A2A)), shape = shape)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BgSecondary, BgPrimary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // File icon
                Text(
                    text = "🎬",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(8.dp)
                )
                
                // Extension badge
                val extColor = getExtensionColor(video.extension)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            Color(0xB3000000),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = video.extension.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = extColor,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Play overlay (visible on focus)
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x80000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Accent, RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", fontSize = 24.sp, color = Color.White)
                        }
                    }
                }
            }
            
            // Card body
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = video.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) Color.White else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "💾 ${video.sizeFormatted}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = video.extension.uppercase(),
                        fontSize = 12.sp,
                        color = getExtensionColor(video.extension)
                    )
                }
            }
        }
    }
}
