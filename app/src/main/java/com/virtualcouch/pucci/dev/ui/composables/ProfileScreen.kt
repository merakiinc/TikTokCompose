package com.virtualcouch.pucci.dev.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.virtualcouch.pucci.dev.domain.models.UserProfile
import com.virtualcouch.pucci.dev.domain.models.VideoData

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    videos: List<VideoData>,
    onLogout: () -> Unit = {},
    onVideoClick: (Int) -> Unit = {},
    onOpenLink: (String) -> Unit = {} // Novo callback para o link
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLogout) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair", tint = Color.White)
            }
        }

        // Profile Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(profile?.avatarUrl),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp).clip(CircleShape).background(Color.DarkGray).border(2.dp, Color.White, CircleShape),
                    contentScale = ContentScale.Crop
                )
                if (profile == null) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = profile?.name ?: "Psicólogo", 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // EXIBIÇÃO DA URL COM ÍCONE (Igual ao Perfil do Autor)
            profile?.link?.let { url ->
                val displayUrl = if (url.startsWith("http")) url else "https://$url"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onOpenLink(url) }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = Color(0xFF1D4EEE),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayUrl,
                        color = Color(0xFF1D4EEE),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ProfileStatItem(profile?.followingCount ?: "0", "Seguindo")
                ProfileStatItem(profile?.followersCount ?: "0", "Seguidores")
                ProfileStatItem(profile?.likesCount ?: "0", "Curtidas")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = profile?.bio ?: "Nenhuma biografia disponível.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { /* Edit Profile */ },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1D4EEE)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(160.dp).height(36.dp)
            ) {
                Text(text = "Editar Perfil", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Divider(color = Color.DarkGray)
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(videos) { index, video ->
                Box(modifier = Modifier
                    .aspectRatio(3f/4f)
                    .background(Color.DarkGray)
                    .clickable { onVideoClick(index) }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(video.previewImageUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "❤️ ${video.likes}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
