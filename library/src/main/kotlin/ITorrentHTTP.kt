import com.google.inject.Inject
import java.net.URL

interface ITorrentHTTP{
    fun get(tracker: String, params: HashMap<String, String>): ByteArray
}