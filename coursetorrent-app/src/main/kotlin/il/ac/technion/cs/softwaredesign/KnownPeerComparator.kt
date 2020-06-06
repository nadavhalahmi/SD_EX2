package il.ac.technion.cs.softwaredesign

class KnownPeerCompartor {

    companion object : Comparator<KnownPeer> {

        override fun compare(p1: KnownPeer, p2: KnownPeer): Int = when {
            p1.ip.split(".")[0] != p2.ip.split(".")[0] ->
                p1.ip.split(".")[0].toInt() - p2.ip.split(".")[0].toInt()
            p1.ip.split(".")[1] != p2.ip.split(".")[1] ->
                p1.ip.split(".")[1].toInt() - p2.ip.split(".")[1].toInt()
            p1.ip.split(".")[2] != p2.ip.split(".")[2] ->
                p1.ip.split(".")[2].toInt() - p2.ip.split(".")[2].toInt()
            else ->
                p1.ip.split(".")[3].toInt() - p2.ip.split(".")[3].toInt()
        }
    }
}