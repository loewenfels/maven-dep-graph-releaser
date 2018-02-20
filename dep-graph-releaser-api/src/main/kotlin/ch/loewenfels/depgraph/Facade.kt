package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import java.io.File

/**
 * Responsible inter alia to create the [ReleasePlan]
 */
interface Facade {
    fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, directoryWithProjects: File): ReleasePlan
}
