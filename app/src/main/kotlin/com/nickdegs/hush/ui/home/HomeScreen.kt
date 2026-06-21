package com.nickdegs.hush.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nickdegs.hush.R
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Forum, null) },
                        label = { Text(stringResource(R.string.tab_chats)) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Campaign, null) },
                        label = { Text(stringResource(R.string.tab_channels)) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.AccountCircle, null) },
                        label = { Text(stringResource(R.string.tab_profile)) }
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (tab) {
                    0 -> RoomListPlaceholder(stringResource(R.string.tab_chats))
                    1 -> RoomListPlaceholder(stringResource(R.string.tab_channels))
                    2 -> ProfilePlaceholder(vm)
                }
            }
        }
    }
}

@Composable
private fun RoomListPlaceholder(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.headlineMedium,
             color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Henüz sohbet yok",
             color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun ProfilePlaceholder(vm: AppViewModel) {
    val state by vm.uiState.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.AccountCircle, null,
             modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(state.displayName ?: state.userId ?: "—",
             style = MaterialTheme.typography.titleMedium,
             color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = { vm.logout() }) {
            Text(stringResource(R.string.logout))
        }
    }
}
