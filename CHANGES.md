Changes:

CourseTorrentModule.kt:

line 6: (forgot to do this as we did in TestModule)
**CHANGE was done in ex1 resubmission too**

REPLACE:

	override fun configure() = TODO("Implement me!")

WITH: 

	override fun configure() {
        install(SecureStorageModule())
        bind<ITorrentHTTP>().to<TorrentHTTP>()
    }


CourseTorrent.kt:
	
line 210:
ADDED

	params["port"] = "6887"
	
Databases.kt:

lines 156-181: (change was done to get rid of get())

REPLACE

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

WITH

    fun getTrackerStats(hash: String, tracker: String): CompletableFuture<ScrapeData?> {
            //TODO: deal with tacker failed
            return trackerExists(hash,tracker).thenCompose {exists ->
                if(exists) {
    
                    trackersDB.thenApply { db ->
                        val complete =
                                storageManager.getValue(db, "$hash-$tracker", "complete").thenApply { res -> res?.toString(charset)!!.toInt() }
                        val downloaded =
                                storageManager.getValue(db, "$hash-$tracker", "downloaded").thenApply { res -> res?.toString(charset)!!.toInt() }
                        val incomplete =
                                storageManager.getValue(db, "$hash-$tracker", "incomplete").thenApply { res -> res?.toString(charset)!!.toInt() }
                        val name = storageManager.getValue(db, "$hash-$tracker", "name").thenApply { res -> res?.toString(charset) }
                        CompletableFuture.completedFuture(listOf(complete, downloaded, incomplete, name))
                    }.thenCompose {
                        it.thenApply {
                            Scrape(it[0] as Int, it[1] as Int, it[2] as Int, it[3] as String) as ScrapeData?
                        }
                    }
                }else{
                    CompletableFuture.completedFuture(null as ScrapeData?)
                }
            }
        }

lines 183-206: (change was done to get rid of get())

REPLACE

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
                    storageManager.setExists(db, hash)
                    TorrentStats(0, 0, 0, 0, 0.0, 0, 0, Duration.ZERO, Duration.ZERO)
                }
            }
        }
        
WITH

     fun getTorrentStats(hash: String): CompletableFuture<TorrentStats>{
            return torrentStatsExists(hash).thenCompose {exists ->
                if(exists) {
                    torrentsStatsDB.thenApply { db ->
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
    
                    CompletableFuture.completedFuture(listOf(uploaded, downloaded, left, wasted, shareRatio, pieces, havePieces, leechTime, seedTime))
                    }.thenCompose {
                        it.thenApply {
                            TorrentStats(it[0] as Long, it[1] as Long, it[2] as Long, it[3] as Long, it[4] as Double, it[5] as Long, it[6] as Long, Duration.ZERO, Duration.ZERO)
                        }
                    }
                }else{
                    torrentsStatsDB.thenApply { db ->
                        storageManager.setExists(db, hash)
                    }
                    CompletableFuture.completedFuture(TorrentStats(0, 0, 0, 0, 0.0, 0, 0, Duration.ZERO, Duration.ZERO))
                }
            }
        }














