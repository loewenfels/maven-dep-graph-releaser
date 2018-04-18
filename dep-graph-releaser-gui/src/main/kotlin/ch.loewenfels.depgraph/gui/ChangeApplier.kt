package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson

object ChangeApplier {

    fun createReleasePlanJsonWithChanges(json: String): Pair<Boolean, String> {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(json)
        val changed = applyChanges(releasePlanJson)
        val newJson = JSON.stringify(releasePlanJson)
        return changed to newJson
    }

    private fun applyChanges(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false
        releasePlanJson.projects.forEach { project ->
            val mavenProjectId = createProjectId(project.id)
            val releaseVersionChanged = replaceReleaseVersionIfChanged(project, mavenProjectId)
            changed = changed || releaseVersionChanged

            project.commands.forEachIndexed { index, command ->
                val stateChanged = replaceStateIfChanged(command, mavenProjectId, index)
                val fieldsChanged = replaceFieldsIfChanged(command, mavenProjectId, index)
                changed = changed || stateChanged || fieldsChanged
            }
        }
        return changed
    }


    private fun replaceReleaseVersionIfChanged(project: ProjectJson, mavenProjectId: ProjectId): Boolean {
        val input = getTextFieldOrNull("${mavenProjectId.identifier}:releaseVersion")
        if (input != null && project.releaseVersion != input.value) {
            project.asDynamic().releaseVersion = input.value
            return true
        }
        return false
    }

    private fun replaceStateIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        val commandStateJson = command.p.state.unsafeCast<CommandStateJson>()
        val state = commandStateJson.state.unsafeCast<String>()
        if (state != CommandStateJson.State.Deactivated.name && state != CommandStateJson.State.Disabled.name) {
            val checkbox = getCheckbox("${mavenProjectId.identifier}:$index:disable")
            if (!checkbox.checked) {
                val previous = JSON.parse<CommandStateJson>(JSON.stringify(commandStateJson))
                commandStateJson.asDynamic().state = CommandStateJson.State.Deactivated.name
                commandStateJson.asDynamic().previous = previous
                return true
            }
        }
        return false
    }

    private fun replaceFieldsIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        return when (command.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN, JENKINS_MULTI_MAVEN_RELEASE_PLUGIN ->
                checkIfNextVersionChanged(command.p, mavenProjectId, index)
            JENKINS_UPDATE_DEPENDENCY -> false //nothing to do
            else -> throw UnsupportedOperationException("${command.t} is not supported.")
        }
    }

    private fun checkIfNextVersionChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val m2Command = command.unsafeCast<M2ReleaseCommand>()
        val input = getTextField("${mavenProjectId.identifier}:$index:nextDevVersion")
        if (m2Command.nextDevVersion != input.value) {
            m2Command.asDynamic().nextDevVersion = input.value
            return true
        }
        return false
    }
}
