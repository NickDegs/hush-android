package com.nickdegs.hush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.auth.LoginScreen
import com.nickdegs.hush.ui.auth.MatrixLoginScreen
import com.nickdegs.hush.ui.auth.OtpVerifyScreen
import com.nickdegs.hush.ui.auth.PhoneSignupScreen
import com.nickdegs.hush.ui.chat.ChatScreen
import com.nickdegs.hush.ui.home.HomeScreen
import com.nickdegs.hush.ui.theme.HushTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HushTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    HushNavGraph(viewModel)
                }
            }
        }
        // Deeplink (hush://chat?token=… veya https://nickdegs.com/hush/chat?token=…)
        intent?.data?.let { uri ->
            uri.getQueryParameter("token")?.let { token ->
                viewModel.enterBusinessMode(token)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HushNavGraph(viewModel: AppViewModel) {
    val nav = rememberNavController()
    val state by viewModel.uiState.collectAsState()

    // Güvenlik kapısı: doğrulanmadan / internetsiz içeri girilmez.
    if (state.isValidating) { ValidatingSplash(); return }
    if (state.needsConnection) { ConnectionRequiredScreen(onRetry = { viewModel.retryValidation() }); return }

    val startDest = if (state.isAuthenticated) "home" else "login"

    NavHost(navController = nav, startDestination = startDest) {
        composable("login") {
            LoginScreen(
                onPhone = { nav.navigate("phone") },
                onMatrixLogin = { nav.navigate("matrix-login") },
            )
        }
        composable("matrix-login") {
            MatrixLoginScreen(
                vm = viewModel,
                onSuccess = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("phone") {
            PhoneSignupScreen(
                vm = viewModel,
                onCodeSent = { phone, name -> nav.navigate("otp/${phone}/${name}") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("otp/{phone}/{name}") { backStack ->
            val phone = backStack.arguments?.getString("phone").orEmpty()
            val name = backStack.arguments?.getString("name").orEmpty()
            OtpVerifyScreen(
                vm = viewModel,
                phone = phone,
                displayName = name,
                onSuccess = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("home") {
            HomeScreen(vm = viewModel, onOpenRoom = { roomId, roomName ->
                val rid = URLEncoder.encode(roomId, "UTF-8")
                val rn = URLEncoder.encode(roomName, "UTF-8")
                nav.navigate("chat/$rid/$rn")
            })
        }
        composable("chat/{roomId}/{roomName}") { backStack ->
            val roomId = URLDecoder.decode(backStack.arguments?.getString("roomId").orEmpty(), "UTF-8")
            val roomName = URLDecoder.decode(backStack.arguments?.getString("roomName").orEmpty(), "UTF-8")
            ChatScreen(vm = viewModel, roomId = roomId, roomName = roomName,
                onBack = { nav.popBackStack() })
        }
    }
}

@androidx.compose.runtime.Composable
private fun ValidatingSplash() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        com.nickdegs.hush.ui.components.LiquidBackground()
        androidx.compose.material3.CircularProgressIndicator(
            color = com.nickdegs.hush.ui.theme.Violet
        )
    }
}

@androidx.compose.runtime.Composable
private fun ConnectionRequiredScreen(onRetry: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        com.nickdegs.hush.ui.components.LiquidBackground()
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.Lock, null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Text(
                "İnternet bağlantısı gerekli",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                "Hush kimliğini doğrulamak için bağlantı şart. Çevrimdışı kullanılamaz.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = com.nickdegs.hush.ui.theme.Violet
                )
            ) { androidx.compose.material3.Text("Tekrar Dene") }
        }
    }
}
