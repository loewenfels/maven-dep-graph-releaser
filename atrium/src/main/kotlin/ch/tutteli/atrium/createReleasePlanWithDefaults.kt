package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.*

/**
 * Creates a [ReleasePlan] with [ReleasePlan.state] = [ReleaseState.Ready],
 * [ReleasePlan.typeOfRun] = [TypeOfRun.SIMULATION], an empty list for [ReleasePlan.warnings], [ReleasePlan.infos]
 * and an empty map for [ReleasePlan.config].
 */
fun createReleasePlanWithDefaults(
    publishId: String,
    rootProjectId: ProjectId,
    projects: Map<ProjectId, Project>,
    submodulesOfProject: Map<ProjectId, Set<ProjectId>>,
    dependents: Map<ProjectId, Set<ProjectId>>
) = ReleasePlan(
    publishId,
    ReleaseState.Ready,
    TypeOfRun.SIMULATION,
    rootProjectId,
    projects,
    submodulesOfProject,
    dependents,
    listOf(),
    listOf(),
    mapOf()
)
