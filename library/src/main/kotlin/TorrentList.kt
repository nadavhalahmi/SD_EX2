class TorrentList() {
    var lst: ArrayList<TorrentElement> = ArrayList<TorrentElement>()

    fun toList(): List<Any>{
        val res = ArrayList<Any>()
        for(elem in lst){
            when(val currVal = elem.value()){
                is TorrentDict -> res.add(currVal.toDict())
                is TorrentList -> res.add(currVal.toList())
                is Int -> res.add(currVal)
                is String -> res.add(currVal)
                else -> throw Exception("invalid list value")
            }
        }
        return res
    }

    fun add(elem: TorrentElement) {
        lst.add(elem)
    }

//    fun Equals(other: Any?): Boolean {
//        if(other !is Map<*, *>)
//            return false
//        for(key in dict.keys){
//            if(!other.containsKey(key))
//                return false
//            val value = dict[key]
//            val toCompareVal = other[key]
//            if (value !== null) {
//                if(value != toCompareVal)
//                    return false
//            }
//        }
//        return true
//    }
}