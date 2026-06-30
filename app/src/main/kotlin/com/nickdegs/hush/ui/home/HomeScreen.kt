package com.nickdegs.hush.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickdegs.hush.R
import com.nickdegs.hush.core.matrix.MatrixRoom
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.components.glassCard
import com.nickdegs.hush.ui.theme.Blue
import com.nickdegs.hush.ui.theme.Cyan
import com.nickdegs.hush.ui.theme.Violet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenRoom: (String, String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                    NavigationBarItem(tab == 0, { tab = 0 },
                        icon = { Icon(Icons.Filled.Forum, null) },
                        label = { Text(stringResource(R.string.tab_chats)) })
                    NavigationBarItem(tab == 1, { tab = 1 },
                        icon = { Icon(Icons.Filled.Campaign, null) },
                        label = { Text(stringResource(R.string.tab_channels)) })
                    NavigationBarItem(tab == 2, { tab = 2 },
                        icon = { Icon(Icons.Filled.AccountCircle, null) },
                        label = { Text(stringResource(R.string.tab_profile)) })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> RoomList(vm, onOpenRoom)
                    1 -> RoomList(vm, onOpenRoom)
                    2 -> Profile(vm)
                }
            }
        }
    }
}

@Composable
private fun RoomList(vm: AppViewModel, onOpenRoom: (String, String) -> Unit) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val syncDone by vm.syncDone.collectAsStateWithLifecycle()

    if (rooms.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!syncDone) {
                CircularProgressIndicator(color = Violet)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Forum, null, tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Henüz sohbet yok", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rooms, key = { it.id }) { room ->
            RoomRow(room) { onOpenRoom(room.id, room.name) }
        }
    }
}

@Composable
private fun RoomRow(room: MatrixRoom, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassCard(20)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Violet, Blue, Cyan))),
            contentAlignment = Alignment.Center
        ) {
            Text(room.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(room.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(room.lastMessage ?: "Yeni oda",
                color = Color.White.copy(alpha = 0.6f), maxLines = 1,
                style = MaterialTheme.typography.bodySmall)
        }
        if (room.unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Blue)))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(minOf(room.unread, 99).toString(), color = Color.White,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Profile(vm: AppViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.AccountCircle, null,
                modifier = Modifier.size(80.dp), tint = Violet)
            Spacer(Modifier.height(12.dp))
            Text(state.displayName ?: state.userId ?: "—",
                style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = { vm.logout() }) {
                Text(stringResource(R.string.logout))
            }
        }
    }
}
