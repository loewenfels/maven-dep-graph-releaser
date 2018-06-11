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
     * Indicates whether the dependency version was defined by the dependent project or by another artifact (such as
     * the parent pom, an import bom file etc.)
     */
    val isDependencyVersionSelfManaged: Boolean
)
