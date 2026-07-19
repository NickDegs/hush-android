package com.nickdegs.hush.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickdegs.hush.core.call.GroupParticipant
import com.nickdegs.hush.core.store.AppViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val Bg = Color(0xFF0B0913)
private val Violet = Color(0xFF7C3AED)

@Composable
fun GroupCallOverlay(vm: AppViewModel) {
    val state by vm.groupCall.state.collectAsStateWithLifecycle()
    if (!state.active) return
    val parts by vm.groupCall.participants.collectAsStateWithLifecycle()
    val local by vm.groupCall.localVideoTrack.collectAsStateWithLifecycle()
    val egl = remember { vm.groupCall.eglBase.eglBaseContext }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(top = 50.dp, bottom = 110.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Tile("Sen", if (state.isVideo) local else null, egl, mirror = true, connected = true) }
            items(parts, key = { it.userId }) { p ->
                Tile(shortName(p.userId), if (state.isVideo) p.videoTrack else null, egl, mirror = false, connected = p.connected)
            }
        }

        Text("Grup araması · ${parts.size + 1} kişi",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp))

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Round(if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                if (state.muted) Color(0xFFE53935) else Color.White.copy(alpha = 0.18f)) { vm.groupCall.toggleMute() }
            Round(Icons.Filled.CallEnd, Color(0xFFE53935), large = true) { vm.groupCall.leave() }
            if (state.isVideo) Round(Icons.Filled.Cameraswitch, Color.White.copy(alpha = 0.18f)) { vm.groupCall.switchCamera() }
        }
    }
}

@Composable
private fun Tile(name: String, track: VideoTrack?, egl: EglBase.Context, mirror: Boolean, connected: Boolean) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.4f))) {
        if (track != null) {
            GroupVideo(track, egl, mirror, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Text(name, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun GroupVideo(track: VideoTrack, egl: EglBase.Context, mirror: Boolean, modifier: Modifier) {
    val holder = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(modifier = modifier, factory = { ctx ->
        SurfaceViewRenderer(ctx).also {
            it.init(egl, null); it.setMirror(mirror); it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            holder.value = it
        }
    })
    DisposableEffect(track, holder.value) {
        val r = holder.value
        if (r != null) track.addSink(r)
        onDispose { if (r != null) track.removeSink(r) }
    }
}

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, large: Boolean = false, onClick: () -> Unit) {
    val s = if (large) 72.dp else 60.dp
    Box(Modifier.size(s).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (large) 30.dp else 24.dp))
    }
}

private fun shortName(uid: String): String = uid.substringBefore(":").removePrefix("@").ifEmpty { "?" }
