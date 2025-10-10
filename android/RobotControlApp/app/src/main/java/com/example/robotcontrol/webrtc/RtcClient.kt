package com.example.robotcontrol.webrtc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.webrtc.*
import java.util.concurrent.Executors

class RtcClient {
    private val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var webSocket: WebSocket? = null

    private val client = OkHttpClient()

    init {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(/* context */ null)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    suspend fun connect(signalingUrl: String, roomId: String) = withContext(Dispatchers.IO) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) { sendSignal(mapOf("type" to "ice", "candidate" to mapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp
            ), "room" to roomId)) }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
            override fun onAddStream(p0: MediaStream) {}
            override fun onRemoveStream(p0: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) { dataChannel = dc }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                // In a full implementation, bind receiver.track() to a SurfaceViewRenderer
            }
        })

        // Create a receiving transceiver for video
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        // Create DataChannel for controls
        dataChannel = peerConnection?.createDataChannel("control", DataChannel.Init())

        // Connect to signaling
        val request = Request.Builder().url(signalingUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                sendSignal(mapOf("type" to "join", "room" to roomId))
                offer(roomId)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignal(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
        })
    }

    fun disconnect() {
        try { webSocket?.close(1000, "bye") } catch (_: Throwable) {}
        dataChannel?.close()
        peerConnection?.close()
        dataChannel = null
        peerConnection = null
        webSocket = null
    }

    fun sendControl(message: String) {
        val buffer = DataChannel.Buffer(ByteString.encodeUtf8(message), false)
        dataChannel?.send(buffer)
    }

    private fun offer(roomId: String) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignal(mapOf("type" to "offer", "sdp" to desc.description, "room" to roomId))
                    }
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun handleSignal(jsonText: String) {
        try {
            val msg = org.json.JSONObject(jsonText)
            when (msg.getString("type")) {
                "answer" -> {
                    val sdp = msg.getString("sdp")
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, answer)
                }
                "ice" -> {
                    val c = msg.getJSONObject("candidate")
                    val candidate = IceCandidate(c.getString("sdpMid"), c.getInt("sdpMLineIndex"), c.getString("candidate"))
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        } catch (e: Exception) {
            Log.e("RtcClient", "signal parse error", e)
        }
    }

    private fun sendSignal(map: Map<String, Any?>) {
        val json = org.json.JSONObject(map).toString()
        webSocket?.send(json)
    }
}
