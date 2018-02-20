package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader

fun <T> JsonAdapter<T>.fromJsonNullSafe(reader: JsonReader, name: String): T
    = fromJson(reader) ?: throw IllegalArgumentException("$name needs to be non null but was null")
