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
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
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

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }

data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val isVideo: Boolean = false,
    val peer: String = "",
    val muted: Boolean = false,
) {
    val active: Boolean get() = phase != CallPhase.IDLE && phase != CallPhase.ENDED
}

/**
 * Hush sesli/görüntülü çağrı yöneticisi (Android).
 * WebRTC + Matrix VoIP signaling (m.call.invite/answer/candidates/hangup, MSC2746 v1).
 * 1:1 çağrılar; grup (LiveKit SFU) sonraki faz.
 */
class CallManager(private val appContext: Context) {

    val eglBase: EglBase = EglBase.create()
    val uiState = MutableStateFlow(CallUiState())
    val remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = MutableStateFlow<VideoTrack?>(null)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val factory: PeerConnectionFactory

    private var client: MatrixClient? = null
    private var myUserId: String = ""
    private var collecting = false

    private var pc: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var callId: String? = null
    private var roomId: String? = null
    private val partyId = UUID.randomUUID().toString().take(8)
    private var isVideo = false
    private var remoteSet = false
    private val pendingCandidates = ArrayList<IceCandidate>()
    private var incomingOffer: String? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /** MatrixClient'a bağla + gelen sinyalleşmeyi topla (giriş sonrası bir kez). */
    fun attach(c: MatrixClient, userId: String) {
        client = c
        myUserId = userId
        if (!collecting) {
            collecting = true
            scope.launch {
                c.callEvents.collect { handleSignaling(it) }
            }
        }
    }

    // MARK: Giden çağrı

    fun startCall(roomId: String, peer: String, video: Boolean) {
        scope.launch {
            reset()
            callId = UUID.randomUUID().toString()
            this@CallManager.roomId = roomId
            isVideo = video
            uiState.value = CallUiState(CallPhase.OUTGOING, video, peer)

            val servers = loadTurn()
            createPeerConnection(servers)
            addLocalTracks(video)

            val offer = createSdp(offer = true, video = video) ?: run { end(); return@launch }
            setLocal(offer)

            val content = buildJsonObject {
                put("call_id", callId!!); put("version", "1"); put("party_id", partyId)
                put("lifetime", 60000)
                putJsonObject("offer") { put("type", "offer"); put("sdp", offer.description) }
            }
            client?.sendEvent(roomId, "m.call.invite", content.toString())
            uiState.value = uiState.value.copy(phase = CallPhase.CONNECTING)
        }
    }

    // MARK: Gelen çağrı

    fun acceptIncoming() {
        val sdp = incomingOffer ?: return
        val rid = roomId ?: return
        scope.launch {
            val servers = loadTurn()
            createPeerConnection(servers)
            addLocalTracks(isVideo)

            setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp))
            remoteSet = true
            drainCandidates()

            val answer = createSdp(offer = false, video = isVideo) ?: run { end(); return@launch }
            setLocal(answer)

            val content = buildJsonObject {
                put("call_id", callId ?: ""); put("version", "1"); put("party_id", partyId)
                putJsonObject("answer") { put("type", "answer"); put("sdp", answer.description) }
            }
            client?.sendEvent(rid, "m.call.answer", content.toString())
            uiState.value = uiState.value.copy(phase = CallPhase.CONNECTING)
        }
    }

    fun hangup() {
        val rid = roomId; val cid = callId
        if (rid != null && cid != null) {
            val content = buildJsonObject {
                put("call_id", cid); put("version", "1"); put("party_id", partyId); put("reason", "user_hangup")
            }
            scope.launch { client?.sendEvent(rid, "m.call.hangup", content.toString()) }
        }
        end()
    }

    fun toggleMute() {
        val m = !uiState.value.muted
        audioTrack?.setEnabled(!m)
        uiState.value = uiState.value.copy(muted = m)
    }

    fun switchCamera() {
        (videoCapturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    // MARK: Sinyalleşme

    private fun handleSignaling(ev: CallEvent) {
        if (ev.sender == myUserId) return
        val cid = ev.content["call_id"]?.jsonPrimitive?.contentOrNull
        when (ev.type) {
            "m.call.invite" -> {
                if (uiState.value.phase != CallPhase.IDLE) return
                val sdp = ev.content["offer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.contentOrNull ?: return
                callId = cid; roomId = ev.roomId
                isVideo = sdp.contains("m=video")
                incomingOffer = sdp
                uiState.value = CallUiState(CallPhase.INCOMING, isVideo, ev.sender)
            }
            "m.call.answer" -> {
                if (cid != callId) return
                val sdp = ev.content["answer"]?.jsonObject?.get("sdp")?.jsonPrimitive?.contentOrNull ?: return
                pc?.setRemoteDescription(setObserver { remoteSet = true; drainCandidates() },
                    SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "m.call.candidates" -> {
                if (cid != callId) return
                ev.content["candidates"]?.jsonArray?.forEach { item ->
                    val o = item.jsonObject
                    val c = o["candidate"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (c.isEmpty()) return@forEach
                    val mid = o["sdpMid"]?.jsonPrimitive?.contentOrNull ?: ""
                    val idx = o["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0
                    val cand = IceCandidate(mid, idx, c)
                    if (remoteSet) pc?.addIceCandidate(cand) else pendingCandidates.add(cand)
                }
            }
            "m.call.hangup", "m.call.reject" -> {
                if (cid != callId) return
                end()
            }
        }
    }

    // MARK: WebRTC iç

    private suspend fun loadTurn(): List<PeerConnection.IceServer> {
        val servers = ArrayList<PeerConnection.IceServer>()
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        client?.turnServer()?.let { creds ->
            servers.add(
                PeerConnection.IceServer.builder(creds.uris)
                    .setUsername(creds.username).setPassword(creds.password).createIceServer()
            )
        }
        return servers
    }

    private fun createPeerConnection(servers: List<PeerConnection.IceServer>) {
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory.createPeerConnection(config, pcObserver)
    }

    private fun addLocalTracks(video: Boolean) {
        val audioSource = factory.createAudioSource(MediaConstraints())
        val aTrack = factory.createAudioTrack("audio0", audioSource)
        audioTrack = aTrack
        pc?.addTrack(aTrack, listOf("stream0"))

        if (video) {
            val vSource = factory.createVideoSource(false)
            videoSource = vSource
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            surfaceHelper = helper
            val capturer = createCameraCapturer()
            videoCapturer = capturer
            capturer?.initialize(helper, appContext, vSource.capturerObserver)
            capturer?.startCapture(1280, 720, 30)
            val vTrack = factory.createVideoTrack("video0", vSource)
            localVideoTrack.value = vTrack
            pc?.addTrack(vTrack, listOf("stream0"))
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        names.firstOrNull { enumerator.isFrontFacing(it) }?.let { return enumerator.createCapturer(it, null) }
        return names.firstOrNull()?.let { enumerator.createCapturer(it, null) }
    }

    private suspend fun createSdp(offer: Boolean, video: Boolean): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
            }
            val obs = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) { if (cont.isActive) cont.resume(sdp) }
                override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resume(null) }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }
            if (offer) pc?.createOffer(obs, constraints) else pc?.createAnswer(obs, constraints)
        }

    private suspend fun setLocal(sdp: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        pc?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(p0: String?) { if (cont.isActive) cont.resume(Unit) }
        }, sdp)
    }

    private suspend fun setRemote(sdp: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        pc?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(p0: String?) { if (cont.isActive) cont.resume(Unit) }
        }, sdp)
    }

    private fun setObserver(onOk: () -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetSuccess() { scope.launch { onOk() } }
        override fun onSetFailure(p0: String?) {}
    }

    private fun drainCandidates() {
        pendingCandidates.forEach { pc?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun end() {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose()
        surfaceHelper?.dispose()
        videoSource?.dispose()
        pc?.close()
        pc = null
        videoCapturer = null
        surfaceHelper = null
        videoSource = null
        audioTrack = null
        remoteVideoTrack.value = null
        localVideoTrack.value = null
        uiState.value = uiState.value.copy(phase = CallPhase.ENDED)
    }

    private fun reset() {
        remoteSet = false
        pendingCandidates.clear()
        incomingOffer = null
        callId = null
        uiState.value = CallUiState()
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            scope.launch {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED ->
                        uiState.value = uiState.value.copy(phase = CallPhase.CONNECTED)
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> end()
                    else -> {}
                }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            val rid = roomId ?: return
            val cid = callId ?: return
            val content = buildJsonObject {
                put("call_id", cid); put("version", "1"); put("party_id", partyId)
                putJsonArray("candidates") {
                    addJsonObject {
                        put("candidate", candidate.sdp)
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                    }
                }
            }
            scope.launch { client?.sendEvent(rid, "m.call.candidates", content.toString()) }
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            if (track is VideoTrack) {
                scope.launch { remoteVideoTrack.value = track }
            }
        }
    }
}
