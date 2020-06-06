package il.ac.technion.cs.softwaredesign

class TestUtils {
    private fun ipAddressToInt(ipNumbers: List<String>): Int {
        var ip = 0
        for (i in 0..3) {
            ip += ipNumbers[i].toInt() shl 24 - 8 * i
        }

        return ip
    }
    fun buildPeersValueAsBinaryString(announces: List<Pair<String, Int>>): String {
        val bytes = ByteArray(announces.size * 6)
        for ((idx, announce) in announces.withIndex()) {
            val ipNumbers = ipAddressToInt(announce.first.split("."))

            bytes[(idx * 6) + 0] = (ipNumbers ushr 24).toByte()
            bytes[(idx * 6) + 1] = (ipNumbers ushr 16).toByte()
            bytes[(idx * 6) + 2] = (ipNumbers ushr 8).toByte()
            bytes[(idx * 6) + 3] = (ipNumbers).toByte()
            bytes[(idx * 6) + 4] = (announce.second ushr 8).toByte()
            bytes[(idx * 6) + 5] = announce.second.toByte()
        }

        return bytes.toString(Charsets.ISO_8859_1)
    }
}