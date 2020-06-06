import java.net.URL

class TorrentHTTP : ITorrentHTTP {
    override fun get(tracker: String, params: HashMap<String, String>): ByteArray {
//        var reqParam = "?"
//        for(p in params){
//            reqParam += p.key + "=" + p.value + "&"
//        }
//        reqParam.dropLast(1)
//
//        val mURL = URL("$tracker$reqParam")
//        return mURL.readBytes()
        return ByteArray(20)
    }
}