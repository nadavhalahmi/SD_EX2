package il.ac.technion.cs.softwaredesign

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * This file contains utilities for handling the BitTorrent Wire Protocol. See the associated test for usage examples.
 * You can modify, delete, replace, or do anything else with this file.
 */

internal object StaffWireProtocolEncoder {
    /**
     * Encode a message of type [messageId] with several int fields.
     */
    fun encode(messageId: Byte, vararg ints: Int): ByteArray = encode(messageId, ByteArray(0), *ints)

    /**
     * Encode a message of type [messageId] with several int fields then a string block of [contents].
     */
    fun encode(messageId: Byte, contents: ByteArray = ByteArray(0), vararg ints: Int): ByteArray {
        val length = 1 + ints.size * 4 + contents.size
        val bb = ByteBuffer.allocate(length + 4)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putInt(length)
        bb.put(messageId)
        ints.forEach { bb.putInt(it) }
        contents.forEach { bb.put(it) }
        return bb.array()
    }

    fun handshake(infohash: ByteArray, peerId: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(68)
        bb.put(19)
        bb.put("BitTorrent protocol".toByteArray())
        bb.putLong(0)
        bb.put(infohash)
        bb.put(peerId)
        return bb.array()
    }
}

internal object StaffWireProtocolDecoder {
    /**
     * Given [message], a valid (maybe partial, at least 4 bytes) bittorrent message, return its stated length.
     *
     * Use this to figure out how much to receive.
     */
    fun length(message: ByteArray): Int = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN).int

    /**
     * Given [message], a valid (full, including length) bittorrent message, return the message ID.
     *
     * Use this to figure out how to parse the message.
     */
    fun messageId(message: ByteArray): Byte = ByteBuffer.wrap(message, 4, 1).get()

    /**
     * Given [message], a valid (full, including length) bittorrent message, and [numOfInts], the number of integer
     * fields in messages of this type, parse it and return a decoded representation.
     */
    fun decode(message: ByteArray, numOfInts: Int): StaffDecodedMessage {
        val bb = ByteBuffer.wrap(message)
        bb.order(ByteOrder.BIG_ENDIAN)
        val length = bb.getInt()
        val contentsLength = length - numOfInts * 4 - 1
        val messageId = bb.get()
        val ints = numOfInts.downTo(1).map { bb.getInt() }
        val contents = ByteArray(contentsLength) // This is an allocation
        if (contentsLength != 0) {
            bb.get(contents) // And this is a copy. Ugly and slow.
        }
        return StaffDecodedMessage(length, messageId, ints, contents)
    }

    fun handshake(message: ByteArray): StaffDecodedHandshake {
        val bb = ByteBuffer.wrap(message, 28, 40)
        val infohash = ByteArray(20)
        val peerId = ByteArray(20)
        bb.get(infohash, 0, 20)
        bb.get(peerId, 0, 20)
        return StaffDecodedHandshake(infohash, peerId)
    }
}

internal data class StaffDecodedHandshake(
    val infohash: ByteArray,
    val peerId: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StaffDecodedHandshake

        if (!infohash.contentEquals(other.infohash)) return false
        if (!peerId.contentEquals(other.peerId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = infohash.contentHashCode()
        result = 31 * result + peerId.contentHashCode()
        return result
    }
}

internal data class StaffDecodedMessage(
    val length: Int,
    val messageId: Byte,
    val ints: List<Int>,
    val contents: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StaffDecodedMessage

        if (length != other.length) return false
        if (messageId != other.messageId) return false
        if (ints != other.ints) return false
        if (!contents.contentEquals(other.contents)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + messageId
        result = 31 * result + ints.hashCode()
        result = 31 * result + contents.contentHashCode()
        return result
    }
}

internal sealed class Message(val id: Byte, val length: Long) {
    companion object {
        fun decode(raw: ByteArray): Message {
            val len = StaffWireProtocolDecoder.length(raw)
            if (len == 0) {
                assert(raw contentEquals ByteArray(4) { 0 })
                return KeepAlive
            }
            assert(len > 0) { "len is ${len}" }
            return when (StaffWireProtocolDecoder.messageId(raw)) {
                Choke.id -> Choke
                Unchoke.id -> Unchoke
                Interested.id -> Interested
                Uninterested.id -> Uninterested
                Have.id -> {
                    val d = StaffWireProtocolDecoder.decode(raw, Have.numOfInts)
                    Have(d.ints[0])
                }
                Bitfield.id -> {
                    val d = StaffWireProtocolDecoder.decode(raw, Bitfield.numOfInts)
                    Bitfield(d.contents)
                }
                Request.id -> {
                    val d = StaffWireProtocolDecoder.decode(raw, Request.numOfInts)
                    Request(d.ints[0], d.ints[1], d.ints[2])
                }
                Piece.id -> {
                    val d = StaffWireProtocolDecoder.decode(raw, Piece.numOfInts)
                    Piece(d.ints[0], d.ints[1], d.contents)
                }
                else -> throw RuntimeException("Unknown message (id: ${raw[5]})")
            }
        }
    }

    open fun encode(): ByteArray {
        return StaffWireProtocolEncoder.encode(id)
    }
}

internal object KeepAlive : Message(-1, 0) {
    override fun encode(): ByteArray {
        return ByteArray(4) { 0 }
    }
}

internal object Choke : Message(0, 1)

internal object Unchoke : Message(1, 1)

internal object Interested : Message(2, 1)

internal object Uninterested : Message(3, 1)

internal data class Have(val pieceIndex: Int) : Message(4, 1) {
    companion object {
        const val id: Byte = 4
        const val numOfInts = 1
    }

    override fun encode(): ByteArray {
        return StaffWireProtocolEncoder.encode(id, pieceIndex)
    }
}

internal data class Bitfield(val bitfield: ByteArray) : Message(5, 1L + bitfield.size) {
    companion object {
        const val id: Byte = 5
        const val numOfInts = 0
    }

    override fun encode(): ByteArray {
        return StaffWireProtocolEncoder.encode(id, bitfield)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bitfield

        if (!bitfield.contentEquals(other.bitfield)) return false

        return true
    }

    override fun hashCode(): Int {
        return bitfield.contentHashCode()
    }
}

internal data class Request(val pieceIndex: Int, val begin: Int, val amount: Int) : Message(6, 13) {
    companion object {
        const val id: Byte = 6
        const val numOfInts = 3
    }

    override fun encode(): ByteArray {
        return StaffWireProtocolEncoder.encode(id, pieceIndex, begin, amount)
    }
}

internal data class Piece(val pieceIndex: Int, val begin: Int, val block: ByteArray) : Message(7, 9L + block.size) {
    companion object {
        const val id: Byte = 7
        const val numOfInts = 2
    }

    override fun encode(): ByteArray {
        return StaffWireProtocolEncoder.encode(id, block, pieceIndex, begin)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Piece

        if (pieceIndex != other.pieceIndex) return false
        if (begin != other.begin) return false
        if (!block.contentEquals(other.block)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pieceIndex
        result = 31 * result + begin
        result = 31 * result + block.contentHashCode()
        return result
    }
}