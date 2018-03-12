package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleaseCommand

/**
 * Represents the command to execute the M2 Release Plugin for a Jenkins job (executes the maven-release-plugin).
 *
 * It carries the [nextDevVersion], the next version (also required for the plugin) is already contain in the [Project]
 * for which this command shall be executed.
 */
data class JenkinsMavenReleasePlugin(
    override val state: CommandState,
    val nextDevVersion: String
) : ReleaseCommand {
    override fun asNewState(newState: CommandState) = JenkinsMavenReleasePlugin(newState, nextDevVersion)
}
