package ch.loewenfels.depgraph.data.maven

import ch.loewenfels.depgraph.data.Id

/**
 * Represents an [ProjectId] for maven projects composed by [groupId]:[artifactId]:[packaging]:[classifier].
 *
 * Have a look at [Apache's Documentation](https://maven.apache.org/pom.html#Maven_Coordinates) for further information.
 *
 * @param groupId The `groupId of the maven project.
 * @param artifactId The artifactId of the maven project.
 * @param packaging the packaging of the maven project; "jar" per default.
 * @param classifier An optional classifier of the maven project; empty per default which means none.
 */
data class MavenProjectId(
    val groupId: String,
    val artifactId: String,
    override val version: String,
    val packaging: String = "jar",
    val classifier: String = "") : Id {

    /**
     * Returns [groupId]:[artifactId]:[packaging]:[classifier]
     * @return The identifier of the maven artifact (without its [version])
     */
    override val identifier = "$groupId:$artifactId:$packaging:$classifier"
}
