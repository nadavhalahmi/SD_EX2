import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture

class MyStorage: SecureStorage{
    private val db = HashMap<MyByteArray, MyByteArray>()
    private val charset = Charsets.UTF_8
    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val res = CompletableFuture<ByteArray?>()
        //println("reading key "+key.toString(charset))
        res.complete(if(db.containsKey(MyByteArray(key)))
            db[MyByteArray(key)]?.arr
        else null)
        return res
    }
    override fun write(key: ByteArray, value: ByteArray) : CompletableFuture<Unit> {
        val res = CompletableFuture<Unit>()
        //println("writing key "+key.toString(charset))
        db[MyByteArray(key)] = MyByteArray(value)
        return res
    }
}

