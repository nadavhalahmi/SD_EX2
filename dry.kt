class Observable<T> {
	private val listeners = HashSet<(T) -> Unit>()
	fun listen(listener: (T) -> Unit): Unit {
		listeners.add(listener)
	}
	fun unlisten(listener: (T) -> Unit): Unit {
		listeners.remove(listener)
	}
	protected fun onChange(t: T): Unit {
		listeners.forEach { it(t) }
	}
	companion object {
		fun <T> of(t: T) = object : Observable<T> {
			override fun listen(f: T -> Unit) = f(t)
		}
	}
	fun <S> flatMap(f: (T) -> Observable<S>): Observable<S>{
		val res = Observable<S>()
		res.listen { f(it).onChange() }
		return res
	}
}
