package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.maven.MavenProjectId

/**
 * Represents the command to update the dependency to [projectId] for a project via a jenkins job.
 * The job has to update the dependencies and commit the changes.
 */
data class JenkinsUpdateDependency(
    override val state: CommandState,
    val projectId: MavenProjectId
) : Command
