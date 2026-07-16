package com.nickdegs.hush.ui.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickdegs.hush.core.billing.HushTier
import com.nickdegs.hush.core.store.AppViewModel
import com.nickdegs.hush.ui.components.LiquidBackground
import com.nickdegs.hush.ui.components.glassCard

@Composable
fun HushProScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val tier by vm.billing.tier.collectAsState()
    val products by vm.billing.products.collectAsState()
    var yearly by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(HushTier.PROPLUS) }
    val active = tier != HushTier.NONE

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Hush Pro", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                if (active) "Aktif: ${tier.display} — ${tier.spaceLimit} özel alan"
                else "Kendi özel topluluk alanlarını oluştur, yönet ve tüm premium özellikleri aç.",
                color = Color.White.copy(0.75f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
            Spacer(Modifier.height(18.dp))

            if (!active) {
                // Dönem seçici
                Row(Modifier.fillMaxWidth().glassCard(14).padding(4.dp)) {
                    PeriodChip("Aylık", !yearly, Modifier.weight(1f)) { yearly = false }
                    PeriodChip("Yıllık · 2 ay bedava", yearly, Modifier.weight(1f)) { yearly = true }
                }
                Spacer(Modifier.height(14.dp))

                listOf(HushTier.PRO, HushTier.PROPLUS, HushTier.ULTRA).forEach { t ->
                    TierCard(t, selected == t, vm.billing.priceOf(t, yearly), yearly) { selected = t }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { if (activity != null) vm.billing.purchase(activity, selected, yearly) },
                    enabled = products.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("${selected.display} Aboneliğini Başlat", fontWeight = FontWeight.SemiBold) }

                TextButton(onClick = { vm.billing.refresh() }) {
                    Text("Satın Alımları Geri Yükle", color = Color.White.copy(0.75f))
                }
            } else {
                Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
                    Text("${tier.display} aktif", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${tier.spaceLimit} özel topluluk alanı hakkın var", color = Color.White.copy(0.75f))
                }
            }

            Spacer(Modifier.height(20.dp))
            Feature("👥", "Kendi özel topluluk alanların", "Kademene göre 1–5 izole alan. Sadece davet ettiklerin girer.")
            Feature("👑", "Tam yöneticilik", "Üye davet et, çıkar, moderatör/admin yetkisi ver.")
            Feature("🔒", "İzole ve gizli", "Alanların keşfedilemez, listelenmez — davet-only.")
            Feature("✨", "Premium yazı tipleri, sınırsız hesap, reklamsız", "")

            Spacer(Modifier.height(16.dp))
            Text(
                "Abonelikler otomatik yenilenir. İstediğin zaman Google Play · Abonelikler'den iptal edebilirsin.",
                fontSize = 11.sp, color = Color.White.copy(0.6f), textAlign = TextAlign.Center
            )
            Row(Modifier.padding(top = 8.dp)) {
                Text("Gizlilik Politikası", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp,
                    modifier = Modifier.clickable { openUrl(ctx, "https://chat.nickdegs.com/privacy") })
                Text("  ·  ", color = Color.White.copy(0.4f), fontSize = 12.sp)
                Text("Kullanım Şartları", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp,
                    modifier = Modifier.clickable { openUrl(ctx, "https://chat.nickdegs.com/terms") })
            }
            Spacer(Modifier.height(30.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Close, "Kapat", tint = Color.White.copy(0.8f))
        }
    }
}

@Composable
private fun PeriodChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.secondary.copy(0.28f) else Color.Transparent
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun TierCard(tier: HushTier, selected: Boolean, price: String?, yearly: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassCard(18, strokeAlpha = if (selected) 0.5f else 0.18f)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.display, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (tier == HushTier.PROPLUS) {
                    Spacer(Modifier.width(8.dp))
                    Text("POPÜLER", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text("${tier.spaceLimit} özel topluluk alanı + premium",
                color = Color.White.copy(0.7f), fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price ?: "—", color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (yearly) "/yıl" else "/ay", color = Color.White.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun Feature(icon: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().glassCard(16).padding(14.dp)) {
        Text(icon, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (detail.isNotEmpty()) Text(detail, color = Color.White.copy(0.7f), fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
