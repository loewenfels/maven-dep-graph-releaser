package ch.loewenfels.depgraph.data

/**
 * Represents an identification of a project built up by an [identifier] and a [version].
 *
 * The [identifier] is typically composed by different parts, have a look at `MavenProjectId` for an example.
 */
interface ProjectId {
    /**
     * The identifier which uniquely identifies a project together with its [version].
     */
    val identifier: String
    /**
     * The current version of the project.
     */
    val version: String
}

