package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [identifier] and a [version].
 *
 * A [Project] has a list of [outerCommands] whereas those [Command]s have a dependency to other commands outside of
 * this project. It also has [innerCommands] and those commands have a dependency to other commands within the project.
 */
data class Project(
    val identifier: Id,
    val version: String,
    val outerCommands: List<Command>,
    val innerCommands: List<Command>
)

