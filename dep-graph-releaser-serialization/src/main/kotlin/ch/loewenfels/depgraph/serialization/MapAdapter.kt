package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class MapAdapterFactory<K : Any>(private val keyType: Class<K>) : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type !is ParameterizedType) {
            return null
        }
        if (Map::class.java != type.rawType || keyType != type.actualTypeArguments[0]) {
            return null
        }
        return MapAdapter(moshi.adapter(keyType), moshi.adapter(type.actualTypeArguments[1]))
    }

    private class MapAdapter<K : Any>(private val keyAdapter: JsonAdapter<K>, private val valueAdapter: JsonAdapter<Any>) : NonNullJsonAdapter<Map<K, Any>>() {

        override fun toJsonNonNull(writer: JsonWriter, value: Map<K, Any>) {
            writer.writeArray {
                value.entries.forEach { (k, v) ->
                    writeObject {
                        writeNameAndValue(KEY, k, keyAdapter)
                        writeNameAndValue(VALUE, v, valueAdapter)
                    }
                }
            }
        }

        override fun fromJson(reader: JsonReader): Map<K, Any>? {
            val map = mutableMapOf<K, Any>()
            reader.readArray {
                while (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    reader.readObject {
                        val k = checkNextNameAndGetValue(KEY, keyAdapter)
                        val v = checkNextNameAndGetValue(VALUE, valueAdapter)
                        map[k] = v
                    }
                }
            }
            return map
        }

        private fun <T> JsonReader.checkNextNameAndGetValue(name: String, adapter: JsonAdapter<T>)
            = checkNextNameAndGetValue("map with object as key", name, adapter)
    }

    companion object {
        const val KEY = "k"
        const val VALUE = "v"
    }
}
