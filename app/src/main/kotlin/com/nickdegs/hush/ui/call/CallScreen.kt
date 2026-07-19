package com.nickdegs.hush.ui.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickdegs.hush.core.call.CallPhase
import com.nickdegs.hush.core.store.AppViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val Bg = Color(0xFF0B0913)
private val Violet = Color(0xFF7C3AED)

/** Uygulamanın her yerinden çıkan çağrı ekranı (kök overlay). */
@Composable
fun CallOverlay(vm: AppViewModel) {
    val state by vm.call.uiState.collectAsStateWithLifecycle()
    if (!state.active) return

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (state.phase) {
            CallPhase.INCOMING -> IncomingCall(vm)
            else -> ActiveCall(vm)
        }
    }
}

@Composable
private fun IncomingCall(vm: AppViewModel) {
    val state by vm.call.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(140.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(70.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(shortName(state.peer), color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(if (state.isVideo) "Gelen görüntülü çağrı" else "Gelen sesli çağrı",
            color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(70.dp)) {
            RoundButton(Icons.Filled.CallEnd, Color(0xFFE53935)) { vm.call.hangup() }
            RoundButton(Icons.Filled.Call, Color(0xFF43A047)) { vm.call.acceptIncoming() }
        }
    }
}

@Composable
private fun ActiveCall(vm: AppViewModel) {
    val state by vm.call.uiState.collectAsStateWithLifecycle()
    val remote by vm.call.remoteVideoTrack.collectAsStateWithLifecycle()
    val local by vm.call.localVideoTrack.collectAsStateWithLifecycle()
    val egl = remember { vm.call.eglBase.eglBaseContext }

    Box(Modifier.fillMaxSize()) {
        if (state.isVideo && remote != null) {
            VideoRenderer(remote, egl, mirror = false, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(140.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(70.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(shortName(state.peer), color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(label(state.phase), color = Color.White.copy(alpha = 0.7f))
            }
        }

        // Yerel önizleme (görüntülüde köşede)
        if (state.isVideo && local != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 16.dp)
                .width(110.dp).height(150.dp).clip(RoundedCornerShape(16.dp))) {
                VideoRenderer(local, egl, mirror = true, modifier = Modifier.fillMaxSize())
            }
        }

        // Kontroller
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundButton(if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                if (state.muted) Color(0xFFE53935) else Color.White.copy(alpha = 0.18f)) { vm.call.toggleMute() }
            RoundButton(Icons.Filled.CallEnd, Color(0xFFE53935), large = true) { vm.call.hangup() }
            if (state.isVideo) {
                RoundButton(Icons.Filled.Cameraswitch, Color.White.copy(alpha = 0.18f)) { vm.call.switchCamera() }
            }
        }
    }
}

@Composable
private fun VideoRenderer(track: VideoTrack?, egl: EglBase.Context, mirror: Boolean, modifier: Modifier) {
    val holder = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).also {
                it.init(egl, null)
                it.setMirror(mirror)
                it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                holder.value = it
            }
        }
    )
    DisposableEffect(track, holder.value) {
        val r = holder.value
        if (track != null && r != null) track.addSink(r)
        onDispose { if (track != null && r != null) track.removeSink(r) }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, large: Boolean = false, onClick: () -> Unit) {
    val s = if (large) 72.dp else 60.dp
    Box(
        Modifier.size(s).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (large) 30.dp else 24.dp))
    }
}

private fun shortName(uid: String): String =
    uid.substringBefore(":").removePrefix("@").ifEmpty { "Çağrı" }

private fun label(phase: CallPhase): String = when (phase) {
    CallPhase.OUTGOING -> "Aranıyor…"
    CallPhase.CONNECTING -> "Bağlanılıyor…"
    CallPhase.CONNECTED -> "Bağlı"
    else -> ""
}
