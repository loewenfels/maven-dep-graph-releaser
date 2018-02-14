package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import com.squareup.moshi.*
import okio.Buffer

/**
 * Responsible to serialize [Project]s to JSON and deserialize them back.
 */
class Serializer {
    private val moshi: Moshi

    init {
        val builder = Moshi.Builder()
            .add(CommandStateAdapter)
            .add(PolymorphicAdapterFactory(ProjectId::class.java))
            .add(PolymorphicAdapterFactory(Command::class.java))
        builder.add(KotlinJsonAdapterFactory())
        moshi = builder.build()
    }

    fun serialize(project: Project): String {
        val adapter = moshi.adapter(Project::class.java)
        return adapter.toJson(project)
    }

    fun deserialize(json: String): Project {
        val entity = consumeJson(json)
        return entity
            ?: throw IllegalStateException("Could not parse JSON or another problem occurred, entity was `null`")
    }

    private fun consumeJson(json: String): Project? {
        val adapter = moshi.adapter(Project::class.java)
        //TODO can be removed if a new version is used which includes: https://github.com/square/moshi/pull/441
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val entity = adapter.fromJson(reader)
        val token = reader.peek()
        if (token != JsonReader.Token.END_DOCUMENT) {
            throw JsonEncodingException("JSON document was not fully consumed, might be malformed. Next token was $token")
        }
        return entity
    }
}
