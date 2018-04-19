package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.gui.Gui.Companion.stateToCssClass
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import kotlin.dom.addClass
import kotlin.dom.removeClass

class Releaser(
    private val jenkinsUrl: String,
    usernameToken: UsernameToken,
    private val modifiableJson: ModifiableJson
) {
    private val jobExecutor = JobExecutor(jenkinsUrl, usernameToken)

    fun release() {
        val releasePlan = deserialize(modifiableJson.json)
        checkConfig(releasePlan)


        val itr = releasePlan.iterator().toPeekingIterator()
        var level: Int
        var notOneReleaseSucceeded: Boolean
        while (itr.hasNext()) {
            notOneReleaseSucceeded = true
            val paramObject = ParamObject(releasePlan, itr.next())
            level = paramObject.project.level

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

    private fun checkConfig(releasePlan: ReleasePlan) {
        val config = releasePlan.config
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.REMOTE_REGEX)
        requireConfigEntry(config, ConfigKey.REMOTE_JOB)
    }

    private fun requireConfigEntry(config: Map<ConfigKey, String>, key: ConfigKey) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    private fun triggerCommandsOfProject(paramObject: ParamObject): Boolean {
        var notOneReleaseSucceeded = true
        val commands = paramObject.project.commands
        val size = commands.size
        for (index in (size - 1)..0) {
            val command = commands[index]
            val isReady = command.state === CommandState.Ready
            if (!isReady && command.state !is CommandState.Waiting) {
                if (command is ReleaseCommand && command.state == CommandState.Succeeded) {
                    notOneReleaseSucceeded = false
                }
                //no need to have a look at previous commands if this one is neither ready nor waiting
                break
            } else if (isReady) {
                triggerCommand(paramObject, command, index)
            }
        }
        return notOneReleaseSucceeded
    }

    private fun triggerCommand(paramObject: ParamObject, command: Command, index: Int) {
        when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(paramObject, command, index)
            is M2ReleaseCommand -> triggerRelease(paramObject, command, index)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(paramObject: ParamObject, command: JenkinsUpdateDependency, index: Int) {
        changeCursorToProgress()
        val project = paramObject.project
        val jobUrl = "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.UPDATE_DEPENDENCY_JOB)}"
        val jobName = "update dependency of $project.id"
        val params = createUpdateDependencyParams(paramObject, command)
        triggerJob(jobUrl, jobName, params, project, index)
    }

    private fun triggerJob(jobUrl: String, jobName: String, params: String, project: Project, index: Int) {
        displayJobState(project, index, stateToCssClass(CommandState.Ready), "queueing", "Currently queueing the job")
        //jobExecutor.trigger(jobUrl, jobName, params, { buildNumber ->
        sleep(1000) {
            //TODO add a link to the job?
            displayJobState(project, index, "queueing", stateToCssClass(CommandState.InProgress), "Job is running")

            //TODO we need to update the release json via publisher
        }.then {
            sleep(1000) {
                displayJobState(
                    project, index, CommandState.InProgress, CommandState.Succeeded, "Job completed successfully"
                )
            }
        }.finally { it: Any? ->
            changeCursorBackToNormal()
            it != null
        }
    }

    private fun displayJobState(
        project: Project,
        index: Int,
        currentState: CommandState,
        newState: CommandState,
        title: String
    ) = displayJobState(project, index, stateToCssClass(currentState), stateToCssClass(newState), title)

    private fun displayJobState(
        project: Project,
        index: Int,
        cssClassToRemove: String,
        cssClassToAdd: String,
        title: String
    ) {
        val commandId = Gui.getCommandId(project, index)
        val command = elementById(commandId)
        command.removeClass(cssClassToRemove)
        command.addClass(cssClassToAdd)
        elementById("$commandId:state").title = title
    }

    private fun createUpdateDependencyParams(paramObject: ParamObject, command: JenkinsUpdateDependency): String {
        val dependency = paramObject.releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return "pathToProject=${paramObject.project.relativePath}" +
            "&groupId=${dependencyMavenProjectId.groupId}" +
            "&artifactId=${dependencyMavenProjectId.artifactId}" +
            "&newVersion=${dependency.releaseVersion}"
    }

    private fun triggerRelease(paramObject: ParamObject, command: M2ReleaseCommand, index: Int) {
        val regex = Regex(paramObject.getConfig(ConfigKey.REMOTE_REGEX))
        val jobUrl = if (regex.matches(paramObject.project.id.identifier)) {
            "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.REMOTE_JOB)}"
        } else {
            "$jenkinsUrl/job/${(paramObject.project.id as MavenProjectId).artifactId}"
        }
        val jobName = "release ${paramObject.project.id}"
        val params = createReleaseParams(paramObject, command)
        triggerJob(jobUrl, jobName, params, paramObject.project, index)
        showInfo("Release triggering not yet implemented, but would trigger: ${paramObject.project.id.identifier}")
    }

    private fun createReleaseParams(paramObject: Releaser.ParamObject, command: M2ReleaseCommand): String {
        //TODO create release params
        return ""
    }

    data class ParamObject(
        val releasePlan: ReleasePlan,
        val project: Project
    ) {
        constructor(paramObject: ParamObject, newProject: Project)
            : this(paramObject.releasePlan, newProject)

        fun getConfig(configKey: ConfigKey): String {
            return releasePlan.config[configKey] ?: throw IllegalArgumentException("unknown config key: $configKey")
        }
    }
}
