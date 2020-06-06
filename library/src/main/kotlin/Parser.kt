import java.security.MessageDigest
import java.util.*
import kotlin.collections.HashMap


class TorrentParser {
    private val charset = Charsets.UTF_8

    /**
     * [torrent]: the torrent to parse
     * [startIndex]: the start index of the element
     * [stopCond]: the condition for stop. depends on current index
     * @return: bytes until stopCond as string
     */
    fun parseBytes(torrent: ByteArray, startIndex: Int = 0, stopCond: (Int) -> Boolean): String {
        var str = ""
        var index = startIndex
        while(!stopCond(index)) {
            str += torrent[index].toChar()
            index++
        }
        return str
    }

    /**
     * @return value as TorrentElement
     */
    private fun parseValue(torrent: ByteArray, startIndex: Int): TorrentElement{
        val res: TorrentElement
        when(torrent[startIndex].toChar()) {
            in '0'..'9' -> {
                res = parseString(torrent, startIndex)
            }
            'i' -> {
                res = parseInt(torrent, startIndex)
            }
            'l' -> {
                res = parseList(torrent, startIndex)
            }
            'd' -> {
                res = parseDict(torrent, startIndex)
            }
            else -> {
                throw Exception("Invalid Torrent")
            }
        }
        return res
    }

    /**
     * parses torrent string.
     * example: 3:one returns:
     * TorrentElement("one", [startIndex], [startIndex]+3)
     */
    public fun parseString(torrent: ByteArray, startIndex: Int = 0): TorrentElement {
        assert(torrent[startIndex].toChar() in '0'..'9')
        var pairLen = 0
        var res: String = parseBytes(torrent, startIndex) {torrent[it].toChar() == ':'}
        //use val elemLen = res.first.toString(charset).toInt() if res.first is ByteArray
        val elemLen = res.toInt()
        val elemStartIndex = startIndex+res.length+1 //pass elemLen and ':' sign
        pairLen += res.length+1
        res = torrent.copyOfRange(elemStartIndex, elemStartIndex+elemLen).toString(Charsets.UTF_8)
        val elem = res
        pairLen += elemLen
        return TorrentElement(elem, startIndex, startIndex+pairLen)
    }

    /**
     * @return TorrentElement of TorrentList
     */
    fun parseList(torrent: ByteArray, startIndex: Int = 0): TorrentElement{
        assert(torrent[startIndex].toChar() == 'l')
        val lst = TorrentList()
        var index = startIndex+1 //pass 'l'
        var res: TorrentElement
        var len = 1 //for 'l'
        while(torrent[index].toChar() != 'e'){
            res = parseValue(torrent, index)
            lst.add(res)
            index += res.len()
            len += res.len()
        }
        assert(torrent[index].toChar() == 'e')
        len++ //pass 'e'
        return TorrentElement(lst, startIndex, startIndex+len)
    }

    /**
     * @return TorrentElement of TorrentDict
     */
    private fun parseDict(torrent: ByteArray, startIndex: Int): TorrentElement{
        assert(torrent[startIndex].toChar() == 'd')
        val dict = TorrentDict()
        var index = startIndex+1
        var key: String
        var res: TorrentElement
        var len = 1
        while(torrent[index].toChar() != 'e'){
            when(torrent[index].toChar()){
                in '0'..'9' -> {
                    res = parseString(torrent, index)
                    key = res.value() as String
                    index += res.len()
                    len += res.len()
                }
                else -> throw Exception("DictKeyShouldBeString")
            }
            res = parseValue(torrent, index)
            dict[key] = res
            index += res.len()
            len += res.len()
        }
        assert(torrent[index].toChar() == 'e')
        len++ //pass 'e'
        return TorrentElement(dict, startIndex, startIndex+len)
    }

    /**
     * parses int like i123e to:
     * TorrentElement(123, [startIndex], [startIndex]+3)
     */
    private fun parseInt(torrent: ByteArray, startIndex: Int): TorrentElement {
        assert(torrent[startIndex].toChar() == 'i')
        val res = parseBytes(torrent, startIndex+1) {torrent[it].toChar() == 'e'}
        return TorrentElement(res.toLong(), startIndex, startIndex+res.length+2) //+2 for i and e
        //use res.first.toString(charset).toInt() in order to get int value
    }

    /**
     * @return the main TorrentDict of the torrent
     */
    fun parse(torrent: ByteArray): TorrentDict{
        when(torrent[0].toChar()){
            'd' -> return parseDict(torrent, 0).value() as TorrentDict
        }
        throw Exception("Torrent should start with dict")
    }


//    fun encode(torrent: Any?): ByteArray {
//        var res: ByteArray
//        when(torrent){
//             is Map<*,*> -> {
//                res = "d".toByteArray()
//                for (it in torrent){
//                    res += encode(it.key)
//                    res += encode(it.value)
//                }
//                res += "e".toByteArray()
//            }
//            is List<*> -> {
//                res = "l".toByteArray()
//                for (it in torrent) {
//                    res += encode(it)
//                }
//                res += "e".toByteArray()
//            }
//            is Int ->{
//                res = "i".toByteArray()
//                res += torrent.toString().toByteArray()
//                res += "e".toByteArray()
//            }
//            is String ->{
//                res = torrent.length.toString().toByteArray()
//                res += ":".toByteArray()
//                res += torrent.toByteArray()
//            }
//            else ->{
//                throw Exception("encode error")
//            }
//        }
//        return res
//    }
}