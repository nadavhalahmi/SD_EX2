import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*

class Coder {
    @ExperimentalUnsignedTypes
    fun hexStringToByteArray(input: String) = input.chunked(2).map { it.toUpperCase().toUByte(16).toByte() }.toByteArray()

    fun SHAsum(convertme: ByteArray) : String{
        val md = MessageDigest.getInstance("SHA-1");
        return byteArray2Hex(md.digest(convertme));
    }

    fun byteArray2Hex(hash: ByteArray) : String{
        val formatter = Formatter();
        for (b in hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    fun binary_encode(str: String, simple: Boolean = false): String{
        val format_template = "%02x"
        var res = ""
        for(i in str.indices step 2){
            var c = (str[i]+""+str[i+1]).toInt(16).toChar()
            if(simple || c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '.' || c == '-' || c == '_' || c == '~'){
                res += c
            }
            else
                res += "%"+format_template.format(c.toInt()).toString()
        }
        return res
    }
    //"65" -> "A"
    fun string_to_hex(str: String): String{
        var res = ByteArray(str.length/2)
        for(i in str.indices step 2){
            res[i/2] = (str[i]+""+str[i+1]).toInt(16).toByte()
        }
        return res.toString(Charsets.UTF_8)
    }

    @ExperimentalUnsignedTypes
    fun get_ip_port(bytes: ByteArray): Pair<String, Int> {
        assert(bytes.size == 6)
        var ip = bytes[0].toUByte().toString()
        for(i in 1 until 4){
            ip += "." + bytes[i].toUByte().toString()
        }
        val port = (bytes[4].toUByte().toInt() shl 8) + bytes[5].toUByte().toInt()
        return Pair(ip, port)
    }


}