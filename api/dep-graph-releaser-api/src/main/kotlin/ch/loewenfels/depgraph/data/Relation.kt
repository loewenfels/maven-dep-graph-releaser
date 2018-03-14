package ch.loewenfels.depgraph.data

data class Relation<out T : ProjectId>(
    /**
     * The [ProjectId] of the dependent project
     */
    val id: T,
    /**
     * The current version of the dependent project
     */
    val currentVersion: String,
    /**
     * The dependency version defined by the dependent project, might be null in case the version is managed
     * by a parent, a bom file or in another way
     */
    val dependencyVersion: String?
)
