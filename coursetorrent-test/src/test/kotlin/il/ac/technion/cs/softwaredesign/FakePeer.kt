package il.ac.technion.cs.softwaredesign

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Supplier

val IO_EXECUTOR = Executors.newCachedThreadPool()

fun <U> supplyAsync(executor: Executor, func: () -> U): CompletableFuture<U> =
    CompletableFuture.supplyAsync(Supplier(func), executor)


internal sealed class FakePeer(protected val infohash: String) {
    protected lateinit var connection: Socket
    protected lateinit var connectionBIS: BufferedInputStream
    protected lateinit var connectionBOS: BufferedOutputStream

    abstract fun connect(): CompletableFuture<Unit>
    abstract fun handshake(): CompletableFuture<Unit>

    open val localPort: Int
        get() = connection.localPort

    open fun close(): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        connectionBOS.close()
        connectionBIS.close()
        connection.close()
    }

    fun sendMessage(message: Message): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        connectionBOS.write(message.encode())
        connectionBOS.flush()
    }

    fun recvMessage(): CompletableFuture<Message> = supplyAsync(IO_EXECUTOR) {
        val prefix = connectionBIS.readNBytes(4)
        val length = StaffWireProtocolDecoder.length(prefix)
        val bb = ByteBuffer.allocate(4 + length)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.put(prefix)
        bb.put(connectionBIS.readNBytes(length))
        Message.decode(bb.array())
    }

    fun waitForMessage(toIgnore: Set<Byte>): CompletableFuture<Message> = supplyAsync(IO_EXECUTOR) {
        lateinit var m: Message
        do {
            m = recvMessage().get() // Annoying
        } while (toIgnore.contains(m.id))
        m
    }

    protected fun sendHandshake() {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(hashCode().toString().toByteArray())
        connectionBOS.write(StaffWireProtocolEncoder.handshake(hexStringToByteArray(infohash), digest.digest()))
        connectionBOS.flush()
    }

    protected fun receiveHandshake() {
        val raw = connectionBIS.readNBytes(68)
        assert(connection.isConnected)
        assert(!connection.isClosed)
        assert(raw.size == 68)
        val handshake = StaffWireProtocolDecoder.handshake(raw)
        if (!(handshake.infohash contentEquals hexStringToByteArray(infohash))) {
            throw RuntimeException("Wrong infohash")
        }
    }
}

internal class LocalFakePeer(port: Int, infohash: String) : FakePeer(infohash) {
    private val listeningSocket = ServerSocket(port, 0, null)

    override val localPort = port

    override fun connect(): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        connection = listeningSocket.accept()
        connectionBIS = BufferedInputStream(connection.inputStream)
        connectionBOS = BufferedOutputStream(connection.outputStream)
    }

    override fun handshake(): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        receiveHandshake()
        sendHandshake()
    }

    override fun close(): CompletableFuture<Unit> = super.close().thenApply {
        listeningSocket.close()
    }
}

internal class RemoteFakePeer(private val port: Int, infohash: String) : FakePeer(infohash) {
    override fun connect(): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        connection = Socket(InetAddress.getLoopbackAddress(), port)
        connectionBIS = BufferedInputStream(connection.inputStream)
        connectionBOS = BufferedOutputStream(connection.outputStream)
    }

    override fun handshake(): CompletableFuture<Unit> = supplyAsync(IO_EXECUTOR) {
        sendHandshake()
        receiveHandshake()
    }
}

fun hexStringToByteArray(input: String) = input.chunked(2).map { it.toUpperCase().toInt(16).toByte() }.toByteArray()