package com.virtualcouch.pucci.dev.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.virtualcouch.pucci.dev.domain.models.VideoData

@Composable
fun ProfileScreen(
    videos: List<VideoData> = emptyList(),
    onVideoClick: (VideoData) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = rememberAsyncImagePainter("https://api.dicebear.com/7.x/avataaars/svg?seed=Leonardo"),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.Gray, CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "@leopucci",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            
            Row(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ProfileStat("120", "Seguindo")
                ProfileStat("45k", "Seguidores")
                ProfileStat("1.2M", "Curtidas")
            }
            
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2F2F2F)),
                modifier = Modifier
                    .width(160.dp)
                    .height(36.dp),
                shape = RoundedCornerShape(4.dp),
                elevation = null
            ) {
                Text("Editar perfil", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tab selection (Mock)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp).padding(bottom = 8.dp)
            )
        }
        Divider(color = Color.DarkGray, thickness = 0.5.dp)

        // Video Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(videos) { video ->
                Image(
                    painter = rememberAsyncImagePainter(video.previewImageUri),
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(3f / 4f)
                        .background(Color.DarkGray)
                        .clickable { onVideoClick(video) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun ProfileStat(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 15.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}
