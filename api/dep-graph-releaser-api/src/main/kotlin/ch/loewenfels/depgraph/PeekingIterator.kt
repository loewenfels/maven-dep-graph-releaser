package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project

expect class PeekingIterator<out T>(itr: Iterator<T>) : Iterator<T> {
    fun peek(): T
}

fun <T> Iterator<T>.toPeekingIterator() = PeekingIterator(this)

fun PeekingIterator<Project>.hasNextOnTheSameLevel(level: Int) = hasNext() && level == peek().level
