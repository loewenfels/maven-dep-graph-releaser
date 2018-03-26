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

        val mapType = Types.newParameterizedType(Map::class.java,
            ProjectId::class.java,
            Types.newParameterizedType(Set::class.java, ProjectId::class.java)
        )
        val mapProjectIdAndSetProjectId =  moshi.adapter<Map<ProjectId, Set<ProjectId>>>(mapType)

        val collectionProjectType = Types.newParameterizedType(Collection::class.java, Project::class.java)
        val projectsAdapter =  moshi.adapter<Collection<Project>>(collectionProjectType)

        val listStringType = Types.newParameterizedType(List::class.java, String::class.java)
        val listStringAdapter = moshi.adapter<List<String>>(listStringType)

        return ReleasePlanAdapter(projectIdAdapter, projectsAdapter, mapProjectIdAndSetProjectId, listStringAdapter)
    }

    private class ReleasePlanAdapter(
        private val projectIdAdapter: JsonAdapter<ProjectId>,
        private val projectsAdapter: JsonAdapter<Collection<Project>>,
        private val mapProjectIdAndSetProjectId: JsonAdapter<Map<ProjectId, Set<ProjectId>>>,
        private val listStringAdapter: JsonAdapter<List<String>>
    ) : NonNullJsonAdapter<ReleasePlan>() {
        override fun toJsonNonNull(writer: JsonWriter, value: ReleasePlan) {
            writer.writeObject {
                writeNameAndValue(ID, value.rootProjectId, projectIdAdapter)
                writeNameAndValue(PROJECTS, value.getProjects(), projectsAdapter)
                writeNameAndValue(SUBMODULES, value.getAllSubmodules(), mapProjectIdAndSetProjectId)
                writeNameAndValue(DEPENDENTS, value.getAllDependents(), mapProjectIdAndSetProjectId)
                writeNameAndValue(WARNINGS, value.warnings, listStringAdapter)
                writeNameAndValue(INFOS, value.infos, listStringAdapter)
            }
        }

        override fun fromJson(reader: JsonReader): ReleasePlan? {
            return reader.readObject {
                val projectId = checkNextNameAndGetValue(ID, projectIdAdapter)
                val projects = checkNextNameAndGetValue(PROJECTS, projectsAdapter)
                val submodules = checkNextNameAndGetValue(SUBMODULES, mapProjectIdAndSetProjectId)
                val dependents = checkNextNameAndGetValue(DEPENDENTS, mapProjectIdAndSetProjectId)
                val warnings = checkNextNameAndGetValue(WARNINGS, listStringAdapter)
                val infos = checkNextNameAndGetValue(INFOS, listStringAdapter)
                ReleasePlan(projectId, projects.associateBy { it.id }, submodules, dependents, warnings, infos)
            }
        }

        private fun <T> JsonReader.checkNextNameAndGetValue(expectedName: String, adapter: JsonAdapter<T>)
            = checkNextNameAndGetValue(ReleasePlan::class.java.simpleName, expectedName, adapter)
    }

    const val ID = "id"
    const val PROJECTS = "projects"
    const val SUBMODULES = "submodules"
    const val DEPENDENTS = "dependents"
    const val WARNINGS = "warnings"
    const val INFOS = "infos"
}
