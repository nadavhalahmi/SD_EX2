import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.nio.charset.Charset
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage


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

    fun setExists(db: SecureStorage, hash: String) {
        db.write(("$hash-exists").toByteArray(charset), "true".toByteArray(charset))
    }

    fun setValid(db: SecureStorage, hash: String) {
        db.write(("$hash-valid").toByteArray(charset), "true".toByteArray(charset))
    }

    fun setValue(db: SecureStorage, hash: String, key: String, value: ByteArray) {
        db.write(("$hash-$key").toByteArray(), value)
    }

    fun exists(db: SecureStorage, hash: String): Boolean {
        return db.read(("$hash-exists").toByteArray(charset))?.get()?.isNotEmpty() ?: false
    }

    fun getValue(db: SecureStorage, hash: String, key: String): ByteArray? {
        return db.read(("$hash-$key").toByteArray(charset)).get()
    }

    fun removeNotExist(db: SecureStorage, hash: String) {
        db.write(("$hash-exists").toByteArray(charset), ByteArray(0))
    }

    fun setInValid(db: SecureStorage, hash: String) {
        db.write(("$hash-valid").toByteArray(charset), ByteArray(0))
    }

    fun isValid(db: SecureStorage, hash: String): Boolean {
        return db.read(("$hash-valid").toByteArray(charset))?.get()?.isNotEmpty() ?: false
    }
}
