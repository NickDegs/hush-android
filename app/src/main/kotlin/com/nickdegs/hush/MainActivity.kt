package com.nickdegs.hush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
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
import com.nickdegs.hush.ui.auth.OtpVerifyScreen
import com.nickdegs.hush.ui.auth.PhoneSignupScreen
import com.nickdegs.hush.ui.home.HomeScreen
import com.nickdegs.hush.ui.theme.HushTheme

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

    val startDest = if (state.isAuthenticated) "home" else "login"

    NavHost(navController = nav, startDestination = startDest) {
        composable("login") {
            LoginScreen(
                onPhone = { nav.navigate("phone") }
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
            HomeScreen(vm = viewModel)
        }
    }
}
