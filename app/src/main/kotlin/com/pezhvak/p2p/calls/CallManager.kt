package com.pezhvak.p2p.calls

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pezhvak.p2p.core.crypto.*
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.nostr.NostrEvent
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC-based call manager for voice, video, and screen share.
 *
 * Signaling is done via Nostr (kind 25050 custom ephemeral events) or
 * via BLE Mesh for fully offline calls (audio only, compressed).
 *
 * Security:
 *  - DTLS-SRTP for media encryption (WebRTC default)
 *  - SDP offer/answer is NIP-44 encrypted before posting to Nostr
 *  - ICE credentials are never exposed in plaintext
 *
 * STUN/TURN: public STUN by default; users can add custom TURN servers.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val nostrRelayManager: NostrRelayManager,
) {
    companion object {
        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
        )
        const val NOSTR_KIND_CALL_SIGNAL = 25050
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var activePeerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var pendingCallPeerId: String? = null

    sealed class CallState {
        object Idle : CallState()
        data class Ringing(val peerId: String, val isIncoming: Boolean) : CallState()
        data class Connecting(val peerId: String) : CallState()
        data class Active(val peerId: String, val hasVideo: Boolean, val isScreenSharing: Boolean) : CallState()
        data class Ended(val reason: EndReason) : CallState()
    }

    enum class EndReason { HUNG_UP, REJECTED, NO_ANSWER, CONNECTION_FAILED, PEER_LEFT }

    @Serializable
    data class CallSignal(
        val type: String,       // "offer" | "answer" | "ice" | "hangup" | "ring"
        val sdp: String? = null,
        val candidate: String? = null,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null,
        val hasVideo: Boolean = false,
        val callId: String,
    )

    // ─── Initialization ──────────────────────────────────────────────────────

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(EglBase.create().eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(EglBase.create().eglBaseContext, true, true))
            .createPeerConnectionFactory()

        listenForIncomingSignals()
    }

    fun dispose() {
        endCall()
        peerConnectionFactory?.dispose()
        scope.cancel()
    }

    // ─── Outgoing Call ───────────────────────────────────────────────────────

    fun startCall(peerId: String, withVideo: Boolean = false) {
        pendingCallPeerId = peerId
        _callState.value = CallState.Ringing(peerId, isIncoming = false)
        scope.launch {
            setupLocalMedia(withVideo)
            val pc = createPeerConnection(peerId) ?: return@launch
            activePeerConnection = pc

            val callId = sha256(
                (keyManager.getPrivateKeyHex() + peerId + System.currentTimeMillis()).toByteArray()
            ).toHexString().take(16)

            // Send ring signal
            sendSignal(peerId, CallSignal(type = "ring", hasVideo = withVideo, callId = callId))

            // Create offer
            val offerOptions = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (withVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            scope.launch {
                                sendSignal(peerId, CallSignal(
                                    type = "offer",
                                    sdp = sdp.description,
                                    hasVideo = withVideo,
                                    callId = callId
                                ))
                                _callState.value = CallState.Connecting(peerId)
                            }
                        }
                        override fun onSetFailure(error: String?) = endCall(EndReason.CONNECTION_FAILED)
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, sdp)
                }
                override fun onCreateFailure(error: String?) = endCall(EndReason.CONNECTION_FAILED)
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, offerOptions)
        }
    }

    fun answerCall(callId: String, withVideo: Boolean = false) {
        val peerId = pendingCallPeerId ?: return
        scope.launch {
            setupLocalMedia(withVideo)
            _callState.value = CallState.Connecting(peerId)
        }
    }

    fun endCall(reason: EndReason = EndReason.HUNG_UP) {
        pendingCallPeerId?.let { peerId ->
            scope.launch {
                sendSignal(peerId, CallSignal(
                    type = "hangup",
                    callId = sha256(peerId.toByteArray()).toHexString().take(16)
                ))
            }
        }
        activePeerConnection?.close()
        activePeerConnection?.dispose()
        activePeerConnection = null
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        _callState.value = CallState.Ended(reason)
        pendingCallPeerId = null
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // ─── Screen Share ────────────────────────────────────────────────────────

    fun startScreenShare(mediaProjectionPermissionData: android.content.Intent) {
        val state = _callState.value
        if (state !is CallState.Active) return
        // ScreenCapturerAndroid will be initialized in ScreenShareService
        // and the video track replaced in the peer connection
        _callState.value = state.copy(isScreenSharing = true)
    }

    fun stopScreenShare() {
        val state = _callState.value
        if (state is CallState.Active) {
            _callState.value = state.copy(isScreenSharing = false)
        }
    }

    // ─── Local Media ─────────────────────────────────────────────────────────

    private fun setupLocalMedia(withVideo: Boolean) {
        val factory = peerConnectionFactory ?: return
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)

        if (withVideo) {
            val eglBase = EglBase.create()
            val videoSource = factory.createVideoSource(false)
            videoCapturer = createCameraCapturer(context)?.also { capturer ->
                capturer.initialize(
                    SurfaceTextureHelper.create("CameraThread", eglBase.eglBaseContext),
                    context, videoSource.capturerObserver
                )
                capturer.startCapture(1280, 720, 30)
            }
            localVideoTrack = factory.createVideoTrack("video0", videoSource)
            localVideoTrack?.setEnabled(true)
        }
    }

    private fun createCameraCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        // Prefer front camera for calls
        enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let {
            return enumerator.createCapturer(it, null)
        }
        return enumerator.deviceNames.firstOrNull()?.let {
            enumerator.createCapturer(it, null)
        }
    }

    // ─── PeerConnection ──────────────────────────────────────────────────────

    private fun createPeerConnection(peerId: String): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val iceServers = STUN_SERVERS.map {
            PeerConnection.IceServer.builder(it).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    sendSignal(peerId, CallSignal(
                        type = "ice",
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        callId = sha256(peerId.toByteArray()).toHexString().take(16)
                    ))
                }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                if (transceiver.receiver.track() is VideoTrack) {
                    _remoteVideoTrack.value = transceiver.receiver.track() as VideoTrack
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED ->
                        _callState.value = CallState.Active(peerId, localVideoTrack != null, false)
                    PeerConnection.PeerConnectionState.FAILED ->
                        endCall(EndReason.CONNECTION_FAILED)
                    PeerConnection.PeerConnectionState.DISCONNECTED ->
                        endCall(EndReason.PEER_LEFT)
                    else -> {}
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })?.also { pc ->
            // Add local tracks
            localAudioTrack?.let { pc.addTrack(it) }
            localVideoTrack?.let { pc.addTrack(it) }
        }
    }

    // ─── Signaling ───────────────────────────────────────────────────────────

    private suspend fun sendSignal(peerId: String, signal: CallSignal) {
        val json = Json.encodeToString(signal)
        val encrypted = nip44Encrypt(keyManager.getPrivateKeyBytes(), peerId.hexToByteArray(), json)
        val event = NostrEvent.build(
            privKeyHex = keyManager.getPrivateKeyHex(),
            kind = NOSTR_KIND_CALL_SIGNAL,
            content = encrypted,
            tags = listOf(listOf("p", peerId))
        )
        nostrRelayManager.publish(event)
    }

    private fun listenForIncomingSignals() {
        scope.launch {
            nostrRelayManager.events
                .filter { it.kind == NOSTR_KIND_CALL_SIGNAL }
                .filter { it.getTag("p") == keyManager.getOrCreateIdentity().pubKeyHex }
                .collect { event ->
                    handleSignal(event)
                }
        }
    }

    private suspend fun handleSignal(event: NostrEvent) {
        val json = try {
            nip44Decrypt(keyManager.getPrivateKeyBytes(), event.pubkey.hexToByteArray(), event.content)
        } catch (e: Exception) { return }

        val signal = try { Json.decodeFromString<CallSignal>(json) } catch (e: Exception) { return }

        when (signal.type) {
            "ring" -> {
                if (_callState.value is CallState.Idle) {
                    pendingCallPeerId = event.pubkey
                    _callState.value = CallState.Ringing(event.pubkey, isIncoming = true)
                }
            }
            "offer" -> {
                val pc = createPeerConnection(event.pubkey) ?: return
                activePeerConnection = pc
                val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.sdp)
                pc.setRemoteDescription(SimpleSdpObserver(), sdp)
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription) {
                        pc.setLocalDescription(SimpleSdpObserver(), answer)
                        scope.launch {
                            sendSignal(event.pubkey, CallSignal(
                                type = "answer",
                                sdp = answer.description,
                                callId = signal.callId
                            ))
                        }
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String?) {}
                }, MediaConstraints())
            }
            "answer" -> {
                activePeerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, signal.sdp)
                )
            }
            "ice" -> {
                signal.candidate?.let { sdp ->
                    activePeerConnection?.addIceCandidate(
                        IceCandidate(signal.sdpMid, signal.sdpMLineIndex ?: 0, sdp)
                    )
                }
            }
            "hangup" -> endCall(EndReason.PEER_LEFT)
        }
    }

    private fun endCall() = endCall(EndReason.HUNG_UP)
}

private class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
