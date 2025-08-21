package com.bitchat.android.call

import android.content.Context
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val eglBase: EglBase = EglBase.create()
) {
    val eglContext = eglBase.eglBaseContext
    private val factory: PeerConnectionFactory
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoCapturer: VideoCapturer? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(
        observer: PeerConnection.Observer
    ): PeerConnection {
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        return factory.createPeerConnection(config, observer)!!
    }

    fun startLocalMedia(localView: SurfaceViewRenderer): MediaStreamTracks {
        localView.init(eglContext, null)
        localView.setMirror(true)
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)

        videoSource = factory.createVideoSource(false)
        videoCapturer = createCameraCapturer()
        videoCapturer?.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        videoCapturer?.startCapture(640, 480, 30)
        val videoTrack = factory.createVideoTrack("video0", videoSource)
        videoTrack.addSink(localView)

        audioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("audio0", audioSource)

        return MediaStreamTracks(videoTrack, audioTrack)
    }

    fun addTracks(pc: PeerConnection, tracks: MediaStreamTracks) {
        pc.addTrack(tracks.video)
        pc.addTrack(tracks.audio)
    }

    fun stop() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        eglBase.release()
    }

    private fun createCameraCapturer(): VideoCapturer {
        val enumerator = Camera2Enumerator(context)
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        return enumerator.createCapturer(front ?: enumerator.deviceNames.first(), null)
            ?: throw IllegalStateException("No camera found")
    }

    data class MediaStreamTracks(val video: VideoTrack, val audio: AudioTrack)
}
