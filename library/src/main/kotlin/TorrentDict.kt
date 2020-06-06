class TorrentDict() : HashMap<String, TorrentElement>(){
    //private var dict: HashMap<String, TorrentElement> = HashMap<String, TorrentElement>()

//    operator fun set(key:String, elem: TorrentElement){
//        dict[key] = elem
//    }
//
//    operator fun get(key:String): TorrentElement?{
//        return dict[key]
//    }

    fun toDict(): HashMap<String, Any>{
        val res = HashMap<String, Any>()
        for(key in keys){
            when(val currVal = this.getValue(key).value()){
                is TorrentDict -> res[key] = currVal.toDict()
                is TorrentList -> res[key] = currVal.toList()
                is Int -> res[key] = currVal
                is String -> res[key] = currVal
                else -> throw Exception("invalid dict value")
            }
        }
        return res
    }

    fun getRange(key: String): Range {
        if(!this.containsKey(key))
            throw Exception("dict does not contain:$key")
        return this.getValue(key).range()
    }

//    fun Equals(other: Any?): Boolean {
//        if(other !is Map<*, *>)
//            return false
//        for(key in dict.keys){
//            if(!other.containsKey(key))
//                return false
//            val value = dict[key]
//            val toCompareVal = other[key]
//            if (value != null) {
//                if(value != toCompareVal)
//                    return false
//            }
//        }
//        return true
//    }
}