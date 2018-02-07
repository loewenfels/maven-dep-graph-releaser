package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import fr.lteconsulting.pomexplorer.Session

class JenkinsReleasePlanCreator {

    fun create(projectToRelease: MavenProjectId, session: Session): ReleasePlan {
        require(session.projects().contains(projectToRelease.toGav())) {
            """Can only release a project which is part of the analysis.
                |Given: $projectToRelease
                |Analysed projects: ${session.projects().keySet().joinToString()}
            """.trimMargin()
        }

        var id = 1
        val rootProject = Project(projectToRelease, emptyList(), listOf(
            JenkinsMavenReleasePlugin(id, CommandState.Ready, emptySet())
        ))
        val projects = mutableListOf<Project>()
        projects.add(rootProject)
        return ReleasePlan(projects.toList())
    }

}
