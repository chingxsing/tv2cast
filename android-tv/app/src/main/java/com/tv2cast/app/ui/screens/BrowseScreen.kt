package com.tv2cast.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv2cast.app.data.model.Video
import com.tv2cast.app.ui.components.VideoCard
import com.tv2cast.app.ui.theme.*

/**
 * Video browse screen — shows grid of videos
 */
@Composable
fun BrowseScreen(
    videos: List<Video>,
    serverUrl: String,
    onVideoClick: (Video) -> Unit,
    onRescan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand
            Text(
                text = "🎬",
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Tv2Cast",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            // Video count
            Text(
                text = "${videos.size} videos",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            // Rescan button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .clickable { onRescan() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("🔄 Rescan", fontSize = 14.sp, color = TextSecondary)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Disconnect button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .clickable { onDisconnect() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("⬅ Disconnect", fontSize = 14.sp, color = TextSecondary)
            }
        }
        
        // Server info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(BgCard, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Success, RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Connected to ",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = serverUrl,
                fontSize = 13.sp,
                color = AccentLight,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Video grid
        if (videos.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎬", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No videos found",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        "Add video files to the server directories",
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        }
    }
}
