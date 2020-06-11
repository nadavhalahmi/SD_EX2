@file:Suppress("UNUSED_PARAMETER")

package il.ac.technion.cs.softwaredesign

import Coder
import ITorrentHTTP
import TorrentDict
import TorrentList
import TorrentParser
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.PeerChokedException
import il.ac.technion.cs.softwaredesign.exceptions.PeerConnectException
import il.ac.technion.cs.softwaredesign.exceptions.PieceHashException
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 * + Communication with peers (downloading! uploading!)
 */
class CourseTorrent @Inject constructor(private val databases: Databases, private val torrentHTTP: ITorrentHTTP) {
    private val activeSockets = HashMap<KnownPeer, Socket>()
    private val parser = TorrentParser()
    private val coder = Coder()
    private lateinit var server : ServerSocket

    /**
     * Load in the torrent metainfo file from [torrent]. The specification for these files can be found here:
     * [Metainfo File Structure](https://wiki.theory.org/index.php/BitTorrentSpecification#Metainfo_File_Structure).
     *
     * After loading a torrent, it will be available in the system, and queries on it will succeed.
     *
     * This is a *create* command.
     *
     * @throws IllegalArgumentException If [torrent] is not a valid metainfo file.
     * @throws IllegalStateException If the infohash of [torrent] is already loaded.
     * @return The infohash of the torrent, i.e., the SHA-1 of the `info` key of [torrent].
     */
    fun load(torrent: ByteArray): CompletableFuture<String>{
        val infoValue: ByteArray
        val dict: TorrentDict
        try {
            //infoValue = parser.getValueByKey(torrent, "info")
            dict = parser.parse(torrent)
            val infoRange = dict.getRange("info")
            infoValue = torrent.copyOfRange(infoRange.startIndex(), infoRange.endIndex())
        }catch (e: Exception){
            throw IllegalArgumentException()
        }
        val infohash = coder.SHAsum(infoValue)
        return databases.torrentExists(infohash).thenApply { exists ->
            if (exists)
                throw IllegalStateException()
            databases.addTorrent(infohash, torrent, dict)
            infohash
        }
    }

    /**
     * Remove the torrent identified by [infohash] from the system.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun unload(infohash: String): CompletableFuture<Unit>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw IllegalArgumentException()
        }.thenCompose {
            databases.deleteTorrent(infohash)
        }
    }

    /**
     * Return the announce URLs for the loaded torrent identified by [infohash].
     *
     * See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more information. This method behaves as follows:
     * * If the "announce-list" key exists, it will be used as the source for announce URLs.
     * * If "announce-list" does not exist, "announce" will be used, and the URL it contains will be in tier 1.
     * * The announce URLs should *not* be shuffled.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Tier lists of announce URLs.
     */
    fun announces(infohash: String): CompletableFuture<List<List<String>>> {
        val announceList = databases.getTorrentField(infohash, "announce-list").thenApply { lst ->
            if (lst !== null) {
                ((parser.parseList(lst).value() as TorrentList).toList() as List<List<String>>)
            }
            else
                null
        }
        val announce = databases.getTorrentField(infohash, "announce").thenApply { lst ->
            if (lst !== null) {
                val newLst = "ll".toByteArray(Charsets.UTF_8) + lst + "ee".toByteArray(Charsets.UTF_8)
                ((parser.parseList(newLst).value() as TorrentList).toList() as List<List<String>>)
            }
            else
                null
        }
        return announceList.thenCombine(announce) { announceListRes, announceRes ->
            when {
                announceListRes !== null -> announceListRes
                announceRes !== null -> announceRes
                else -> throw IllegalArgumentException()
            }
        }
//        return databases.getTorrentField(infohash, "announce-list").thenApply { announceList: ByteArray? ->
//            if (announceList !== null) {
//                announceList
//            }
//        }.thenCompose {
//            databases.getTorrentField(infohash, "announce").thenApply { announce ->
//                if (announce === null)
//                    throw IllegalArgumentException()
//                "ll".toByteArray(Charsets.UTF_8) + announce + "ee".toByteArray(Charsets.UTF_8)
//            }
//        }.thenApply{ lst ->
//            ((parser.parseList(lst!!).value() as TorrentList).toList() as List<List<String>>)
//        }
    }

    /**
     * Send an "announce" HTTP request to a single tracker of the torrent identified by [infohash], and update the
     * internal state according to the response. The specification for these requests can be found here:
     * [Tracker Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_HTTP.2FHTTPS_Protocol).
     *
     * If [event] is [TorrentEvent.STARTED], shuffle the announce-list before selecting a tracker (future calls to
     * [announces] should return the shuffled list). See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more
     * information on shuffling and selecting a tracker.
     *
     * [event], [uploaded], [downloaded], and [left] should be included in the tracker request.
     *
     * The "compact" parameter in the request should be set to "1", and the implementation should support both compact
     * and non-compact peer lists.
     *
     * Peer ID should be set to "-CS1000-{Student ID}{Random numbers}", where {Student ID} is the first 6 characters
     * from the hex-encoded SHA-1 hash of the student's ID numbers (i.e., `hex(sha1(student1id + student2id))`), and
     * {Random numbers} are 6 random characters in the range [0-9a-zA-Z] generated at instance creation.
     *
     * If the connection to the tracker failed or the tracker returned a failure reason, the next tracker in the list
     * will be contacted and the announce-list will be updated as per
     * [BEP 12](http://bittorrent.org/beps/bep_0012.html).
     * If the final tracker in the announce-list has failed, then a [TrackerException] will be thrown.
     *
     * This is an *update* command.
     *
     * @throws TrackerException If the tracker returned a "failure reason". The failure reason will be the exception
     * message.
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return The interval in seconds that the client should wait before announcing again.
     */
    fun announce(
        infohash: String,
        event: TorrentEvent,
        uploaded: Long,
        downloaded: Long,
        left: Long
    ): CompletableFuture<Int> {
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            val randLen = 6
            val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            announces(infohash = infohash).thenApply { announceList ->
                var respDict: TorrentDict? = null
                var newList = announceList.toMutableList()
                if (event == TorrentEvent.STARTED) {
                    var k = 0
                    for (i in (announceList.indices).shuffled()) {
                        newList[k] = announceList[i].shuffled()
                        k++
                    }
                    databases.updateAnnounce(infohash, newList)
                }
                for (l in newList) {
                    for (tracker in l) {
                        try {
                            val params = HashMap<String, String>()
                            params["info_hash"] = coder.binary_encode(infohash)
                            params["event"] = event.toString().toLowerCase()
                            params["uploaded"] = uploaded.toString()
                            params["downloaded"] = downloaded.toString()
                            params["left"] = left.toString()
                            params["compact"] = "1"
                            val randomString = (1..randLen)
                                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                                .map(charPool::get)
                                .joinToString("")
                            val ids = coder.SHAsum(("206784258" + "314628090").toByteArray()).slice(0 until 6)
                            params["peer_id"] = "-CS1000-$ids$randomString"
                            var resp = torrentHTTP.get(tracker, params)
                            respDict = parser.parse(resp)
                            val peers = HashSet<KnownPeer>()
                            val peersBytes =
                                resp.copyOfRange(respDict["peers"]!!.startIndex(), respDict["peers"]!!.endIndex())
                            if (peersBytes[0].toChar() == 'l') {
                                val peersList = respDict["peers"]?.value() as TorrentList
                                for (p in peersList) {
                                    val currPeer = p.value() as TorrentDict
                                    val port = (currPeer["port"]?.value() as Long).toInt()
                                    val ip = currPeer["ip"]?.value() as String
                                    peers.add(KnownPeer(ip = ip, port = port, peerId = null))
                                }
                            } else {
                                val start =
                                    (parser.parseBytes(peersBytes) { peersBytes[it].toChar() == ':' }).length + 1
                                val end = peersBytes.size
                                for (i in start until end step 6) {
                                    val (ip, port) = coder.get_ip_port(peersBytes.copyOfRange(i, i + 6))
                                    peers.add(KnownPeer(ip = ip, port = port, peerId = null))
                                }
                            }
                            databases.updatePeersList(infohash, peersBytes, peers)
                            if (respDict["interval"] != null)
                                return@thenApply (respDict["interval"]?.value() as Long).toInt()
                        } catch (e: Throwable) {
                            continue
                        }
                    }
                }
                if (respDict !== null)
                    throw TrackerException(respDict["failure reason"]?.value().toString())
                else
                    throw TrackerException("generic announce exception") //TODO: check this works
            }
        }
    }

    /**
     * Scrape all trackers identified by a torrent, and store the statistics provided. The specification for the scrape
     * request can be found here:
     * [Scrape Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_.27scrape.27_Convention).
     *
     * All known trackers for the torrent will be scraped.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun scrape(infohash: String): CompletableFuture<Unit> {
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {announces(infohash = infohash).thenApply { announceList ->
                for (l in announceList) {
                    for (tracker in l) {
                        if (tracker.split('/').last().startsWith("announce")) {
                            val lastIndex = tracker.lastIndexOf(char = '/')
                            val scrape =
                                tracker.slice(0..lastIndex) + "scrape" + tracker.slice((lastIndex + "/announce".length) until tracker.length)
                            val params = HashMap<String, String>()
                            params["info_hash"] = coder.binary_encode(infohash)
                            var resp = torrentHTTP.get(scrape, params)
                            val respDict = parser.parse(resp)
                            val files = respDict["files"]?.value() as TorrentDict
                            val stats = files[coder.string_to_hex(infohash)]?.value() as TorrentDict?
                            databases.updateTracker(infohash, tracker, stats)
                        }
                    }
                }
            }
        }
    }

    /**
     * Invalidate a previously known peer for this torrent.
     *
     * If [peer] is not a known peer for this torrent, do nothing.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun invalidatePeer(infohash: String, peer: KnownPeer): CompletableFuture<Unit>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            databases.invalidatePeer(infohash, peer)
        }
    }

    /**
     * Return all known peers for the torrent identified by [infohash], in sorted order. This list should contain all
     * the peers that the client can attempt to connect to, in ascending numerical order. Note that this is not the
     * lexicographical ordering of the string representation of the IP addresses: i.e., "127.0.0.2" should come before
     * "127.0.0.100".
     *
     * The list contains unique peers, and does not include peers that have been invalidated.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Sorted list of known peers.
     */
    fun knownPeers(infohash: String): CompletableFuture<List<KnownPeer>>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
            val res = HashSet<KnownPeer>()
            databases.getPeers(infohash).thenApply { peersBytes ->
                if (peersBytes != null) {
                    if (peersBytes[0].toChar() == 'l') {
                        val peersList = parser.parseList(peersBytes).value() as TorrentList
                        for (p in peersList) {
                            val currPeer = p.value() as TorrentDict
                            val port = (currPeer["port"]?.value() as Long).toInt()
                            val ip = currPeer["ip"]?.value() as String
                            val peer = KnownPeer(ip, port, null)
                            databases.peerIsValid(infohash, peer).thenApply { valid ->
                                if (valid)
                                    res.add(peer)
                            }
                        }
                    } else {
                        val start = (parser.parseBytes(peersBytes) { peersBytes[it].toChar() == ':' }).length + 1
                        val end = peersBytes.size
                        for (i in start until end step 6) {
                            val (ip, port) = coder.get_ip_port(peersBytes.copyOfRange(i, i + 6))
                            val peer = KnownPeer(ip, port, null)
                            databases.peerIsValid(infohash, peer).thenApply { valid ->
                                if (valid)
                                    res.add(peer)
                            }
                        }
                    }
                }
            }
            res.sortedWith(KnownPeerCompartor)
        }
    }

    /**
     * Return all known statistics from trackers of the torrent identified by [infohash]. The statistics displayed
     * represent the latest information seen from a tracker.
     *
     * The statistics are updated by [announce] and [scrape] calls. If a response from a tracker was never seen, it
     * will not be included in the result. If one of the values of [ScrapeData] was not included in any tracker response
     * (e.g., "downloaded"), it would be set to 0 (but if there was a previous result that did include that value, the
     * previous result would be shown).
     *
     * If the last response from the tracker was a failure, the failure reason would be returned ([ScrapeData] is
     * defined to allow for this). If the failure was a failed connection to the tracker, the reason should be set to
     * "Connection failed".
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return A mapping from tracker announce URL to statistics.
     */
    fun trackerStats(infohash: String): CompletableFuture<Map<String, ScrapeData>>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            val res = HashMap<String, ScrapeData>()
            announces(infohash).thenApply {announceList ->
                for (l in announceList) {
                    for (tracker in l) {
                        databases.getTrackerStats(infohash, tracker).thenAccept { stats ->
                            if (stats != null) {
                                res[tracker] = stats
                            }
                        }
                    }
                }
                res as Map<String, ScrapeData>
            }
        }
    }

    /**
     * Return information about the torrent identified by [infohash]. These statistics represent the current state
     * of the client at the time of querying.
     *
     * See [TorrentStats] for more information about the required data.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     * @return Torrent statistics.
     */
    fun torrentStats(infohash: String): CompletableFuture<TorrentStats> {
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            databases.getTorrentStats(infohash)
        }
    }

    /**
     * Start listening for peer connections on a chosen port.
     *
     * The port chosen should be in the range 6881-6889, inclusive. Assume all ports in that range are free.
     *
     * For a given instance of [CourseTorrent], the port sent to the tracker in [announce] and the port chosen here
     * should be the same.
     *
     * This is a *update* command. (maybe)
     *
     * @throws IllegalStateException If already listening.
     */
    fun start(): CompletableFuture<Unit>{
        if(!this::server.isInitialized)
            server = ServerSocket(6887)
        println("Server is running on port ${server.localPort}")
        return CompletableFuture.completedFuture(Unit)
    }

    /**
     * Disconnect from all connected peers, and stop listening for new peer connections
     *
     * You may assume that this method is called before the instance is destroyed, and perform clean-up here.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalStateException If not listening.
     */
    fun stop(): CompletableFuture<Unit>{
        activeSockets.map { it -> it.value.close() }
        server.close()
        return CompletableFuture.completedFuture(Unit)
    }

    /**
     * Connect to [peer] using the peer protocol described in [BEP 003](http://bittorrent.org/beps/bep_0003.html).
     * Only connections over TCP are supported. If connecting to the peer failed, an exception is thrown.
     *
     * After connecting, send a handshake message, and receive and process the peer's handshake message. The peer's
     * handshake will contain a "peer_id", and future calls to [knownPeers] should return this peer_id for this peer.
     *
     * If this torrent has anything downloaded, send a bitfield message.
     *
     * Wait 100ms, and in that time handle any bitfield or have messages that are received.
     *
     * In the handshake, the "reserved" field should be set to 0 and the peer_id should be the same as the one that was
     * sent to the tracker.
     *
     * [peer] is equal to (and has the same [hashCode]) an object that was returned by [knownPeers] for [infohash].
     *
     * After a successful connection, this peer should be listed by [connectedPeers]. Peer connections start as choked
     * and not interested for both this client and the peer.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded or [peer] is not known.
     * @throws PeerConnectException if the connection to [peer] failed (timeout, connection closed after handshake, etc.)
     */
    fun connect(infohash: String, peer: KnownPeer): CompletableFuture<Unit> = TODO("Implement me!")

    /**
     * Disconnect from [peer] by closing the connection.
     *
     * There is no need to send any messages.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded or [peer] is not connected.
     */
    fun disconnect(infohash: String, peer: KnownPeer): CompletableFuture<Unit> = TODO("Implement me!")

    /**
     * Return a list of peers that this client is currently connected to, with some statistics.
     *
     * See [ConnectedPeer] for more information.
     *
     * This is a *read* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     */
    fun connectedPeers(infohash: String): CompletableFuture<List<ConnectedPeer>>{
        val ports = activeSockets.map { it.value.port }
        val lst = activeSockets.map { it ->
            ConnectedPeer(KnownPeer(it.value.inetAddress.toString(), it.value.port, ""), amChoking = true,
                    amInterested = true, averageSpeed = 0.0, completedPercentage = 0.0, peerChoking = true,
                    peerInterested = true)
        }
        return CompletableFuture.completedFuture(lst)
    }

    /**
     * Send a choke message to [peer], which is currently connected. Future calls to [connectedPeers] should show that
     * this peer is choked.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded or [peer] is not connected.
     */
    fun choke(infohash: String, peer: KnownPeer): CompletableFuture<Unit>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            val s = activeSockets[peer]
            if(s==null) throw java.lang.IllegalArgumentException()
            s.outputStream.write(WireProtocolEncoder.encode(0))
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Send an unchoke message to [peer], which is currently connected. Future calls to [connectedPeers] should show
     * that this peer is not choked.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded or [peer] is not connected.
     */
    fun unchoke(infohash: String, peer: KnownPeer): CompletableFuture<Unit>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            val s = activeSockets[peer]
            if(s==null) throw java.lang.IllegalArgumentException()
            s.outputStream.write(WireProtocolEncoder.encode(1))
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Handle any messages that peers have sent, and send keep-alives if needed, as well as interested/not interested
     * messages.
     *
     * Messages to receive and handle from peers:
     *
     * 1. keep-alive: Do nothing.
     * 2. unchoke: Mark this peer as not choking in future calls to [connectedPeers].
     * 3. choke: Mark this peer as choking in future calls to [connectedPeers].
     * 4. have: Update the internal state of which pieces this client has, as seen in future calls to [availablePieces]
     * and [connectedPeers].
     * 5. request: Mark the peer as requesting a piece, as seen in future calls to [requestedPieces]. Ignore if the peer
     * is choked.
     * 6. handshake: When a new peer connects and performs a handshake, future calls to [knownPeers] and
     * [connectedPeers] should return it.
     *
     * Messages to send to each peer:
     *
     * 1. keep-alive: If it has been more than one minute since we sent a keep-alive message (it is OK to keep a global
     * count)
     * 2. interested: If the peer has a piece we don't, and we're currently not interested, send this message and mark
     * the client as interested in future calls to [connectedPeers].
     * 3. not interested: If the peer does not have any pieces we don't, and we're currently interested, send this
     * message and mark the client as not interested in future calls to [connectedPeers].
     *
     * These messages can also be handled by different parts of the code, as desired. In that case this method can do
     * less, or even nothing. It is guaranteed that this method will be called reasonably often.
     *
     * This is an *update* command. (maybe)
     */
    fun handleSmallMessages(): CompletableFuture<Unit>{

        val s = server.accept()
        val output = s.inputStream.readNBytes(68)
        s.outputStream.write(output)
        activeSockets[KnownPeer(ip = s.inetAddress.toString(), port = s.port, peerId = "")] = s
        return CompletableFuture.completedFuture(Unit)
    }

    /**
     * Download piece number [pieceIndex] of the torrent identified by [infohash].
     *
     * Attempt to download a complete piece by sending a series of request messages and receiving piece messages in
     * response. This method finishes successfully (i.e., the [CompletableFuture] is completed) once an entire piece has
     * been received, or an error.
     *
     * Requests should be of piece subsets of length 16KB (2^14 bytes). If only a part of the piece is downloaded, an
     * exception is thrown. It is unspecified whether partial downloads are kept between two calls to requestPiece:
     * i.e., on failure, you can either keep the partially downloaded data or discard it.
     *
     * After a complete piece has been downloaded, its SHA-1 hash will be compared to the appropriate SHA-1 has from the
     * torrent meta-info file (see 'pieces' in the 'info' dictionary), and in case of a mis-match an exception is
     * thrown and the downloaded data is discarded.
     *
     * This is an *update* command.
     *
     * @throws PeerChokedException if the peer choked the client before a complete piece has been downloaded.
     * @throws PeerConnectException if the peer disconnected before a complete piece has been downloaded.
     * @throws PieceHashException if the piece SHA-1 hash does not match the hash from the meta-info file.
     * @throws IllegalArgumentException if [infohash] is not loaded, [peer] is not known, or [peer] does not have [pieceIndex].
     */
    fun requestPiece(infohash: String, peer: KnownPeer, pieceIndex: Long): CompletableFuture<Unit> {
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose { //TODO: handle execptions
            val s = activeSockets[peer]
            if(s==null) throw java.lang.IllegalArgumentException()
            s.outputStream.write(WireProtocolEncoder.encode(6, ints= *intArrayOf(13, pieceIndex.toInt())))
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Send piece number [pieceIndex] of the [infohash] torrent to [peer].
     *
     * Upload a complete piece (as much as possible) by sending a series of piece messages. This method finishes
     * successfully (i.e., the [CompletableFuture] is completed) if [peer] hasn't requested another subset of the piece
     * in 100ms.
     *
     * This is an *update* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded, [peer] is not known, or [peer] did not request [pieceIndex].
     */
    fun sendPiece(infohash: String, peer: KnownPeer, pieceIndex: Long): CompletableFuture<Unit> = TODO("Implement me!")

    /**
     * List pieces that are currently available for download immediately.
     *
     * That is, pieces that:
     * 1. We don't have yet,
     * 2. A peer we're connected to does have,
     * 3. That peer is not choking us.
     *
     * Returns a mapping from connected, unchoking, interesting peer to a list of maximum length [perPeer] of pieces
     * that meet the above criteria. The lists may overlap (contain the same piece indices). The pieces in the list
     * should begin at [startIndex] and continue sequentially in a cyclical manner up to `[startIndex]-1`.
     *
     * For example, there are 3 pieces, we don't have any of them, and we are connected to PeerA that has piece 1 and
     * 2 and is not choking us. So, `availablePieces(infohash, 3, 2) => {PeerA: [2, 1]}`.
     *
     * This is a *read* command. (maybe)
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     * @return Mapping from peer to a list of [perPeer] pieces that can be downloaded from it, starting at [startIndex].
     */
    fun availablePieces(
        infohash: String,
        perPeer: Long,
        startIndex: Long
    ): CompletableFuture<Map<KnownPeer, List<Long>>> = TODO("Implement me!")

    /**
     * List pieces that have been requested by (unchoked) peers.
     *
     * If a a peer sent us a request message for a subset of a piece (possibly more than one), that piece will be listed
     * here.
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     * @return Mapping from peer to a list of unique pieces that it has requested.
     */
    fun requestedPieces(
        infohash: String
    ): CompletableFuture<Map<KnownPeer, List<Long>>> = TODO("Implement me!")

    /**
     * Return the downloaded files for torrent [infohash].
     *
     * Partially downloaded files are allowed. Bytes that haven't been downloaded yet are zeroed.
     * File names are given including path separators, e.g., "foo/bar/file.txt".
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     * @return Mapping from file name to file contents.
     */
    fun files(infohash: String): CompletableFuture<Map<String, ByteArray>>{
        TODO("NOT IMPLEMENTED")
//        return databases.torrentExists(infohash).thenApply { exists ->
//            if (!exists)
//                throw java.lang.IllegalArgumentException()
//        }.thenCompose {
//            databases.getFiles(infohash)
//        }
    }

    /**
     * Load files into the client.
     *
     * If [files] has extra files, they are ignored. If it is missing a file, it is treated as all zeroes. If file
     * contents are too short, the file is padded with zeroes. If the file contents are too long, they are truncated.
     *
     * @param files A mapping from filename to file contents.
     * @throws IllegalArgumentException if [infohash] is not loaded,
     */
    fun loadFiles(infohash: String, files: Map<String, ByteArray>): CompletableFuture<Unit>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            var allFiles = ByteArray(0)
            for (file in files) {
                databases.addFile(infohash, file.key, file.value)
                allFiles += file.value
            }
            databases.addAllFiles(infohash, allFiles)
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Compare SHA-1 hash for the loaded pieces of torrent [infohash] against the meta-info file. If a piece fails hash
     * checking, it is zeroed and marked as not downloaded.
     *
     * @throws IllegalArgumentException if [infohash] is not loaded.
     * @return True if all the pieces have been downloaded and passed hash checking, false otherwise.
     */
    fun recheck(infohash: String): CompletableFuture<Boolean>{
        return databases.torrentExists(infohash).thenApply { exists ->
            if (!exists)
                throw java.lang.IllegalArgumentException()
        }.thenCompose {
            databases.getTorrentField(infohash, "info").thenCompose {info ->
                val infoDict = parser.parse(info!!)
                var pieces = info.copyOfRange(infoDict["pieces"]!!.startIndex(), infoDict["pieces"]!!.endIndex())
                val start =
                        (parser.parseBytes(pieces) { pieces[it].toChar() == ':' }).length + 1
                val end = pieces.size
                pieces = pieces.copyOfRange(start, end)
                val pieceLength = (infoDict["piece length"]!!.value() as Long).toInt()
                val files = infoDict["files"]!!.value() as TorrentList
                //val filePaths = files.map { elem ->  elem["path"]!![0].value() as String}

                var res = true
                var pi = 0
                //TODO: CHECK if allfiles is too short or pieces is too short
                databases.getAllFiles(infohash).thenApply { allfiles ->
                    for ( fpi in allfiles!!.indices step pieceLength) {
                        val filePiece = allfiles.copyOfRange(fpi, minOf(fpi+pieceLength, allfiles.lastIndex+1))
                        val filePieceHash = coder.SHAsum(filePiece)
                        val pieceFromPieces = pieces.copyOfRange(pi * 20, (pi + 1) * 20)
                        val firstFilePieceHashBytes = coder.binary_encode(filePieceHash, simple = true).toByteArray(Charsets.ISO_8859_1)
                        res = pieceFromPieces contentEquals firstFilePieceHashBytes
                        pi++
                        if (!res)
                            break
                    }
                    res
                }
            }
        }
    }
}