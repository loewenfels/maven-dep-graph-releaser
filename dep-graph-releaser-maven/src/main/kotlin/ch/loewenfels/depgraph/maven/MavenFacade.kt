package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import java.io.File

class MavenFacade  {
    fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, directoryWithProjects: File): ReleasePlan {
        require(projectToRelease is MavenProjectId) {
            "Can only create a release plan for a maven project, $projectToRelease given."
        }
        val analyser = Analyser(directoryWithProjects)
        val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer())
        return jenkinsReleasePlanCreator.create(projectToRelease as MavenProjectId, analyser)
    }
}
