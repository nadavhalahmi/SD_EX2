package il.ac.technion.cs.softwaredesign

import com.dampcake.bencode.BencodeOutputStream
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import java.util.TreeMap
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

data class Peer(val addr: InetSocketAddress, val seed: Boolean) {
    fun serialize(): ByteArray {
        val bb = ByteBuffer.allocate(6)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.put(addr.address.address)
        bb.putShort(addr.port.toShort())
        val ret = bb.array()
        assert(ret.size == 6)
        return ret
    }
}

class FakeTracker(val port: Int, val peers: Map<String, Collection<Peer>>) {
    private val server = Server(port)

    var lastAnnouncedPort = 0

    init {
        server.handler = object : AbstractHandler() {
            override fun handle(
                target: String,
                baseRequest: Request,
                request: HttpServletRequest,
                response: HttpServletResponse
            ) {
                println("In handle")
                response.contentType = "text/plain; charset=ISO-8859-1"
                if (baseRequest.httpURI.path.endsWith("announce", ignoreCase = true)) {
                    handleAnnounce(target, baseRequest, request, response)
                } else if (baseRequest.httpURI.path.endsWith("scrape", ignoreCase = true)) {
                    handleScrape(target, baseRequest, request, response)
                }
            }

            private fun handleScrape(
                target: String?,
                baseRequest: Request,
                request: HttpServletRequest?,
                response: HttpServletResponse?
            ) {

            }

            private fun handleAnnounce(
                target: String,
                baseRequest: Request,
                request: HttpServletRequest,
                response: HttpServletResponse
            ) {
                request.setAttribute("org.eclipse.jetty.server.Request.queryEncoding", "ISO-8859-1");
                val port = request.getParameter("port")!!

                val infohash = request.getParameter("info_hash")
                    .map { Integer.toHexString(0xFF and it.toInt()).padStart(2, '0') }
                    .joinToString("")

                println(infohash)

                lastAnnouncedPort = port.toInt()

                val peerBB = ByteBuffer.allocate(peers[infohash]!!.size * 6)
                peers[infohash]?.map(Peer::serialize)?.forEach { peerBB.put(it) }
                val peersArray = peerBB.array()
                assert(peersArray.size == peers[infohash]!!.size * 6)
                println(peersArray.map { it.toInt().toString() }.joinToString())
                BencodeOutputStream(response.outputStream).writeDictionary(
                    TreeMap(mapOf(
                        Pair("interval", 360),
                        Pair("complete", (peers[infohash] ?: error("No infohash")).count(Peer::seed)),
                        Pair("incomplete", (peers[infohash] ?: error("No infohash")).count { !it.seed }),
                        Pair("peers", peersArray.toString(Charsets.ISO_8859_1))
                    ))
                )
                response.status = HttpServletResponse.SC_OK
                baseRequest.isHandled = true
            }
        }
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop()
    }
}
