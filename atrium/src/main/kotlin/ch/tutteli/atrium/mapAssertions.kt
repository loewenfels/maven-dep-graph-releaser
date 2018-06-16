package ch.tutteli.atrium

import ch.tutteli.atrium.api.cc.en_GB.property
import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.creating.Assert

//TODO replace with keyValue from Atrium as soon as 0.8.0 is out
fun <K : Any, V : Any> Assert<Map.Entry<K, V>>.keyValue(k: K, v: V): Assert<Map.Entry<K, V>> {
    property(subject::key).toBe(k)
    property(subject::value).toBe(v)
    return this
}
