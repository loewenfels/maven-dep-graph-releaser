package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi

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
        val adapter = moshi.adapter(Project::class.java)
        val entity = adapter.fromJson(json)
        return entity ?: throw IllegalStateException("Could not parse JSON or another problem occurred, entity was `null`")
    }
}
