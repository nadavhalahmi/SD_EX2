package il.ac.technion.cs.softwaredesign
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

import ITorrentHTTP
import MyStorage
import MyStorageFactory
import TorrentHTTP
import dev.misfitlabs.kotlinguice4.KotlinModule
import io.mockk.mockk

class TestModule : KotlinModule() {
    override fun configure() {
        //install(SecureStorageModule())
        bind<SecureStorageFactory>().to<MyStorageFactory>()
        bind<SecureStorage>().to<MyStorage>()
        //bind<ITorrentHTTP>().to<TorrentHTTPDummyImpl>()
        bind<ITorrentHTTP>().toInstance(mockk<TorrentHTTP>())
    }
}


