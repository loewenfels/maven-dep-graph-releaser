package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import java.io.File

/**
 * Responsible inter alia to create the root [Project]
 */
interface Facade {
    fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, directoryWithProjects: File): Project
}
