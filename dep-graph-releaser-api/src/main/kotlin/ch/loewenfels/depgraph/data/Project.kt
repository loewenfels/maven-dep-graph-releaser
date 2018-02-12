package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [id].
 *
 * Moreover, a [Project] defines a list of [commands] to carry out
 * and a list of [dependents] (dependent projects) which shall be triggered once this project is processed.
 */
data class Project(
    val id: ProjectId,
    val commands: List<Command>,
    val dependents: List<Project>
)
