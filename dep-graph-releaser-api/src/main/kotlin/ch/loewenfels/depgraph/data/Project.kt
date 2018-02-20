package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [id].
 *
 * Moreover, a [Project] defines its [currentVersion], a [releaseVersion] (which shall be used when it is released),
 * a list of [commands] to carry out.
 */
data class Project(
    val id: ProjectId,
    val currentVersion: String,
    val releaseVersion: String,
    val commands: List<Command>
)
