package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import com.squareup.moshi.*
import java.lang.reflect.Type


object ReleasePlanAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (ReleasePlan::class.java != type) {
            return null
        }
        return ReleasePlanAdapter(moshi)
    }

    private class ReleasePlanAdapter(private val moshi: Moshi) : NonNullJsonAdapter<ReleasePlan>() {
        override fun toJsonNonNull(writer: JsonWriter, value: ReleasePlan) {
            writer.writeObject {
                writeNameAndValue(ID, value.rootProjectId, getProjectIdAdapter())
                writeNameAndValue(PROJECTS, value.projects.values, getProjectsAdapter())
                writeNameAndValue(DEPENDENTS, value.dependents, getDependentsAdapter())
            }
        }

        override fun fromJson(reader: JsonReader): ReleasePlan? {
            return reader.readObject {
                val projectId = checkNextNameAndGetValue(ID, getProjectIdAdapter())
                val projects = checkNextNameAndGetValue(PROJECTS, getProjectsAdapter())
                val dependents = checkNextNameAndGetValue(DEPENDENTS, getDependentsAdapter())
                ReleasePlan(projectId, projects.associateBy { it.id }, dependents)
            }
        }

        private fun getProjectIdAdapter() = moshi.adapter(ProjectId::class.java)

        private fun getProjectsAdapter(): JsonAdapter<Collection<Project>> {
            val type = Types.newParameterizedType(Collection::class.java, Project::class.java)
            return moshi.adapter<Collection<Project>>(type)
        }

        private fun getDependentsAdapter(): JsonAdapter<Map<ProjectId, Set<ProjectId>>> {
            val type = Types.newParameterizedType(Map::class.java, ProjectId::class.java, Types.newParameterizedType(Set::class.java, ProjectId::class.java))
            return moshi.adapter<Map<ProjectId, Set<ProjectId>>>(type)
        }

        private fun <T> JsonReader.checkNextNameAndGetValue(expectedName: String, adapter: JsonAdapter<T>)
            = checkNextNameAndGetValue(ReleasePlan::class.java.simpleName, expectedName, adapter)
    }

    const val ID = "id"
    const val PROJECTS = "projects"
    const val DEPENDENTS = "dependents"

}
