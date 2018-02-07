package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState

/**
 * Represents a command to execute the maven-release-plugin of a Jenkins Job for a project.
 */
class JenkinsMavenReleasePlugin(
    override val id: Int,
    override val state: CommandState,
    override val dependent: Set<Int>
) : Command
