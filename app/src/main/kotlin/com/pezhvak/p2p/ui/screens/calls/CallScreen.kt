package com.pezhvak.p2p.ui.screens.calls

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pezhvak.p2p.calls.CallManager
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    peerId: String,
    hasVideo: Boolean,
    onCallEnd: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val callState by viewModel.callState.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(true) }
    var isCameraOn by remember { mutableStateOf(hasVideo) }
    var isScreenSharing by remember { mutableStateOf(false) }

    LaunchedEffect(peerId) {
        viewModel.startCall(peerId, hasVideo)
    }

    LaunchedEffect(callState) {
        if (callState is CallManager.CallState.Ended) onCallEnd()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Remote video / avatar background
        when {
            remoteVideoTrack != null && hasVideo -> {
                WebRtcVideoView(
                    videoTrack = remoteVideoTrack!!,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Large avatar placeholder
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = peerId.take(12) + "…",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    CallStatusText(callState)
                }
            }
        }

        // Controls overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
        ) {
            CallControls(
                isMuted = isMuted,
                isSpeaker = isSpeaker,
                isCameraOn = isCameraOn,
                isScreenSharing = isScreenSharing,
                hasVideo = hasVideo,
                onToggleMute = {
                    isMuted = !isMuted
                    viewModel.toggleMute(isMuted)
                },
                onToggleSpeaker = { isSpeaker = !isSpeaker },
                onToggleCamera = {
                    isCameraOn = !isCameraOn
                    viewModel.toggleVideo(isCameraOn)
                },
                onSwitchCamera = { viewModel.switchCamera() },
                onScreenShare = { isScreenSharing = !isScreenSharing },
                onHangUp = { viewModel.endCall(); onCallEnd() }
            )
        }
    }
}

@Composable
fun CallStatusText(callState: CallManager.CallState) {
    val text = when (callState) {
        is CallManager.CallState.Ringing -> if (callState.isIncoming) "Incoming…" else "Ringing…"
        is CallManager.CallState.Connecting -> "Connecting…"
        is CallManager.CallState.Active -> "Connected"
        else -> ""
    }
    Text(text, style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun CallControls(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    isScreenSharing: Boolean,
    hasVideo: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onScreenShare: () -> Unit,
    onHangUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControlButton(
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            label = if (isMuted) "Unmute" else "Mute",
            onClick = onToggleMute,
            active = !isMuted,
        )
        CallControlButton(
            icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
            label = "Speaker",
            onClick = onToggleSpeaker,
            active = isSpeaker,
        )
        if (hasVideo) {
            CallControlButton(
                icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = "Camera",
                onClick = onToggleCamera,
                active = isCameraOn,
            )
            CallControlButton(
                icon = Icons.Default.Cameraswitch,
                label = "Flip",
                onClick = onSwitchCamera,
                active = true,
            )
        }
        CallControlButton(
            icon = Icons.Default.ScreenShare,
            label = "Screen",
            onClick = onScreenShare,
            active = isScreenSharing,
        )
        // Hang-up (red, large)
        FloatingActionButton(
            onClick = onHangUp,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Default.CallEnd, "End call", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, null)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WebRtcVideoView(videoTrack: VideoTrack, modifier: Modifier = Modifier) {
    // AndroidView wrapping SurfaceViewRenderer
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            org.webrtc.SurfaceViewRenderer(ctx).apply {
                val eglBase = org.webrtc.EglBase.create()
                init(eglBase.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
                videoTrack.addSink(this)
            }
        },
        modifier = modifier,
    )
}
