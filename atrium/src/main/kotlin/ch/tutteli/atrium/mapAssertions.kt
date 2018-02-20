package ch.tutteli.atrium

import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

//TODO replace with isEmpty from Atrium as soon as 0.7.0 is out
fun <K, V> Assert<Map<K, V>>.isEmpty() = size(0)

//TODO replace with size from Atrium as soon as 0.7.0 is out
fun <K, V> Assert<Map<K, V>>.size(size: Int) = property(subject::size).toBe(size)
