package com.bitchat.android.call

import java.util.UUID

interface SignalingTransport {
    fun send(toPeer: String, bytes: ByteArray)
    fun onReceive(handler: (fromPeer: String, bytes: ByteArray) -> Unit)
}

enum class SignalType(val code: Byte) { OFFER(0x30), ANSWER(0x31), CANDIDATE(0x32), HANGUP(0x33) }

object SignalingCodec {
    fun encode(type: SignalType, callId: UUID, payload: ByteArray): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(1 + 16 + payload.size)
        bb.put(type.code)
        bb.putLong(callId.mostSignificantBits)
        bb.putLong(callId.leastSignificantBits)
        bb.put(payload)
        return bb.array()
    }
    fun decode(bytes: ByteArray): Decoded {
        val bb = java.nio.ByteBuffer.wrap(bytes)
        val type = when (bb.get()) {
            0x30.toByte() -> SignalType.OFFER
            0x31.toByte() -> SignalType.ANSWER
            0x32.toByte() -> SignalType.CANDIDATE
            0x33.toByte() -> SignalType.HANGUP
            else -> throw IllegalArgumentException("Unknown type")
        }
        val msb = bb.long
        val lsb = bb.long
        val payload = ByteArray(bb.remaining())
        bb.get(payload)
        return Decoded(type, UUID(msb, lsb), payload)
    }
    data class Decoded(val type: SignalType, val callId: UUID, val payload: ByteArray)
}

class InMemoryLoopbackTransport : SignalingTransport {
    private var handler: ((String, ByteArray) -> Unit)? = null
    override fun send(toPeer: String, bytes: ByteArray) {
        handler?.invoke(toPeer, bytes)
    }
    override fun onReceive(handler: (fromPeer: String, bytes: ByteArray) -> Unit) {
        this.handler = handler
    }
}
