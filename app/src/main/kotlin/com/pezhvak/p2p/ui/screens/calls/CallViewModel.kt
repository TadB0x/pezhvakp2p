package com.pezhvak.p2p.ui.screens.calls

import androidx.lifecycle.ViewModel
import com.pezhvak.p2p.calls.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callManager: CallManager,
) : ViewModel() {
    val callState = callManager.callState
    val remoteVideoTrack = callManager.remoteVideoTrack

    fun startCall(peerId: String, hasVideo: Boolean) = callManager.startCall(peerId, hasVideo)
    fun endCall() = callManager.endCall()
    fun toggleMute(muted: Boolean) = callManager.toggleMute(muted)
    fun toggleVideo(enabled: Boolean) = callManager.toggleVideo(enabled)
    fun switchCamera() = callManager.switchCamera()

    override fun onCleared() { callManager.endCall() }
}
