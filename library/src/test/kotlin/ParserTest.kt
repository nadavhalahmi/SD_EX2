import org.junit.jupiter.api.Test

class ParserTest {
    private val parser = TorrentParser()
    private val simpleDictTorrent = "d3:cow3:moo4:spam4:eggse"
    private val simpleDictRes = mapOf<String, String>("cow" to "moo", "spam" to "eggs")
    private val dictWithIntsTorrent = "d7:-twelvei-12e3:onei1ee"
    private val dictWithIntsRes = mapOf<String, Int>("-twelve" to -12, "one" to 1)
    //private val dictWithIntsTorrent = "d3:onei1e7:-twelvei-12ee"
    //private val dictWithIntsRes = mapOf<String, Int>( "one" to 1, "-twelve" to -12)
    private val dictOfDictsTorrent = ("d3:one"+simpleDictTorrent+ "3:two"+dictWithIntsTorrent+"e")
    private val dictOfDictsRes = mapOf<String, Any>("one" to simpleDictRes, "two" to dictWithIntsRes)
    private val dictWithListTorrent = "d4:spaml1:a1:bee"
    private val dictWithListRes = mapOf<String, Any>("spam" to listOf("a", "b"))
    private val emptyDictTorrent = "de"
    private val emptyDictRes = mapOf<String, Any>()
    private val dictWithEmptyListTorrent = "d3:onelee"
    private val dictWithEmptyListRes = mapOf<String, Any>("one" to listOf<Any>())
    private val dictWithLongIntTorrent = "d1:ai123ee"
    private val dictWithLongIntRes = mapOf<String, Any>("a" to 123)

    @Test
    fun `empty dict`() {
        val dict = parser.parse(emptyDictTorrent.toByteArray())

        assert(dict.toDict() == emptyDictRes)
    }

    @Test
    fun `full check simple strings dict`() {
        val dict = parser.parse(simpleDictTorrent.toByteArray())

        assert(dict.toDict()==simpleDictRes)
    }

    @Test
    fun `dict with int`() {
        val dict = parser.parse(dictWithIntsTorrent.toByteArray())

        assert(dict.toDict() == dictWithIntsRes)
    }

    @Test
    fun `dict with long single int`() {
        val dict = parser.parse(dictWithLongIntTorrent.toByteArray())

        assert(dict.toDict() == dictWithLongIntRes)
    }

    @Test
    fun `dict of dicts`() {
        val dict = parser.parse(dictOfDictsTorrent.toByteArray())

        assert(dict.toDict() == dictOfDictsRes)
    }

    @Test
    fun `dict with list`() {
        val dict = parser.parse(dictWithListTorrent.toByteArray())

        assert(dict.toDict() == dictWithListRes)
    }

    @Test
    fun `dict with empty list`() {
        val dict = parser.parse(dictWithEmptyListTorrent.toByteArray())

        assert(dict.toDict() == dictWithEmptyListRes)
    }

//    @Test
//    fun `encode test 1`() {
//        val dict = parser.parse(simpleDictTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(simpleDictTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 2`() {
//        val dict = parser.parse(dictWithIntsTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(dictWithIntsTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 3`() {
//        val dict = parser.parse(dictOfDictsTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(dictOfDictsTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 4`() {
//        val dict = parser.parse(dictWithListTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(dictWithListTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 5`() {
//        val dict = parser.parse(emptyDictTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(emptyDictTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 6`() {
//        val dict = parser.parse(dictWithEmptyListTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(dictWithEmptyListTorrent.toByteArray()))
//    }
//
//    @Test
//    fun `encode test 7`() {
//        val dict = parser.parse(dictWithLongIntTorrent.toByteArray())
//        val encoded = parser.encode(dict)
//        assert(encoded.contentEquals(dictWithLongIntTorrent.toByteArray()))
//    }



}