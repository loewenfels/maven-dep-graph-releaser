package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [id].
 *
 * Moreover, a [Project] defines a [newVersion] (which shall be used when it is released), a list of [commands] to
 * carry out and a list of [dependents] (dependent projects) which shall be triggered once this project is processed.
 */
data class Project(
    val id: ProjectId,
    val newVersion: String,
    val commands: List<Command>,
    val dependents: List<Project>
)