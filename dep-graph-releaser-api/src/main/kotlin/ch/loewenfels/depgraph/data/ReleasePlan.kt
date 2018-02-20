package ch.loewenfels.depgraph.data

/**
 * Represents a release plan where the [Project] with the given [rootProjectId] shall be released.
 *
 * Dependent projects (incl. transitive) of the root project as well as the root project itself
 * are stored in [projects]. The relation between the projects are stored in [dependents]
 * where the key represents the dependency and the value the dependent.
 */
data class ReleasePlan(
    val rootProjectId: ProjectId,
    val projects: Map<ProjectId, Project>,
    val dependents: Map<ProjectId, Set<ProjectId>>
)
