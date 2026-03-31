package com.virtualcouch.pucci.dev.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VirtualCouchBottomNavigation(
    modifier: Modifier = Modifier,
    currentRoute: String? = "main",
    onNavigate: (String) -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    // Surface preta que cobre até a barra de navegação do sistema
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding() // Garante que o fundo preto cubra a área do sistema
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Home,
                label = "Sessões",
                selected = currentRoute == "main",
                onClick = { onNavigate("main") }
            )
            NavigationItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CalendarMonth,
                label = "Agenda",
                selected = currentRoute == "agenda",
                onClick = { onNavigate("agenda") }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                VirtualCouchAddButton(onClick = onAddClick)
            }
            NavigationItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.MailOutline,
                label = "Mensagens",
                selected = currentRoute == "messages",
                onClick = { /* onNavigate("messages") */ }
            )
            NavigationItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Person,
                label = "Perfil",
                selected = currentRoute == "profile",
                onClick = { onNavigate("profile") }
            )
        }
    }
}

@Composable
fun NavigationItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun VirtualCouchAddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(45.dp)
            .height(28.dp)
            .clickable(onClick = onClick)
    ) {
        // Aesthetic layers maintained but with simplified therapist colors if needed
        
        // Dark Blue layer (left)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(37.dp)
                .background(
                    color = Color(0xFF1D4EEE),
                    shape = RoundedCornerShape(8.dp)
                )
                .align(Alignment.CenterStart)
        )
        // Light Blue layer (right)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(37.dp)
                .background(
                    color = Color(0xFF69C9D0),
                    shape = RoundedCornerShape(8.dp)
                )
                .align(Alignment.CenterEnd)
        )
        // White layer (center)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(37.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
                .align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar",
                tint = Color.Black,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
