package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.maven.MavenProjectId

/**
 * Represents the command to update the dependency to [projectId] for a project via a jenkins job.
 * The job has to update the dependencies and commit the changes.
 */
data class JenkinsUpdateDependency(
    override var state: CommandState,
    val projectId: MavenProjectId,
    override val buildUrl: String? = null
) : JenkinsCommand {
    override fun asNewState(newState: CommandState) = JenkinsUpdateDependency(newState, projectId, buildUrl)
}
