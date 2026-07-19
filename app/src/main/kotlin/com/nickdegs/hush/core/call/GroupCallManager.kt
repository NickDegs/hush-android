package com.nickdegs.hush.core.call

import android.content.Context
import com.nickdegs.hush.core.matrix.CallEvent
import com.nickdegs.hush.core.matrix.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import kotlin.coroutines.resume

data class GroupParticipant(val userId: String, val videoTrack: VideoTrack? = null, val connected: Boolean = false)
data class GroupCallState(val active: Boolean = false, val isVideo: Boolean = false, val muted: Boolean = false)

/**
 * Hush MESH grup araması (Android). SFU yok — herkes birbirine P2P bağlanır.
 * Üyelik m.call.member state event'iyle; her çift için ayrı PeerConnection.
 * Küçük gruplar (sesli ~6, görüntülü ~3-4). Mevcut TURN → ek maliyet yok.
 * PeerConnectionFactory 1:1 CallManager'la PAYLAŞILIR (initialize tek sefer).
 */
class GroupCallManager(
    private val appContext: Context,
    private val factory: PeerConnectionFactory,
    val eglBase: EglBase,
) {
    val state = MutableStateFlow(GroupCallState())
    val participants = MutableStateFlow<List<GroupParticipant>>(emptyList())
    val localVideoTrack = MutableStateFlow<VideoTrack?>(null)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var client: MatrixClient? = null
    private var myUserId = ""
    private var collecting = false

    private var confId: String? = null
    private var roomId: String? = null
    private var isVideo = false

    private var localAudio: AudioTrack? = null
    private var localVideo: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

    private class PeerCtx(val pc: PeerConnection) {
        var remoteSet = false
        val pending = ArrayList<IceCandidate>()
    }
    private val peers = HashMap<String, PeerCtx>()
    private val parts = LinkedHashMap<String, GroupParticipant>()

    fun attach(c: MatrixClient, userId: String) {
        client = c; myUserId = userId
        if (!collecting) {
            collecting = true
            scope.launch { c.callEvents.collect { handleGroup(it) } }
        }
    }

    fun joinOrStart(roomId: String, video: Boolean) {
        if (state.value.active) return
        scope.launch {
            this@GroupCallManager.roomId = roomId
            isVideo = video
            parts.clear(); peers.clear(); pushParts()
            iceServers = loadTurn()
            setupLocalMedia(video)

            // Mevcut aktif üyeleri bul
            val existing = ArrayList<String>()
            var foundConf: String? = null
            client?.roomState(roomId)?.forEach { ev ->
                if (ev["type"]?.jsonPrimitive?.contentOrNull != "m.call.member") return@forEach
                val uid = ev["state_key"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (uid == myUserId) return@forEach
                val c = ev["content"]?.jsonObject ?: return@forEach
                if (c["active"]?.jsonPrimitive?.booleanOrNull == true) {
                    foundConf = foundConf ?: c["conf_id"]?.jsonPrimitive?.contentOrNull
                    existing.add(uid)
                }
            }
            confId = foundConf ?: UUID.randomUUID().toString()
            state.value = GroupCallState(active = true, isVideo = video, muted = false)

            // Üyeliğimi duyur
            val member = buildJsonObject { put("conf_id", confId!!); put("active", true); put("video", video) }
            client?.sendStateEvent(roomId, "m.call.member", myUserId, member.toString())

            // Zaten oradakilere teklif et
            existing.forEach { uid ->
                addPart(uid)
                createPeer(uid, initiator = true)
            }
        }
    }

    fun leave() {
        val rid = roomId
        scope.launch {
            if (rid != null) {
                val member = buildJsonObject { put("active", false) }
                client?.sendStateEvent(rid, "m.call.member", myUserId, member.toString())
                peers.keys.toList().forEach { sendSignal("m.call.hangup", it, buildJsonObject { put("reason", "user_hangup") }) }
            }
            teardown()
        }
    }

    fun toggleMute() {
        val m = !state.value.muted
        localAudio?.setEnabled(!m)
        state.value = state.value.copy(muted = m)
    }

    fun switchCamera() { (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null) }

    // MARK: sinyalleşme (callEvents'ten conf_id'li / member)

    private fun handleGroup(ev: CallEvent) {
        if (ev.sender == myUserId) return
        val isGroup = ev.type == "m.call.member" || ev.content["conf_id"] != null
        if (!isGroup || !state.value.active) return
        val sender = ev.sender
        when (ev.type) {
            "m.call.member" -> {
                val c = ev.content
                val active = c["active"]?.jsonPrimitive?.booleanOrNull == true
                val cid = c["conf_id"]?.jsonPrimitive?.contentOrNull
                if (active && cid == confId) addPart(sender)   // yeni gelen bize teklif eder, bekleriz
                else removePeer(sender)
            }
            "m.call.invite" -> {
                if (ev.content["conf_id"]?.jsonPrimitive?.contentOrNull != confId) return
                if (ev.content["invitee"]?.jsonPrimitive?.contentOrNull != myUserId) return
                val sdp = ev.content["offer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.contentOrNull ?: return
                addPart(sender)
                scope.launch { answerPeer(sender, sdp) }
            }
            "m.call.answer" -> {
                if (ev.content["invitee"]?.jsonPrimitive?.contentOrNull != myUserId) return
                val sdp = ev.content["answer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.contentOrNull ?: return
                val ctx = peers[sender] ?: return
                ctx.pc.setRemoteDescription(setObs(sender), SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "m.call.candidates" -> {
                if (ev.content["invitee"]?.jsonPrimitive?.contentOrNull != myUserId) return
                val ctx = peers[sender] ?: return
                ev.content["candidates"]?.jsonArray?.forEach { item ->
                    val o = item.jsonObject
                    val c = o["candidate"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (c.isEmpty()) return@forEach
                    val mid = o["sdpMid"]?.jsonPrimitive?.contentOrNull ?: ""
                    val idx = o["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0
                    val cand = IceCandidate(mid, idx, c)
                    if (ctx.remoteSet) ctx.pc.addIceCandidate(cand) else ctx.pending.add(cand)
                }
            }
            "m.call.hangup" -> {
                if (ev.content["invitee"]?.jsonPrimitive?.contentOrNull != myUserId) return
                removePeer(sender)
            }
        }
    }

    private fun createPeer(uid: String, initiator: Boolean) {
        val pc = makePeer(uid)
        if (!initiator) return
        val c = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(emptyObs(), sdp)
                scope.launch {
                    sendSignal("m.call.invite", uid, buildJsonObject {
                        putJsonObject("offer") { put("type", "offer"); put("sdp", sdp.description) }
                    })
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, c)
    }

    private suspend fun answerPeer(uid: String, offerSdp: String) {
        val pc = makePeer(uid)
        val ctx = peers[uid] ?: return
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onSetFailure(p0: String?) { if (cont.isActive) cont.resume(Unit) }
            }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        }
        ctx.remoteSet = true
        ctx.pending.forEach { pc.addIceCandidate(it) }; ctx.pending.clear()
        val c = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(emptyObs(), sdp)
                scope.launch {
                    sendSignal("m.call.answer", uid, buildJsonObject {
                        putJsonObject("answer") { put("type", "answer"); put("sdp", sdp.description) }
                    })
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, c)
    }

    private fun makePeer(uid: String): PeerConnection {
        peers[uid]?.let { return it.pc }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = factory.createPeerConnection(config, observerFor(uid))!!
        localAudio?.let { pc.addTrack(it, listOf("stream0")) }
        localVideo?.let { pc.addTrack(it, listOf("stream0")) }
        peers[uid] = PeerCtx(pc)
        return pc
    }

    private fun observerFor(uid: String) = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            scope.launch {
                val conn = s == PeerConnection.IceConnectionState.CONNECTED || s == PeerConnection.IceConnectionState.COMPLETED
                parts[uid]?.let { parts[uid] = it.copy(connected = conn); pushParts() }
                if (s == PeerConnection.IceConnectionState.FAILED || s == PeerConnection.IceConnectionState.CLOSED) removePeer(uid)
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                sendSignal("m.call.candidates", uid, buildJsonObject {
                    putJsonArray("candidates") {
                        addJsonObject {
                            put("candidate", candidate.sdp); put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex)
                        }
                    }
                })
            }
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            if (track is VideoTrack) scope.launch { parts[uid]?.let { parts[uid] = it.copy(videoTrack = track); pushParts() } }
        }
    }

    private fun setObs(uid: String) = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetSuccess() {
            scope.launch {
                val ctx = peers[uid] ?: return@launch
                ctx.remoteSet = true
                ctx.pending.forEach { ctx.pc.addIceCandidate(it) }; ctx.pending.clear()
            }
        }
        override fun onSetFailure(p0: String?) {}
    }

    private fun emptyObs() = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetSuccess() {}
        override fun onSetFailure(p0: String?) {}
    }

    private suspend fun sendSignal(type: String, uid: String, extra: JsonObject) {
        val rid = roomId ?: return; val cid = confId ?: return
        val content = buildJsonObject {
            put("call_id", cid); put("conf_id", cid); put("version", "1"); put("party_id", myUserId); put("invitee", uid)
            extra.forEach { (k, v) -> put(k, v) }
        }
        client?.sendEvent(rid, type, content.toString())
    }

    private fun addPart(uid: String) {
        if (!parts.containsKey(uid)) { parts[uid] = GroupParticipant(uid); pushParts() }
    }

    private fun removePeer(uid: String) {
        peers.remove(uid)?.pc?.close()
        parts.remove(uid); pushParts()
    }

    private fun pushParts() { participants.value = parts.values.toList() }

    private suspend fun loadTurn(): List<PeerConnection.IceServer> {
        val list = ArrayList<PeerConnection.IceServer>()
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        client?.turnServer()?.let {
            list.add(PeerConnection.IceServer.builder(it.uris).setUsername(it.username).setPassword(it.password).createIceServer())
        }
        return list
    }

    private fun setupLocalMedia(video: Boolean) {
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("audio0", audioSource)
        if (video) {
            val vs = factory.createVideoSource(false)
            videoSource = vs
            val helper = SurfaceTextureHelper.create("GroupCapture", eglBase.eglBaseContext)
            surfaceHelper = helper
            val enumerator = Camera2Enumerator(appContext)
            val cap = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let { enumerator.createCapturer(it, null) }
                ?: enumerator.deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }
            capturer = cap
            cap?.initialize(helper, appContext, vs.capturerObserver)
            cap?.startCapture(1280, 720, 30)
            val vt = factory.createVideoTrack("video0", vs)
            localVideo = vt
            localVideoTrack.value = vt
        }
    }

    private fun teardown() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose(); surfaceHelper?.dispose(); videoSource?.dispose()
        peers.values.forEach { it.pc.close() }
        peers.clear(); parts.clear(); pushParts()
        localAudio = null; localVideo = null; videoSource = null; capturer = null; surfaceHelper = null
        confId = null
        localVideoTrack.value = null
        state.value = GroupCallState(active = false)
    }
}
