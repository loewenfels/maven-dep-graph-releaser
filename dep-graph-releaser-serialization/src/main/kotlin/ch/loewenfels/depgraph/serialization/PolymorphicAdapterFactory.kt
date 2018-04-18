package ch.loewenfels.depgraph.serialization

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * A [JsonAdapter.Factory] which relies on the order of the fields to provide fast processing for an [abstractType]
 * (interface or abstract class).
 *
 * This factory does not support processing of concrete classes with subtypes (introduce an interface,
 * that is better anyway).
 */
class PolymorphicAdapterFactory<T : Any>(private val abstractType: Class<T>) : JsonAdapter.Factory {
    init {
        require(abstractType.isInterface || Modifier.isAbstract(abstractType.modifiers)) {
            "Do not use ${PolymorphicAdapterFactory::class.simpleName} for non abstract types (neither an interface nor an abstract class).\n" +
                "Given: ${abstractType.name}"
        }
    }

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        // we only deal with the abstract type here
        if (abstractType != type) {
            return null
        }
        return PolymorphicAdapter(abstractType, moshi)
    }

    private class PolymorphicAdapter<T : Any>(private val abstractType: Class<T>, private val moshi: Moshi) : NonNullJsonAdapter<T>() {

        override fun toJsonNonNull(writer: JsonWriter, value: T) {
            val runtimeClass = value::class.java
            val adapter: JsonAdapter<T> = getAdapter(runtimeClass)
            writer.writeObject {
                //If you make changes here, then you have to make changes in fromJson
                writeNameAndValue(TYPE, runtimeClass.name)
                writeNameAndValue(PAYLOAD, value, adapter)
            }
        }

        private fun getAdapter(runtimeClass: Class<out T>): JsonAdapter<T> {
            //TODO change if https://youtrack.jetbrains.com/issue/KT-20372 is solved
            require(!runtimeClass.name.matches(ANONYMOUS_CLASSNAME_REGEX)) {
                "Cannot serialize an anonymous class, given: ${runtimeClass.name}"
            }
            @Suppress("UNCHECKED_CAST" /* entity is of type T, should be fine, required for toJson */)
            return moshi.adapter(runtimeClass) as JsonAdapter<T>
        }

        override fun fromJson(reader: JsonReader): T? = reader.readObject {
            //If you make changes here, then you have to make changes in toJson
            checkName(reader, TYPE)
            val entityName = reader.nextString()
            val runtimeClass = loadClass(entityName)
            checkName(reader, PAYLOAD)
            moshi.adapter(runtimeClass).fromJson(reader)
        }

        private fun checkName(reader: JsonReader, expectedName: String) {
            reader.checkNextName("polymorphic type", expectedName)
        }

        private fun loadClass(commandName: String?): Class<T> {
            val runtimeClass = Class.forName(commandName)
            require(abstractType.isAssignableFrom(runtimeClass)) {
                """"Found a wrong type, cannot deserialize JSON
                    |Expected: ${abstractType.name}
                    |Given: ${runtimeClass.name}
                """.trimMargin()
            }
            @Suppress("UNCHECKED_CAST" /* we checked it above */)
            return runtimeClass as Class<T>
        }
    }

    companion object {
        const val TYPE = "t"
        const val PAYLOAD = "p"
        val ANONYMOUS_CLASSNAME_REGEX = Regex(".*\\$[0-9]+$")
    }
}
