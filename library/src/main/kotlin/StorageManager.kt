import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.nio.charset.Charset
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
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
class StorageManager{

    fun setExists(db: SecureStorage, hash: String) : CompletableFuture<Unit>{
        return db.write(("$hash-exists").toByteArray(charset), "true".toByteArray(charset))
    }

    fun setValid(db: SecureStorage, hash: String) : CompletableFuture<Unit>{
        return db.write(("$hash-valid").toByteArray(charset), "true".toByteArray(charset))
    }

    fun setValue(db: SecureStorage, hash: String, key: String, value: ByteArray) : CompletableFuture<Unit>{
        return db.write(("$hash-$key").toByteArray(), value)
    }

    fun exists(db: SecureStorage, hash: String): CompletableFuture<Boolean> {
        return db.read(("$hash-exists").toByteArray(charset)).thenApply{ res ->
            res?.isNotEmpty() ?: false
        }
    }

    fun getValue(db: SecureStorage, hash: String, key: String): CompletableFuture<ByteArray?> {
        return db.read(("$hash-$key").toByteArray(charset))
    }

    fun removeNotExist(db: SecureStorage, hash: String) : CompletableFuture<Unit>{
        return db.write(("$hash-exists").toByteArray(charset), ByteArray(0))
    }

    fun setInValid(db: SecureStorage, hash: String) : CompletableFuture<Unit>{
        return db.write(("$hash-valid").toByteArray(charset), ByteArray(0))
    }

    fun isValid(db: SecureStorage, hash: String): CompletableFuture<Boolean> {
        return db.read(("$hash-valid").toByteArray(charset)).thenApply { res ->
            res?.isNotEmpty() ?: false
        }
    }
}
