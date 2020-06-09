package il.ac.technion.cs.softwaredesign

import StorageManager
import TorrentDict
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture


private val charset: Charset = Charsets.UTF_8
/**
 * Wrapper class for read/write calls
 * each dict is saved as above:
 *      -hash -> value
 *      -hash^key1 -> dict[key1] as ByteArray
 *      -hash^key2 -> dict[key2] as ByteArray
 *      -...
 */
class Databases @Inject constructor(private val db_factory: SecureStorageFactory) {
    private val torrentsDB = db_factory.open("my_torrents".toByteArray(charset))
    private val peersDB = db_factory.open("peers".toByteArray(charset))
    private val trackersDB = db_factory.open("trackers".toByteArray(charset))
    private val torrentsStatsDB = db_factory.open("torrents_statistics".toByteArray(charset))
    private val filesDB = db_factory.open("files".toByteArray(charset))
    private val storageManager = StorageManager()

    /**
     * saves torrent to database as mentioned above
     */
    fun addTorrent(hash: String, value: ByteArray, dict: TorrentDict): CompletableFuture<Unit>{
        return torrentsDB.thenApply { db ->
            storageManager.setExists(db, hash)
            for (key in dict.keys) {
                //TODO: CHECK if whole info can be saved
                if (key == "announce" || key == "announce-list" || key == "info") {
                    val range = dict.getRange(key)
                    storageManager.setValue(
                        db,
                        hash,
                        key,
                        value.copyOfRange(dict[key]!!.startIndex(), dict[key]!!.endIndex())
                    )
                    db.write((hash + key).toByteArray(), value.copyOfRange(range.startIndex(), range.endIndex()))
                }
            }
        }
    }

    fun torrentExists(hash: String): CompletableFuture<Boolean> {
        return torrentsDB.thenCompose { db ->
            storageManager.exists(db, hash)
        }
    }

    /**
     * gets value from database
     */
    fun getTorrentField(hash: String, key: String): CompletableFuture<ByteArray?> {
        return torrentsDB.thenCompose { db ->
            storageManager.exists(db, hash).thenCompose { exists ->
                if (!exists) CompletableFuture.completedFuture(null)
                storageManager.getValue(db, hash, key)
            }
        }
    }

    /**
     * delete value from databaase:
     * (writes empty ByteArray to that key)
     */
    fun deleteTorrent(hash: String): CompletableFuture<Unit> {
        return torrentsDB.thenCompose { db ->
            storageManager.removeNotExist(db, hash)
        }
    }

    fun getPeers(hash: String): CompletableFuture<ByteArray?> {
        return torrentsDB.thenCompose { db ->
            storageManager.getValue(db, hash, "peers")
        }
    }

    fun updateAnnounce(hash: String, announceList: List<List<String>>) : CompletableFuture<Unit> {
        return torrentsDB.thenCompose { db ->
            var newAnnounce = "l"
            for (l in announceList) {
                newAnnounce += "l"
                for (url in l) {
                    newAnnounce += url.toByteArray(charset).size.toString() + ":" + url
                }
                newAnnounce += "e"
            }
            newAnnounce += "e"
            storageManager.setValue(db, hash, "announce", newAnnounce.toByteArray(charset))
            storageManager.setValue(db, hash, "announce-list", newAnnounce.toByteArray(charset))
        }
    }

    fun updatePeersList(hash: String, peersBytes: ByteArray, peers: HashSet<KnownPeer>) : CompletableFuture<Unit>{
        return torrentsDB.thenCombine(peersDB){ tdb, pdb ->
            storageManager.setValue(tdb, hash, "peers", peersBytes)
            for(peer in peers) {
                storageManager.setValid(pdb, "$hash-${peer.ip}-${peer.port}")
            }
        }
    }

    fun updateTracker(hash: String,tracker: String, stats: TorrentDict?) : CompletableFuture<Unit> {
        return trackersDB.thenCombine(trackerExists(hash, tracker)) { db, exists ->
            if (stats != null) {
                if (!exists) {
                    storageManager.setExists(db, "$hash-$tracker")
                    storageManager.setValue(db, hash, "$tracker-complete", "0".toByteArray())
                    storageManager.setValue(db, hash, "$tracker-downloaded", "0".toByteArray())
                    storageManager.setValue(db, hash, "$tracker-incomplete", "0".toByteArray())
                    //name is null by default
                }
                for (key in stats.keys) {
                    storageManager.setValue(
                        db,
                        hash,
                        "$tracker-$key",
                        (stats[key]?.value() as Long).toString().toByteArray()
                    )
                }
            }
        }
    }

    fun trackerExists(hash: String, tracker: String): CompletableFuture<Boolean> {
        return trackersDB.thenCompose { db ->
            storageManager.exists(db, "$hash-$tracker")
        }
    }

    fun invalidatePeer(hash: String, peer: KnownPeer) : CompletableFuture<Unit> {
        return peersDB.thenCombine(peerIsValid(hash, peer)) { db, valid ->
            if (valid) {
                storageManager.setInValid(db, "$hash-${peer.ip}-${peer.port}")
            }
        }
    }

    fun peerIsValid(hash: String, peer: KnownPeer): CompletableFuture<Boolean> {
        return peersDB.thenCompose { db ->
            storageManager.isValid(db, "$hash-${peer.ip}-${peer.port}")
        }
    }

    fun getTrackerStats(hash: String, tracker: String): CompletableFuture<Scrape?> {
        //TODO: deal with tacker failed
        return trackersDB.thenCombine(trackerExists(hash, tracker)) { db, exists ->
            if (exists) {
                val complete =
                    storageManager.getValue(db, "$hash-$tracker", "complete").thenApply{res -> res?.toString(charset)!!.toInt() }
                val downloaded =
                    storageManager.getValue(db, "$hash-$tracker", "downloaded").thenApply{res -> res?.toString(charset)!!.toInt() }
                val incomplete =
                    storageManager.getValue(db, "$hash-$tracker", "incomplete").thenApply{res -> res?.toString(charset)!!.toInt() }
                val name = storageManager.getValue(db, "$hash-$tracker", "name").thenApply{res -> res?.toString(charset)}

//                CompletableFuture.completedFuture(CompletableFuture.allOf(
//                        complete, downloaded, incomplete, name)
//                        .thenCompose { _ ->
//                            Scrape(complete.get(), downloaded.get(), incomplete.get(), name.get())
//                        })
                //val futures = listOf<CompletableFuture<*>>(complete, downloaded, incomplete, name)

                Scrape(complete.get(), downloaded.get(), incomplete.get(), name.get()) //TODO: FIX
            }
            else {
                null
            }
        }
    }

    fun getTorrentStats(hash: String): CompletableFuture<TorrentStats>{
        return torrentsStatsDB.thenCombine(torrentStatsExists(hash)) { db, exists ->
            if (exists) {
                val uploaded =
                        storageManager.getValue(db, hash, "uploaded").thenApply{res -> res?.toString(charset)!!.toLong() }
                val downloaded =
                        storageManager.getValue(db, hash, "downloaded").thenApply{res -> res?.toString(charset)!!.toLong() }
                val left =
                        storageManager.getValue(db, hash, "left").thenApply{res -> res?.toString(charset)!!.toLong() }
                val wasted = storageManager.getValue(db, hash, "wasted").thenApply{res -> res?.toString(charset)!!.toLong() }
                val shareRatio = storageManager.getValue(db, hash, "shareRatio").thenApply{res -> res?.toString(charset)!!.toDouble() }
                val pieces = storageManager.getValue(db, hash, "pieces").thenApply{res -> res?.toString(charset)!!.toLong() }
                val havePieces = storageManager.getValue(db, hash, "wasted").thenApply{res -> res?.toString(charset)!!.toLong() }
                val leechTime = storageManager.getValue(db, hash, "leechTime").thenApply{res -> res?.toString(charset)!!.toLong() }//TODO: FIX
                val seedTime = storageManager.getValue(db, hash, "seedTime").thenApply{res -> res?.toString(charset)!!.toLong() }//TODO: FIX

                TorrentStats(uploaded.get(), downloaded.get(), left.get(), wasted.get(), shareRatio.get(), pieces.get(), havePieces.get(), Duration.ZERO, Duration.ZERO) //TODO: FIX
            }
            else {
                null //TODO: CHECK THIS null (return time is not nullable right now)
            }
        }
    }

    private fun torrentStatsExists(hash: String): CompletableFuture<Boolean> {
        return torrentsStatsDB.thenCompose { db ->
            storageManager.exists(db, hash)
        }
    }

    fun addFile(hash: String, key: String, value: ByteArray): CompletableFuture<Unit> {
        return filesDB.thenCompose { db ->
            storageManager.setValue(db, hash, key, value)
        }
    }

    fun getFile(hash: String, key: String): CompletableFuture<ByteArray?> {
        return filesDB.thenCompose { db ->
            storageManager.getValue(db, hash, key)
        }
    }

    fun addAllFiles(infohash: String, allFiles: ByteArray): CompletableFuture<Unit> {
        return filesDB.thenCompose { db ->
            storageManager.setValue(db, infohash, "", allFiles)
        }
    }

    fun getAllFiles(infohash: String) : CompletableFuture<ByteArray?>{
        return filesDB.thenCompose { db ->
            storageManager.getValue(db, infohash, "")
        }
    }
}
