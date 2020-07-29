package il.ac.technion.cs.softwaredesign

import ITorrentHTTP
import TorrentHTTP
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import io.mockk.mockk

class CourseTorrentModule : KotlinModule() {
    override fun configure() {
        install(SecureStorageModule())
        bind<ITorrentHTTP>().to<TorrentHTTP>()
    }
}