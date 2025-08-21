package com.bitchat.android.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.webrtc.*
import org.json.JSONObject
import java.util.UUID

class CallActivity : ComponentActivity() {

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var webrtc: WebRtcManager
    private var pc: PeerConnection? = null
    private lateinit var tracks: WebRtcManager.MediaStreamTracks

    private val transport: SignalingTransport = InMemoryLoopbackTransport()
    private val callId: UUID = UUID.randomUUID()

    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startCall() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)
        remoteView.init(EglBase.create().eglBaseContext, null)

        findViewById<ImageButton>(R.id.btnHangup).setOnClickListener { finishCall() }

        if (!hasPermissions()) {
            perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        } else startCall()

        transport.onReceive { _, bytes ->
            val msg = SignalingCodec.decode(bytes)
            val sdp = String(msg.payload)
            when (msg.type) {
                SignalType.OFFER -> onRemoteOffer(sdp)
                SignalType.ANSWER -> onRemoteAnswer(sdp)
                SignalType.CANDIDATE -> onRemoteCandidate(sdp)
                SignalType.HANGUP -> finishCall()
            }
        }
    }

    private fun startCall() {
        webrtc = WebRtcManager(this)
        tracks = webrtc.startLocalMedia(localView)
        pc = webrtc.createPeerConnection(object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                send(
                    SignalType.CANDIDATE,
                    """{"mid":"${c.sdpMid}","mLine":${c.sdpMLineIndex},"cand":"${c.sdp}"}"""
                )
            }
            override fun onTrack(transceiver: RtpTransceiver?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRenegotiationNeeded() {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
        webrtc.addTracks(pc!!, tracks)
        createAndSendOffer()
    }

    private fun createAndSendOffer() {
        pc!!.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc!!.setLocalDescription(this, desc)
                send(SignalType.OFFER, desc.description)
            }
        }, MediaConstraints())
    }

    private fun onRemoteOffer(sdp: String) {
        pc!!.setRemoteDescription(
            SdpObserverAdapter(),
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
        pc!!.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc!!.setLocalDescription(this, answer)
                send(SignalType.ANSWER, answer.description)
            }
        }, MediaConstraints())
    }

    private fun onRemoteAnswer(sdp: String) {
        pc!!.setRemoteDescription(
            SdpObserverAdapter(),
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )

        // Make sure remote renderer is still ready for inbound media
        if (!remoteView.isInitialized) {
            remoteView.init(EglBase.create().eglBaseContext, null)
        }
    }

    private fun onRemoteCandidate(json: String) {
        val obj = JSONObject(json)
        val candidate = IceCandidate(
            obj.getString("mid"),
            obj.getInt("mLine"),
            obj.getString("cand")
        )
        pc?.addIceCandidate(candidate)
    }

    private fun finishCall() {
        tracks.dispose()
        pc?.close()
        webrtc.dispose()
        finish()
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun send(type: SignalType, payload: String) {
        val msg = SignalingCodec.encode(callId, type, payload.toByteArray())
        transport.send(callId, msg)
    }
}
