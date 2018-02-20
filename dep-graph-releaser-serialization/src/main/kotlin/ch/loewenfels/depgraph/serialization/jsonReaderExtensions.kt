package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader

inline fun <T> JsonReader.readObject(act: JsonReader.() -> T): T {
    beginObject()
    val t = act(this)
    endObject()
    return t
}

inline fun JsonReader.readArray(act: JsonReader.() -> Unit) {
    beginArray()
    act(this)
    endArray()
}

fun <T> JsonReader.checkNextNameAndGetValue(type: String, expectedName: String, adapter: JsonAdapter<T>): T {
    checkNextName(type, expectedName)
    return adapter.fromJsonNullSafe(this, expectedName)
}

fun JsonReader.checkNextName(type: String, expectedName: String) {
    val nextName = nextName()
    require(nextName == expectedName) {
        """Cannot map Json to $type, field order matters
                    |Expected: $expectedName
                    |Given: $nextName
                """.trimMargin()
    }
}

