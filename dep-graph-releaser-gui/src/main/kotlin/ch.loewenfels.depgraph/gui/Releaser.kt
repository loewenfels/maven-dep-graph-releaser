package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.Config
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator

class Releaser(
    private val jenkinsUrl: String,
    usernameToken: UsernameToken,
    private val modifiableJson: ModifiableJson
) {
    private val jobExecutor = JobExecutor(jenkinsUrl, usernameToken)

    fun release() {
        val releasePlan = deserialize(modifiableJson.json)
        val config = checkConfig(releasePlan)


        val itr = releasePlan.iterator().toPeekingIterator()
        var level: Int
        var notOneReleaseSucceeded: Boolean
        while (itr.hasNext()) {
            notOneReleaseSucceeded = true
            val paramObject = ParamObject(releasePlan, config, itr.next())
            val project = itr.next()
            level = project.level

            notOneReleaseSucceeded = notOneReleaseSucceeded or triggerCommandsOfProject(paramObject)

            while (itr.hasNextOnTheSameLevel(level)) {
                val nextParamObject = ParamObject(paramObject, itr.next())
                notOneReleaseSucceeded = notOneReleaseSucceeded or triggerCommandsOfProject(nextParamObject)
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

    private fun triggerCommandsOfProject(paramObject: ParamObject): Boolean {
        var notOneReleaseSucceeded = true
        val commands = paramObject.project.commands
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
            trigger(paramObject, command)

        }
        return notOneReleaseSucceeded
    }

    private fun trigger(paramObject: ParamObject, command: Command) {
        when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(paramObject, command)
            is M2ReleaseCommand -> triggerRelease(paramObject, command)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(paramObject: ParamObject, command: JenkinsUpdateDependency) {
        changeCursorToProgress()
        val identifier = paramObject.project.id.identifier
        elementById(identifier)
        val jobUrl = "$jenkinsUrl/${paramObject.config[Config.UPDATE_DEPENDENCY_JOB]}"
        val jobName = "update dependency of $identifier"
        val params = createUpdateDependencyParams(paramObject, command)
        jobExecutor.trigger(jobUrl, jobName, params, { buildNumber ->
            //TODO we need to update the release json via publisher

        }).finally { it: Any? ->
            changeCursorBackToNormal()
            it != null
        }
    }

    private fun createUpdateDependencyParams(
        paramObject: ParamObject,
        command: JenkinsUpdateDependency
    ): String {
        val dependency = paramObject.releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return "pathToProject=${paramObject.project.relativePath}" +
            "&groupId=${dependencyMavenProjectId.groupId}" +
            "&artifactId=${dependencyMavenProjectId.artifactId}" +
            "&newVersion=${dependency.releaseVersion}"
    }

    private fun triggerRelease(paramObject: ParamObject, command: M2ReleaseCommand) {
        showInfo("Release triggering not yet implemented, but would trigger: ${paramObject.project.id.identifier}")

    }

    data class ParamObject(
        val releasePlan: ReleasePlan,
        val config: Map<String, String>,
        val project: Project
    ) {
        constructor(paramObject: ParamObject, newProject: Project)
            : this(paramObject.releasePlan, paramObject.config, newProject)
    }
}
