package ch.loewenfels.depgraph.data

/**
 * Represents a project which shall be released, identified by an [id].
 *
 * Moreover, a [Project] defines its [currentVersion], a [releaseVersion] (which shall be used when it is released),
 * a list of [commands] to carry out.
 */
data class Project(
    val id: ProjectId,
    val isSubmodule: Boolean,
    val currentVersion: String,
    val releaseVersion: String,
    /**
     * The number of edges between this project an the root project of the [ReleasePlan].
     * Or in other words, the number of dependents + 1 between this project and the root project.
     * Only the root project itself has level 0.
     *
     * In case there are several paths to the root project, then always the highest number should be returned.
     * For instance, if we have the following situation:
     *
     * ROOT -> A -> B
     * ROOT -> B
     *
     * Then B has level 2 even though it is a direct dependent of ROOT.
     * To be complete, A has level 1 and ROOT has level 0.
     */
    val level: Int,
    val commands: List<Command>,
    /**
     * The relative path to this project from the root directory of the analysis.
     */
    val relativePath: String
) {
    /**
     * Copy constructor for the use case, that only the commands of the project need to change.
     */
    constructor(project: Project, commands: List<Command>) :
        this(
            project.id,
            project.isSubmodule,
            project.currentVersion,
            project.releaseVersion,
            project.level,
            commands,
            project.relativePath
        )

    /**
     * Copy constructor for the use case, that only the level of the projects needs to change.
     */
    constructor(project: Project, level: Int) :
        this(
            project.id,
            project.isSubmodule,
            project.currentVersion,
            project.releaseVersion,
            level,
            project.commands,
            project.relativePath
        )
}
