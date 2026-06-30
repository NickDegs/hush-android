package com.nickdegs.hush.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nickdegs.hush.core.matrix.ChatMessage
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.theme.Blue
import com.nickdegs.hush.ui.theme.Violet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, roomId: String, roomName: String, onBack: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(roomId) { vm.openRoom(roomId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(roomName, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    )
                )
            },
            bottomBar = {
                InputBar(
                    text = input,
                    onText = { input = it },
                    onSend = {
                        vm.sendMessage(roomId, input)
                        input = ""
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg, vm)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, vm: AppViewModel) {
    val bubbleBrush = Brush.horizontalGradient(listOf(Violet, Blue))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (msg.mine) Modifier.background(bubbleBrush, RoundedCornerShape(18.dp))
                        else Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (msg.type) {
                    "image" -> AsyncImage(
                        model = vm.mediaUrl(msg.mediaMxc, thumb = true),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                    )
                    "audio" -> Text("🎙️ Sesli mesaj", color = Color.White)
                    "video" -> Text("🎬 Video", color = Color.White)
                    "file"  -> Text("📎 ${msg.body}", color = Color.White)
                    else    -> Text(msg.body, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(text: String, onText: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text("Mesaj…") },
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedBorderColor = Violet,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            )
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)
        ) {
            Icon(
                if (text.isBlank()) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send,
                contentDescription = "Gönder", tint = Color.White
            )
        }
    }
}
