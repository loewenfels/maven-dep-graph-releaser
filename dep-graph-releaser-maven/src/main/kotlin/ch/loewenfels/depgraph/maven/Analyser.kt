package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectIdWithCurrentVersion
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import fr.lteconsulting.pomexplorer.*
import fr.lteconsulting.pomexplorer.graph.relation.Relation
import fr.lteconsulting.pomexplorer.model.Gav
import java.io.File

class Analyser(directoryWithProjects: File) {
    private val session: Session
    private val dependents: Map<String, Set<ProjectIdWithCurrentVersion<MavenProjectId>>>
    private val projectIds: Map<MavenProjectId, String>

    init {
        require(directoryWithProjects.exists()) {
            "Cannot analyse because the given directory does not exists: ${directoryWithProjects.absolutePath}"
        }
        session = analyseDirectory(directoryWithProjects)
        checkNoDuplicates(directoryWithProjects)

        dependents = analyseDependents()
        projectIds = getInternalAnalysedProjects()
            .associateBy({ it.toMavenProjectId() }, { it.version })
    }

    private fun analyseDirectory(directoryWithProjects: File): Session {
        val session = Session()
        val nullLogger = Log { }
        PomAnalysis.runFullRecursiveAnalysis(directoryWithProjects.absolutePath, session, DefaultPomFileLoader(session, true), null, false, nullLogger)
        return session
    }

    private fun checkNoDuplicates(directoryWithProjects: File) {

        val map = hashMapOf<String, Project>()
        session.projects().keySet()
            .asSequence()
            .map { it.toProject() }
            .filter { it.isNotExternal }
            .forEach { second ->
                val key = second.gav.toMapKey()
                val first = map[key]
                check(first == null) {
                    "found twice the same project in the given directoryWithProjects.\n" +
                        "directory: ${directoryWithProjects.canonicalPath}\n" +
                        "first: ${projectToString(first!!)}\n" +
                        "second: ${projectToString(second)}"
                }
                map[key] = second
            }
    }

    private fun projectToString(project: Project): String = "${project.gav} (${project.pomFile.canonicalPath})"

    private fun analyseDependents(): Map<String, Set<ProjectIdWithCurrentVersion<MavenProjectId>>> {
        val analysedProjects = getInternalAnalysedProjects()
            .map { it.toMapKey() }
            .toSet()
        val dependents = hashMapOf<String, MutableSet<ProjectIdWithCurrentVersion<MavenProjectId>>>()
        session.projects().keySet().forEach { gav ->
            session.graph().read().relations(gav)
                .asSequence()
                .filter { analysedProjects.contains(it.targetToMapKey()) }
                .forEach { relation ->
                    val set = dependents.getOrPut(relation.targetToMapKey(), { mutableSetOf() })
                    set.add(gav.toProjectIdWithCurrentVersion())
                }
        }
        return dependents
    }

    private fun getInternalAnalysedProjects() = session.projects()
        .keySet()
        .asSequence()
        .filter { it.isNotExternal }

    private val Gav.isNotExternal get() = toProject().isNotExternal
    private fun Gav.toProject() = session.projects().forGav(this)
    private val Project.isNotExternal get() = !isExternal

    private fun Gav.toProjectIdWithCurrentVersion() = ProjectIdWithCurrentVersion(toMavenProjectId(), version)
    private fun Relation.targetToMapKey() = target.toMapKey()
    private fun Gav.toMapKey() = toMavenProjectId().identifier
    private fun Gav.toMavenProjectId() = MavenProjectId(groupId, artifactId)

    /**
     * Returns the current version for the given [projectId] if it was involved in the analysis; `null` otherwise.
     * @return The current version or `null` if [projectId] was not part of the analysis.
     */
    fun getCurrentVersion(projectId: MavenProjectId): String? = projectIds[projectId]


    /**
     * Returns the [MavenProjectId]s of the analysed projects together with the current version.
     */
    fun getAnalysedProjectsAsString(): CharSequence {
        val sb = StringBuilder()
        projectIds.entries.joinTo(sb, transform = { (k, v) -> "$k:$v" })
        return sb
    }

    /**
     * Returns a set of dependent [MavenProjectId]s for the given [projectId] where only the
     * [MavenProjectId.groupId] and [MavenProjectId.artifactId] of the given [projectId] are considered.
     *
     * Meaning, if a project ch.loewenfels:A has a dependency on Project ch.loewenfels:B:1.0 and
     * the analysed project B is in version 2.0-SNAPSHOT then project A is still dependent of project B.
     */
    fun getDependentsOf(projectId: MavenProjectId): Set<ProjectIdWithCurrentVersion<MavenProjectId>> {
        return dependents[projectId.identifier] ?: emptySet()
    }

    /**
     * Returns the number of analysed projects.
     */
    fun getNumberOfProjects(): Int = projectIds.size
}

