package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ReleaseCommand

data class JenkinsMultiMavenReleasePlugin(
    override val state: CommandState,
    override val nextDevVersion: String
) : M2ReleaseCommand {
    override fun asNewState(newState: CommandState) = JenkinsMultiMavenReleasePlugin(newState, nextDevVersion)
}
