package ch.tutteli.atrium

import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

//TODO replace with isEmpty from Atrium as soon as 0.7.0 is out
fun <K, V> Assert<Map<K, V>>.isEmpty() = hasSize(0)

//TODO replace with hasSize from Atrium as soon as 0.7.0 is out
fun <K, V> Assert<Map<K, V>>.hasSize(size: Int): Assert<Map<K, V>> {
    property(subject::size).toBe(size)
    return this
}

//TODO replace with keyValue from Atrium as soon as 0.8.0 is out
fun <K : Any, V : Any> Assert<Map.Entry<K, V>>.keyValue(k: K, v: V): Assert<Map.Entry<K, V>> {
    property(subject::key).toBe(k)
    property(subject::value).toBe(v)
    return this
}
