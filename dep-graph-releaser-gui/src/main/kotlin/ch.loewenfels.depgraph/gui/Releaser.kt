package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.Config
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator

class Releaser(
    private val jenkinsUrl: String,
    private val usernameToken: UsernameToken,
    private val modifiableJson: ModifiableJson
) {
    fun release() {
        val releasePlan = deserialize(modifiableJson.json)
        val config = checkConfig(releasePlan)

        val itr = releasePlan.iterator().toPeekingIterator()
        var level: Int
        var notOneReleaseSucceeded: Boolean
        while (itr.hasNext()) {
            notOneReleaseSucceeded = true
            val project = itr.next()
            level = project.level
            notOneReleaseSucceeded = notOneReleaseSucceeded or triggerCommandsOfProject(project, config)

            while (itr.hasNextOnTheSameLevel(level)) {
                val nextProject = itr.next()
                notOneReleaseSucceeded = notOneReleaseSucceeded or triggerCommandsOfProject(nextProject, config)
            }

            if (notOneReleaseSucceeded) {
                //we don't have to go on to the next level if there isn't one released project on this level
                break
            }
        }
    }

    private fun checkConfig(releasePlan: ReleasePlan): Map<String, String> {
        val config = releasePlan.config.associateBy({ it.first }, { it.second })
        requireConfigEntry(config, Config.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, Config.REMOTE_REGEX)
        requireConfigEntry(config, Config.REMOTE_JOB)
        requireConfigEntry(config, Config.REMOTE_JOB)
        return config
    }

    private fun requireConfigEntry(config: Map<String, String>, key: String) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    private fun triggerCommandsOfProject(project: Project, config: Map<String, String>): Boolean {
        var notOneReleaseSucceeded = true
        val commands = project.commands
        val size = commands.size
        for (i in (size - 1)..0) {
            val command = commands[i]
            if (command.state != CommandState.Ready && command.state !is CommandState.Waiting) {
                if (command is ReleaseCommand && command.state == CommandState.Succeeded) {
                    notOneReleaseSucceeded = false
                }
                //no need to have a look at previous commands if this one is neither ready nor waiting
                break
            }
            trigger(project, command, config)

        }
        return notOneReleaseSucceeded
    }

    private fun trigger(project: Project, command: Command, config: Map<String, String>) {
        when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(project, command, config)
            is M2ReleaseCommand -> triggerRelease(project, command, config)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(project: Project, command: JenkinsUpdateDependency, config: Map<String, String>) {
        showInfo("Update dependency triggering not yet implemented, but would trigger: ${project.id.identifier}")
    }

    private fun triggerRelease(project: Project, command: M2ReleaseCommand, config: Map<String, String>) {
        showInfo("Release triggering not yet implemented, but would trigger: ${project.id.identifier}")

    }
}
