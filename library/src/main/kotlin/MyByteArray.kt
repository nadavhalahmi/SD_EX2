class MyByteArray(val arr: ByteArray){
    override fun equals(other: Any?): Boolean =
        this === other || (other is MyByteArray && this.arr contentEquals other.arr)
    override fun hashCode(): Int = arr.contentHashCode()
    override fun toString(): String = arr.contentToString()
}