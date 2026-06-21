package com.nickdegs.hush.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickdegs.hush.R
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.components.glassCapsule
import com.nickdegs.hush.ui.theme.Blue
import com.nickdegs.hush.ui.theme.Violet

@Composable
fun LoginScreen(onPhone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Violet, Blue))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.tagline),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(Modifier.weight(1f))

            // Ana eylem: Telefonla
            Button(
                onClick = onPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.PhoneAndroid, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.continue_with_phone), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.terms_blurb),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
