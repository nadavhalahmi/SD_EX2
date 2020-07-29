import java.net.URI

rootProject.name = "base"

sourceControl {
    gitRepository(URI("https://github.com/chaosite/sd_s19_storage_impl.git")) {
        producesModule("il.ac.technion.cs.softwaredesign:primitive-storage-layer")
    }
}

include("library")
include("coursetorrent-app")
include("coursetorrent-test")