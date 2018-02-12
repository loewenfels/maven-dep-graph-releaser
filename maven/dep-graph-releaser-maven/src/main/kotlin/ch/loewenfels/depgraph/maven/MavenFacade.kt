package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.Facade
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import java.io.File

class MavenFacade : Facade {
    override fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, directoryWithProjects: File): Project {
        require(projectToRelease is MavenProjectId) {
            "Can only create a release plan for a maven project, $projectToRelease given."
        }
        val analyser = Analyser(directoryWithProjects)
        return JenkinsReleasePlanCreator().create(projectToRelease as MavenProjectId, analyser)
    }
}
