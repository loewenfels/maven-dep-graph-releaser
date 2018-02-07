package ch.loewenfels.depgraph.data

/**
 * Represents an identification of a project built up by an [identifier] and a [version].
 *
 * The [identifier] is typically composed by different parts, have a look at [MavenId] for an example.
 */
interface Id {
    /**
     * The version of the project
     */
    val version: String
    /**
     * The identifier which uniquely identifies a project together with its [version].
     */
    val identifier: String

    /**
     * Returns the unique identity of this project.
     * @return The following: "[identifier]:[version]"
     */
    fun getIdentity() = "$identifier:$version"

}

/**
 * Represents an [Id] for maven projects composed by [groupId]:[artifactId]:[packaging]:[classifier].
 *
 * Have a look at [Apache's Documentation](https://maven.apache.org/pom.html#Maven_Coordinates) for further information.
 *
 * @param groupId The `groupId of the maven project.
 * @param artifactId The artifactId of the maven project.
 * @param packaging the packaging of the maven project; "jar" per default.
 * @param classifier An optional classifier of the maven project; empty per default which means none.
 */
data class MavenId(
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
