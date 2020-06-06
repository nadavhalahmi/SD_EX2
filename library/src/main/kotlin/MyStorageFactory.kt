import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture

class MyStorageFactory: SecureStorageFactory {
    private val dbs = HashMap<MyByteArray, SecureStorage>()
    override fun open(name: ByteArray): CompletableFuture<SecureStorage> {
        val res = CompletableFuture<SecureStorage>()
        if(dbs.containsKey(MyByteArray(name))) {
            res.complete(dbs[MyByteArray(name)]!!)
            return res
        }
        dbs[MyByteArray(name)] = MyStorage()
        res.complete(dbs[MyByteArray(name)]!!)
        return res
    }
}