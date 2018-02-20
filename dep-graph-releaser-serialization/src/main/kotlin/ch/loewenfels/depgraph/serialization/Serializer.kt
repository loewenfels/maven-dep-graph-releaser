package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import okio.Buffer

/**
 * Responsible to serialize [Project]s to JSON and deserialize them back.
 */
class Serializer {

    fun serialize(releasePlan: ReleasePlan): String {
        val moshi = setUpMoshi()
        val adapter = moshi.adapter(ReleasePlan::class.java)
        return adapter.toJson(releasePlan)
    }

    fun deserialize(json: String): ReleasePlan {
        val moshi = setUpMoshi()
        val entity = consumeJson(json, moshi)
        return entity
            ?: throw IllegalStateException("Could not parse JSON or another problem occurred, entity was `null`")
    }

    private fun consumeJson(json: String, moshi: Moshi): ReleasePlan? {
        val adapter = moshi.adapter(ReleasePlan::class.java)
        //TODO can be removed if a new version is used which includes: https://github.com/square/moshi/pull/441
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val entity = adapter.fromJson(reader)
        val token = reader.peek()
        if (token != JsonReader.Token.END_DOCUMENT) {
            throw JsonEncodingException("JSON document was not fully consumed, might be malformed. Next token was $token")
        }
        return entity
    }

    private fun setUpMoshi(): Moshi {
        return Moshi.Builder()
            .add(ReleasePlanAdapterFactory)
            //allow that ProjectId can be used as key in Maps
            .add(MapAdapterFactory(ProjectId::class.java))
            .add(PolymorphicAdapterFactory(ProjectId::class.java))
            .add(PolymorphicAdapterFactory(Command::class.java))
            .add(CommandStateAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
    }

}
