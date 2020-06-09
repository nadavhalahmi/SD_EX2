class Range(private var startIndex: Int, private var endIndex: Int) {
    fun len(): Int{
        return endIndex - startIndex
    }

    fun startIndex(): Int{
        return startIndex
    }
    fun endIndex(): Int{
        return endIndex
    }
}

//TODO: replace Any with union of dict, list, int, string
open class TorrentElement(protected val value: Any, startIndex: Int, endIndex: Int) {
    private val range: Range = Range(startIndex, endIndex)
    fun range(): Range{
        return range
    }
    fun len():Int{
        return range.len()
    }
    fun value(): Any{
        return value
    }

    fun startIndex(): Int{
        return range.startIndex()
    }

    fun endIndex(): Int{
        return range.endIndex()
    }

    operator fun get(index: Int): TorrentElement{
        return (value as TorrentList)[index]
    }

    operator fun get(key: String): TorrentElement?{
        return (value as TorrentDict)[key]
    }
}