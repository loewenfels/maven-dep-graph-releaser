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
        val projectIdAdapter = moshi.adapter(ProjectId::class.java)
        val collectionType = Types.newParameterizedType(Collection::class.java, Project::class.java)
        val projectsAdapter =  moshi.adapter<Collection<Project>>(collectionType)
        val mapType = Types.newParameterizedType(Map::class.java, ProjectId::class.java, Types.newParameterizedType(Set::class.java, ProjectId::class.java))
        val dependentsAdapter =  moshi.adapter<Map<ProjectId, Set<ProjectId>>>(mapType)
        val listAdapter = Types.newParameterizedType(List::class.java, String::class.java)
        val warningsAdapter = moshi.adapter<List<String>>(listAdapter)
        return ReleasePlanAdapter(projectIdAdapter, projectsAdapter, dependentsAdapter, warningsAdapter)
    }

    private class ReleasePlanAdapter(
        private val projectIdAdapter: JsonAdapter<ProjectId>,
        private val projectsAdapter: JsonAdapter<Collection<Project>>,
        private val dependentsAdapter: JsonAdapter<Map<ProjectId, Set<ProjectId>>>,
        private val warningsAdapter: JsonAdapter<List<String>>
    ) : NonNullJsonAdapter<ReleasePlan>() {
        override fun toJsonNonNull(writer: JsonWriter, value: ReleasePlan) {
            writer.writeObject {
                writeNameAndValue(ID, value.rootProjectId, projectIdAdapter)
                writeNameAndValue(PROJECTS, value.getProjects(), projectsAdapter)
                writeNameAndValue(DEPENDENTS, value.getAllDependents(), dependentsAdapter)
                writeNameAndValue(WARNINGS, value.warnings, warningsAdapter)
            }
        }

        override fun fromJson(reader: JsonReader): ReleasePlan? {
            return reader.readObject {
                val projectId = checkNextNameAndGetValue(ID, projectIdAdapter)
                val projects = checkNextNameAndGetValue(PROJECTS, projectsAdapter)
                val dependents = checkNextNameAndGetValue(DEPENDENTS, dependentsAdapter)
                val warnings = checkNextNameAndGetValue(WARNINGS, warningsAdapter)
                ReleasePlan(projectId, projects.associateBy { it.id }, dependents, warnings)
            }
        }

        private fun <T> JsonReader.checkNextNameAndGetValue(expectedName: String, adapter: JsonAdapter<T>)
            = checkNextNameAndGetValue(ReleasePlan::class.java.simpleName, expectedName, adapter)
    }

    const val ID = "id"
    const val PROJECTS = "projects"
    const val DEPENDENTS = "dependents"
    const val WARNINGS = "warnings"
}
