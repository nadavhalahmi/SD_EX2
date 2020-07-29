package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.SequenceInputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@Timeout(value = 1, unit = TimeUnit.MINUTES)
internal class CourseTorrentStaffTest {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()
    private val lameChunky = this::class.java.getResource("/lame_chunky.torrent").readBytes()
    private val lameInfohash = "dfe8b0cfd81c4b05fb96ec6b63c041bacce10263"
    private val lameChunkyInfohash = "2618261af23b3d4ae6e70b2fdadf2ba3d7ad187f"
    private val lameExe = this::class.java.getResource("/lame.exe")
    private val lameEnc = this::class.java.getResource("/lame_enc.dll")
    private val lamePieceLength = 16384
    private val lameChunkyPieceLength = 16384 * 8

    private var torrent = injector.getInstance<CourseTorrent>()


    @Nested
    inner class SmallTest {
        var tracker: FakeTracker? = null
        var peer: FakePeer? = null

        @AfterEach
        fun cleanup() {
            tracker?.stop()
            peer?.close()?.get()
            try {
                torrent.stop().get()
            } catch (e: Throwable) {
            }
        }

        @Test
        fun `announce to server`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf(Peer(InetSocketAddress(Inet4Address.getLoopbackAddress(), 6969), true))))
            )
            tracker!!.start()
            torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0).get()

            assertThat(tracker!!.lastAnnouncedPort, greaterThanOrEqualTo(6881) and lessThanOrEqualTo(6889))
            assertThat(infohash, equalTo(lameInfohash))
        }

        @Test
        fun `remote peer handshakes successfully`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf()))
            )
            tracker!!.start()
            val peers = remoteHandshake(infohash).thenCompose {
                torrent.connectedPeers(infohash)
            }.get()
            val knownPeers = torrent.knownPeers(infohash).get()

            assertThat(peers, hasSize(equalTo(1)))
            assertThat(peers[0], has(ConnectedPeer::knownPeer, has(KnownPeer::port, equalTo(peer!!.localPort))))
            assertThat(knownPeers, hasSize(equalTo(1)))
            assertThat(knownPeers[0], has(KnownPeer::port, equalTo(peer!!.localPort)))
        }


        @Test
        fun `local peer handshakes successfully`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf(Peer(InetSocketAddress(Inet4Address.getLoopbackAddress(), 6969), true))))
            )
            peer = LocalFakePeer(6969, infohash)
            tracker!!.start()

            val peers = localHandshake(infohash, Inet4Address.getLoopbackAddress().hostAddress, 6969)
                .thenCompose {
                    torrent.connectedPeers(infohash)
                }.get()

            assertThat(peers, hasSize(equalTo(1)))
            assertThat(peers[0], has(ConnectedPeer::knownPeer, has(KnownPeer::port, equalTo(6969))))
        }

        @Test
        fun `sends unchoke message and updates info`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf()))
            )
            tracker!!.start()
            val beforeConnectedPeer = remoteHandshake(infohash).thenCompose {
                torrent.connectedPeers(infohash)
            }.get().first()
            assertTrue(beforeConnectedPeer.amChoking)

            val message =
                peer!!.recvMessage().thenCombine(torrent.unchoke(infohash, beforeConnectedPeer.knownPeer)) { m, _ -> m }
                    .get()
            val connectedPeer = torrent.connectedPeers(infohash).get().first()

            assertThat(message, isA<Unchoke>())
            assertFalse(connectedPeer.amChoking)
        }

        @Test
        fun `sends unchoke, choke message and updates info`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf()))
            )
            tracker!!.start()
            val beforeConnectedPeer = remoteHandshake(infohash).thenCompose {
                torrent.connectedPeers(infohash)
            }.get().first()
            assertTrue(beforeConnectedPeer.amChoking)

            val message1 =
                peer!!.recvMessage().thenCombine(torrent.unchoke(infohash, beforeConnectedPeer.knownPeer)) { m, _ -> m }
                    .get()
            val message2 =
                peer!!.recvMessage().thenCombine(torrent.choke(infohash, beforeConnectedPeer.knownPeer)) { m, _ -> m }
                    .get()
            val connectedPeer = torrent.connectedPeers(infohash).get().first()

            assertThat(message1, isA<Unchoke>())
            assertThat(message2, isA<Choke>())
            assertTrue(connectedPeer.amChoking)
        }

        @Test
        fun `sends interested after receiving have`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf()))
            )
            tracker!!.start()
            val beforeConnectedPeer = remoteHandshake(infohash).thenCompose {
                torrent.connectedPeers(infohash)
            }.get().first()
            assertFalse(beforeConnectedPeer.amInterested)

            peer!!.sendMessage(Have(2)).thenCombine(torrent.handleSmallMessages()) { _, _ -> }.get(5, TimeUnit.SECONDS)
            val message = peer!!.recvMessage().get(5, TimeUnit.SECONDS)
            val connectedPeer = torrent.connectedPeers(infohash).get().first()


            assertThat(message, isA<Interested>())
            assertTrue(connectedPeer.amInterested)
        }

        @Test
        fun `after receiving have and unchoke, marks piece available`() {
            val infohash = torrent.load(lame).get()
            tracker = FakeTracker(
                8082,
                mapOf(Pair(infohash, listOf()))
            )
            tracker!!.start()
            remoteHandshake(infohash).thenCompose {
                torrent.connectedPeers(infohash)
            }.get()

            peer!!.sendMessage(Have(17)).thenCombine(torrent.handleSmallMessages()) { _, _ -> }.get(5, TimeUnit.SECONDS)
            peer!!.sendMessage(Unchoke).thenCombine(torrent.handleSmallMessages()) { _, _ -> }.get(5, TimeUnit.SECONDS)

            val pieces = torrent.availablePieces(infohash, 10, 0).get()

            assertThat(pieces.keys, hasSize(equalTo(1)))
            assertThat(pieces.values.first(), hasElement(17L))
        }

        @Test
        fun `exceptions are thrown inside the CompletableFuture`() {
            val future = assertDoesNotThrow { torrent.knownPeers("this is not a valid infohash") }
            val throwable = assertThrows<CompletionException> { future.join() }

            checkNotNull(throwable.cause)
            assertThat(throwable.cause!!, isA<IllegalArgumentException>())
        }

        @Test
        fun `lame torrent is loaded, file data is loaded, and recheck returns true`() {
            val infohash = torrent.load(lame).get()

            val done = torrent.loadFiles(
                infohash,
                mapOf("lame.exe" to lameExe.readBytes(), "lame_enc.dll" to lameEnc.readBytes())
            )
                .thenCompose { torrent.recheck(infohash) }.get()

            assertTrue(done)
        }

        @Test
        fun `lame torrent is loaded, wrong file data is loaded, and recheck returns false`() {
            val infohash = torrent.load(lame).get()

            val done = torrent.loadFiles(
                infohash,
                mapOf("lame.exe" to "wrong data".toByteArray(), "lame_enc.dll" to "wrongest data".toByteArray())
            )
                .thenCompose { torrent.recheck(infohash) }.get()

            assertFalse(done)
        }

        private fun remoteHandshake(infohash: String) =
            torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0).thenCompose {
                torrent.start()
            }.thenCompose {
                peer = RemoteFakePeer(tracker!!.lastAnnouncedPort, infohash)
                peer!!.connect()
            }.thenCompose {
                peer!!.handshake()
                    .thenCombine(torrent.handleSmallMessages()) { _, _ -> }
            }

        private fun localHandshake(infohash: String, addr: String, port: Int): CompletableFuture<Unit> {
            val knownPeers = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0).thenCompose {
                torrent.start()
            }.thenCompose {
                torrent.knownPeers(infohash)
            }.get()

            val knownPeer = knownPeers.find { it.ip == addr && it.port == port }!!
            return peer!!.connect().thenCompose { peer!!.handshake() }
                .thenCombine(torrent.connect(infohash, knownPeer)) { a, _ -> a }
        }
    }

    @Nested
    inner class MediumTests {
        private val invLocalRatio = 3
        private var tracker: FakeTracker? = null
        private var peers: List<FakePeer> = listOf()

        @AfterEach
        fun cleanup() {
            tracker?.stop()
            for (peer in peers) {
                peer.close().get()
            }
            torrent.stop().get()
        }

        private fun initialize(n: Int, metainfo: ByteArray, seeds: Set<Int> = setOf()) {
            val numOfLocalPeers = n / invLocalRatio
            val localPeers = (0 until numOfLocalPeers).map {
                Peer(
                    InetSocketAddress(Inet4Address.getLoopbackAddress(), 6969 + it),
                    seeds.contains(it)
                )
            }

            val infohash = torrent.load(metainfo).get()

            tracker = FakeTracker(8082, mapOf(Pair(infohash, localPeers)))
            tracker!!.start()

            torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0).get()

            peers = localPeers.map {
                LocalFakePeer(
                    it.addr.port,
                    infohash
                )
            } + (0 until n - numOfLocalPeers).map { RemoteFakePeer(tracker!!.lastAnnouncedPort, infohash) }

            torrent.start().get()

            for (knownPeer in torrent.knownPeers(infohash).get()) {

                val peer = peers.filterIsInstance<LocalFakePeer>().find { it.localPort == knownPeer.port }!!
                peer.connect().thenCompose { peer.handshake() }
                    .thenCombine(torrent.connect(infohash, knownPeer)) { _, _ -> }
                    .thenCompose { torrent.handleSmallMessages() }.get()
            }

            CompletableFuture.allOf(*peers.slice(numOfLocalPeers until n).map { it.connect() }.toTypedArray())
                .thenCompose {
                    CompletableFuture.allOf(*peers.slice(numOfLocalPeers until n).map { it.handshake() }.toTypedArray())
                        .thenCombine(torrent.handleSmallMessages()) { _, _ -> }
                }.get()
        }

        // Assume it's in lame.exe
        private fun sendPiece(
            peer: FakePeer,
            piece: Int,
            numberOfBlocks: Int,
            pieceLength: Int,
            metainfo: ByteArray
        ): CompletableFuture<Unit> {
            val contents = readPiece(metainfo, piece, pieceLength)
            var future = CompletableFuture.completedFuture(Unit)
            for (i in 0 until numberOfBlocks) {
                future = future.thenCompose {
                    peer.recvMessage().thenCompose {
                        val request = it as Request
                        val end = pieceLength.coerceAtMost(request.begin + 16384)
                        peer.sendMessage(Piece(piece, request.begin, contents.sliceArray(request.begin until end)))
                    }
                }
            }
            return future
        }

        private fun readPiece(metainfo: ByteArray, piece: Int, pieceLength: Int): ByteArray {
            val fis =
                if (metainfo == lame) SequenceInputStream(lameExe.openStream(), lameEnc.openStream())
                else SequenceInputStream(lameEnc.openStream(), lameExe.openStream())
            fis.skip(piece.toLong() * pieceLength.toLong())
            return fis.readNBytes(pieceLength)
        }

        private fun receivePiece(
            peer: FakePeer,
            piece: Int,
            pieceLength: Int
        ): CompletableFuture<ByteArray> {
            val bb = ByteBuffer.allocate(pieceLength)
            var future = CompletableFuture.completedFuture(Unit)
            for (i in 0 until ceil(pieceLength / 16384.0 - 0.1).toInt()) {
                future = future.thenCompose {
                    peer.sendMessage(Request(piece, i * 16384, 16384)).thenCompose {
                        peer.recvMessage()
                    }.thenApply {
                        val piece = it as Piece
                        bb.put(piece.block)
                    }.thenApply { }
                }
            }
            return future.thenApply { bb.array() }
        }

        @Test
        fun `receive and send piece`() {
            val pieceNumber = 69
            val sendingPeer = 0
            val receivingPeer = 1
            initialize(3, metainfo = lame)

            val interested = peers[sendingPeer].sendMessage(Have(pieceNumber)).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                peers[sendingPeer].recvMessage()
            }.get()

            assertThat(interested, isA<Interested>())

            val availablePieces = peers[sendingPeer].sendMessage(Unchoke).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                torrent.availablePieces(lameInfohash, 5, 5)
            }.get()

            assertThat(availablePieces.keys, hasSize(equalTo(1)))
            assertThat(availablePieces.keys.first(), has(KnownPeer::port, equalTo(peers[sendingPeer].localPort)))
            assertThat(availablePieces.values, hasSize(equalTo(1)))
            assertThat(availablePieces.values.first(), hasSize(equalTo(1)))
            assertThat(availablePieces.values.first().first(), equalTo(pieceNumber.toLong()))

            val sourceKnownPeer = availablePieces.keys.first()

            sendPiece(peers[sendingPeer], pieceNumber, 1, lamePieceLength, lame).thenCombine(
                torrent.requestPiece(
                    lameInfohash,
                    sourceKnownPeer,
                    pieceNumber.toLong()
                )
            ) { _, _ -> }.get()

            val have = torrent.handleSmallMessages().thenCompose {
                peers[receivingPeer].recvMessage()
            }.get()

            assertThat(have, isA<Have>())
            assertThat(have as Have, has(Have::pieceIndex, equalTo(pieceNumber)))

            val unchoke = peers[receivingPeer].sendMessage(Interested)
                .thenCompose { torrent.handleSmallMessages() }
                .thenCompose { torrent.connectedPeers(lameInfohash) }
                .thenApply { it.first { p -> p.knownPeer.port == peers[receivingPeer].localPort }.knownPeer }
                .thenCompose { p -> torrent.unchoke(lameInfohash, p).thenApply { p } }
                .thenCompose { peers[receivingPeer].recvMessage() }.get()

            assertThat(unchoke, isA<Unchoke>())

            val requestedPieces = peers[receivingPeer].sendMessage(Request(pieceNumber, 0, 16384))
                .thenCompose { torrent.handleSmallMessages() }
                .thenCompose { torrent.requestedPieces(lameInfohash) }.get()

            assertThat(requestedPieces.keys, hasSize(equalTo(1)))
            assertThat(requestedPieces.keys.first(), has(KnownPeer::port, equalTo(peers[receivingPeer].localPort)))
            assertThat(requestedPieces.values.first(), hasSize(equalTo(1)))
            assertThat(requestedPieces.values.first().first(), equalTo(pieceNumber.toLong()))

            val targetKnownPeer = requestedPieces.keys.first()

            val received = torrent.sendPiece(lameInfohash, targetKnownPeer, pieceNumber.toLong())
                .thenCombine(receivePiece(peers[receivingPeer], pieceNumber, lamePieceLength)) { _, b -> b }.get()

            assertTrue(received contentEquals readPiece(lame, pieceNumber, lamePieceLength))
        }

        @Test
        fun `receive and send piece from chunky torrent`() {
            val pieceNumber = 7
            val sendingPeer = 0
            val receivingPeer = 1
            initialize(3, metainfo = lameChunky)

            val interested = peers[sendingPeer].sendMessage(Have(pieceNumber)).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                peers[sendingPeer].recvMessage()
            }.get()

            assertThat(interested, isA<Interested>())

            val availablePieces = peers[sendingPeer].sendMessage(Unchoke).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                torrent.availablePieces(lameChunkyInfohash, 5, 5)
            }.get()

            assertThat(availablePieces.keys, hasSize(equalTo(1)))
            assertThat(availablePieces.keys.first(), has(KnownPeer::port, equalTo(peers[sendingPeer].localPort)))
            assertThat(availablePieces.values, hasSize(equalTo(1)))
            assertThat(availablePieces.values.first(), hasSize(equalTo(1)))
            assertThat(availablePieces.values.first().first(), equalTo(pieceNumber.toLong()))

            val sourceKnownPeer = availablePieces.keys.first()

            sendPiece(
                peers[sendingPeer],
                pieceNumber,
                ceil(lameChunkyPieceLength / 16384.0).toInt(),
                lameChunkyPieceLength,
                lameChunky
            )
                .thenCombine(
                    torrent.requestPiece(
                        lameChunkyInfohash,
                        sourceKnownPeer,
                        pieceNumber.toLong()
                    )
                ) { _, _ -> }.get()

            val have = torrent.handleSmallMessages().thenCompose {
                peers[receivingPeer].recvMessage()
            }.get()

            assertThat(have, isA<Have>())
            assertThat(have as Have, has(Have::pieceIndex, equalTo(pieceNumber)))

            val unchoke = peers[receivingPeer].sendMessage(Interested)
                .thenCompose { torrent.handleSmallMessages() }
                .thenCompose { torrent.connectedPeers(lameChunkyInfohash) }
                .thenApply { it.first { p -> p.knownPeer.port == peers[receivingPeer].localPort }.knownPeer }
                .thenCompose { p -> torrent.unchoke(lameChunkyInfohash, p).thenApply { p } }
                .thenCompose { peers[receivingPeer].recvMessage() }.get()

            assertThat(unchoke, isA<Unchoke>())

            val requestedPieces = peers[receivingPeer].sendMessage(Request(pieceNumber, 0, 16384))
                .thenCompose { torrent.handleSmallMessages() }
                .thenCompose { torrent.requestedPieces(lameChunkyInfohash) }.get()

            assertThat(requestedPieces.keys, hasSize(equalTo(1)))
            assertThat(requestedPieces.keys.first(), has(KnownPeer::port, equalTo(peers[receivingPeer].localPort)))
            assertThat(requestedPieces.values.first(), hasSize(equalTo(1)))
            assertThat(requestedPieces.values.first().first(), equalTo(pieceNumber.toLong()))

            val targetKnownPeer = requestedPieces.keys.first()

            val received = receivePiece(peers[receivingPeer], pieceNumber, lameChunkyPieceLength)
                .thenCombine(torrent.sendPiece(lameChunkyInfohash, targetKnownPeer, pieceNumber.toLong())) { a, _ -> a }
                .get()

            assertTrue(received contentEquals readPiece(lameChunky, pieceNumber, lameChunkyPieceLength))
        }


        @Test
        fun `receive and send piece multiple times`() {
            val pieceNumber = 120
            val sendingPeer = 3
            val receivingPeer = 5..10
            initialize(24, metainfo = lame)

            val interested = peers[sendingPeer].sendMessage(Have(pieceNumber)).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                peers[sendingPeer].recvMessage()
            }.get()

            assertThat(interested, isA<Interested>())

            val availablePieces = peers[sendingPeer].sendMessage(Unchoke).thenCompose {
                torrent.handleSmallMessages()
            }.thenCompose {
                torrent.availablePieces(lameInfohash, 5, 5)
            }.get()

            assertThat(availablePieces.keys, hasSize(equalTo(1)))
            assertThat(availablePieces.keys.first(), has(KnownPeer::port, equalTo(peers[sendingPeer].localPort)))
            assertThat(availablePieces.values, hasSize(equalTo(1)))
            assertThat(availablePieces.values.first(), hasSize(equalTo(1)))
            assertThat(availablePieces.values.first().first(), equalTo(pieceNumber.toLong()))

            val sourceKnownPeer = availablePieces.keys.first()

            sendPiece(peers[sendingPeer], pieceNumber, 1, lamePieceLength, lame).thenCombine(
                torrent.requestPiece(
                    lameInfohash,
                    sourceKnownPeer,
                    pieceNumber.toLong()
                )
            ) { _, _ -> }.get()

            torrent.handleSmallMessages().thenCompose {
                CompletableFuture.allOf(*receivingPeer.map { peers[it].recvMessage() }.toTypedArray())
            }.get()

            CompletableFuture.allOf(*receivingPeer.map { peers[it].sendMessage(Interested) }.toTypedArray())
                .thenCompose { torrent.handleSmallMessages() }
                .thenCompose { torrent.connectedPeers(lameInfohash) }
                .thenApply { it.filter { p -> receivingPeer.map { peers[it].localPort }.contains(p.knownPeer.port) } }
                .thenCompose { ps ->
                    ps.fold(CompletableFuture.completedFuture(Unit)) { future, p ->
                        future.thenCompose { torrent.unchoke(lameInfohash, p.knownPeer) }
                    }
                }.thenCompose { CompletableFuture.allOf(*receivingPeer.map { peers[it].recvMessage() }.toTypedArray()) }
                .get()

            val requestedPieces =
                CompletableFuture.allOf(*receivingPeer.map { peers[it].sendMessage(Request(pieceNumber, 0, 16384)) }
                    .toTypedArray())
                    .thenCompose { torrent.handleSmallMessages() }
                    .thenCompose { torrent.requestedPieces(lameInfohash) }.get()

            val targetKnownPeer = requestedPieces.keys.toList()

            assertThat(targetKnownPeer, hasSize(equalTo(receivingPeer.toSet().size)))

            for (knownPeer in targetKnownPeer) {
                val targetPeer = peers.first { it.localPort == knownPeer.port }
                val received = torrent.sendPiece(lameInfohash, knownPeer, pieceNumber.toLong())
                    .thenCombine(receivePiece(targetPeer, pieceNumber, lamePieceLength)) { _, b -> b }.get()

                assertTrue(received contentEquals readPiece(lame, pieceNumber, lamePieceLength))
            }
        }
    }
}
