package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

/**
 * A [JsonAdapter] which tracks already visited [Project]s and does not repeat [Project.commands] and [Project.dependents] in such a case.
 */
object ProjectAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): NonNullJsonAdapter<*>? {
        if (Project::class.java != type) {
            return null
        }
        val projectAdapter = moshi.nextAdapter<Project>(this, type, annotations)
        val projectIdAdapter = moshi.adapter(ProjectId::class.java)
        return ProjectAdapter(projectAdapter, projectIdAdapter)
    }

    class ProjectAdapter(
        private val projectAdapter: JsonAdapter<Project>,
        private val projectIdAdapter: JsonAdapter<ProjectId>
    ) : NonNullJsonAdapter<Project>() {
        private val visitedProjects = hashMapOf<ProjectId, Project>()

        override fun fromJson(reader: JsonReader): Project? {
            val project = projectAdapter.fromJson(reader) ?: return null
            return visitedProjects[project.id] ?: registerProject(project)
        }

        private fun registerProject(project: Project): Project {
            visitedProjects[project.id] = project
            return project
        }

        override fun toJsonNonNull(writer: JsonWriter, value: Project) {

            if (!visitedProjects.contains(value.id)) {
                visitedProjects[value.id] = value
                return projectAdapter.toJson(writer, value)
            } else {
                writer.writeObject {
                    writer.name(Project::id.name)
                    projectIdAdapter.toJson(writer, value.id)
                    writer.writeNameAndValue(value::currentVersion.name, value.currentVersion)
                    writer.writeNameAndValue(value::releaseVersion.name, value.releaseVersion)
                    writer.writeEmptyArray(Project::commands.name)
                    writer.writeEmptyArray(Project::dependents.name)
                }
            }
        }
    }
}
