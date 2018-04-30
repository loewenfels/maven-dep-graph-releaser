package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsCommand
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand

object ChangeApplier {

    fun createReleasePlanJsonWithChanges(json: String): Pair<Boolean, String> {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(json)
        val changed = applyChanges(releasePlanJson)
        val newJson = JSON.stringify(releasePlanJson)
        return changed to newJson
    }

    private fun applyChanges(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false

        changed = changed or replacePublishIdIfChanged(releasePlanJson)

        releasePlanJson.projects.forEach { project ->
            val mavenProjectId = deserializeProjectId(project.id)
            changed = changed or replaceReleaseVersionIfChanged(project, mavenProjectId)

            project.commands.forEachIndexed { index, command ->
                changed = changed or
                    replaceStateIfChanged(command, mavenProjectId, index) or
                    replaceFieldsIfChanged(command, mavenProjectId, index)
            }
        }

        changed = changed or replaceConfigEntriesIfChanged(releasePlanJson)
        return changed
    }

    private fun replacePublishIdIfChanged(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false
        val input = getTextField(Gui.PUBLISH_ID)
        if (releasePlanJson.publishId != input.value) {
            check(input.value.isNotBlank()) {
                "An empty or blank PublishId is not allowed"
            }
            releasePlanJson.publishId = input.value
            changed = true
        }
        return changed
    }


    private fun replaceConfigEntriesIfChanged(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false
        releasePlanJson.config.forEach { arr ->
            if (arr.size != 2) return@forEach

            val input = getTextField("config-${arr[0]}")
            if (arr[1] != input.value) {
                arr[1] = input.value
                changed = true
            }
        }
        return changed
    }


    private fun replaceReleaseVersionIfChanged(project: ProjectJson, mavenProjectId: ProjectId): Boolean {
        val input = getTextFieldOrNull("${mavenProjectId.identifier}:releaseVersion")
        if (input != null && project.releaseVersion != input.value) {
            check(input.value.isNotBlank()) {
                "An empty or blank Release Version is not allowed"
            }
            project.releaseVersion = input.value
            return true
        }
        return false
    }

    private fun replaceStateIfChanged(
        genericCommand: GenericType<Command>,
        mavenProjectId: ProjectId,
        index: Int
    ): Boolean {
        val command = genericCommand.p
        val previousState = deserializeState(command)
        val newState = Gui.getCommandState(mavenProjectId, index)
        if (previousState::class != newState::class) {
            val stateObject = js("({})")
            stateObject.state = newState::class.simpleName
            if (newState is CommandState.Deactivated) {
                stateObject.previous = command.state
            }
            command.asDynamic().state = stateObject
            return true
        }
        if (previousState is CommandState.Waiting && newState is CommandState.Waiting && previousState.dependencies.size != newState.dependencies.size) {
            /* state has to be put in the following structure
            "state": {
                "state": "Waiting",
                "dependencies": [
                    {
                        "t": "ch.loewenfels.depgraph.data.maven.MavenProjectId",
                        "p": {
                            "groupId": "com.example",
                            "artifactId": "artifact"
                        }
                    }
                ]
            },
            */
            val newDependencies = newState.dependencies.map {
                when (it) {
                    is MavenProjectId -> {
                        val entry = js("({})")
                        entry.t = MAVEN_PROJECT_ID
                        val p = js("({})")
                        p.groupId = it.groupId
                        p.artifactId = it.artifactId
                        entry.p = p
                        entry.unsafeCast<GenericMapEntry<String, ProjectId>>()
                    }
                    else -> throw UnsupportedOperationException("$it is not supported.")
                }
            }
            command.state.asDynamic().dependencies = newDependencies.toTypedArray()
        }
        return false
    }

    private fun replaceFieldsIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        return when (command.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN, JENKINS_MULTI_MAVEN_RELEASE_PLUGIN -> {
                replaceNextVersionIfChanged(command.p, mavenProjectId, index) or
                    replaceBuildUrlIfChanged(command.p, mavenProjectId, index)
            }
            JENKINS_UPDATE_DEPENDENCY -> replaceBuildUrlIfChanged(command.p, mavenProjectId, index)
            else -> throw UnsupportedOperationException("${command.t} is not supported.")
        }
    }

    private fun replaceNextVersionIfChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val m2Command = command.unsafeCast<M2ReleaseCommand>()
        val input = getTextField(Gui.getCommandId(mavenProjectId, index) + Gui.NEXT_DEV_VERSION_SUFFIX)
        if (m2Command.nextDevVersion != input.value) {
            check(input.value.isNotBlank()) {
                "An empty or blank Next Dev Version is not allowed"
            }
            m2Command.asDynamic().nextDevVersion = input.value
            return true
        }
        return false
    }

    private fun replaceBuildUrlIfChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val jenkinsCommand = command.unsafeCast<JenkinsCommand>()
        val guiCommand = Gui.getCommand(mavenProjectId, index)

        val newBuildUrl = guiCommand.asDynamic().buildUrl as? String
        if (newBuildUrl != null && jenkinsCommand.buildUrl != newBuildUrl) {
            jenkinsCommand.asDynamic().buildUrl = newBuildUrl
            return true
        }
        return false
    }
}
