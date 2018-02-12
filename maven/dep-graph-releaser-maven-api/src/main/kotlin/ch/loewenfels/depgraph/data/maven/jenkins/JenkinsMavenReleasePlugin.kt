package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState

/**
 * Represents a command to execute the maven-release-plugin of a Jenkins Job for a project.
 */
data class JenkinsMavenReleasePlugin(
    override val state: CommandState
) : Command
