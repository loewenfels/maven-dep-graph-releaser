package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.ReleaseState
import com.squareup.moshi.*
import java.lang.reflect.Type


object ReleasePlanAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (ReleasePlan::class.java != type) {
            return null
        }

        val stringAdapter = moshi.adapter<String>(String::class.java)
        val stateAdapter = moshi.adapter<ReleaseState>(ReleaseState::class.java)
        val projectIdAdapter = moshi.adapter(ProjectId::class.java)

        val mapType = Types.newParameterizedType(
            Map::class.java,
            ProjectId::class.java,
            Types.newParameterizedType(Set::class.java, ProjectId::class.java)
        )
        val mapProjectIdAndSetProjectId = moshi.adapter<Map<ProjectId, Set<ProjectId>>>(mapType)

        val collectionProjectType = Types.newParameterizedType(Collection::class.java, Project::class.java)
        val projectsAdapter = moshi.adapter<Collection<Project>>(collectionProjectType)

        val listStringType = Types.newParameterizedType(List::class.java, String::class.java)
        val listStringAdapter = moshi.adapter<List<String>>(listStringType)

        val listPairStringType = Types.newParameterizedType(
            List::class.java,
            Types.newParameterizedType(Pair::class.java, String::class.java, String::class.java)
        )
        val listPairStringAdapter = moshi.adapter<List<Pair<String, String>>>(listPairStringType)

        return ReleasePlanAdapter(
            stringAdapter,
            stateAdapter,
            projectIdAdapter,
            projectsAdapter,
            mapProjectIdAndSetProjectId,
            listStringAdapter,
            listPairStringAdapter
        )
    }

    private class ReleasePlanAdapter(
        private val stringAdapter: JsonAdapter<String>,
        private val stateAdapter: JsonAdapter<ReleaseState>,
        private val projectIdAdapter: JsonAdapter<ProjectId>,
        private val projectsAdapter: JsonAdapter<Collection<Project>>,
        private val mapProjectIdAndSetProjectId: JsonAdapter<Map<ProjectId, Set<ProjectId>>>,
        private val listStringAdapter: JsonAdapter<List<String>>,
        private val listPairStringAdapter: JsonAdapter<List<Pair<String, String>>>
    ) : NonNullJsonAdapter<ReleasePlan>() {

        override fun toJsonNonNull(writer: JsonWriter, value: ReleasePlan) {
            writer.writeObject {
                writeNameAndValue(PUBLISH_ID, value.publishId, stringAdapter)
                writeNameAndValue(STATE, value.state, stateAdapter)
                writeNameAndValue(ID, value.rootProjectId, projectIdAdapter)
                writeNameAndValue(PROJECTS, value.getProjects(), projectsAdapter)
                writeNameAndValue(SUBMODULES, value.getAllSubmodules(), mapProjectIdAndSetProjectId)
                writeNameAndValue(DEPENDENTS, value.getAllDependents(), mapProjectIdAndSetProjectId)
                writeNameAndValue(WARNINGS, value.warnings, listStringAdapter)
                writeNameAndValue(INFOS, value.infos, listStringAdapter)
                val stringConfig = value.config.entries.map({ it.key.asString() to it.value })
                writeNameAndValue(CONFIG, stringConfig, listPairStringAdapter)

            }
        }

        override fun fromJson(reader: JsonReader): ReleasePlan? {
            return reader.readObject {
                val publishId = checkNextNameAndGetValue(PUBLISH_ID, stringAdapter)
                val state: ReleaseState = checkNextNameAndGetValue(STATE, stateAdapter)
                val projectId = checkNextNameAndGetValue(ID, projectIdAdapter)
                val projects = checkNextNameAndGetValue(PROJECTS, projectsAdapter)
                val submodules = checkNextNameAndGetValue(SUBMODULES, mapProjectIdAndSetProjectId)
                val dependents = checkNextNameAndGetValue(DEPENDENTS, mapProjectIdAndSetProjectId)
                val warnings = checkNextNameAndGetValue(WARNINGS, listStringAdapter)
                val infos = checkNextNameAndGetValue(INFOS, listStringAdapter)
                val stringConfig = checkNextNameAndGetValue(CONFIG, listPairStringAdapter)
                val config = stringConfig.associate { ConfigKey.fromString(it.first) to it.second }

                ReleasePlan(
                    publishId,
                    state,
                    projectId,
                    projects.associateBy { it.id },
                    submodules,
                    dependents,
                    warnings,
                    infos,
                    config
                )
            }
        }

        private fun <T> JsonReader.checkNextNameAndGetValue(expectedName: String, adapter: JsonAdapter<T>) =
            checkNextNameAndGetValue(ReleasePlan::class.java.simpleName, expectedName, adapter)
    }

    const val PUBLISH_ID = "publishId"
    const val STATE = "state"
    const val ID = "id"
    const val PROJECTS = "projects"
    const val SUBMODULES = "submodules"
    const val DEPENDENTS = "dependents"
    const val WARNINGS = "warnings"
    const val INFOS = "infos"
    const val CONFIG = "config"
}
