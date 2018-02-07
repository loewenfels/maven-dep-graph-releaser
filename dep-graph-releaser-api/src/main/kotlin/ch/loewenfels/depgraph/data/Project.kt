package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [id].
 *
 * A [Project] has a list of [outerCommands] whereas those [Command]s have a dependency to other commands outside of
 * this project. It also has [innerCommands] and those commands have a dependency to other commands within the project.
 */
data class Project(
    val id: ProjectId,
    val outerCommands: List<Command>,
    val innerCommands: List<Command>
)

