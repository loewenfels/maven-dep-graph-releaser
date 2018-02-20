package ch.loewenfels.depgraph.data

/**
 * Represents an identification of a project represented by [identifier].
 *
 * The [identifier] is typically composed by different parts, e.g., `MavenProjectId` consists of `groupId`
 * and `artifactId`.
 *
 * Notice that the version of a project or the current git-hash or similar information does not belong to a project id
 * because they identify a certain point in time of the project but not the project as such.
 */
interface ProjectId {
    /**
     * The identifier which uniquely identifies a project.
     */
    val identifier: String
}

