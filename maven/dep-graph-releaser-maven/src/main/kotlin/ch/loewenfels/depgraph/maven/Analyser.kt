package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import fr.lteconsulting.pomexplorer.DefaultPomFileLoader
import fr.lteconsulting.pomexplorer.Log
import fr.lteconsulting.pomexplorer.PomAnalysis
import fr.lteconsulting.pomexplorer.Session
import fr.lteconsulting.pomexplorer.model.Gav
import java.io.File

class Analyser(directoryWithProjects: File) {
    private val session: Session
    private val dependents: Map<String, Set<MavenProjectId>>
    private val projectIds: Set<MavenProjectId>

    init {
        require(directoryWithProjects.exists()) {
            "Cannot analyse because the given directory does not exists: ${directoryWithProjects.absolutePath}"
        }
        session = analyseDirectory(directoryWithProjects)
        dependents = analyseDependents()
        projectIds = session.projects().keySet()
            .asSequence()
            .map { it.toMavenProjectId() }
            .toSet()

    }

    private fun analyseDirectory(directoryWithProjects: File): Session {
        val session = Session()
        val nullLogger = Log { }
        PomAnalysis.runFullRecursiveAnalysis(directoryWithProjects.absolutePath, session, DefaultPomFileLoader(session, true), null, false, nullLogger)
        return session
    }

    private fun analyseDependents(): Map<String, Set<MavenProjectId>> {
        val analysedGroupIdsAndArtifactIds = session.projects().keySet()
            .asSequence()
            .map { "${it.groupId}:${it.artifactId}" }
            .toSet()
        val dependents = hashMapOf<String, MutableSet<MavenProjectId>>()
        session.projects().keySet().forEach { gav ->
            session.graph().read().relations(gav)
                .asSequence()
                .filter { analysedGroupIdsAndArtifactIds.contains("${it.target.groupId}:${it.target.artifactId}") }
                .forEach { relation ->
                    val set = dependents.getOrPut(relation.target.toMapKey(), { mutableSetOf() })
                    set.add(gav.toMavenProjectId())
                }
        }
        return dependents
    }

    private fun Gav.toMavenProjectId() = MavenProjectId(groupId, artifactId, version)
    private fun Gav.toMapKey() = "$groupId:$artifactId"
    private fun MavenProjectId.toMapKey() = "$groupId:$artifactId"

    fun hasAnalysedProject(projectId: MavenProjectId): Boolean {
        return projectIds.contains(projectId)
    }

    fun getAnalysedProjectsAsString(): CharSequence {
        val sb = StringBuilder()
        projectIds.joinTo(sb)
        return sb
    }

    /**
     * Returns a set of dependent [MavenProjectId]s for the given [projectId] where only the
     * [MavenProjectId.groupId] and [MavenProjectId.artifactId] of the given [projectId] are considered.
     *
     * Meaning, if a project ch.loewenfels:A has a dependency on Project ch.loewenfels:B:1.0 and
     * the analysed project B is in version 2.0-SNAPSHOT then project A is still dependent of project B.
     */
    fun getDependentsOf(projectId: MavenProjectId): Set<MavenProjectId> {
        return dependents[projectId.toMapKey()] ?: emptySet()
    }
}

