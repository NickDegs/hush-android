package com.nickdegs.hush.ui.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickdegs.hush.core.auth.Network
import com.nickdegs.hush.core.spaces.*
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.components.glassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPro: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val tier by vm.billing.tier.collectAsState()
    val token = state.accessToken
    val scope = rememberCoroutineScope()
    var spaces by remember { mutableStateOf<List<SpaceDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf<SpaceDto?>(null) }
    val isPro = tier != com.nickdegs.hush.core.billing.HushTier.NONE

    suspend fun reload() {
        val t = token ?: return
        runCatching {
            Network.spaces.sync(SyncReq(t, purchase_token = vm.billing.purchaseToken, product_id = vm.billing.productId))
            spaces = Network.spaces.list(ListReq(t)).spaces
            error = null
        }.onFailure { error = it.message }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Topluluk Alanları") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            LiquidBackground()
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(
                    if (isPro) "${spaces.size}/${tier.spaceLimit} alan kullanımda" else "Özel Topluluk Alanları",
                    color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)
                )
                Text("Sadece davet ettiklerinin girebildiği, senin yönettiğin izole sohbet alanları.",
                    color = Color.White.copy(0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))

                error?.let { Text(it, color = Color(0xFFFFA726), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }

                if (!isPro) {
                    Column(Modifier.fillMaxWidth().glassCard(20).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kendi özel alanını oluşturmak için Hush Pro gerekir.", color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenPro) { Text("Hush Pro'ya Geç") }
                    }
                } else {
                    spaces.forEach { s ->
                        Row(Modifier.fillMaxWidth().glassCard(18).clickable { admin = s }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when { s.legal_hold -> "Hukuki muhafaza"; s.frozen -> "Donuk (abonelik pasif)"; else -> "Aktif" },
                                    color = Color.White.copy(0.65f), fontSize = 12.sp
                                )
                            }
                            Text("›", color = Color.White.copy(0.5f), fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (spaces.isEmpty()) {
                        Text("Henüz alanın yok. İlk özel topluluk alanını oluştur.", color = Color.White.copy(0.6f),
                            fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    if (spaces.size < tier.spaceLimit) {
                        Button(onClick = { newName = ""; showCreate = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("Yeni Alan Oluştur")
                        }
                    } else {
                        Text("Kademe limitine ulaştın (${tier.spaceLimit}). Daha fazlası için üst kademeye geç.",
                            color = Color.White.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Yeni Alan") },
            text = {
                Column {
                    Text("Sadece davet ettiklerin girebilir. Yöneticisi sen olursun.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(newName, { newName = it }, label = { Text("Alan adı") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = token; val nm = newName.trim()
                    if (t != null && nm.isNotEmpty()) {
                        showCreate = false; busy = true
                        scope.launch {
                            runCatching {
                                val r = Network.spaces.create(CreateReq(t, nm, purchase_token = vm.billing.purchaseToken, product_id = vm.billing.productId))
                                if (!r.ok) error = r.error
                            }.onFailure { error = it.message }
                            reload(); busy = false
                        }
                    }
                }) { Text("Oluştur") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("İptal") } }
        )
    }

    admin?.let { sp ->
        SpaceAdminDialog(vm, sp, onClose = { admin = null; scope.launch { reload() } })
    }
}

@Composable
private fun SpaceAdminDialog(vm: AppViewModel, space: SpaceDto, onClose: () -> Unit) {
    val token = vm.uiState.collectAsState().value.accessToken
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(op: suspend (String) -> SimpleResp, okMsg: String) {
        val t = token ?: return
        busy = true; status = null
        scope.launch {
            runCatching { val r = op(t); status = if (r.ok) okMsg else (r.error ?: "hata") }
                .onFailure { status = it.message }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(space.name) },
        text = {
            Column {
                if (space.frozen) Text("Bu alan donuk (abonelik pasif). Yeniden abone olunca yönetim açılır.", color = Color(0xFFFFA726), fontSize = 12.sp)
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon (+90…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = { run({ Network.spaces.invite(InviteReq(it, space.room_id, phone)) }, "Davet gönderildi.") },
                    enabled = !busy && phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Davet Et") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { run({ Network.spaces.role(MemberReq(it, space.room_id, phoneToUid(phone), 50)) }, "Moderatör yapıldı.") },
                        enabled = !busy && phone.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Moderatör") }
                    OutlinedButton(onClick = { run({ Network.spaces.role(MemberReq(it, space.room_id, phoneToUid(phone), 100)) }, "Admin yapıldı.") },
                        enabled = !busy && phone.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Admin") }
                }
                OutlinedButton(onClick = { run({ Network.spaces.kick(MemberReq(it, space.room_id, phoneToUid(phone))) }, "Çıkarıldı.") },
                    enabled = !busy && phone.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Alandan Çıkar") }
                status?.let { Text(it, color = Color.White.copy(0.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
    )
}
