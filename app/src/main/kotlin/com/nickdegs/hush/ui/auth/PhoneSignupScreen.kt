package com.nickdegs.hush.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickdegs.hush.R
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.theme.Violet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSignupScreen(
    vm: AppViewModel,
    onCodeSent: (phone: String, name: String) -> Unit,
    onBack: () -> Unit
) {
    var dialCode by remember { mutableStateOf("+90") }
    var local by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val phoneE164 = dialCode + local.filter { it.isDigit() }
    val canContinue = local.filter { it.isDigit() }.length >= 7 && !sending

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 22.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.phone_title), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.phone_subtitle),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(28.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dialCode,
                        onValueChange = { dialCode = it.filter { c -> c.isDigit() || c == '+' }.take(4) },
                        modifier = Modifier.width(90.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = local,
                        onValueChange = { local = it.filter { c -> c.isDigit() }.take(13) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.phone_hint)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name_hint)) },
                    singleLine = true
                )

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        errorMessage = null
                        sending = true
                        scope.launch {
                            val ok = vm.startPhoneVerification(phoneE164)
                            sending = false
                            if (ok) {
                                onCodeSent(phoneE164, name)
                            } else {
                                errorMessage = vm.uiState.value.errorMessage ?: "Hata"
                            }
                        }
                    },
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else {
                        Icon(Icons.Filled.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_sms), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
