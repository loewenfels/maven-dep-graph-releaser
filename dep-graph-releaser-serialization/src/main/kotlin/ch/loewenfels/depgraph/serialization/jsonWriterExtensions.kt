package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonWriter

fun JsonWriter.writeNameAndValue(name: String, value: String) {
    name(name)
    value(value)
}

inline fun JsonWriter.writeObject(act: () -> Unit) {
    beginObject()
    act()
    endObject()
}

fun JsonWriter.writeEmptyArray(name: String) {
    name(name)
    writeArray {}
}

inline fun JsonWriter.writeArray(act: () -> Unit) {
    beginArray()
    act()
    endArray()
}
