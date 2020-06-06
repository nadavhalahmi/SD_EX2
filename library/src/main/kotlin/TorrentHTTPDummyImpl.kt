import ITorrentHTTP
import java.net.URL

class TorrentHTTPDummyImpl: ITorrentHTTP {
    override fun get(tracker: String, params: HashMap<String, String>): ByteArray {
//        return """d8:intervali900e5:peers300:\�������J��d�	� JeU�՗���iR@���^��oA������XSQ���.�1�	�m�� ����T3S�-��zd��XG߷�U� �
//        ��@��
//        ^�-f���)�`�%:9d��\���I�����TÚ(��Ն�����,���T�<{�O���V�]�V�tm`PtUj�B��#'�HE.�LGq����dHE���X���{Rf�e�m��:�R�t}�/ +��^ˁ"�/)�s'[����f'qd����|l�²�t1��Po�W��ma+��
//        ��,$�Y���V��>f���bZ,���6:peers60:e""".toByteArray(Charsets.UTF_8)
        return """d8:intervali900ee""".toByteArray(Charsets.UTF_8)
    }
}