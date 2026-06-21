package com.nickdegs.hush.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickdegs.hush.R
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerifyScreen(
    vm: AppViewModel,
    phone: String,
    displayName: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(60) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (countdown > 0) { delay(1000); countdown-- }
    }

    LaunchedEffect(code) {
        if (code.length == 6) {
            verifying = true
            val ok = vm.verifyPhoneCode(phone, code, displayName)
            verifying = false
            if (ok) onSuccess() else {
                errorMessage = vm.uiState.value.errorMessage ?: "Geçersiz kod"
                code = ""
            }
        }
    }

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
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Filled.CheckCircle, null,
                     modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.otp_title), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.otp_subtitle, phone),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall
                )

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            verifying = true
                            val ok = vm.verifyPhoneCode(phone, code, displayName)
                            verifying = false
                            if (ok) onSuccess() else errorMessage = "Geçersiz kod"
                        }
                    },
                    enabled = code.length >= 4 && !verifying,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) {
                    if (verifying) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(R.string.otp_verify), fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(18.dp))
                if (countdown > 0) {
                    Text(stringResource(R.string.otp_resend_in, countdown), fontSize = 12.sp,
                         color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                } else {
                    TextButton(onClick = {
                        scope.launch { vm.startPhoneVerification(phone); countdown = 60 }
                    }) {
                        Text(stringResource(R.string.otp_resend))
                    }
                }
            }
        }
    }
}
