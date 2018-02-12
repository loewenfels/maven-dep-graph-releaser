package ch.loewenfels.depgraph.data.maven

import ch.loewenfels.depgraph.data.ProjectId

/**
 * Represents a [ProjectId] for maven projects composed by [groupId]:[artifactId] and its [version].
 *
 * `packaging` and `classifier` is neglected on purpose. For instance, having a tests jar is seen as an additional
 * artifact during a release. We assume that such an artifact is never released alone but always together with all other
 * artifacts (otherwise it would have been released under a different artifactId and not just distinguished in its
 * classifier). Open an issue if you require it.
 *
 * Have a look at [Apache's Documentation](https://maven.apache.org/pom.html#Maven_Coordinates) for further information.
 *
 * @param groupId The `groupId of the maven project.
 * @param artifactId The artifactId of the maven project.
 */
data class MavenProjectId(
    val groupId: String,
    val artifactId: String,
    override val version: String) : ProjectId {

    /**
     * Returns [groupId]:[artifactId]
     * @return The identifier of the maven artifact (without its [version])
     */
    override val identifier = "$groupId:$artifactId"
}
