package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonWriter

abstract class NonNullJsonAdapter<T> : com.squareup.moshi.JsonAdapter<T>() {
    final override fun toJson(writer: JsonWriter, value: T?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        toJsonNonNull(writer, value)
    }

    abstract fun toJsonNonNull(writer: JsonWriter, value: T)
}

