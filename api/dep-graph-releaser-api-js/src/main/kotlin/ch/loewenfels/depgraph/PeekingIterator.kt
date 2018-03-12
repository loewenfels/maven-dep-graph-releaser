package ch.loewenfels.depgraph

actual class PeekingIterator<out T>
actual constructor(private val itr: Iterator<T>) : Iterator<T> {
    private var peek: T? = null


    override fun hasNext() = peek != null || itr.hasNext()

    override fun next(): T {
        val peeked = peek
        return if (peeked != null) {
            peek = null
            peeked
        } else {
            itr.next()
        }
    }

    actual fun peek(): T {
        if (peek == null) {
            peek = itr.next()
        }
        return peek!!
    }
}
