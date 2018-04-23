package ch.loewenfels.depgraph.data.maven.jenkins

import ch.loewenfels.depgraph.data.ReleaseCommand

/**
 * Marker interface for [ReleaseCommand] which involve the M2 Release Plugin.
 */
interface M2ReleaseCommand: ReleaseCommand, JenkinsCommand {
    val nextDevVersion: String
}
